package at.usmile.auth.module.face.activity;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;
import at.usmile.auth.module.face.R;

/**
 * Allows user to manipulate (delete) recorded auth data.
 * 
 * @author Rainhard Findling
 * @date 7 Apr 2015
 * @version 1
 */
public class ManageDataActivity extends Activity {

	private final String TAG = "ManageDataActivity";

	@Override
	protected void onCreate(Bundle _savedInstanceState) {
		super.onCreate(_savedInstanceState);
		Log.d(TAG, "onCreate()");

		setContentView(R.layout.layout_activity_face_manage_data);

		// get info from calling Activity
		Bundle extras = getIntent().getExtras();
		if (extras != null) {
			String value = extras.getString(Statics.FACE_DETECTION_PURPOSE);
			Toast.makeText(this, value, Toast.LENGTH_LONG).show();
		}
	}

	@Override
	protected void onResume() {
		super.onResume();
	}

}
