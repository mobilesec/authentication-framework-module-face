package at.usmile.panshot.util;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.highgui.Highgui;
import org.opencv.imgproc.Imgproc;

import android.content.Context;
import android.content.res.Resources.NotFoundException;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.os.Environment;
import android.util.Log;
import android.widget.Toast;
import at.usmile.auth.module.face.R;
import at.usmile.panshot.PanshotImage;
import at.usmile.panshot.User;
import at.usmile.panshot.exception.SdCardNotAvailableException;
import at.usmile.panshot.recognition.RecognitionModule;
import at.usmile.panshot.recognition.svm.SvmClassifier;
import at.usmile.panshot.sensor.SensorComponent;
import at.usmile.panshot.sensor.SensorValues;
import at.usmile.panshot.sensor.SensorValues.Observation;
import at.usmile.tuple.GenericTuple2;
import au.com.bytecode.opencsv.CSVReader;

// TODO extract all UI stuff to FaceModuleUtil

/**
 * Tool for access to file system stuff (loading and saving face auth. data)
 * 
 * @author Rainhard Findling
 * @date 7 Apr 2015
 * @version 1
 */
public class DataUtil {

	private static final String TAG = DataUtil.class.getSimpleName();

	/**
	 * in our panshot data folder: filters for dirctories that contain user
	 * (class) face data.
	 */
	protected static FileFilter PANSHOT_USER_FOLDER_FILE_FILTER = new FileFilter() {
		@Override
		public boolean accept(File file) {
			// we use directories that start with a letter
			return file.isDirectory() && !file.getName().startsWith("_");
		}
	};

	/**
	 * Creates a new user object that can e.g. be connected to newly recorded
	 * face data. just by this generation the user does not get connected to
	 * anything - we just create the user object here.
	 * 
	 * @param name
	 * @return
	 */
	public static User createNewUser(String name) {
		String id = Long.toString(Math.abs(new Random(System.currentTimeMillis()).nextLong()));
		return new User(id, name);
	}

	/**
	 * Load existing users based on user data stored on the file system (no
	 * authentication data is loaded).
	 * 
	 * @param _context
	 * @return
	 * @throws NotFoundException
	 * @throws IOException
	 */
	public static List<User> loadExistingUsers(Context _context) throws NotFoundException, IOException {
		if (!isSdCardAvailableRW()) {
			throw new SdCardNotAvailableException();
		}
		File mediaDir = getMediaStorageDirectory(_context.getResources().getString(R.string.app_media_directory_name));
		// load from fs/db
		File[] files = mediaDir.listFiles(PANSHOT_USER_FOLDER_FILE_FILTER);
		List<User> list = new ArrayList<User>();
		for (File inFile : files) {
			if (inFile.isDirectory()) {
				// split id / name
				String name = inFile.getName().split("_")[0];
				String id = inFile.getName().split("_")[1];
				list.add(new User(id, name));
			}
		}
		return list;
	}

