package at.usmile.auth.module.face;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import at.usmile.auth.framework.api.AbstractAuthenticationModule;
import at.usmile.auth.framework.api.AuthenticationStatusData;
import at.usmile.auth.framework.api.AuthenticationStatusData.Status;
import at.usmile.auth.module.face.activity.FaceDetectionActivity;
import at.usmile.auth.module.face.activity.Statics;

public class FaceAuthenticationModule extends AbstractAuthenticationModule {

	public static final String START_AUTHENTICATION = "START_AUTHENTICATION";
	public static final String ON_AUTHENTICATION = "ON_AUTHENTICATION";
	public static final String CONFIDENCE = "CONFIDENCE";

	// ================================================================================================================
	// MEMBERS

	private String TAG = "FaceModule";

	/**
	 * receives authentication information from the explicit authentication
	 * Activity.
	 */
	private BroadcastReceiver authenticationReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			Log.d(TAG, "onReceiveBroadcast(" + intent + ")");

			// reported confidence from the used authentication
			double confidence = intent.getDoubleExtra(CONFIDENCE, 0.0);
			Log.d(TAG, "confidence = " + confidence);

			// push update to framework
			publishUpdate(new AuthenticationStatusData().status(Status.OPERATIONAL).confidence(confidence));
		}
	};

	// ================================================================================================================
	// METHODS

	@Override
	public void onCreate() {
		super.onCreate();

		LocalBroadcastManager.getInstance(this).registerReceiver(authenticationReceiver, new IntentFilter(ON_AUTHENTICATION));
	}

	@Override
	public void onDestroy() {
		LocalBroadcastManager.getInstance(this).unregisterReceiver(authenticationReceiver);

		super.onDestroy();
	}

	@Override
	protected void onUpdateAuthenticationStatus(int reason) {
		Log.d(TAG, "onUpdateAuthenticationStatus(" + reason + ")");

		// TODO explain
		sendBroadcast(new Intent(START_AUTHENTICATION));

		// switch to your target Activity here
		Intent i = new Intent(this, FaceDetectionActivity.class);
		// state that we do it for authentication purposes
		i.putExtra(Statics.FACE_DETECTION_PURPOSE, Statics.FACE_DETECTION_PURPOSE_AUTHENTICATION);
		// need to set this as the module is a service, not a regular activity
		i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		startActivity(i);

		Log.d(TAG, "onUpdateAuthenticationStatus done.");
	}
}
