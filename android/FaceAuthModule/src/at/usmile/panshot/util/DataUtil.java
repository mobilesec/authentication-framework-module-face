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
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.highgui.Highgui;
import org.opencv.imgproc.Imgproc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.content.Context;
import android.content.res.Resources.NotFoundException;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.os.Environment;
import android.util.Log;
import android.widget.Toast;
import at.usmile.auth.module.face.R;
import at.usmile.auth.module.face.activity.OldMainActivity;
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
// TODO comments

/**
 * Tool for access to file system stuff (loading and saving face auth. data)
 * 
 * @author Rainhard Findling
 * @date 7 Apr 2015
 * @version 1
 */
public class DataUtil {
	private static final Logger LOGGER = LoggerFactory.getLogger(DataUtil.class);

	protected static FileFilter PANSHOT_USER_FOLDER_FILE_FILTER = new FileFilter() {
		@Override
		public boolean accept(File file) {
			// we use directories that start with a letter
			return file.isDirectory() && !file.getName().startsWith("_");
		}
	};

	public static User getUserForName(Context _context, String name) throws NotFoundException, IOException {
		for (User u : loadExistingUsers(_context)) {
			if (u.getName().equalsIgnoreCase(name)) {
				return u;
			}
		}
		throw new RuntimeException(name + ": no such user.");
	}

	public static User createNewUser(String name) {
		String id = Long.toString(Math.abs(new Random(System.currentTimeMillis()).nextLong()));
		return new User(id, name);
	}

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

	public static void savePanshotImages(Context _context, User _currentUser, List<PanshotImage> _images,
			int _angleArrayUsedIndex, String csvFileExtension, String _sessionId, boolean _useFrontalOnly,
			float _panshotTargetMinAngle) {
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
		Log.d(OldMainActivity.class.getSimpleName(), "mediaDir=" + mediaDir.getAbsolutePath());
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
			if (minAngle > image.angleValues[_angleArrayUsedIndex]) {
				minAngle = image.angleValues[_angleArrayUsedIndex];
			}
			if (maxAngle < image.angleValues[_angleArrayUsedIndex]) {
				maxAngle = image.angleValues[_angleArrayUsedIndex];
			}
			LOGGER.debug("image " + imageNr + ": " + image.toString() + " has channels=" + image.grayImage.channels()
					+ ", depth=" + image.grayImage.depth() + ", type=" + image.grayImage.type());
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

	public static List<PanshotImage> loadTrainingData(Context _context) {
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
					LOGGER.debug("angleIndex=" + angleIndex + ", angleNormalizer=" + angleNormalizer);
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
					Mat face = Highgui.imread(image.getAbsolutePath());
					// convert to 1 channel = gray
					Mat tmp = new Mat(face.rows(), face.cols(), face.type());
					Imgproc.cvtColor(face, tmp, Imgproc.COLOR_RGBA2GRAY);
					face = tmp;
					panshotImage.grayFace = face;
					panshotImage.angleValues[angleIndex] -= angleNormalizer;
					panshotImage.rec.angleIndex = angleIndex;
					LOGGER.debug("loaded image has channels=" + panshotImage.grayFace.channels() + ", depth="
							+ panshotImage.grayFace.depth() + ", type=" + panshotImage.grayFace.type());
					panshotImages.add(panshotImage);
				}
			}
		}
		return panshotImages;
	}

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

	public static RecognitionModule deserializeRecognitiosModule(File _directory) throws NotFoundException, IOException,
			ClassNotFoundException {
		ObjectInputStream objectInputStream = null;
		RecognitionModule r = null;
		try {
			// deserialize
			objectInputStream = new ObjectInputStream(new BufferedInputStream(new FileInputStream(new File(_directory,
					"recognition_module.ser"))));
			r = (RecognitionModule) objectInputStream.readObject();

			// load native data
			for (SvmClassifier c : r.getSvmClassifiers().values()) {
				c.loadNativeData(_directory);
			}

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

	public static File getMediaStorageDirectory(String name) throws IOException {
		// This location works best if you want the created images to be shared
		// between applications and persist after your app has been uninstalled.
		File mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), name);
		// Create the storage directory if it does not exist
		if (!mediaStorageDir.exists()) {
			LOGGER.info("getMediaStorageDirectory(): creating directory " + mediaStorageDir.getAbsolutePath());
			if (!mediaStorageDir.mkdirs()) {
				throw new IOException("Unable to create directory: " + mediaStorageDir);
			}
		}
		return mediaStorageDir;
	}

	public static File ensureDirectoryExists(File parent, String name) throws IOException {
		File dir = new File(parent.getAbsolutePath() + "/" + name);
		if (!dir.exists()) {
			LOGGER.info("ensureDirectoryExists(): creating directory " + dir.getAbsolutePath());
			if (!dir.mkdirs()) {
				throw new IOException("Unable to create directory: " + dir);
			}
		}
		return dir;
	}

	public static void saveYuvImageToJpgFile(File file, YuvImage image) throws IOException {
		FileOutputStream filecon = new FileOutputStream(file);
		image.compressToJpeg(new Rect(0, 0, image.getWidth(), image.getHeight()), 90, filecon);
		filecon.close();
	}

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
			LOGGER.debug("trying to save bitmap to " + _folderPath + "/" + _filename);
			File folder = new File(_folderPath);
			folder.mkdirs();

			FileOutputStream out = new FileOutputStream(_folderPath + "/" + _filename);
			_b.compress(Bitmap.CompressFormat.PNG, 90, out);
			LOGGER.debug("saving bitmap done.");
		} catch (Exception e) {
			LOGGER.warn("saving bitmap failed.");
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
}
