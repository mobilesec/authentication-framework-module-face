package at.usmile.panshot;

import java.util.Arrays;

import org.opencv.core.Mat;

public class PanshotImage {

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

}
