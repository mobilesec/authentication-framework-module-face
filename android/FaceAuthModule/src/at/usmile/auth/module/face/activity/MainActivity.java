package at.usmile.auth.module.face.activity;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import at.usmile.auth.module.face.R;

public class MainActivity extends Activity {

	private final String TAG = "MainActivity";

	@Override
	protected void onCreate(Bundle _savedInstanceState) {
		super.onCreate(_savedInstanceState);
		Log.d(TAG, "onCreate()");

		setContentView(R.layout.layout_activity_face_main);

		Button buttonTrain = (Button) findViewById(R.id.button_train);
		buttonTrain.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View _v) {
				Log.d(TAG, "buttonTrain#OnClickListener()");
				// TODO trigger intent to camera activity with training here
			}
		});
		Button buttonManageData = (Button) findViewById(R.id.button_manage_data);
		buttonManageData.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View _v) {
				Log.d(TAG, "buttonManageData#OnClickListener()");
				// TODO trigger intent to data manage activity here
			}
		});
		Button buttonSettings = (Button) findViewById(R.id.button_settings);
		buttonSettings.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View _v) {
				Log.d(TAG, "buttonSettings#OnClickListener()");
				// TODO trigger intent to settings here
			}
		});
	}

	@Override
	protected void onResume() {
		super.onResume();
	}

}