	/**
	 * Save the given panshot images to the FS.
	 * 
	 * @param _context
	 * @param _currentUser
	 * @param _images
	 * @param csvFileExtension
	 * @param _sessionId
	 * @param _useFrontalOnly
	 * @param _panshotTargetMinAngle
	 */
	public static void savePanshotImages(Context _context, User _currentUser, List<PanshotImage> _images,
			String csvFileExtension, String _sessionId, boolean _useFrontalOnly, float _panshotTargetMinAngle) {
		// store images to sd card
		if (!isSdCardAvailableRW()) {
			Toast.makeText(_context, _context.getResources().getString(R.string.sd_card_not_available), Toast.LENGTH_SHORT)
					.show();
			return;
		}
		File mediaDir = null;
		try {
			// ensure access to media directory
			mediaDir = getMediaStorageDirectory(_context.getResources().getString(R.string.app_media_directory_name));
		} catch (NotFoundException e) {
			e.printStackTrace();
			Toast.makeText(_context, _context.getResources().getString(R.string.media_directory_not_found_exception),
					Toast.LENGTH_LONG).show();
			return;
		} catch (IOException e) {
			e.printStackTrace();
			Toast.makeText(_context, _context.getResources().getString(R.string.media_directory_cannot_be_created),
					Toast.LENGTH_LONG).show();
			return;
		}
		Log.d(TAG, "mediaDir=" + mediaDir.getAbsolutePath());
		// ensure access to user's directory
		File userDir = null;
		User user = _currentUser;
		try {
			userDir = ensureDirectoryExists(mediaDir, user.getFoldername());
		} catch (IOException e) {
			e.printStackTrace();
			Toast.makeText(_context, _context.getResources().getString(R.string.user_directory_cannot_be_created),
					Toast.LENGTH_LONG).show();
			return;
		}
		File panshotDir = null;
		String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
		try {
			panshotDir = ensureDirectoryExists(userDir, timestamp);
		} catch (IOException e) {
			e.printStackTrace();
			Toast.makeText(_context, _context.getResources().getString(R.string.panshot_directory_cannot_be_created),
					Toast.LENGTH_LONG).show();
			return;
		}

		// sensor data for images
		StringBuilder textfileSb = new StringBuilder(
				"sessionid,filename,subjectname,subjectid,panshotid,imagenr,timestamp,angle1,angle2,angle3,acc1,acc2,acc3,light\n");
		float minAngle = Float.MAX_VALUE;
		float maxAngle = -Float.MAX_VALUE;
		for (int imageNr = 0; imageNr < _images.size(); imageNr++) {
			PanshotImage image = _images.get(imageNr);
			if (minAngle > image.angleValues[image.rec.angleIndex]) {
				minAngle = image.angleValues[image.rec.angleIndex];
			}
			if (maxAngle < image.angleValues[image.rec.angleIndex]) {
				maxAngle = image.angleValues[image.rec.angleIndex];
			}
			Log.d(TAG, "image " + imageNr + ": " + image.toString() + " has channels=" + image.grayImage.channels() + ", depth="
					+ image.grayImage.depth() + ", type=" + image.grayImage.type());
			String imageNrString = "" + imageNr;
			String filename = "" + user.getId() + "_" + timestamp + "_"
					+ ("00" + imageNrString).substring(imageNrString.length());
			File imageFile = new File(panshotDir, "/" + filename + ".jpg");
			File faceFile = new File(panshotDir, "/" + filename + "_face.jpg");
			try {
				saveMatToJpgFile(imageFile, image.grayImage);
				if (image.grayFace != null) {
					saveMatToJpgFile(faceFile, image.grayFace);
				}
			} catch (IOException e) {
				Toast.makeText(_context, _context.getResources().getString(R.string.image_could_not_be_saved), Toast.LENGTH_LONG)
						.show();
				e.printStackTrace();
				return;
			}
			textfileSb.append(_sessionId + "," + filename + "," + user.getName() + "," + user.getId() + "," + timestamp + ","
					+ imageNr + "," + image.timestamp + "," + image.angleValues[0] + "," + image.angleValues[1] + ","
					+ image.angleValues[2] + "," + (image.accelerationValues == null ? null : image.accelerationValues[0]) + ","
					+ (image.accelerationValues == null ? null : image.accelerationValues[1]) + ","
					+ (image.accelerationValues == null ? null : image.accelerationValues[2]) + ","
					+ (image.light == null ? null : image.light) + "\n");
		}
		// store csv file
		String filename = user.getId() + "_" + timestamp + "_images";
		try {
			saveTextToFile(textfileSb.toString(), panshotDir, filename + csvFileExtension);
		} catch (IOException e) {
			e.printStackTrace();
			Toast.makeText(_context, _context.getResources().getString(R.string.error_could_not_save_metadata_to_file, filename),
					Toast.LENGTH_LONG).show();
		}
		// acceleration and gyroscope sensor series - separate files
		SensorValues sensorValues = SensorComponent.instance().getSensorValues();
		textfileSb = new StringBuilder("angle1,angle2,angle3,timestamp\n");
		for (Observation<Float[]> o : sensorValues.getRotationHistory()) {
			textfileSb.append(o.value[0] + "," + o.value[1] + "," + o.value[2] + "," + o.timestamp + "\n");
		}
		String filenameAngle = user.getId() + "_" + timestamp + "_panshot_angles";
		try {
			saveTextToFile(textfileSb.toString(), panshotDir, filenameAngle + csvFileExtension);
		} catch (IOException e) {
			e.printStackTrace();
			Toast.makeText(_context,
					_context.getResources().getString(R.string.error_could_not_save_metadata_to_file, filenameAngle),
					Toast.LENGTH_LONG).show();
			return;
		}
		textfileSb = new StringBuilder("acc1,acc2,acc3,timestamp\n");
		for (Observation<Float[]> o : sensorValues.getAccValueHistory()) {
			textfileSb.append(o.value[0] + "," + o.value[1] + "," + o.value[2] + "," + o.timestamp + "\n");
		}
		String filenameAcceleration = user.getId() + "_" + timestamp + "_panshot_accelerations";
		try {
			saveTextToFile(textfileSb.toString(), panshotDir, filenameAcceleration + csvFileExtension);
		} catch (IOException e) {
			e.printStackTrace();
			Toast.makeText(_context,
					_context.getResources().getString(R.string.error_could_not_save_metadata_to_file, filenameAcceleration),
					Toast.LENGTH_LONG).show();
			return;
		}
		// store descriptive file which has subjectid etc + link to the two
		// files for easier loading
		textfileSb = new StringBuilder("sessionid,subjectname,subjectid,panshotid,filetype,filename\n");
		textfileSb.append(_sessionId + "," + user.getName() + "," + user.getId() + "," + timestamp + "," + "angle" + ","
				+ filenameAngle + "\n");
		textfileSb.append(_sessionId + "," + user.getName() + "," + user.getId() + "," + timestamp + "," + "acceleration" + ","
				+ filenameAcceleration + "\n");
		filename = user.getId() + "_" + timestamp + "_panshot_files";
		try {
			saveTextToFile(textfileSb.toString(), panshotDir, filename + csvFileExtension);
		} catch (IOException e) {
			e.printStackTrace();
			Toast.makeText(_context, _context.getResources().getString(R.string.error_could_not_save_metadata_to_file, filename),
					Toast.LENGTH_LONG).show();
			return;
		}
		if (_useFrontalOnly) {
			Toast.makeText(_context,
					_context.getResources().getString(R.string.frontalonly_image_saved_successfully, _currentUser.getName()),
					Toast.LENGTH_LONG).show();
			PanshotUtil.playSoundfile(_context, R.raw.pure_bell);
		} else {
			// check if images were sufficient for user feedback
			float totalAngle = maxAngle - minAngle;
			if (totalAngle < _panshotTargetMinAngle) {
				Toast.makeText(
						_context,
						_context.getResources().getString(R.string.panshot_images_saved_angle_too_small,
								(totalAngle / Math.PI * 180f)), Toast.LENGTH_LONG).show();
				PanshotUtil.playSoundfile(_context, R.raw.whisper);
			} else {
				Toast.makeText(
						_context,
						_context.getResources().getString(R.string.panshot_images_saved_successfully, _images.size(),
								(totalAngle / Math.PI * 180f)), Toast.LENGTH_LONG).show();
				PanshotUtil.playSoundfile(_context, R.raw.pure_bell);
			}
		}
	}

