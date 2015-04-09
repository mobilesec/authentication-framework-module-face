package at.usmile.auth.module.face.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import at.usmile.auth.module.face.R;

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
	private static final int REQUEST_CODE_TRAIN = 3;

	@Override
	protected void onCreate(Bundle _savedInstanceState) {
		super.onCreate(_savedInstanceState);
		Log.d(TAG, "onCreate()");

		setContentView(R.layout.layout_activity_face_main);

		Button buttonTrain = (Button) findViewById(R.id.button_record_data);
		buttonTrain.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View _v) {
				Log.d(TAG, "buttonTrain#OnClickListener()");
				// switch to face rec.
				Intent i = new Intent(MainActivity.this, FaceDetectionActivity.class);
				i.putExtra(Statics.FACE_DETECTION_PURPOSE, Statics.FACE_DETECTION_PURPOSE_RECORD_DATA);
				startActivityForResult(i, REQUEST_CODE_TRAIN);
			}
		});

		Button buttonRetrainClassifiers = (Button) findViewById(R.id.button_retrain_classifiers);
		buttonRetrainClassifiers.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View _v) {
				Log.d(TAG, "buttonRetrainClassifiers#OnClickListener()");
				// TODO start background training service, block button until
				// done
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
				startActivityForResult(i, REQUEST_CODE_TRAIN);
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
