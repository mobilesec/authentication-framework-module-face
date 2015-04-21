package at.usmile.panshot;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Map;

import org.opencv.core.Mat;

import android.util.Log;
import at.usmile.panshot.util.PanshotUtil;

public class PanshotImage implements Serializable {
	private static final long serialVersionUID = 1L;

	private static final String TAG = PanshotImage.class.getSimpleName();

	// ================================================================================================================
	// MEMBERS

	public Mat pcaFace;
	public Mat grayImage;
	public Mat grayFace;
	public Float[] angleValues;
	public Float[] accelerationValues;
	public Float light;
	public long timestamp;
	public RecognitionInfo rec = new RecognitionInfo();

	// ================================================================================================================
	// METHODS
	public PanshotImage(Mat _grayImage, Mat _grayFace, Float[] _angleObservation, Float[] _accelerationValues, Float _light,
			long _timestamp) {
		grayImage = _grayImage;
		grayFace = _grayFace;
		angleValues = _angleObservation;
		accelerationValues = _accelerationValues;
		timestamp = _timestamp;
		light = _light;
	}

	@Override
	public String toString() {
		return "PanshotImage [grayImage=" + (grayImage == null ? null : "isSet") + ", grayFace="
				+ (grayFace == null ? null : "isSet") + ", angleValues=" + Arrays.toString(angleValues) + ", accelerationValues="
				+ Arrays.toString(accelerationValues) + ", light=" + light + ", " + rec.toString() + ", timestamp=" + timestamp
				+ "]";
	}

	/**
	 * Encapsulates information only needed for recognition tasks.
	 * 
	 * @author Rainhard Findling
	 * @date 10 Jan 2014
	 * @version 1
	 */
	public class RecognitionInfo {
		/** reference to the user who originated this image/face. */
		public User user;
		/** index to the angle the device was rotated along in this panshot. */
		public Integer angleIndex;

		@Override
		public String toString() {
			return "user=" + (user == null ? null : user.getName()) + ", angleIndex=" + angleIndex;
		}
	}

	// ========================================================================================================================
	// SERIALIZATION

	/**
	 * We need custom serialization as we can't serialize opencv members.
	 */
	private void writeObject(ObjectOutputStream out) throws IOException {
		if (pcaFace == null) {
			out.writeObject(null);
		} else {
			out.writeObject(PanshotUtil.matToMapFloat(pcaFace));
		}
		if (grayFace == null) {
			out.writeObject(null);
		} else {
			Log.d(TAG, "grayFace=" + grayFace);
			out.writeObject(PanshotUtil.matToMapByte(grayFace));
		}
		out.writeObject(rec.user);
	}

	/**
	 * We need custom serialization as we can't serialize opencv members.
	 */
	@SuppressWarnings("unchecked")
	private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
		Object tmp = in.readObject();
		if (tmp != null) {
			pcaFace = PanshotUtil.matFromMapFloat((Map<String, Object>) tmp);
		}
		tmp = in.readObject();
		if (tmp != null) {
			grayFace = PanshotUtil.matFromMapByte((Map<String, Object>) tmp);
		}
		rec = new RecognitionInfo();
		rec.user = (User) in.readObject();
	}
}