	/**
	 * Loads training data from the FS. this contains: all face images and
	 * correlated sensor data and users.
	 * 
	 * @param _context
	 * @param _useFrontalOnly
	 * @param _angleBetweenPerspectives
	 * @return
	 */
	public static List<PanshotImage> loadTrainingData(Context _context, boolean _useFrontalOnly, float _angleBetweenPerspectives) {
		// store images to sd card
		if (!isSdCardAvailableRW()) {
			Toast.makeText(_context, _context.getResources().getString(R.string.sd_card_not_available), Toast.LENGTH_SHORT)
					.show();
			return null;
		}
		File mediaDir = null;
		try {
			// ensure access to media directory
			mediaDir = getMediaStorageDirectory(_context.getResources().getString(R.string.app_media_directory_name));
		} catch (NotFoundException e) {
			e.printStackTrace();
			Toast.makeText(_context, _context.getResources().getString(R.string.media_directory_not_found_exception),
					Toast.LENGTH_LONG).show();
			return null;
		} catch (IOException e) {
			e.printStackTrace();
			Toast.makeText(_context, _context.getResources().getString(R.string.media_directory_cannot_be_created),
					Toast.LENGTH_LONG).show();
			return null;
		}
		Log.d(DataUtil.class.getSimpleName(), "mediaDir=" + mediaDir.getAbsolutePath());
		// load all images of all panshots of all users
		List<PanshotImage> panshotImages = new ArrayList<PanshotImage>();
		File[] userDirectories = mediaDir.listFiles(PANSHOT_USER_FOLDER_FILE_FILTER);
		for (File userDir : userDirectories) {
			String[] userDirNameParts = userDir.getName().split("_");
			User user = new User(userDirNameParts[1], userDirNameParts[0]);
			File[] panshotDirectories = userDir.listFiles();
			for (File panshotDir : panshotDirectories) {
				Map<Integer, PanshotImage> panshotimagesOfThisPanshot = new HashMap<Integer, PanshotImage>();
				// load angle values from CSV file
				File angleCsvFile = panshotDir.listFiles(new FilenameFilter() {
					@Override
					public boolean accept(File _dir, String _filename) {
						return _filename.endsWith("_images.csv.jpg");
					}
				})[0];
				// imagename, panshotimage
				int angleIndex = -1;
				float angleNormalizer = 0;
				try {
					InputStream csvStream = new FileInputStream(angleCsvFile);
					InputStreamReader csvStreamReader = new InputStreamReader(csvStream);
					CSVReader csvReader = new CSVReader(csvStreamReader);
					String[] line;
					// throw away the header
					csvReader.readNext();
					Float[] a1 = null;
					Float[] angleValues = null;
					while ((line = csvReader.readNext()) != null) {
						// "sessionid,filename,subjectname,subjectid,panshotid,imagenr,timestamp,angle1,angle2,angle3,acc1,acc2,acc3,light\n");
						int imageNr = Integer.parseInt(line[5]);
						angleValues = new Float[] { Float.parseFloat(line[7]), Float.parseFloat(line[8]),
								Float.parseFloat(line[9]) };
						if (a1 == null) {
							a1 = angleValues;
						}
						PanshotImage panshotImage = new PanshotImage(null, null, angleValues, null, null, Long.MAX_VALUE);
						panshotImage.rec.user = user;
						panshotimagesOfThisPanshot.put(imageNr, panshotImage);
					}
					csvReader.close(); // yeah, being lazy, we should do
										// that in finally...
					// calculate angle normaliser
					GenericTuple2<Integer, Float[]> angleIndexNormaliser = PanshotUtil.calculateAngleIndexAndNormalizer(a1,
							angleValues);
					angleIndex = angleIndexNormaliser.value1;
					angleNormalizer = angleIndexNormaliser.value2[angleIndex];
					Log.d(TAG, "angleIndex=" + angleIndex + ", angleNormalizer=" + angleNormalizer);
				} catch (IOException e) {
					e.printStackTrace();
					return null;
				}
				// load face images
				File[] images = panshotDir.listFiles(new FilenameFilter() {
					@Override
					public boolean accept(File _dir, String _filename) {
						return _filename.endsWith("_face.jpg");
					}
				});
				for (File image : images) {
					int imageNr = Integer.parseInt(image.getName().replaceAll(".jpg", "").split("_")[3]);
					PanshotImage panshotImage = panshotimagesOfThisPanshot.get(imageNr);
					panshotImage.angleValues[angleIndex] -= angleNormalizer;
					// if we use fronal only: only load is image is
					// "frontal enough"
					if (_useFrontalOnly) {
						if (Math.abs(panshotImage.angleValues[angleIndex]) > _angleBetweenPerspectives / 2.0f) {
							// image is not frontal
							Log.d(TAG, "skipping image for angle " + Arrays.toString(panshotImage.angleValues)
									+ " as it is not frontal (we use frontal only)");
							continue;
						}
					}
					Mat face = Highgui.imread(image.getAbsolutePath());
					// convert to 1 channel = gray
					Mat tmp = new Mat(face.rows(), face.cols(), face.type());
					Imgproc.cvtColor(face, tmp, Imgproc.COLOR_RGBA2GRAY);
					face = tmp;
					panshotImage.grayFace = face;
					panshotImage.rec.angleIndex = angleIndex;
					Log.d(TAG, "loaded image has channels=" + panshotImage.grayFace.channels() + ", depth="
							+ panshotImage.grayFace.depth() + ", type=" + panshotImage.grayFace.type());
					panshotImages.add(panshotImage);
				}
			}
		}
		return panshotImages;
	}

