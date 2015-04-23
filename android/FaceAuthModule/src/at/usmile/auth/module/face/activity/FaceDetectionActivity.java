package at.usmile.auth.module.face.activity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewFrame;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener2;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfRect;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;
import org.opencv.samples.facedetect.DetectionBasedTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.DialogInterface.OnKeyListener;
import android.content.Intent;
import android.content.res.Resources.NotFoundException;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import at.usmile.auth.framework.api.AuthenticationStatusData;
import at.usmile.auth.module.face.FaceAuthenticationModule;
import at.usmile.auth.module.face.R;
import at.usmile.functional.FunApply;
import at.usmile.functional.FunFilter;
import at.usmile.functional.FunUtil;
import at.usmile.panshot.PanshotImage;
import at.usmile.panshot.SharedPrefs;
import at.usmile.panshot.Statics;
import at.usmile.panshot.User;
import at.usmile.panshot.recognition.RecognitionModule;
import at.usmile.panshot.sensor.PhotoGyroListener;
import at.usmile.panshot.sensor.SensorComponent;
import at.usmile.panshot.sensor.SensorValues;
import at.usmile.panshot.sensor.SensorValues.Observation;
import at.usmile.panshot.util.DataUtil;
import at.usmile.panshot.util.FaceModuleUtil;
import at.usmile.panshot.util.PanshotUtil;
import at.usmile.tuple.GenericTuple2;
import at.usmile.tuple.GenericTuple3;

/**
 * Does the face recording. If called from framework: gives back confidence that
 * it's the right user. If called for training: records and stores training
 * data.
 * 
 * @author Rainhard Findling
 * @date 7 Apr 2015
 * @version 1
 */
public class FaceDetectionActivity extends Activity implements CvCameraViewListener2 {

	// TODO replace all by LOGGER or kick logger

	// ================================================================================================================
	// MEMBERS
	// private final String TAG = "FaceRecognitionActivity";

	private static final Logger LOGGER = LoggerFactory.getLogger(FaceDetectionActivity.class);

	private static enum FaceDetectionPurpose {
		RECORD_DATA, RECOGNITION_TEST, AUTHENTICATION
	}

	/**
	 * Which recognition gets used when a) creating+training classifiers from
	 * FS-data and b) when classifying new pan shot input.
	 */
	private FaceDetectionPurpose mFaceDetectionPurpose = FaceDetectionPurpose.RECORD_DATA;

	// OpenCV settings
	private static final String TAG = FaceDetectionActivity.class.getSimpleName();
	private static final Scalar FACE_RECT_COLOR = new Scalar(0, 255, 0, 255);
	public static final int JAVA_DETECTOR = 0;
	public static final int NATIVE_DETECTOR = 1;

	private CascadeClassifier mJavaDetector_LBPCascadeFrontalface;
	private DetectionBasedTracker mNativeDetector_LBPCascadeFrontalface;

	private CascadeClassifier mJavaDetector_LBPCascadeProfileface;
	private DetectionBasedTracker mNativeDetector_LBPCascadeProfileface;

	private CascadeClassifier mJavaDetector_HaarCascadeProfileface;
	private DetectionBasedTracker mNativeDetector_HaarCascadeProfileface;

	private int mDetectorType = JAVA_DETECTOR;
	private String[] mDetectorName;

	private CameraBridgeViewBase mOpenCvCameraView;

	// ================================================================================================================
	// CAMVIEW STATICS

	public static enum RecognitionType {
		SVM, KNN
	}

	/** SW version */
	public static final String SESSION_ID = "01";

	private static final String CSV_FILENAME_EXTENSION = ".csv.jpg";

	/**
	 * BAD: hard coded index of angle values in angle array (we are only looking
	 * at one axis here).
	 */

	// ================================================================================================================
	// CAMVIEW MEMBERS

	/** user currently selected. only used in training mode. */
	private User mCurrentUser = null;

	/** true if the media recorder currently is recording via the cam. */
	private boolean mIsTakingPictures = false;
	/**
	 * checks if it is time to take a new photo in the faceview, based on the
	 * gyro-sensor.
	 */
	private PhotoGyroListener mPhotoGyroListener;

	private List<PanshotImage> mImages = new ArrayList<PanshotImage>();

	private TextView textviewIdentity;

	private Mat mRgba;
	private Mat mGray;

	private float mRelativeFaceSize = 0.4f;
	private int mAbsoluteFaceSize = 0;

	// ================================================================================================================
	// OPENCV LOADING CALLBACK MEMBER

