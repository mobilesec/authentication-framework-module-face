package at.fhooe.facelogin.recognition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import at.fhooe.facelogin.detection.model.Pose;
import at.fhooe.facelogin.recognition.api.ClassifiedData;
import at.fhooe.facelogin.recognition.api.Face;
import at.fhooe.facelogin.recognition.api.RecognitionProbabiliy;
import at.fhooe.facelogin.recognition.api.RecognitionResult;
import at.fhooe.facelogin.recognition.api.TrainingFace;
import at.fhooe.facelogin.recognition.api.UnclassifiedData;
import at.fhooe.facelogin.recognition.api.User;

/**
 * Can be trained (use {@link #add(ClassifiedData)} first to add all training
 * data, then use {@link #train()} to train the classifier), and can classify
 * data using {@link #classify(UnclassifiedData)} afterwards.
 * 
 * @author Rainhard Findling
 * @date 29.02.2012
 * @version 1
 */
public class FaceRecognitionComponent {
	private static final Logger					LOGGER								= LoggerFactory
																							.getLogger(FaceRecognitionComponent.class);
	/** rangle (angle) for which one classifier gets used. */
	private static final Float					CLASSIFIER_RANGE_ANGLE				= 0.34906585f;
	/** maximum angle which will get classified (0.0f is frontal face). */
	private static final Float					CLASSIFIER_MAX_ANGLE				= (float) (Math.PI / 2);
	/** which angle (index in gyrovalues) to use for classifier chosing */
	private static final int					GYRO_ANGLE_INDEX					= 1;
	/**
	 * each classifier the {@link FaceRecognitionComponent} has when trying to
	 * call {@link #train()} must at least have this many {@link TrainingFace}s.
	 */
	public static final int						MIN_AMOUNT_OF_FACES_PER_CLASSIFIER	= 3;

	// ================================================================================================================
	// MEMBERS

	/** different face recognizers for different poses. */
	private HashMap<Integer, FaceRecognizer>	mFaceRecognizers					= new HashMap<Integer, FaceRecognizer>();

	// ================================================================================================================
	// METHODS

	/**
	 * Adds this classified data to the classified data the classifier already
	 * knows. Training is done with all classified data added totally when
	 * {@link #train()} is called.
	 * 
	 * @param trainingFileName
	 *            the given training text index file
	 */
	public void add(ClassifiedData _classifiedData) {
		for (TrainingFace f : _classifiedData.getPersonFaces()) {
			// find out which classifier to use
			FaceRecognizer faceRecognizer = getFaceRecognizerForPose(f.getPose());
			if (faceRecognizer != null) {
				faceRecognizer.add(f);
			} else {
				LOGGER.error("cannot add this face " + f + " as there is no classifier for this pose.");
			}
		}
	}

	/**
	 * Trains the classifier with all classified data added previously via
	 * {@link #add(ClassifiedData)}.
	 */
	public void train() {
		// train all recognizers
		int i = 0;
		for (FaceRecognizer r : mFaceRecognizers.values()) {
			r.train(i++);
		}
	}