	/**
	 * Serialize a {@link RecognitionModule} to the given file. Attention: not
	 * all SVM data can be serialized (LibSVM provides its own load and save
	 * method for trained SVM modules - you have to call these for each
	 * {@link SvmClassifier} yourself.
	 * 
	 * @param _directory
	 * @param _recognitionModule
	 * @throws IOException
	 */
	public static void serializeRecognitionModule(File _directory, RecognitionModule _recognitionModule) throws IOException {
		ObjectOutputStream objectOutputStream = null;
		if (!_directory.exists()) {
			_directory.mkdir();
		}
		try {
			// serialize recognition module
			objectOutputStream = new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(new File(_directory,
					"recognition_module.ser"))));
			objectOutputStream.writeObject(_recognitionModule);

			// store native data
			for (SvmClassifier c : _recognitionModule.getSvmClassifiers().values()) {
				c.storeNativeData(_directory);
			}

		} finally {
			if (objectOutputStream != null) {
				try {
					objectOutputStream.close();
				} catch (IOException e) {
				}
			}
		}
	}

	/**
	 * 
	 * Deserialize a {@link RecognitionModule} from the given file. Attention:
	 * not all SVM data can be serialized (LibSVM provides its own load and save
	 * method for trained SVM modules - you have to call these for each
	 * {@link SvmClassifier} yourself.
	 * 
	 * @param _directory
	 * @return
	 * @throws NotFoundException
	 * @throws IOException
	 * @throws ClassNotFoundException
	 */
	public static RecognitionModule deserializeRecognitiosModule(File _directory) throws NotFoundException, IOException,
			ClassNotFoundException {
		ObjectInputStream objectInputStream = null;
		RecognitionModule r = null;
		try {
			// deserialize
			objectInputStream = new ObjectInputStream(new BufferedInputStream(new FileInputStream(new File(_directory,
					"recognition_module.ser"))));
			r = (RecognitionModule) objectInputStream.readObject();

			// load ivm native data
			if (r.getSvmClassifiers() != null) {
				for (SvmClassifier c : r.getSvmClassifiers().values()) {
					c.loadNativeData(_directory);
				}
			}
			Log.d(TAG, "deserialized recognitionmodule: " + r);

		} finally {
			if (objectInputStream != null) {
				try {
					objectInputStream.close();
				} catch (IOException e) {
				}
			}
		}
		return r;
	}

	/**
	 * @return true if sd card is available (mounted) with read/write access.
	 */
	public static boolean isSdCardAvailableRW() {
		if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED))
			return true;
		return false;
	}

	/**
	 * Get the application's medie storage folder (create it if it does not
	 * exist).
	 * 
	 * @param name
	 * @return
	 * @throws IOException
	 */
	public static File getMediaStorageDirectory(String name) throws IOException {
		// This location works best if you want the created images to be shared
		// between applications and persist after your app has been uninstalled.
		File mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), name);
		// Create the storage directory if it does not exist
		if (!mediaStorageDir.exists()) {
			Log.i(TAG, "getMediaStorageDirectory(): creating directory " + mediaStorageDir.getAbsolutePath());
			if (!mediaStorageDir.mkdirs()) {
				throw new IOException("Unable to create directory: " + mediaStorageDir);
			}
		}
		return mediaStorageDir;
	}

	public static File ensureDirectoryExists(File parent, String name) throws IOException {
		File dir = new File(parent.getAbsolutePath() + "/" + name);
		if (!dir.exists()) {
			Log.i(TAG, "ensureDirectoryExists(): creating directory " + dir.getAbsolutePath());
			if (!dir.mkdirs()) {
				throw new IOException("Unable to create directory: " + dir);
			}
		}
		return dir;
	}

	/**
	 * Save an Android-Yuv image to a jpg file.
	 * 
	 * @param file
	 * @param image
	 * @throws IOException
	 */
	public static void saveYuvImageToJpgFile(File file, YuvImage image) throws IOException {
		FileOutputStream filecon = new FileOutputStream(file);
		image.compressToJpeg(new Rect(0, 0, image.getWidth(), image.getHeight()), 90, filecon);
		filecon.close();
	}

	/**
	 * Save a OpenCV mat to a png file.
	 * 
	 * @param file
	 * @param matWithFace
	 * @throws IOException
	 */
	public static void saveMatToJpgFile(File file, Mat matWithFace) throws IOException {
		Bitmap bitmap = Bitmap.createBitmap(matWithFace.cols(), matWithFace.rows(), Bitmap.Config.ARGB_8888);
		Utils.matToBitmap(matWithFace, bitmap);
		FileOutputStream out = new FileOutputStream(file);
		bitmap.compress(Bitmap.CompressFormat.PNG, 90, out);
		out.close();
	}

	/**
	 * Saves a bitmap to the sd card.
	 * 
	 * @param _b
	 * @param _folderPath
	 * @param _filename
	 */
	public static void saveBitmapToFs(Bitmap _b, String _folderPath, String _filename) {
		try {
			Log.d(TAG, "trying to save bitmap to " + _folderPath + "/" + _filename);
			File folder = new File(_folderPath);
			folder.mkdirs();

			FileOutputStream out = new FileOutputStream(_folderPath + "/" + _filename);
			_b.compress(Bitmap.CompressFormat.PNG, 90, out);
			Log.d(TAG, "saving bitmap done.");
		} catch (Exception e) {
			Log.w(TAG, "saving bitmap failed.");
			e.printStackTrace();
		}
	}

	/**
	 * Saves text to a textfile. Assumes that the files does not exist yet.
	 * 
	 * @param _string
	 * @param _parentDirectory
	 * @param _filename
	 * @throws IOException
	 */
	public static void saveTextToFile(String _string, File _parentDirectory, String _filename) throws IOException {
		File textFile = new File(_parentDirectory, _filename);
		FileWriter writer = new FileWriter(textFile);
		writer.append(_string);
		writer.flush();
		writer.close();
	}

	/**
	 * Recursively delete a file or folder. Equivalent to unix style rm -r.
	 * 
	 * @param fileOrDirectory
	 * @return
	 */
	public static boolean recursiveDelete(File fileOrDirectory) {
		boolean ok = true;
		if (fileOrDirectory.isDirectory()) {
			for (File child : fileOrDirectory.listFiles()) {
				ok &= recursiveDelete(child);
			}
		}
		return ok & fileOrDirectory.delete();
	}

	/**
	 * Delete previously trained and stored classifiers.
	 * 
	 * @param _context
	 */
	public static void deleteClassifiers(Context _context) {
		try {
			File directory = DataUtil.getMediaStorageDirectory(_context.getResources().getString(
					R.string.app_classifier_directory_name));
			if (!recursiveDelete(directory.getCanonicalFile())) {
				Toast.makeText(_context, "Deleting classifiers failed without providing a detailed cause", Toast.LENGTH_LONG)
						.show();
			}
		} catch (NotFoundException e2) {
			e2.printStackTrace();
			Toast.makeText(_context, "Deleting classifiers failed: " + e2.toString(), Toast.LENGTH_LONG).show();
		} catch (IOException e2) {
			e2.printStackTrace();
			Toast.makeText(_context, "Deletion classifiers failed: " + e2.toString(), Toast.LENGTH_LONG).show();
		}
	}
}
