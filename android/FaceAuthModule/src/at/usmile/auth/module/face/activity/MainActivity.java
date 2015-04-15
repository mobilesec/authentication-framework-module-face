package at.usmile.auth.module.face.activity;

import java.io.File;
import java.io.IOException;

import org.opencv.android.InstallCallbackInterface;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources.NotFoundException;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import at.usmile.auth.module.face.R;
import at.usmile.auth.module.face.service.TrainingService;
import at.usmile.panshot.SharedPrefs;
import at.usmile.panshot.nu.DataUtil;
import at.usmile.panshot.nu.RecognitionModule;
import at.usmile.panshot.util.MediaSaveUtil;

/**
 * Entry point if user opens app to train or change settings (= if not called by
 * auth framework)
 * 
 * @author Rainhard Findling
 * @date 7 Apr 2015
 * @version 1
 */
public class MainActivity extends Activity {

	private static final String TAG = "MainActivity";

	// Intent requst codes
	private static final int REQUEST_CODE_MANAGE_DATA = 1;
	private static final int REQUEST_CODE_SETTINGS = 2;
	private static final int REQUEST_CODE_FACE_DETECTION = 3;

	/**
	 * starts face authentication training in a separate service. Is deactivated
	 * while training is ongoing.
	 */
	private Button mButtonRetrainClassifiersBackground;

	/**
	 * does face authentication training in the UI thread. mainly for testing
	 * purposes. is deactivated while trainig.
	 */
	private Button mButtonRetrainClassifiersForeground;

	private Button mButtonRecordData;

	private Button mButtonTestFaceAuth;

	private Button mButtonSettings;

	private Button mButtonManageData;

	private ProgressBar mProgressbarTrainingOngoing;

	private TextView mTextviewTrainingOngoing;

