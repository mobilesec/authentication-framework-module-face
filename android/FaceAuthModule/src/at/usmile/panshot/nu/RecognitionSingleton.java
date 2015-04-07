package at.usmile.panshot.nu;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.util.Log;
import at.usmile.panshot.PanshotImage;
import at.usmile.panshot.User;
import at.usmile.panshot.recognition.TrainingData;
import at.usmile.panshot.recognition.knn.DistanceMetric;
import at.usmile.panshot.recognition.knn.KnnClassifier;
import at.usmile.panshot.recognition.svm.SvmClassifier;
import at.usmile.tuple.GenericTuple2;
import at.usmile.tuple.GenericTuple3;

public class RecognitionSingleton {
	private static final Logger LOGGER = LoggerFactory.getLogger(RecognitionSingleton.class);

	// ================================================================================================================
	// MEMBERS

	/**
	 * classifiers get created when switching from training to classification
	 * and destroyed when switching back.
	 */
	private Map<Integer, SvmClassifier> mSvmClassifiers = new HashMap<Integer, SvmClassifier>();
	private Map<Integer, KnnClassifier> mKnnClassifiers = new HashMap<Integer, KnnClassifier>();

	// ================================================================================================================
	// METHODS

	/**
	 * @return the index of the classifier in {@link #mSvmClassifiers} which
	 *         corresponds to the angle.
	 */
	public static int getClassifierIndexForAngle(float _normalizedAngle, float _classifierSeparationAngle) {
		return (int) (_normalizedAngle / _classifierSeparationAngle);
	}

	public void trainKnn(Map<Integer, TrainingData> _trainingdataPerClassifier, boolean _usePca, int _pcaAmountOfFeatures) {
		for (Integer classifierIndex : _trainingdataPerClassifier.keySet()) {
			// ensure classifier exists
			TrainingData trainingData = _trainingdataPerClassifier.get(classifierIndex);
			if (!mKnnClassifiers.containsKey(classifierIndex)) {
				mKnnClassifiers.put(classifierIndex, new KnnClassifier());
			}
			KnnClassifier classifier = mKnnClassifiers.get(classifierIndex);
			classifier.train(trainingData, _usePca, _pcaAmountOfFeatures);
		}
	}

	public GenericTuple3<User, Integer, Map<User, Integer>> classifyKnn(List<PanshotImage> _images, int _k,
			DistanceMetric _distanceMetric, boolean _usePca, int _pcaAmountOfFeatures, float _classifierSeparationAngle) {
		// classify each image, combine votings
		Map<User, Integer> votings = new HashMap<User, Integer>();
		GenericTuple2<User, Integer> mostVotedUser = null;
		for (PanshotImage image : _images) {
			int classifierIndex = getClassifierIndexForAngle(image.angleValues[image.rec.angleIndex], _classifierSeparationAngle);
			if (!mKnnClassifiers.containsKey(classifierIndex)) {
				// skip, we cannot classify images we have no classifier for
				continue;
			}
			// classify and remember results
			KnnClassifier classifier = mKnnClassifiers.get(classifierIndex);
			GenericTuple2<User, Map<User, Integer>> res = classifier.classify(image, _k,
			// TODO put that to settings
					new DistanceMetric() {
						@Override
						public double distance(double _sample1Feat, double _sample2Feat) {
							return Math.pow(_sample1Feat - _sample2Feat, 2);
							// = euclidean without sqrt. alternative:
							// manhatten distance
							// Math.abs(_sample1Feat - _sample2Feat);
						}
					}, _usePca, _pcaAmountOfFeatures);
			for (User u : res.value2.keySet()) {
				if (!votings.containsKey(u)) {
					votings.put(u, res.value2.get(u));
				} else {
					votings.put(u, votings.get(u) + res.value2.get(u));
				}
				if (mostVotedUser == null || mostVotedUser.value2 < votings.get(u)) {
					mostVotedUser = new GenericTuple2<User, Integer>(u, votings.get(u));
				}
			}
		}
		LOGGER.info("votings: " + votings.toString());
		return new GenericTuple3<User, Integer, Map<User, Integer>>(mostVotedUser.value1, mostVotedUser.value2, votings);
	}

	public void trainSvm(Map<Integer, TrainingData> _trainingdataPerClassifier, boolean _usePca, int _pcaAmountOfFeatures) {
		for (Integer classifierIndex : _trainingdataPerClassifier.keySet()) {
			// ensure classifier exists
			TrainingData trainingData = _trainingdataPerClassifier.get(classifierIndex);
			if (!mSvmClassifiers.containsKey(classifierIndex)) {
				mSvmClassifiers.put(classifierIndex, new SvmClassifier());
			}
			SvmClassifier classifier = mSvmClassifiers.get(classifierIndex);
			classifier.train(trainingData, _usePca, _pcaAmountOfFeatures);
		}
	}

	public GenericTuple3<User, Double, Map<User, Double>> classifySvm(List<PanshotImage> _images, boolean _usePca,
			int _pcaAmountOfFeatures, float _classifierSeparationAngle) {
		Log.i("SVM", "start recognizeSvm - image count: " + _images.size());
		// classify each image, combine votings
		Map<User, Double> probabilities = new HashMap<User, Double>();
		GenericTuple2<User, Double> mostVotedUser = null;
		for (PanshotImage image : _images) {
			int classifierIndex = getClassifierIndexForAngle(image.angleValues[image.rec.angleIndex], _classifierSeparationAngle);
			if (!mSvmClassifiers.containsKey(classifierIndex)) {
				// skip, we cannot classify images we have no classifier for
				Log.i("SVM", "no classifier trained for index: " + classifierIndex);
				continue;
			}
			// get the trained classifier
			SvmClassifier classifier = mSvmClassifiers.get(classifierIndex);
			// classify
			GenericTuple2<User, Map<User, Double>> res = classifier.classify(image, _usePca, _pcaAmountOfFeatures);
			// remember probabilities for later images
			for (User u : res.value2.keySet()) {
				if (!probabilities.containsKey(u)) {
					probabilities.put(u, res.value2.get(u));
				} else {
					probabilities.put(u, probabilities.get(u) + res.value2.get(u));
				}
				if (mostVotedUser == null || mostVotedUser.value2 < probabilities.get(u)) {
					mostVotedUser = new GenericTuple2<User, Double>(u, probabilities.get(u));
				}
			}
		}
		LOGGER.info("probabilities: " + probabilities.toString());
		return new GenericTuple3<User, Double, Map<User, Double>>(mostVotedUser.value1, mostVotedUser.value2, probabilities);
	}

}
