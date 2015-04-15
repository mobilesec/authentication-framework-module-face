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

	@Override
	protected void onCreate(Bundle _savedInstanceState) {
		super.onCreate(_savedInstanceState);
		Log.d(TAG, "onCreate()");

		setContentView(R.layout.layout_activity_face_main);

		Button buttonRecordData = (Button) findViewById(R.id.button_record_data);
		buttonRecordData.setOnClickListener(new OnClickListener() {
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
				mButtonRetrainClassifiersBackground.setEnabled(false);
				mButtonRetrainClassifiersForeground.setEnabled(false);
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

								mButtonRetrainClassifiersBackground.setEnabled(false);
								mButtonRetrainClassifiersForeground.setEnabled(false);

								RecognitionModule recognitionModule = new RecognitionModule();

								// train and persist recognitionmodule
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

								mButtonRetrainClassifiersBackground.setEnabled(true);
								mButtonRetrainClassifiersForeground.setEnabled(true);

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

		Button buttonTestFaceAuth = (Button) findViewById(R.id.button_test_authentication);
		buttonTestFaceAuth.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View _v) {
				Log.d(TAG, "buttonTestFaceAuth#OnClickListener()");

				// switch to face rec.
				Intent i = new Intent(MainActivity.this, FaceDetectionActivity.class);
				i.putExtra(Statics.FACE_DETECTION_PURPOSE, Statics.FACE_DETECTION_PURPOSE_RECOGNITION_TEST);
				startActivityForResult(i, REQUEST_CODE_FACE_DETECTION);
			}
		});

		Button buttonManageData = (Button) findViewById(R.id.button_manage_data);
		buttonManageData.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View _v) {
				Log.d(TAG, "buttonManageData#OnClickListener()");
				Intent i = new Intent(MainActivity.this, ManageDataActivity.class);
				startActivityForResult(i, REQUEST_CODE_MANAGE_DATA);
			}
		});
		Button buttonSettings = (Button) findViewById(R.id.button_settings);
		buttonSettings.setOnClickListener(new OnClickListener() {
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
			buttonRecordData.setEnabled(false);
			mButtonRetrainClassifiersBackground.setEnabled(false);
			mButtonRetrainClassifiersForeground.setEnabled(false);
			buttonTestFaceAuth.setEnabled(false);
		}

		// broadcast receiver
		BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
			// Called when the BroadcastReceiver gets an Intent it's registered
			// to receive
			@Override
			public void onReceive(Context context, Intent intent) {
				Log.d(TAG, "broadcastReceiver#onReceive()");
				mButtonRetrainClassifiersBackground.setEnabled(true);
				mButtonRetrainClassifiersForeground.setEnabled(true);
			}
		};
		// The filter's action is BROADCAST_ACTION
		IntentFilter intentFilter = new IntentFilter(Statics.TRAINING_SERVICE_BROADCAST_ACTION);
		// Registers the DownloadStateReceiver and its intent filters
		LocalBroadcastManager.getInstance(this).registerReceiver(broadcastReceiver, intentFilter);
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