	@Override
	protected void onCreate(Bundle _savedInstanceState) {
		super.onCreate(_savedInstanceState);
		Log.d(TAG, "onCreate()");

		setContentView(R.layout.layout_activity_face_main);

		mButtonRecordData = (Button) findViewById(R.id.button_record_data);
		mButtonRecordData.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View _v) {
				Log.d(TAG, "buttonTrain#OnClickListener()");
				// switch to face rec.
				Intent i = new Intent(MainActivity.this, FaceDetectionActivity.class);
				i.putExtra(Statics.FACE_DETECTION_PURPOSE, Statics.FACE_DETECTION_PURPOSE_RECORD_DATA);
				startActivityForResult(i, REQUEST_CODE_FACE_DETECTION);
			}
		});

		mButtonRetrainClassifiersBackground = (Button) findViewById(R.id.button_retrain_classifiers_background);
		// TODO eg use file in FS as lock for this button (only unlock button if
		// file does not exist at app start)
		mButtonRetrainClassifiersBackground.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View _v) {
				Log.d(TAG, "mButtonRetrainClassifiersBackground#OnClickListener()");
				// trigger training service
				Intent i = new Intent(MainActivity.this, TrainingService.class);
				startService(i);
				setTrainingOngoingUIEnabled(false);
			}
		});

		mButtonRetrainClassifiersForeground = (Button) findViewById(R.id.button_retrain_classifiers_foreground);
		mButtonRetrainClassifiersForeground.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View _v) {
				Log.d(TAG, "buttonRetrainClassifiersForeground#OnClickListener()");

				// "are you sure" dialogue box
				DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						switch (which) {

							case DialogInterface.BUTTON_POSITIVE:
								Log.d(ManageDataActivity.class.getSimpleName(), "dialogClickListener.YES");

								setTrainingOngoingUIEnabled(false);

								// train and persist recognitionmodule
								RecognitionModule recognitionModule = new RecognitionModule();
								Toast.makeText(MainActivity.this, "Training started, may take a while...", Toast.LENGTH_LONG)
										.show();
								recognitionModule.train(MainActivity.this,
										SharedPrefs.getAngleBetweenClassifiers(MainActivity.this),
										SharedPrefs.getMinAmountOfTrainingImagesPerSubjectAntClassifier(MainActivity.this));
								try {
									File directory = MediaSaveUtil.getMediaStorageDirectory(getResources().getString(
											R.string.app_classifier_directory_name));
									DataUtil.serializeRecognitionModule(directory, recognitionModule);
								} catch (NotFoundException e2) {
									e2.printStackTrace();
									Toast.makeText(MainActivity.this, "Storing failed: NotFoundException", Toast.LENGTH_LONG)
											.show();
								} catch (IOException e2) {
									e2.printStackTrace();
									Toast.makeText(MainActivity.this, "Storing failed: IOException", Toast.LENGTH_LONG).show();
								}
								Toast.makeText(MainActivity.this, "Training finished.", Toast.LENGTH_LONG).show();

								setTrainingOngoingUIEnabled(true);

								break;

							case DialogInterface.BUTTON_NEGATIVE:
								Log.d(ManageDataActivity.class.getSimpleName(), "dialogClickListener.NO");
								break;
						}
					}
				};
				AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
				builder.setMessage(MainActivity.this.getResources().getString(R.string.ask_train_foreground))
						.setPositiveButton(MainActivity.this.getResources().getString(R.string.yes), dialogClickListener)
						.setNegativeButton(MainActivity.this.getResources().getString(R.string.no), dialogClickListener).show();

			}
		});

		mButtonTestFaceAuth = (Button) findViewById(R.id.button_test_authentication);
		mButtonTestFaceAuth.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View _v) {
				Log.d(TAG, "buttonTestFaceAuth#OnClickListener()");

				// switch to face rec.
				Intent i = new Intent(MainActivity.this, FaceDetectionActivity.class);
				i.putExtra(Statics.FACE_DETECTION_PURPOSE, Statics.FACE_DETECTION_PURPOSE_RECOGNITION_TEST);
				startActivityForResult(i, REQUEST_CODE_FACE_DETECTION);
			}
		});

		mButtonManageData = (Button) findViewById(R.id.button_manage_data);
		mButtonManageData.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View _v) {
				Log.d(TAG, "buttonManageData#OnClickListener()");
				Intent i = new Intent(MainActivity.this, ManageDataActivity.class);
				startActivityForResult(i, REQUEST_CODE_MANAGE_DATA);
			}
		});
		mButtonSettings = (Button) findViewById(R.id.button_settings);
		mButtonSettings.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View _v) {
				Log.d(TAG, "buttonSettings#OnClickListener()");
				Intent i = new Intent(MainActivity.this, SettingsActivity.class);
				startActivityForResult(i, REQUEST_CODE_SETTINGS);
			}
		});

		// OpenCV
		if (!OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_2_4_3, this, new LoaderCallbackInterface() {
			@Override
			public void onPackageInstall(int _operation, InstallCallbackInterface _callback) {
			}

			@Override
			public void onManagerConnected(int _status) {
			}
		})) {
			Log.e(TAG, "onCreate: cannot connect to opencv");
			Toast.makeText(MainActivity.this, MainActivity.this.getResources().getText(R.string.error_opencv_not_loaded),
					Toast.LENGTH_LONG).show();
			// modify UI
			mButtonRetrainClassifiersBackground.setEnabled(false);
			mButtonRetrainClassifiersForeground.setEnabled(false);
			mButtonRecordData.setEnabled(false);
			mButtonTestFaceAuth.setEnabled(false);
		}

		// broadcast receiver
		BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
			// Called when the BroadcastReceiver gets an Intent it's registered
			// to receive
			@Override
			public void onReceive(Context context, Intent _intent) {
				Log.d(TAG, "broadcastReceiver#onReceive()");
				setTrainingOngoingUIEnabled(true);

				// get info from calling Activity
				Bundle extras = _intent.getExtras();
				if (extras != null) {
					String status = extras.getString(Statics.TRAINING_SERVICE_STATUS);

					if (status.equals(Statics.TRAINING_SERVICE_STATUS_FAILED)) {
						String errorString = extras.getString(Statics.TRAINING_SERVICE_STATUS_ERROR_STRING);
						AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
						builder.setTitle(MainActivity.this.getResources().getString(R.string.error))
								.setMessage(
										MainActivity.this.getResources().getString(R.string.error_service_training, errorString))
								.setPositiveButton(MainActivity.this.getResources().getString(R.string.ok),
										new DialogInterface.OnClickListener() {
											@Override
											public void onClick(DialogInterface _dialog, int _which) {
											}
										}).show();
					}
				}
			}
		};

		mTextviewTrainingOngoing = (TextView) findViewById(R.id.textview_training_ongoing);
		mProgressbarTrainingOngoing = (ProgressBar) findViewById(R.id.progressbar_training_ongoing);

		// The filter's action is BROADCAST_ACTION
		IntentFilter intentFilter = new IntentFilter(Statics.TRAINING_SERVICE_BROADCAST_ACTION);
		// Registers the DownloadStateReceiver and its intent filters
		LocalBroadcastManager.getInstance(this).registerReceiver(broadcastReceiver, intentFilter);
	}

	/**
	 * control how UI looks if training is/is not ongoing.
	 * 
	 * @param _uiEnabled
	 */
	public void setTrainingOngoingUIEnabled(boolean _uiEnabled) {
		mButtonManageData.setEnabled(_uiEnabled);
		mButtonRecordData.setEnabled(_uiEnabled);
		mButtonRetrainClassifiersBackground.setEnabled(_uiEnabled);
		mButtonRetrainClassifiersForeground.setEnabled(_uiEnabled);
		mButtonSettings.setEnabled(_uiEnabled);
		mButtonTestFaceAuth.setEnabled(_uiEnabled);

		int visibility = _uiEnabled ? View.INVISIBLE : View.VISIBLE;
		mTextviewTrainingOngoing.setVisibility(visibility);
		mProgressbarTrainingOngoing.setVisibility(visibility);
	}

	@Override
	protected void onActivityResult(int _requestCode, int _resultCode, Intent _data) {
		switch (_requestCode) {
			case REQUEST_CODE_MANAGE_DATA:
				Log.d(TAG, "onActivityResult#requestCodeManageData");
				// TODO
				break;

			default:
				super.onActivityResult(_requestCode, _resultCode, _data);
				break;
		}
	}

	@Override
	protected void onResume() {
		super.onResume();
	}

}
