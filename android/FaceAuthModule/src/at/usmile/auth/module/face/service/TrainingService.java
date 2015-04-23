package at.usmile.auth.module.face.service;

import java.io.File;
import java.io.IOException;
import java.util.Map;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources.NotFoundException;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import at.usmile.auth.module.face.R;
import at.usmile.panshot.SharedPrefs;
import at.usmile.panshot.Statics;
import at.usmile.panshot.recognition.RecognitionModule;
import at.usmile.panshot.util.DataUtil;
import at.usmile.panshot.util.ServiceUtil;
import at.usmile.tuple.GenericTuple2;

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

		// load training data
		float angleBetweenClassifiers = SharedPrefs.getAngleBetweenClassifiers(this);
		int minAmountOfTrainingImagesPerSubjectAntClassifier = SharedPrefs
				.getMinAmountOfTrainingImagesPerSubjectAntClassifier(this);
		RecognitionModule recognitionModule = new RecognitionModule();
		recognitionModule.loadTrainingData(this, angleBetweenClassifiers, minAmountOfTrainingImagesPerSubjectAntClassifier,
				SharedPrefs.isFrontalOnly(this));

		// do not have enough training data, notify and abort
		GenericTuple2<Boolean, Map<GenericTuple2<String, Integer>, Integer>> isEnoughTrainingDataPerPerspective = recognitionModule
				.isEnoughTrainingDataPerPerspective(this, minAmountOfTrainingImagesPerSubjectAntClassifier);
		if (!isEnoughTrainingDataPerPerspective.value1) {
			localIntent.putExtra(Statics.TRAINING_SERVICE_STATUS, Statics.TRAINING_SERVICE_STATUS_TOO_LESS_DATA);
			localIntent.putExtra(Statics.TRAINING_SERVICE_STATUS_TOO_LESS_DATA_DETAILS, isEnoughTrainingDataPerPerspective);
		}

		// train and persist recognitionmodule
		else {
			recognitionModule.train(this, angleBetweenClassifiers, minAmountOfTrainingImagesPerSubjectAntClassifier);
			try {
				File directory = DataUtil.getMediaStorageDirectory(getResources().getString(
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
		}

		// Broadcasts the Intent to receivers in this app
		LocalBroadcastManager.getInstance(this).sendBroadcast(localIntent);
		Log.d(TAG, "TrainingService#onHandleIntent() finished.");
	}

	public static boolean isServiceRunning(Context _context) {
		return ServiceUtil.isServiceRunning(_context, TrainingService.class);
	}
}
