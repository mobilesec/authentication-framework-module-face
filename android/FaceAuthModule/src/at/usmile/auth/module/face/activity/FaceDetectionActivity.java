package at.usmile.auth.module.face.activity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
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
import android.content.Context;
import android.content.res.Resources.NotFoundException;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import at.usmile.auth.module.face.R;
import at.usmile.functional.Fun;
import at.usmile.functional.FunFilter;
import at.usmile.functional.FunUtil;
import at.usmile.panshot.PanshotImage;
import at.usmile.panshot.PhotoGyroListener;
import at.usmile.panshot.SensorComponent;
import at.usmile.panshot.SensorValues;
import at.usmile.panshot.SensorValues.Observation;
import at.usmile.panshot.SharedPrefs;
import at.usmile.panshot.User;
import at.usmile.panshot.nu.DataUtil;
import at.usmile.panshot.nu.RecognitionSingleton;
import at.usmile.panshot.recognition.PCAUtil;
import at.usmile.panshot.recognition.TrainingData;
import at.usmile.panshot.util.MediaSaveUtil;
import at.usmile.panshot.util.PanshotUtil;
import at.usmile.tuple.GenericTuple2;
import at.usmile.tuple.GenericTuple3;

import com.google.common.base.Preconditions;

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

	// ================================================================================================================
	// MEMBERS
	// private final String TAG = "FaceRecognitionActivity";

	private static final Logger LOGGER = LoggerFactory.getLogger(FaceDetectionActivity.class);

	private static enum ClassifierMode {
		Training, Recognition
	}

	/**
	 * Which recognition gets used when a) creating+training classifiers from
	 * FS-data and b) when classifying new pan shot input.
	 */
	private ClassifierMode mClassifierMode = ClassifierMode.Training;

	// OpenCV settings
	private static final String TAG = "OCVSample::Activity";
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

	private RecognitionSingleton mRecognitionSingleton;

	// ================================================================================================================
	// CAMVIEW STATICS

	public static enum RecognitionType {
		SVM, KNN
	}

	/** SW version */
	public static final String SESSION_ID = "01";

	private static final float PANSHOT_TARGET_MIN_ANGLE = (float) (Math.PI * 170.0f / 180.0f);

	private static final String CSV_FILENAME_EXTENSION = ".csv.jpg";

	/**
	 * Angle that lies between two neighbouring images of the same panshot and
	 * angle that lies between two neighbouring classifiers.
	 */
	private static final float ANGLE_DIFF_OF_PHOTOS = (float) (22.5f / 2f / 180f * Math.PI);
	// 22.5 degree = 9 pics / 180 degree
	/**
	 * why ODD x ANGLE_DIFF_OF_PHOTOS? Because as classifiers depend on
	 * ANGLE_DIFF_OF_PHOTOS this prevents classifiers of having to handle
	 * frontal AND profile images, as the separation is exactly in between.
	 */
	private static final float FRONTAL_MAX_ANGLE = 3 * ANGLE_DIFF_OF_PHOTOS;

	/**
	 * BAD: hard coded index of angle values in angle array (we are only looking
	 * at one axis here).
	 */
	private static final int ANGLE_INDEX = 0;

	/**
	 * how much images are required per perspective and user in order to allow
	 * training.
	 */
	private static final Integer MIN_AMOUNT_IMAGES_PER_SUBJECT_AND_CLASSIFIER = 3;

	/**
	 * if only frontal images are used. if yes: recording stops immediately
	 * after taking the first pic, so user does not have to press twice.
	 */
	private static final boolean USE_FRONTAL_ONLY = true;
	// TODO extract to settings

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
	private PhotoGyroListener mPhotoGyroListener = new PhotoGyroListener(ANGLE_DIFF_OF_PHOTOS);

	private List<PanshotImage> images = new ArrayList<PanshotImage>();

	private TextView textviewIdentity;

	private Mat mRgba;
	private Mat mGray;

	private float mRelativeFaceSize = 0.4f;
	private int mAbsoluteFaceSize = 0;

	// ================================================================================================================
	// SPECIAL MEMBER

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

		// get info from calling Activity
		Bundle extras = getIntent().getExtras();
		if (extras != null) {
			String value = extras.getString(Statics.FACE_DETECTION_PURPOSE);
			Toast.makeText(this, value, Toast.LENGTH_LONG).show();
		}

		// getWindow().requestFeature(Window.FEATURE_ACTION_BAR);

		setContentView(R.layout.layout_fragment_main_recording);

		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

		mRecognitionSingleton = new RecognitionSingleton();
	}

	@Override
	protected void onResume() {
		super.onResume();

		mOpenCvCameraView = (CameraBridgeViewBase) findViewById(R.id.fd_activity_surface_view);
		mOpenCvCameraView.setCameraIndex(1);
		// ((MainActivity)
		// FaceRecognitionActivity.this).mOpenCvCameraView.setDisplayOrientation(90);
		// ((MainActivity)
		// FaceRecognitionActivity.this).mOpenCvCameraView.setRotation(90);
		mOpenCvCameraView.setCvCameraViewListener(this);

		// create sensor stuff
		SensorComponent.init(this);
		SensorComponent.instance().addObserver(mPhotoGyroListener);

		textviewIdentity = (TextView) findViewById(R.id.textview_identity);
		Preconditions.checkNotNull(textviewIdentity, "textviewIdentity is null.");

		OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_2_4_3, this, mLoaderCallback);

		if (!MediaSaveUtil.isSdCardAvailableRW()) {
			Toast.makeText(this, getResources().getString(R.string.sd_card_not_available), Toast.LENGTH_LONG).show();
			return;
		}

		// updateUiFromCurrentUser();

		Log.d(OldMainActivity.class.getSimpleName(), "CameraFragment.onResume()");
	}

	@Override
	public void onPause() {
		super.onPause();
		if (mOpenCvCameraView != null) {
			mOpenCvCameraView.disableView();
		}
		// stop taking pictures if we're still taking some by now
		stopTakingPictures();
		images.clear();
		Log.d(OldMainActivity.class.getSimpleName(), "CameraFragment.onPause()");
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		Log.v(OldMainActivity.class.getSimpleName(), event.toString());
		switch (keyCode) {
			case KeyEvent.KEYCODE_VOLUME_DOWN:
			case KeyEvent.KEYCODE_VOLUME_UP:
				Log.d(OldMainActivity.class.getSimpleName(), "keyodwn: vol down/up detected");
				// TODO toggle recording
				return true;
			default:
				return false;
		}
	}

	@Override
	public boolean onKeyUp(int keyCode, KeyEvent event) {
		Log.v(OldMainActivity.class.getSimpleName(), event.toString());
		switch (keyCode) {
			case KeyEvent.KEYCODE_VOLUME_DOWN:
			case KeyEvent.KEYCODE_VOLUME_UP:
				return true;
			default:
				return false;
		}
	}

	public void onDestroy() {
		Log.i(TAG, "CameraFragment.onDestroy()");
		mOpenCvCameraView.disableView();
		super.onDestroy();
	}

	// ================================================================================================================
	// METHODS

	private void startTakingPictures() {
		if (mCurrentUser == null) {
			Toast.makeText(this, getResources().getString(R.string.no_user_selected), Toast.LENGTH_SHORT).show();
			return;
		}
		Log.d(OldMainActivity.class.getSimpleName(), "starting image taking...");
		mIsTakingPictures = true;
		mPhotoGyroListener.reset();
		images.clear();
		SensorComponent.instance().start();
		PanshotUtil.playSoundfile(this, R.raw.beep);
		if (!USE_FRONTAL_ONLY) {
			ImageView imageViewRedDot = (ImageView) findViewById(R.id.imageView_redDot);
			imageViewRedDot.setVisibility(ImageView.VISIBLE);
		}
	}

	private void stopTakingPictures() {
		Log.d(OldMainActivity.class.getSimpleName(), "stopping image taking. we recorded " + images.size() + " images.");
		mIsTakingPictures = false;
		SensorComponent.instance().stop();
		if (!USE_FRONTAL_ONLY) {
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
			images.add(panshotImage);
			if (USE_FRONTAL_ONLY) {
				// we've taken 1 image, stop recording automatically right
				// now
				// TODO toggle recording
			}
		}
		return mGray;
	}

	public void detectFacesInRecordedImagesDependingOnAngle() {
		// determine angle index and angle normalization value
		GenericTuple2<Integer, Float[]> angleIndexNormaliser = PanshotUtil.calculateAngleIndexAndNormalizer(
				images.get(0).angleValues, images.get(images.size() - 1).angleValues);
		int angleIndex = angleIndexNormaliser.value1;
		float angleNormalizer = angleIndexNormaliser.value2[angleIndex];
		for (PanshotImage panshotImage : images) {
			// remember recognition related info
			panshotImage.rec.angleIndex = angleIndex;
			// normalize this image's panshot rotation axis' angle values
			float angle = panshotImage.angleValues[angleIndex] - angleNormalizer;
			// select face detection classifier
			CascadeClassifier cascadeClassifier = mJavaDetector_LBPCascadeFrontalface;
			DetectionBasedTracker detectionBasedTracker = mNativeDetector_LBPCascadeFrontalface;
			boolean mirroring = angle < -FRONTAL_MAX_ANGLE;
			boolean usingProfile = Math.abs(angle) > FRONTAL_MAX_ANGLE;
			Mat image = panshotImage.grayImage;
			if (usingProfile) {

				// LBP profile face does not seem to do too good atm, use
				// haarcascades instead
				// cascadeClassifier = ((MainActivity)
				// FaceRecognitionActivity.this).mJavaDetector_LBPCascadeProfileface;
				// detectionBasedTracker = ((MainActivity)
				// FaceRecognitionActivity.this).mNativeDetector_LBPCascadeProfileface;

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

	private void setMinFaceSize(float faceSize) {
		mRelativeFaceSize = faceSize;
		mAbsoluteFaceSize = 0;
	}

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
			if (images.size() > 0) {
				// do face detection for each image
				detectFacesInRecordedImagesDependingOnAngle();
				switch (mClassifierMode) {
					case Training:
						// save images
						DataUtil.savePanshotImages(this, mCurrentUser, images, ANGLE_INDEX, CSV_FILENAME_EXTENSION, SESSION_ID,
								USE_FRONTAL_ONLY, ANGLE_DIFF_OF_PHOTOS);
						break;

					case Recognition:
						// only use images in which faces where
						// detected
						List<PanshotImage> imagesWithFaces = FunUtil.filter(images, new FunFilter<PanshotImage>() {
							@Override
							public boolean filter(PanshotImage _t) {
								return _t.grayFace != null;
							}
						});
						// normalise energy of all images
						if (SharedPrefs.useImageEnergyNormlization(FaceDetectionActivity.this)) {
							FunUtil.map(imagesWithFaces, new Fun<PanshotImage, PanshotImage>() {
								@Override
								public PanshotImage apply(PanshotImage panshotImage) {
									// normalise the face's energy
									// use convolution (kernel = 2D
									// filter) to get image energy
									// (brightness)
									// distribution
									// and normalise face with it
									GenericTuple2<Mat, Mat> normalizedMatEnergy = PanshotUtil.normalizeMatEnergy(
											panshotImage.grayFace, (int) (panshotImage.grayFace.rows() / SharedPrefs
													.getImageEnergyNormalizationSubsamplingFactor(FaceDetectionActivity.this)),
											(int) (panshotImage.grayFace.cols() / SharedPrefs
													.getImageEnergyNormalizationSubsamplingFactor(FaceDetectionActivity.this)),
											255.0);
									panshotImage.grayFace = normalizedMatEnergy.value1;
									// DEBUG save energy image for
									// review
									try {
										File f = MediaSaveUtil.getMediaStorageDirectory(FaceDetectionActivity.this.getResources()
												.getString(R.string.app_media_directory_name));
										MediaSaveUtil.saveMatToJpgFile(new File(f.getAbsolutePath() + "/normalized.jpg"),
												normalizedMatEnergy.value1);
										MediaSaveUtil.saveMatToJpgFile(new File(f.getAbsolutePath() + "/energy.jpg"),
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
						FunUtil.map(imagesWithFaces, new Fun<PanshotImage, PanshotImage>() {
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
						if (imagesWithFaces.size() > 0) {
							// decide which recognition to use
							switch (SharedPrefs.getRecognitionType(FaceDetectionActivity.this)) {
								case KNN: {
									GenericTuple3<User, Integer, Map<User, Integer>> classificationResult = mRecognitionSingleton
											.classifyKnn(imagesWithFaces, SharedPrefs.getKnnK(this), null,
													SharedPrefs.usePca(this), SharedPrefs.getAmountOfPcaFeatures(this),
													ANGLE_DIFF_OF_PHOTOS);
									Toast.makeText(
											this,
											getResources().getString(
													R.string.most_likely_user,
													classificationResult.value1.getName() + " (" + classificationResult.value2
															+ " votes)"), Toast.LENGTH_LONG).show();
									break;
								}

								case SVM: {
									GenericTuple3<User, Double, Map<User, Double>> classificationResult = mRecognitionSingleton
											.classifySvm(imagesWithFaces, SharedPrefs.usePca(this),
													SharedPrefs.getAmountOfPcaFeatures(this), ANGLE_DIFF_OF_PHOTOS);
									Toast.makeText(
											this,
											getResources().getString(
													R.string.most_likely_user,
													classificationResult.value1.getName() + " (" + classificationResult.value2
															+ " votes)"), Toast.LENGTH_LONG).show();
									break;
								}

							}
						} else {
							Toast.makeText(FaceDetectionActivity.this,
									FaceDetectionActivity.this.getResources().getString(R.string.no_faces_detected),
									Toast.LENGTH_LONG).show();
						}
				}
				images.clear();
			}
		}
	}

	private void doRecognition() {

		// load training data
		Log.d(TAG, "CameraFragment.update(): loading training panshot images...");
		List<PanshotImage> trainingPanshotImages = DataUtil.loadTrainingData(this);
		// do image energy normalisation
		if (SharedPrefs.useImageEnergyNormlization(this)) {
			final float subsamplingFactor = SharedPrefs.getImageEnergyNormalizationSubsamplingFactor(this);
			FunUtil.map(trainingPanshotImages, new Fun<PanshotImage, PanshotImage>() {
				@Override
				public PanshotImage apply(PanshotImage panshotImage) {
					// normalise the face's energy
					// use convolution (kernel = 2D filter) to get
					// image energy (brightness) distribution and
					// normalise
					// face with it
					GenericTuple2<Mat, Mat> normalizedMatEnergy = PanshotUtil.normalizeMatEnergy(panshotImage.grayFace,
							(int) (panshotImage.grayFace.rows() / subsamplingFactor),
							(int) (panshotImage.grayFace.cols() / subsamplingFactor), 255.0);
					panshotImage.grayFace = normalizedMatEnergy.value1;
					return panshotImage;
				}
			});
		}
		// separate images to perspectives...
		Map<Integer, TrainingData> trainingdataPerClassifier = new HashMap<Integer, TrainingData>();
		// ... and track amount of images per (subject, perspective)
		Map<GenericTuple2<String, Integer>, Integer> imageAmount = new HashMap<GenericTuple2<String, Integer>, Integer>();
		for (PanshotImage image : trainingPanshotImages) {
			int classifierIndex = RecognitionSingleton.getClassifierIndexForAngle(image.angleValues[image.rec.angleIndex],
					ANGLE_DIFF_OF_PHOTOS);
			if (!trainingdataPerClassifier.containsKey(classifierIndex)) {
				trainingdataPerClassifier.put(classifierIndex, new TrainingData());
			}
			trainingdataPerClassifier.get(classifierIndex).images.add(image);

			// increase amount of images for this subject and
			// perspective
			// would not look so damn complex if Java had more FP...
			GenericTuple2<String, Integer> subjectPerspectiveKey = new GenericTuple2(image.rec.user.getName(), classifierIndex);
			if (!imageAmount.containsKey(subjectPerspectiveKey)) {
				imageAmount.put(subjectPerspectiveKey, 0);
			}
			imageAmount.put(subjectPerspectiveKey, imageAmount.get(subjectPerspectiveKey) + 1);
		}
		for (GenericTuple2<String, Integer> key : imageAmount.keySet()) {
			int amount = imageAmount.get(key);
			if (amount < MIN_AMOUNT_IMAGES_PER_SUBJECT_AND_CLASSIFIER) {
				Toast.makeText(
						this,
						getResources().getString(R.string.too_less_training_data, key.value1, "" + amount, "" + key.value2,
								"" + MIN_AMOUNT_IMAGES_PER_SUBJECT_AND_CLASSIFIER), Toast.LENGTH_LONG).show();
				return;
			}
		}

		// RESIZE images as KNN, SVM etc need images that are of
		// same size
		for (TrainingData trainingData : trainingdataPerClassifier.values()) {
			FunUtil.map(trainingData.images, new Fun<PanshotImage, PanshotImage>() {
				@Override
				public PanshotImage apply(PanshotImage _t) {
					Imgproc.resize(_t.grayFace, _t.grayFace, new Size(SharedPrefs.getFaceWidth(FaceDetectionActivity.this),
							SharedPrefs.getFaceHeight(FaceDetectionActivity.this)));
					return _t;
				}
			});
		}

		// PCA
		if (SharedPrefs.usePca(this)) {
			for (TrainingData trainingData : trainingdataPerClassifier.values()) {
				GenericTuple3<Mat, Mat, Mat> pcaComponents = PCAUtil.pcaCompute(trainingData.images);
				trainingData.pcaMean = pcaComponents.value1;
				trainingData.pcaEigenvectors = pcaComponents.value2;
				trainingData.pcaProjections = pcaComponents.value3;
			}
		}

		// we know we have sufficient training data for each
		// classifier
		switch (SharedPrefs.getRecognitionType(this)) {
			case KNN:
				mRecognitionSingleton.trainKnn(trainingdataPerClassifier, SharedPrefs.usePca(this),
						SharedPrefs.getAmountOfPcaFeatures(this));
				// trainJavaCv(trainingdataPerClassifier);
				break;

			case SVM:
				mRecognitionSingleton.trainSvm(trainingdataPerClassifier, SharedPrefs.usePca(this),
						SharedPrefs.getAmountOfPcaFeatures(this));

			default:
				break;
		}
		Log.i(TAG, "CameraFragment.update(): switching to recognition mode done.");
	}
}
