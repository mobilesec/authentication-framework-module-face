package at.usmile.auth.module.face.activity;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import at.usmile.auth.module.face.R;

/**
 * Allows user to change some authentication settings.
 * 
 * @author Rainhard Findling
 * @date 7 Apr 2015
 * @version 1
 */
public class SettingsActivity extends Activity {

	private final String TAG = "SettingsActivity";

	@Override
	protected void onCreate(Bundle _savedInstanceState) {
		super.onCreate(_savedInstanceState);
		Log.d(TAG, "onCreate()");

		setContentView(R.layout.layout_activity_face_settings);
	}

	@Override
	protected void onResume() {
		super.onResume();
	}

}
