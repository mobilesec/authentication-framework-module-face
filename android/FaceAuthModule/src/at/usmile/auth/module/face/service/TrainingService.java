package at.usmile.auth.module.face.service;

import android.app.IntentService;
import android.content.Intent;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import at.usmile.auth.module.face.activity.Statics;

/**
 * Service that trains classifiers needed for face recognition, as training
 * usually is a longer process. *
 * 
 * @author Rainhard Findling
 * @date 13 Apr 2015
 * @version 1
 */
public class TrainingService extends IntentService {

	// ================================================================================================================
	// MEMBERS

	private static final String TAG = "TrainingService";

	// ================================================================================================================
	// METHODS

	public TrainingService() {
		super(TrainingService.class.getSimpleName());
	}

	@Override
	protected void onHandleIntent(Intent workIntent) {
		Log.d(TAG, "TrainingService#onHandleIntent()");

		// Do work here, based on the contents of dataString

		// send info that training has finished
		Intent localIntent = new Intent(Statics.TRAINING_SERVICE_BROADCAST_ACTION).putExtra(Statics.TRAINING_SERVICE_STATUS,
				Statics.TRAINING_SERVICE_STATUS_FINISHED);
		// Broadcasts the Intent to receivers in this app
		LocalBroadcastManager.getInstance(this).sendBroadcast(localIntent);

		Log.d(TAG, "TrainingService#onHandleIntent() finished.");
	}
}
