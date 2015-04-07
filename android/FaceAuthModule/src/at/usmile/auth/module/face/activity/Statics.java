package at.usmile.auth.module.face.activity;

/**
 * Static keys and their possible values for consistently communicating infos
 * across Activities.
 * 
 * @author Rainhard Findling
 * @date 7 Apr 2015
 * @version 1
 */
public class Statics {

	/** key and values of why {@link FaceDetectionActivity} is called */
	protected static final String FACE_DETECTION_PURPOSE = "FACE_DETECTION_PURPOSE";
	/** key and values of why {@link FaceDetectionActivity} is called */
	protected static final String FACE_DETECTION_PURPOSE_VALUE_RECOGNITION = "recognition";
	/** key and values of why {@link FaceDetectionActivity} is called */
	protected static final String FACE_DETECTION_PURPOSE_VALUE_TRAINING = "training";

	protected static final String FACE_DETECTION_TRAINING_USER_ID = "FACE_DETECTION_TRAINING_USER_ID";
	protected static final String FACE_DETECTION_TRAINING_USER_NAME = "FACE_DETECTION_TRAINING_USER_NAME";

}
