package at.usmile.panshot.util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.net.Uri;
import android.os.Environment;

/**
 * Util for saving media (photos, files).
 * 
 * @author Rainhard Findling
 * @date 19.11.2011
 * @version 1
 */
public class MediaSaveUtil {

	// ================================================================================================================
	// MEMBERS
	private static final Logger LOGGER = LoggerFactory.getLogger(MediaSaveUtil.class);

	public static final int MEDIA_TYPE_IMAGE = 1;
	public static final int MEDIA_TYPE_VIDEO = 2;

	// ================================================================================================================
	// METHODS

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
			LOGGER.info("MediaSaveUtil.getMediaStorageDirectory(): creating directory " + mediaStorageDir.getAbsolutePath());
			if (!mediaStorageDir.mkdirs()) {
				throw new IOException("Unable to create directory: " + mediaStorageDir);
			}
		}
		return mediaStorageDir;
	}

	public static File ensureDirectoryExists(File parent, String name) throws IOException {
		File dir = new File(parent.getAbsolutePath() + "/" + name);
		if (!dir.exists()) {
			LOGGER.info("MediaSaveUtil.ensureDirectoryExists(): creating directory " + dir.getAbsolutePath());
			if (!dir.mkdirs()) {
				throw new IOException("Unable to create directory: " + dir);
			}
		}
		return dir;
	}

	/**
	 * Create a file Uri for saving an image or video
	 * 
	 * @throws IOException
	 */
	public static Uri getOutputMediaFileUri(int type, String mediaStorageDirectoryName) throws IOException {
		return Uri.fromFile(getOutputMediaFile(type, mediaStorageDirectoryName));
	}

	/**
	 * Create a File for saving an image or video
	 * 
	 * @throws IOException
	 */
	public static File getOutputMediaFile(int type, String mediaStorageDirectoryName) throws IOException {
		// File mediaStorageDir = new
		// File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
		// "MyCameraApp");
		File mediaStorageDir = getMediaStorageDirectory(mediaStorageDirectoryName);

		// Create a media file name
		String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
		File mediaFile;
		if (type == MEDIA_TYPE_IMAGE) {
			mediaFile = new File(mediaStorageDir.getPath() + File.separator + "IMG_" + timeStamp + ".jpg");
		} else if (type == MEDIA_TYPE_VIDEO) {
			mediaFile = new File(mediaStorageDir.getPath() + File.separator + "VID_" + timeStamp + ".mp4");
		} else {
			return null;
		}
		if (mediaFile.exists()) {
			LOGGER.error("getOutputMediaFile(): unique output file already exists.");
			return null;
		}
		try {
			if (!mediaFile.createNewFile()) {
				LOGGER.error("getOutputMediaFile(): cannot create file (false).");
				return null;
			}
		} catch (IOException e) {
			LOGGER.error("getOutputMediaFile(): cannot create file (error).");
			e.printStackTrace();
			return null;
		}
		return mediaFile;
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
