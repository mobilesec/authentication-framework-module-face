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

	/** key of why {@link FaceDetectionActivity} is called */
	public static final String FACE_DETECTION_PURPOSE = "Face_detection_purpose";
	/** value for authentication (called by the auth framework) */
	public static final String FACE_DETECTION_PURPOSE_AUTHENTICATION = "authentication";
	/** value for the user doing a recognition test */
	public static final String FACE_DETECTION_PURPOSE_RECOGNITION_TEST = "recognition_test";
	/** value for the user recording new authentication date */
	public static final String FACE_DETECTION_PURPOSE_RECORD_DATA = "record_data";

	public static final String FACE_DETECTION_TRAINING_USER_ID = "FACE_DETECTION_TRAINING_USER_ID";
	public static final String FACE_DETECTION_TRAINING_USER_NAME = "FACE_DETECTION_TRAINING_USER_NAME";

	public static final String TRAINING_SERVICE_BROADCAST_ACTION = "TRAINING_SERVICE_BROADCAST_ACTION";
	public static final String TRAINING_SERVICE_STATUS = "TRAINING_SERVICE_STATUS";
	public static final String TRAINING_SERVICE_STATUS_FINISHED = "finished";

}
