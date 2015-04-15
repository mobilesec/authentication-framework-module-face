package at.usmile.auth.module.face.service;

import java.io.File;
import java.io.IOException;

import android.app.IntentService;
import android.content.Intent;
import android.content.res.Resources.NotFoundException;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import at.usmile.auth.module.face.R;
import at.usmile.auth.module.face.activity.Statics;
import at.usmile.panshot.SharedPrefs;
import at.usmile.panshot.nu.DataUtil;
import at.usmile.panshot.nu.RecognitionModule;
import at.usmile.panshot.util.MediaSaveUtil;

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

		// prepare back-report intent
		Intent localIntent = new Intent(Statics.TRAINING_SERVICE_BROADCAST_ACTION);

		// // train and persist recognitionmodule
		RecognitionModule recognitionModule = new RecognitionModule();
		recognitionModule.train(this, SharedPrefs.getAngleBetweenClassifiers(this),
				SharedPrefs.getMinAmountOfTrainingImagesPerSubjectAntClassifier(this));
		try {
			File directory = MediaSaveUtil.getMediaStorageDirectory(getResources().getString(
					R.string.app_classifier_directory_name));
			DataUtil.serializeRecognitionModule(directory, recognitionModule);

			localIntent.putExtra(Statics.TRAINING_SERVICE_STATUS, Statics.TRAINING_SERVICE_STATUS_FINISHED);

		} catch (NotFoundException e2) {
			e2.printStackTrace();
			localIntent.putExtra(Statics.TRAINING_SERVICE_STATUS, Statics.TRAINING_SERVICE_STATUS_FAILED);
			localIntent.putExtra(Statics.TRAINING_SERVICE_STATUS_ERROR_STRING, e2.toString());

		} catch (IOException e2) {
			e2.printStackTrace();
			localIntent.putExtra(Statics.TRAINING_SERVICE_STATUS, Statics.TRAINING_SERVICE_STATUS_FAILED);
			localIntent.putExtra(Statics.TRAINING_SERVICE_STATUS_ERROR_STRING, e2.toString());
		}

		// Broadcasts the Intent to receivers in this app
		LocalBroadcastManager.getInstance(this).sendBroadcast(localIntent);
		Log.d(TAG, "TrainingService#onHandleIntent() finished.");
	}
}