	/**
	 * Classifies a set of unclassified faces.
	 * 
	 * @param _data
	 * @return
	 */
	public RecognitionResult classify(UnclassifiedData _data) {
		RecognitionResult result = new RecognitionResult(new ArrayList<RecognitionProbabiliy>());
		HashMap<User, List<RecognitionProbabiliy>> personRecoProbMap = new HashMap<User, List<RecognitionProbabiliy>>();
		for (Face f : _data.getFaces()) {
			// find out which classifier to use
			FaceRecognizer faceRecognizer = getFaceRecognizerForPose(f.getPose());
			if (faceRecognizer == null || faceRecognizer.getTrainingFaces().size() < MIN_AMOUNT_OF_FACES_PER_CLASSIFIER) {
				// either: angle is too big, or:
				// this classifier was created new, it does not have enough
				// training faces to classify: skip this face
				LOGGER.error("did not classify this face: " + f);
				LOGGER.error("recognizer==null: " + (faceRecognizer == null) + ", trainingFaces="
						+ ((faceRecognizer == null) ? "null" : faceRecognizer.getTrainingFaces().size()));
				continue;
			}
			// result.getPersonProbabilities().addAll(faceRecognizer.classify(f));
			// for each probably detected person, add the probability to the
			// person array
			List<RecognitionProbabiliy> l = faceRecognizer.classify(f);
			for (RecognitionProbabiliy p : l) {
				List<RecognitionProbabiliy> list = personRecoProbMap.get(p.getPerson());
				if (list == null) {
					personRecoProbMap.put(p.getPerson(), new ArrayList<RecognitionProbabiliy>());
					list = personRecoProbMap.get(p.getPerson());
				}
				list.add(p);
			}
		}
		// now find out which persons were most likely detected
		for (List<RecognitionProbabiliy> list : personRecoProbMap.values()) {
			double totalProb = 0;
			for (RecognitionProbabiliy r : list) {
				// simply sum up confidence
				// could also do average here
				totalProb += r.getProbability();
			}
			// add probability to final result
			result.getPersonProbabilities().add(new RecognitionProbabiliy(list.get(0).getPerson(), totalProb));
		}
		// sort list
		Collections.sort(result.getPersonProbabilities(), new java.util.Comparator<RecognitionProbabiliy>() {
			public int compare(RecognitionProbabiliy _lhs, RecognitionProbabiliy _rhs) {
				return _lhs.compareTo(_rhs);
			}
		});
		LOGGER.info("Pan Shot Recognition done: " + result.getPersonProbabilities());
		return result;
	}

	public HashMap<Integer, FaceRecognizer> getFaceRecognizers() {
		return mFaceRecognizers;
	}

	/**
	 * Get the correct classifier for a given pose. E.g. profile face recognizer
	 * != frontal face recognizer.
	 * 
	 * @param _p
	 * @return
	 */
	private FaceRecognizer getFaceRecognizerForPose(Pose _p) {
		// angle to work with
		float angle = _p.getGyroValues()[GYRO_ANGLE_INDEX];
		float originalAngle = angle;
		if (Math.abs(angle) > CLASSIFIER_MAX_ANGLE) {
			LOGGER.error("this angle is too big, no classifier gets created for it: " + angle);
			return null;
		}

		// dummy version, only uses 1 classifier in total
		// if (mFaceRecognizers.keySet().size() == 0) {
		// mFaceRecognizers.put(0, new FaceRecognizer());
		// }
		// return mFaceRecognizers.get(0);

		// CAREFULL: if choosing the face detection and face recognition angles
		// wrong, shifting this angle could be dangerous
		// as for exactly one classifier mirrored and nonmirrored faces
		// are possible input. this can lead to a person A having only e.g.
		// nonmirrored images, and a person B having only mirrored images for
		// training. a nonmirrored image of person B then has higher possibility
		// of being classified as being originated by person A.
		if (angle < 0) {
			angle -= CLASSIFIER_RANGE_ANGLE / 2;
		} else {
			angle += CLASSIFIER_RANGE_ANGLE / 2;
		}

		// Integer key = (int) Math.abs(angle / CLASSIFIER_RANGE_ANGLE);
		Integer key = (int) (angle / CLASSIFIER_RANGE_ANGLE);
		LOGGER.debug("angle " + originalAngle + "(" + originalAngle / 3.14f * 180f + " degree) uses key " + key);
		if (mFaceRecognizers.get(key) == null) {
			LOGGER.trace("creating new Classifier for key " + key);
			mFaceRecognizers.put(key, new FaceRecognizer());
		}
		return mFaceRecognizers.get(key);
	}

	/**
	 * Releases the saved training images.
	 */
	public void clear() {
		for (FaceRecognizer r : mFaceRecognizers.values()) {
			r.clear();
		}
		mFaceRecognizers.clear();
	}
}