	private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
		@Override
		public void onManagerConnected(int status) {
			LOGGER.info("BaseLoaderCallback.onManagerConnected()");
			switch (status) {
				case LoaderCallbackInterface.SUCCESS: {
					Log.i(TAG, "OpenCV loaded successfully");

					// Load native library after(!) OpenCV initialization
					System.loadLibrary("detection_based_tracker");

					try {
						// load cascade file from application resources
						File cascadeDir = getDir("cascade", Context.MODE_PRIVATE);

						// LBPCASCADE FRONTALFACE
						InputStream is = getResources().openRawResource(R.raw.lbpcascade_frontalface);
						File cascadeFile = new File(cascadeDir, "lbpcascade_frontalface.xml");
						FileOutputStream os = new FileOutputStream(cascadeFile);
						byte[] buffer = new byte[4096];
						int bytesRead = 0;
						while ((bytesRead = is.read(buffer)) != -1) {
							os.write(buffer, 0, bytesRead);
						}
						is.close();
						os.close();
						mJavaDetector_LBPCascadeFrontalface = new CascadeClassifier(cascadeFile.getAbsolutePath());
						if (mJavaDetector_LBPCascadeFrontalface.empty()) {
							Log.e(TAG, "Failed to load cascade classifier from " + cascadeFile.getAbsolutePath());
							mJavaDetector_LBPCascadeFrontalface = null;
						} else
							Log.i(TAG, "Loaded cascade classifier from " + cascadeFile.getAbsolutePath());

						mNativeDetector_LBPCascadeFrontalface = new DetectionBasedTracker(cascadeFile.getAbsolutePath(), 0);

						// LBPCASCADE PROFILEFACE
						is = getResources().openRawResource(R.raw.lbpcascade_profileface);
						cascadeFile = new File(cascadeDir, "lbpcascade_profileface.xml");
						os = new FileOutputStream(cascadeFile);
						buffer = new byte[4096];
						bytesRead = 0;
						while ((bytesRead = is.read(buffer)) != -1) {
							os.write(buffer, 0, bytesRead);
						}
						is.close();
						os.close();
						mJavaDetector_LBPCascadeProfileface = new CascadeClassifier(cascadeFile.getAbsolutePath());
						if (mJavaDetector_LBPCascadeProfileface.empty()) {
							Log.e(TAG, "Failed to load cascade classifier from " + cascadeFile.getAbsolutePath());
							mJavaDetector_LBPCascadeProfileface = null;
						} else
							Log.i(TAG, "Loaded cascade classifier from " + cascadeFile.getAbsolutePath());

						mNativeDetector_LBPCascadeProfileface = new DetectionBasedTracker(cascadeFile.getAbsolutePath(), 0);

						// HAARCASCADE PROFILEFACE
						is = getResources().openRawResource(R.raw.haarcascade_profileface);
						cascadeFile = new File(cascadeDir, "haarcascade_profileface.xml");
						os = new FileOutputStream(cascadeFile);
						buffer = new byte[4096];
						bytesRead = 0;
						while ((bytesRead = is.read(buffer)) != -1) {
							os.write(buffer, 0, bytesRead);
						}
						is.close();
						os.close();
						mJavaDetector_HaarCascadeProfileface = new CascadeClassifier(cascadeFile.getAbsolutePath());
						if (mJavaDetector_HaarCascadeProfileface.empty()) {
							Log.e(TAG, "Failed to load cascade classifier from " + cascadeFile.getAbsolutePath());
							mJavaDetector_HaarCascadeProfileface = null;
						} else
							Log.i(TAG, "Loaded cascade classifier from " + cascadeFile.getAbsolutePath());

						mNativeDetector_HaarCascadeProfileface = new DetectionBasedTracker(cascadeFile.getAbsolutePath(), 0);

						cascadeDir.delete();

					} catch (IOException e) {
						e.printStackTrace();
						Log.e(TAG, "Failed to load cascade. Exception thrown: " + e);
					}

					mOpenCvCameraView.enableView();
				}
					break;
				default: {
					super.onManagerConnected(status);
				}
					break;
			}
		}
	};

	/**
	 * recognition component. only used if we are not recording new training
	 * data.
	 */
	private RecognitionModule mRecognitionModule = null;

	// ================================================================================================================
	// ANDROID METHODS

	public FaceDetectionActivity() {
		mDetectorName = new String[2];
		mDetectorName[JAVA_DETECTOR] = "Java";
		mDetectorName[NATIVE_DETECTOR] = "Native (tracking)";
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.layout_activity_face_detection);
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

		// for updating user in UI
		textviewIdentity = (TextView) findViewById(R.id.textview_identity);

		// fit recording hint to our approach
		TextView textviewRecordingHint = (TextView) findViewById(R.id.textview_recording_hint);
		if (SharedPrefs.isFrontalOnly(this)) {
			textviewRecordingHint.setText(R.string.how_to_record_hint_frontal);
		}

		// camview
		mOpenCvCameraView = (CameraBridgeViewBase) findViewById(R.id.fd_activity_surface_view);
		mOpenCvCameraView.setCameraIndex(1);
		// mOpenCvCameraView.setDisplayOrientation(90);
		// mOpenCvCameraView.setRotation(90);
		mOpenCvCameraView.setCvCameraViewListener(this);

		// load opencv
		if (!OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_2_4_3, this, mLoaderCallback)) {
			Log.e(TAG, "onCreate: cannot connect to opencv");
		}

		mPhotoGyroListener = new PhotoGyroListener(SharedPrefs.getAngleBetweenClassifiers(this));

		// get info from calling Activity
		Bundle extras = getIntent().getExtras();

		if (extras != null) {
			String value = extras.getString(Statics.FACE_DETECTION_PURPOSE);

			// TEST FACE REC
			if (value.equals(Statics.FACE_DETECTION_PURPOSE_RECOGNITION_TEST)) {
				mFaceDetectionPurpose = FaceDetectionPurpose.RECOGNITION_TEST;
				// make user text invisible
				textviewIdentity.setVisibility(View.INVISIBLE);

				// load pre-trained recognitionmodule
				final EditText edittext = new EditText(FaceDetectionActivity.this);
				DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int whichButton) {
						Log.d(TAG, "DialogInterface.OnClickListener#onClick()");
						FaceDetectionActivity.this.finish();
					}
				};
				Builder builder = new AlertDialog.Builder(FaceDetectionActivity.this)
						.setTitle(FaceDetectionActivity.this.getResources().getString(R.string.error))
						.setPositiveButton(FaceDetectionActivity.this.getResources().getString(R.string.ok), listener)
						.setOnKeyListener(new OnKeyListener() {
							@Override
							public boolean onKey(DialogInterface _dialog, int _keyCode, KeyEvent _event) {
								Log.d(TAG, "DialogInterface.OnClickListener#onKey()");
								if (_keyCode == KeyEvent.KEYCODE_BACK) {
									FaceDetectionActivity.this.finish();
								}
								return false;
							}
						});
				try {
					File directory = DataUtil.getMediaStorageDirectory(FaceDetectionActivity.this.getResources().getString(
							R.string.app_classifier_directory_name));
					mRecognitionModule = DataUtil.deserializeRecognitiosModule(directory);
				} catch (NotFoundException e1) {
					e1.printStackTrace();
					builder.setMessage(
							FaceDetectionActivity.this.getResources().getString(R.string.error_classifier_could_not_be_loaded,
									e1.toString())).show();
				} catch (IOException e1) {
					e1.printStackTrace();
					builder.setMessage(
							FaceDetectionActivity.this.getResources().getString(R.string.error_classifier_could_not_be_loaded,
									e1.toString())).show();
				} catch (ClassNotFoundException e1) {
					e1.printStackTrace();
					builder.setMessage(
							FaceDetectionActivity.this.getResources().getString(R.string.error_classifier_could_not_be_loaded,
									e1.toString())).show();
				}
			}

			// AUTHENTICATE
			else if (value.equals(Statics.FACE_DETECTION_PURPOSE_AUTHENTICATION)) {
				mFaceDetectionPurpose = FaceDetectionPurpose.AUTHENTICATION;
				// make user text invisible
				textviewIdentity.setVisibility(View.INVISIBLE);

				try {
					File directory = DataUtil.getMediaStorageDirectory(FaceDetectionActivity.this.getResources().getString(
							R.string.app_classifier_directory_name));
					mRecognitionModule = DataUtil.deserializeRecognitiosModule(directory);
				} catch (NotFoundException e1) {
					e1.printStackTrace();
					sendConfidenceToAuthFrameworkInvalid();
				} catch (IOException e1) {
					e1.printStackTrace();
					sendConfidenceToAuthFrameworkInvalid();
				} catch (ClassNotFoundException e1) {
					e1.printStackTrace();
					sendConfidenceToAuthFrameworkInvalid();
				}

				// inform user that it's the authentication framework calling
				// for face auth.
				Toast.makeText(this, getResources().getText(R.string.info_called_from_framework), Toast.LENGTH_SHORT);
			}

			// RECORD NEW DATA
			else if (value.equals(Statics.FACE_DETECTION_PURPOSE_RECORD_DATA)) {
				mFaceDetectionPurpose = FaceDetectionPurpose.RECORD_DATA;

				// load users - exit activity if that fails
				final List<User> users = FaceModuleUtil.loadExistingUsers(this, new OnClickListener() {
					@Override
					public void onClick(DialogInterface _dialog, int _which) {
						Log.d(TAG, "Dialog#onClick()");
						finish();
					}
				}, new Dialog.OnKeyListener() {
					@Override
					public boolean onKey(DialogInterface _dialog, int _keyCode, KeyEvent _event) {
						Log.d(TAG, "Dialog#onKey(Back)");
						finish();
						return false;
					}
				});
				if (users == null) {
					// don't load other stuff
					return;
				}

				// ask for user to train for
				AlertDialog.Builder builder = new Builder(this).setTitle(getResources().getString(R.string.chose_user_to_train))
						.setOnKeyListener(new OnKeyListener() {
							@Override
							public boolean onKey(DialogInterface _dialog, int _keyCode, KeyEvent _event) {
								finish();
								return false;
							}
						});
				// back = close dialog AND activity
				final OnKeyListener onKeyListener = new OnKeyListener() {
					@Override
					public boolean onKey(DialogInterface _dialog, int _keyCode, KeyEvent _event) {
						if (_keyCode == KeyEvent.KEYCODE_BACK) {
							FaceDetectionActivity.this.finish();
						}
						return false;
					}
				};
				builder.setOnKeyListener(onKeyListener);
				// add users
				final String[] userNames = new String[users.size() + 1];
				for (int i = 0; i < users.size(); i++) {
					userNames[i] = users.get(i).getName();
				}
				userNames[userNames.length - 1] = getResources().getString(R.string.create_new_user);
				// add onclick listener
				OnClickListener listener = new OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						dialog.dismiss();
						Log.d(TAG, "#onClick(" + which + ")");
						if (which < users.size()) {
							mCurrentUser = users.get(which);
							updateUiFromCurrentUser();
						} else {
							// ask for name of new user

							// #1 dialog for name already taken (just in
							// case...)
							DialogInterface.OnClickListener listener2 = new DialogInterface.OnClickListener() {
								public void onClick(DialogInterface dialog, int whichButton) {
									FaceDetectionActivity.this.finish();
								}
							};
							final Builder dialog2 = new AlertDialog.Builder(FaceDetectionActivity.this)
									.setTitle(FaceDetectionActivity.this.getResources().getString(R.string.error))
									.setMessage(
											FaceDetectionActivity.this.getResources().getString(
													R.string.toast_username_already_taken))
									.setPositiveButton(FaceDetectionActivity.this.getResources().getString(R.string.ok),
											listener2).setOnKeyListener(onKeyListener);

							// #2 ask for name of new user
							final EditText edittext = new EditText(FaceDetectionActivity.this);
							DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {
								public void onClick(DialogInterface dialog, int whichButton) {
									String name = edittext.getText().toString();
									// check is username is already in use
									for (String s : userNames) {
										if (s.equalsIgnoreCase(name)) {
											dialog2.show();
										} else {
											mCurrentUser = DataUtil.createNewUser(name);
											updateUiFromCurrentUser();
										}
									}
								}
							};
							new AlertDialog.Builder(FaceDetectionActivity.this)
									.setTitle(FaceDetectionActivity.this.getResources().getString(R.string.create_new_user))
									.setMessage(FaceDetectionActivity.this.getResources().getString(R.string.chose_username))
									.setView(edittext)
									.setPositiveButton(FaceDetectionActivity.this.getResources().getString(R.string.ok), listener)
									.setOnKeyListener(onKeyListener).show();
						}
					}
				};
				builder.setItems(userNames, listener);
				builder.show();
			}
		}
	}

	@Override
	protected void onResume() {
		super.onResume();
		Log.d(FaceDetectionActivity.class.getSimpleName(), "onResume()");

		// create sensor stuff
		SensorComponent.init(this);
		SensorComponent.instance().addObserver(mPhotoGyroListener);

		updateUiFromCurrentUser();
	}

	@Override
	public void onPause() {
		super.onPause();
		if (mOpenCvCameraView != null) {
			mOpenCvCameraView.disableView();
		}
		// stop taking pictures if we're still taking some by now
		stopTakingPictures();
		mImages.clear();
		Log.d(MainActivity.class.getSimpleName(), "CameraFragment.onPause()");
	}

	@Override
	public boolean onKeyDown(int _keyCode, KeyEvent _event) {
		Log.v(MainActivity.class.getSimpleName(), _event.toString());
		switch (_keyCode) {
			case KeyEvent.KEYCODE_VOLUME_DOWN:
			case KeyEvent.KEYCODE_VOLUME_UP:
				Log.d(FaceDetectionActivity.class.getSimpleName(), "keyodwn: vol down/up detected");
				toggleRecording();
				return true;
		}
		return super.onKeyDown(_keyCode, _event);
	}

	@Override
	public boolean onKeyUp(int _keyCode, KeyEvent _event) {
		Log.v(MainActivity.class.getSimpleName(), _event.toString());
		switch (_keyCode) {
			case KeyEvent.KEYCODE_VOLUME_DOWN:
			case KeyEvent.KEYCODE_VOLUME_UP:
				// ignore as we are already handling the key down
				return true;
		}
		return super.onKeyUp(_keyCode, _event);
	}

	public void onDestroy() {
		Log.i(TAG, "CameraFragment.onDestroy()");
		if (mOpenCvCameraView != null) {
			mOpenCvCameraView.disableView();
		}
		super.onDestroy();
	}

	// ================================================================================================================
	// METHODS

	/**
	 * This is how your explicit authentication Activity can stop its task and
	 * send back acquired confidence to your module of the authentication
	 * framework.
	 */
	private void sendConfidenceToAuthFramework(String _authenticationStatus, float _confidence) {
		Log.d(TAG, "sendConfidenceToAuthFramework");

		// create intent and add confidence from authentication process
		Intent resultIntent = new Intent(FaceAuthenticationModule.ON_AUTHENTICATION);
		resultIntent.putExtra(FaceAuthenticationModule.AUTHENTICATION_STATUS, _authenticationStatus);
		resultIntent.putExtra(FaceAuthenticationModule.CONFIDENCE, _confidence);

		// sent info back to auth module
		LocalBroadcastManager.getInstance(this).sendBroadcast(resultIntent);

		// we're done with authenticating and can terminate
		finish();
	}

	/**
	 * See
	 * {@link FaceDetectionActivity#sendConfidenceToAuthFramework(AuthenticationStatusData, float)}
	 * .
	 * 
	 * @param _confidence
	 */
	private void sendConfidenceToAuthFrameworkOk(float _confidence) {
		sendConfidenceToAuthFramework(FaceAuthenticationModule.AUTHENTICATION_STATUS_OK, _confidence);
	}

	/**
	 * See
	 * {@link FaceDetectionActivity#sendConfidenceToAuthFramework(AuthenticationStatusData, float)}
	 * .
	 * 
	 * @param _confidence
	 */
	private void sendConfidenceToAuthFrameworkInvalid() {
		sendConfidenceToAuthFramework(FaceAuthenticationModule.AUTHENTICATION_STATUS_FAILED, 0);
	}

	private void updateUiFromCurrentUser() {
		if (mFaceDetectionPurpose == FaceDetectionPurpose.RECORD_DATA) {
			if (mCurrentUser == null) {
				textviewIdentity.setText(getResources().getString(R.string.current_user,
						getResources().getString(R.string.not_available)));
			} else {
				textviewIdentity.setText(getResources().getString(R.string.current_user, mCurrentUser.getName()));
			}
		}
	}

	private void startTakingPictures() {
		Log.d(FaceDetectionActivity.class.getSimpleName(), "starting image taking...");
		// ensure we really have a user
		if (mFaceDetectionPurpose == FaceDetectionPurpose.RECORD_DATA && mCurrentUser == null) {
			DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int whichButton) {
				}
			};
			final Builder dialog = new AlertDialog.Builder(FaceDetectionActivity.this)
					.setTitle(FaceDetectionActivity.this.getResources().getString(R.string.error))
					.setMessage(FaceDetectionActivity.this.getResources().getString(R.string.no_user_selected))
					.setPositiveButton(FaceDetectionActivity.this.getResources().getString(R.string.ok), listener);
			dialog.show();
			return;
		}
		// start stuff that we need for recording pictures
		mIsTakingPictures = true;
		mPhotoGyroListener.reset();
		mImages.clear();
		SensorComponent.instance().start();
		PanshotUtil.playSoundfile(this, R.raw.beep);
		if (!SharedPrefs.isFrontalOnly(this)) {
			ImageView imageViewRedDot = (ImageView) findViewById(R.id.imageView_redDot);
			imageViewRedDot.setVisibility(ImageView.VISIBLE);
		}
	}

	private void stopTakingPictures() {
		Log.d(FaceDetectionActivity.class.getSimpleName(), "stopping image taking. we recorded " + mImages.size() + " images.");
		mIsTakingPictures = false;
		SensorComponent.instance().stop();
		if (!SharedPrefs.isFrontalOnly(this)) {
			ImageView imageViewRedDot = (ImageView) findViewById(R.id.imageView_redDot);
			imageViewRedDot.setVisibility(ImageView.INVISIBLE);
		}
	}

	public void onCameraViewStarted(int width, int height) {
		Log.i(TAG, "CameraFragment.onCameraViewStarted()");
		mGray = new Mat();
		mRgba = new Mat();
	}

	public void onCameraViewStopped() {
		Log.i(TAG, "CameraFragment.onCameraViewStopped()");
		mGray.release();
		mRgba.release();
	}

	public Mat onCameraFrame(CvCameraViewFrame inputFrame) {

		// mRgba = inputFrame.rgba();
		mGray = inputFrame.gray();

		// try to rotate inputFrame for portrait mode
		// Mat mRgbaT = mGray.t();
		// Core.flip(mGray.t(), mRgbaT, 0);
		// Imgproc.resize(mRgbaT, mRgbaT, mGray.size());
		//
		// mGray = mRgbaT;

		// ImageOperationUtil.rotate(mGray, 90);
		// ImageOperationUtil.rotate(mRgba, 90);

		if (mAbsoluteFaceSize == 0) {
			int height = mGray.rows();
			if (Math.round(height * mRelativeFaceSize) > 0) {
				mAbsoluteFaceSize = Math.round(height * mRelativeFaceSize);
			}
			mNativeDetector_LBPCascadeFrontalface.setMinFaceSize(mAbsoluteFaceSize);
		}

		// copy grayface in case we need it later
		Mat graycopy = null;
		if (mPhotoGyroListener.isNextPhoto()) {
			graycopy = new Mat(mGray.rows(), mGray.cols(), mGray.type());
			mGray.copyTo(graycopy);
		}

		// LIVE FACE DETECTION - NOT THE ONE DONE FOR RECOGNITION!
		Rect[] facesArray = detectFaces(mGray, mJavaDetector_LBPCascadeFrontalface, mNativeDetector_LBPCascadeFrontalface);
		Rect faceBiggest = null;
		for (int i = 0; i < facesArray.length; i++) {
			Core.rectangle(mGray, facesArray[i].tl(), facesArray[i].br(), FACE_RECT_COLOR, 3);
			if (faceBiggest == null || faceBiggest.width < facesArray[i].width) {
				faceBiggest = facesArray[i];
			}
		}

		// time to take next photo
		if (graycopy != null) {
			Mat grayface = null;
			if (faceBiggest != null) {
				grayface = graycopy.submat(faceBiggest);
				// grayface = new Mat(grayface2.rows(), grayface2.cols(),
				// grayface2.type());
				// grayface2.copyTo(grayface);
			}
			long timestamp = System.currentTimeMillis();
			// notify observer about image
			SensorValues sensorValues = SensorComponent.instance().getSensorValues();
			Observation<Float[]> acc = sensorValues.getAccelerationValues();
			Observation<Float[]> rot = sensorValues.getRotationValues();
			Observation<Float> light = sensorValues.getLightValue();
			Log.i(TAG, "taking picture at rotation " + Arrays.toString(rot.value));
			// remember image
			PanshotImage panshotImage = new PanshotImage(graycopy, grayface, sensorValues.getRotationValues().value,
					(acc == null ? null : acc.value), (light == null ? null : light.value), timestamp);
			mImages.add(panshotImage);
			if (SharedPrefs.isFrontalOnly(this)) {
				// we've taken 1 image, stop recording automatically right now
				runOnUiThread(new Runnable() {
					@Override
					public void run() {
						toggleRecording();
					}
				});
			}
		}
		return mGray;
	}

	public void detectFacesInRecordedImagesDependingOnAngle() {
		// determine angle index and angle normalization value
		GenericTuple2<Integer, Float[]> angleIndexNormaliser = PanshotUtil.calculateAngleIndexAndNormalizer(
				mImages.get(0).angleValues, mImages.get(mImages.size() - 1).angleValues);
		Log.d(TAG, "mImages.get(0)=" + mImages.get(0));
		Log.d(TAG, "mImages.get(mImages.size() - 1)=" + mImages.get(mImages.size() - 1));
		Log.d(TAG, "angleIndexNormaliser=" + angleIndexNormaliser.value1 + ", " + Arrays.toString(angleIndexNormaliser.value2));
		int angleIndex = angleIndexNormaliser.value1;
		float angleNormalizer = angleIndexNormaliser.value2[angleIndex];
		for (PanshotImage panshotImage : mImages) {
			// remember recognition related info
			panshotImage.rec.angleIndex = angleIndex;
			// normalize this image's panshot rotation axis' angle values
			panshotImage.angleValues[angleIndex] -= angleNormalizer;
			float angle = panshotImage.angleValues[angleIndex];
			// select face detection classifier
			CascadeClassifier cascadeClassifier = mJavaDetector_LBPCascadeFrontalface;
			DetectionBasedTracker detectionBasedTracker = mNativeDetector_LBPCascadeFrontalface;
			boolean mirroring = angle < -SharedPrefs.getFrontalMaxAngle(this);
			boolean usingProfile = Math.abs(angle) > SharedPrefs.getFrontalMaxAngle(this);
			Mat image = panshotImage.grayImage;
			if (usingProfile) {

				// LBP profile face does not seem to do too good atm, use
				// haarcascades instead
				// cascadeClassifier = mJavaDetector_LBPCascadeProfileface;
				// detectionBasedTracker =
				// mNativeDetector_LBPCascadeProfileface;

				cascadeClassifier = mJavaDetector_HaarCascadeProfileface;
				detectionBasedTracker = mNativeDetector_HaarCascadeProfileface;
				if (mirroring) {
					// flip horizontally
					Mat tmp = new Mat(image.rows(), image.cols(), image.type());
					Core.flip(image, tmp, 1);
					image = tmp;
				}
			}
			Log.d(TAG, "angle is " + angle + ", usingProfile=" + usingProfile + ", mirroring=" + mirroring);
			if (usingProfile || panshotImage.grayFace == null) {
				// we either use profile or don't have a face by now, so
				// detect faces
				Rect[] faces = detectFaces(image, cascadeClassifier, detectionBasedTracker);
				Log.d(TAG, "we found " + faces.length + " faces.");
				// determine biggest face and extract facial region
				Rect faceBiggest = null;
				for (Rect r : faces) {
					if (faceBiggest == null || faceBiggest.width < r.width) {
						faceBiggest = r;
					}
				}
				if (faceBiggest != null) {
					panshotImage.grayFace = panshotImage.grayImage.submat(faceBiggest);
					if (mirroring) {
						// flip horizontally
						Mat tmp = new Mat(panshotImage.grayFace.rows(), panshotImage.grayFace.cols(),
								panshotImage.grayFace.type());
						Core.flip(panshotImage.grayFace, tmp, 1);
						panshotImage.grayFace = tmp;
					}
				}
			} else {
				Log.d(TAG, "image already contained a detected face, not doing face detection again.");
			}
		}
	}

	private Rect[] detectFaces(Mat image, CascadeClassifier cascadeClassifier, DetectionBasedTracker detectionBasedTracker) {
		MatOfRect faces = new MatOfRect();
		if (mDetectorType == JAVA_DETECTOR) {
			if (cascadeClassifier != null)
				cascadeClassifier.detectMultiScale(image, faces, 1.1, 2, 2, // opencvtodo:
																			// objdetect.CV_HAAR_SCALE_IMAGE
						new Size(mAbsoluteFaceSize, mAbsoluteFaceSize), new Size());
		} else if (mDetectorType == NATIVE_DETECTOR) {
			if (detectionBasedTracker != null)
				detectionBasedTracker.detect(image, faces);
		} else {
			Log.e(TAG, "Detection method is not selected!");
		}
		return faces.toArray();
	}

	// TODO use those somewhere...
	private void setMinFaceSize(float faceSize) {
		mRelativeFaceSize = faceSize;
		mAbsoluteFaceSize = 0;
	}

	// TODO use those somewhere...
	private void setDetectorType(int type) {
		if (mDetectorType != type) {
			mDetectorType = type;

			if (type == NATIVE_DETECTOR) {
				Log.i(TAG, "Detection Based Tracker enabled");
				mNativeDetector_LBPCascadeFrontalface.start();
			} else {
				Log.i(TAG, "Cascade detector enabled");
				mNativeDetector_LBPCascadeFrontalface.stop();
			}
		}
	}

	private void toggleRecording() {
		if (!mIsTakingPictures) {
			startTakingPictures();
		} else {
			stopTakingPictures();
			// decide what to do with the images
			if (mImages.size() > 0) {
				// do face detection for each image
				detectFacesInRecordedImagesDependingOnAngle();
				Log.d(TAG, "Recorded images after face detection" + mImages.toString());

				switch (mFaceDetectionPurpose) {

					case RECORD_DATA:
						// save images
						DataUtil.savePanshotImages(this, mCurrentUser, mImages, CSV_FILENAME_EXTENSION, SESSION_ID,
								SharedPrefs.isFrontalOnly(this), SharedPrefs.getAngleBetweenClassifiers(this));
						break;

					case RECOGNITION_TEST:
					case AUTHENTICATION:

						Log.d(TAG, "Deserialized: " + mRecognitionModule);
						if (mRecognitionModule == null) {
							Toast.makeText(this,
									getResources().getText(R.string.error_classifier_could_not_be_loaded, "aborting"),
									Toast.LENGTH_SHORT).show();
							break;
						}

						// only use images in which faces where
						// detected
						List<PanshotImage> imagesWithFaces = FunUtil.filter(mImages, new FunFilter<PanshotImage>() {
							@Override
							public boolean filter(PanshotImage _t) {
								return _t.grayFace != null;
							}
						});
						if (imagesWithFaces.size() == 0) {
							Toast.makeText(this, R.string.no_faces_detected_please_try_again, Toast.LENGTH_SHORT).show();
							break;
						}

						// normalise energy of all images
						if (SharedPrefs.useImageEnergyNormlization(FaceDetectionActivity.this)) {
							FunUtil.apply(imagesWithFaces, new FunApply<PanshotImage, PanshotImage>() {
								@Override
								public PanshotImage apply(PanshotImage panshotImage) {
									// normalise the face's energy use
									// convolution (kernel = 2D filter) to get
									// image energy (brightness) distribution
									// and normalise face with it
									GenericTuple2<Mat, Mat> normalizedMatEnergy = PanshotUtil.normalizeMatEnergy(
											panshotImage.grayFace, (int) (panshotImage.grayFace.rows() / SharedPrefs
													.getImageEnergyNormalizationSubsamplingFactor(FaceDetectionActivity.this)),
											(int) (panshotImage.grayFace.cols() / SharedPrefs
													.getImageEnergyNormalizationSubsamplingFactor(FaceDetectionActivity.this)),
											255.0);
									panshotImage.grayFace = normalizedMatEnergy.value1;
									// DEBUG save energy image for review
									try {
										File f = DataUtil.getMediaStorageDirectory(FaceDetectionActivity.this.getResources()
												.getString(R.string.app_media_directory_name));
										DataUtil.saveMatToJpgFile(new File(f.getAbsolutePath() + "/normalized.jpg"),
												normalizedMatEnergy.value1);
										DataUtil.saveMatToJpgFile(new File(f.getAbsolutePath() + "/energy.jpg"),
												normalizedMatEnergy.value2);
									} catch (NotFoundException e) {
										e.printStackTrace();
									} catch (IOException e) {
										e.printStackTrace();
									}
									return panshotImage;
								}
							});
						}
						// RESIZE images as KNN, SVM etc need images
						// that are of same size
						FunUtil.apply(imagesWithFaces, new FunApply<PanshotImage, PanshotImage>() {
							@Override
							public PanshotImage apply(PanshotImage _t) {
								Imgproc.resize(
										_t.grayFace,
										_t.grayFace,
										new Size(SharedPrefs.getFaceWidth(FaceDetectionActivity.this), SharedPrefs
												.getFaceHeight(FaceDetectionActivity.this)));
								return _t;
							}
						});

						switch (mFaceDetectionPurpose) {
							case AUTHENTICATION: {
								if (imagesWithFaces.size() == 0) {
									// sendConfidenceToAuthFrameworkInvalid();
									// repeat until we have a face
									break;
								}

								// decide which recognition to use
								switch (SharedPrefs.getRecognitionType(FaceDetectionActivity.this)) {
									case KNN: {
										GenericTuple3<User, Integer, Map<User, Integer>> classificationResult = mRecognitionModule
												.classifyKnn(imagesWithFaces, SharedPrefs.getKnnK(this), null,
														SharedPrefs.usePca(this), SharedPrefs.getAmountOfPcaFeatures(this),
														SharedPrefs.getAngleBetweenClassifiers(this));
										sendConfidenceToAuthFrameworkOk(classificationResult.value2.floatValue());
										break;
									}

									case SVM: {
										GenericTuple3<User, Double, Map<User, Double>> classificationResult = mRecognitionModule
												.classifySvm(imagesWithFaces, SharedPrefs.usePca(this),
														SharedPrefs.getAmountOfPcaFeatures(this),
														SharedPrefs.getAngleBetweenClassifiers(this));
										sendConfidenceToAuthFrameworkOk(classificationResult.value2.floatValue());
										break;
									}
								}
								break;
							}

							case RECOGNITION_TEST: {
								if (imagesWithFaces.size() > 0) {
									// decide which recognition to use
									switch (SharedPrefs.getRecognitionType(FaceDetectionActivity.this)) {
										case KNN: {
											GenericTuple3<User, Integer, Map<User, Integer>> classificationResult = mRecognitionModule
													.classifyKnn(imagesWithFaces, SharedPrefs.getKnnK(this), null,
															SharedPrefs.usePca(this), SharedPrefs.getAmountOfPcaFeatures(this),
															SharedPrefs.getAngleBetweenClassifiers(this));
											Toast.makeText(
													this,
													getResources().getString(R.string.most_likely_user_knn,
															classificationResult.value1.getName(), classificationResult.value2),
													Toast.LENGTH_LONG).show();
											break;
										}

										case SVM: {
											GenericTuple3<User, Double, Map<User, Double>> classificationResult = mRecognitionModule
													.classifySvm(imagesWithFaces, SharedPrefs.usePca(this),
															SharedPrefs.getAmountOfPcaFeatures(this),
															SharedPrefs.getAngleBetweenClassifiers(this));

											Toast.makeText(
													this,
													getResources().getString(R.string.most_likely_user_svm,
															classificationResult.value1.getName(), classificationResult.value2),
													Toast.LENGTH_LONG).show();
											break;
										}

									}
								} else {
									Toast.makeText(FaceDetectionActivity.this,
											FaceDetectionActivity.this.getResources().getString(R.string.no_faces_detected),
											Toast.LENGTH_LONG).show();
								}
								break;
							}

							default:
								throw new RuntimeException("not implemented?");
						}

				}
				mImages.clear();
			}
		}
	}

}
