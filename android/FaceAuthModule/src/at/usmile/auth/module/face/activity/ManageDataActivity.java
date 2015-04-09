package at.usmile.auth.module.face.activity;

import java.util.List;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import at.usmile.auth.module.face.R;
import at.usmile.panshot.User;
import at.usmile.panshot.nu.FaceModuleUtil;

/**
 * Allows user to manipulate (e.g. delete) recorded auth data.
 * 
 * @author Rainhard Findling
 * @date 7 Apr 2015
 * @version 1
 */
public class ManageDataActivity extends Activity {

	private final String TAG = "ManageDataActivity";

	private int mSpinnerSelectedIndex = -1;

	@Override
	protected void onCreate(Bundle _savedInstanceState) {
		super.onCreate(_savedInstanceState);
		Log.d(TAG, "onCreate()");

		setContentView(R.layout.layout_activity_face_manage_data);

		// load users
		final List<User> users = FaceModuleUtil.loadExistingUsers(this, null, null);
		// delete user UI components
		final Spinner spinnerIdentity = (Spinner) findViewById(R.id.spinner_identity);
		final ArrayAdapter<String> spinnerAdapterUsers = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item,
				android.R.id.text1);
		// Specify the layout to use when the list of choices appears
		spinnerAdapterUsers.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		// Apply the adapter to the spinner
		spinnerIdentity.setAdapter(spinnerAdapterUsers);
		spinnerIdentity.setOnItemSelectedListener(new OnItemSelectedListener() {
			@Override
			public void onItemSelected(AdapterView<?> _arg0, View _arg1, int _arg2, long _arg3) {
				Log.d(ManageDataActivity.class.getSimpleName(), "spinner.onItemSelected()");
				mSpinnerSelectedIndex = _arg2;
			}

			@Override
			public void onNothingSelected(AdapterView<?> _arg0) {
				Log.d(ManageDataActivity.class.getSimpleName(), "spinner.onNothingSelected()");
				mSpinnerSelectedIndex = -1;
			}
		});
		final Button buttonDeleteIdentity = (Button) findViewById(R.id.button_delete_identity);
		buttonDeleteIdentity.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View _v) {
				Log.d(ManageDataActivity.class.getSimpleName(), "mButtonDeleteIdentity.setOnClickListener()");
				Log.d(ManageDataActivity.class.getSimpleName(), "delete user: " + users.get(mSpinnerSelectedIndex));

				// "are you sure" dialogue box
				DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						switch (which) {
							case DialogInterface.BUTTON_POSITIVE:
								Log.d(ManageDataActivity.class.getSimpleName(), "buttonDeleteIdentity.YES");
								break;
							case DialogInterface.BUTTON_NEGATIVE:
								Log.d(ManageDataActivity.class.getSimpleName(), "buttonDeleteIdentity.NO");
								break;
						}
					}
				};
				AlertDialog.Builder builder = new AlertDialog.Builder(ManageDataActivity.this);
				builder.setMessage(
						ManageDataActivity.this.getResources().getString(R.string.really_delete_user,
								users.get(mSpinnerSelectedIndex).getName()))
						.setPositiveButton(ManageDataActivity.this.getResources().getString(R.string.yes), dialogClickListener)
						.setNegativeButton(ManageDataActivity.this.getResources().getString(R.string.no), dialogClickListener)
						.show();
			}
		});
		// update UI
		if (users == null) {
			buttonDeleteIdentity.setEnabled(false);
			spinnerIdentity.setEnabled(false);
		} else {
			buttonDeleteIdentity.setEnabled(true);
			spinnerIdentity.setEnabled(true);

			spinnerAdapterUsers.clear();
			for (User u : users) {
				spinnerAdapterUsers.add(u.getName());
			}
			spinnerAdapterUsers.notifyDataSetChanged();
		}
	}

	@Override
	protected void onResume() {
		super.onResume();
	}

}
