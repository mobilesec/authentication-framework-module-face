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

		Button buttonDone = (Button) findViewById(R.id.button_done);
		buttonDone.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View _v) {
				Log.d(TAG, "buttonDone#OnClickListener()");
				Intent returnIntent = new Intent();
				// returnIntent.putExtra("result", -1);
				setResult(RESULT_OK, returnIntent);
				finish();
			}
		});
	}

	@Override
	protected void onResume() {
		super.onResume();
	}

}
