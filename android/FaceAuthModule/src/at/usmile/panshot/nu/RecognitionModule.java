package at.usmile.panshot.nu;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.content.Context;
import android.util.Log;
import android.widget.Toast;
import at.usmile.auth.module.face.R;
import at.usmile.functional.FunApply;
import at.usmile.functional.FunUtil;
import at.usmile.panshot.PanshotImage;
import at.usmile.panshot.SharedPrefs;
import at.usmile.panshot.User;
import at.usmile.panshot.recognition.PCAUtil;
import at.usmile.panshot.recognition.TrainingData;
import at.usmile.panshot.recognition.knn.DistanceMetric;
import at.usmile.panshot.recognition.knn.KnnClassifier;
import at.usmile.panshot.recognition.svm.SvmClassifier;
import at.usmile.panshot.util.PanshotUtil;
import at.usmile.tuple.GenericTuple2;
import at.usmile.tuple.GenericTuple3;

public class RecognitionModule implements Serializable {
	private static final long serialVersionUID = 1L;

	private static final Logger LOGGER = LoggerFactory.getLogger(RecognitionModule.class);

	private static final String TAG = RecognitionModule.class.getSimpleName();

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
				SvmClassifier c = new SvmClassifier(classifierIndex);
				mSvmClassifiers.put(classifierIndex, c);
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

	/**
	 * Load training data, do energy normalization, split images by angle,
	 * generate classifiers and train them.
	 * 
	 * @param _context
	 * @param _angleDiffOfPhotos
	 * @param _minAmountImagesPerSubjectAndClassifier
	 */
	public void train(final Context _context, float _angleDiffOfPhotos, int _minAmountImagesPerSubjectAndClassifier) {
		// TODO externalize context stuff

		// load training data
		Log.d(TAG, "loading training panshot images...");
		List<PanshotImage> trainingPanshotImages = DataUtil.loadTrainingData(_context);
		// do image energy normalisation
		if (SharedPrefs.useImageEnergyNormlization(_context)) {
			final float subsamplingFactor = SharedPrefs.getImageEnergyNormalizationSubsamplingFactor(_context);
			FunUtil.apply(trainingPanshotImages, new FunApply<PanshotImage, PanshotImage>() {
				@Override
				public PanshotImage apply(PanshotImage panshotImage) {
					// normalise the face's energy use convolution (kernel = 2D
					// filter) to get image energy (brightness) distribution and
					// normalise face with it
					GenericTuple2<Mat, Mat> normalizedMatEnergy = PanshotUtil.normalizeMatEnergy(panshotImage.grayFace,
							(int) (panshotImage.grayFace.rows() / subsamplingFactor),
							(int) (panshotImage.grayFace.cols() / subsamplingFactor), 255.0);
					panshotImage.grayFace = normalizedMatEnergy.value1;
					return panshotImage;
				}
			});
		}
		// separate images to perspectives...
		Map<Integer, TrainingData> trainingdataPerClassifier = new HashMap<Integer, TrainingData>();
		// ... and track amount of images per (subject, perspective)
		Map<GenericTuple2<String, Integer>, Integer> imageAmount = new HashMap<GenericTuple2<String, Integer>, Integer>();
		for (PanshotImage image : trainingPanshotImages) {
			int classifierIndex = RecognitionModule.getClassifierIndexForAngle(image.angleValues[image.rec.angleIndex],
					_angleDiffOfPhotos);
			if (!trainingdataPerClassifier.containsKey(classifierIndex)) {
				trainingdataPerClassifier.put(classifierIndex, new TrainingData());
			}
			trainingdataPerClassifier.get(classifierIndex).images.add(image);

			// increase amount of images for this subject and
			// perspective
			// would not look so damn complex if Java had more FP...
			GenericTuple2<String, Integer> subjectPerspectiveKey = new GenericTuple2(image.rec.user.getName(), classifierIndex);
			if (!imageAmount.containsKey(subjectPerspectiveKey)) {
				imageAmount.put(subjectPerspectiveKey, 0);
			}
			imageAmount.put(subjectPerspectiveKey, imageAmount.get(subjectPerspectiveKey) + 1);
		}
		for (GenericTuple2<String, Integer> key : imageAmount.keySet()) {
			int amount = imageAmount.get(key);
			if (amount < _minAmountImagesPerSubjectAndClassifier) {
				Toast.makeText(
						_context,
						_context.getResources().getString(R.string.too_less_training_data, key.value1, "" + amount,
								"" + key.value2, "" + _minAmountImagesPerSubjectAndClassifier), Toast.LENGTH_LONG).show();
				return;
			}
		}

		// RESIZE images as KNN, SVM etc need images that are of
		// same size
		for (TrainingData trainingData : trainingdataPerClassifier.values()) {
			FunUtil.apply(trainingData.images, new FunApply<PanshotImage, PanshotImage>() {
				@Override
				public PanshotImage apply(PanshotImage _t) {
					Imgproc.resize(_t.grayFace, _t.grayFace,
							new Size(SharedPrefs.getFaceWidth(_context), SharedPrefs.getFaceHeight(_context)));
					return _t;
				}
			});
		}

		// PCA
		if (SharedPrefs.usePca(_context)) {
			for (TrainingData trainingData : trainingdataPerClassifier.values()) {
				GenericTuple3<Mat, Mat, Mat> pcaComponents = PCAUtil.pcaCompute(trainingData.images);
				trainingData.pcaMean = pcaComponents.value1;
				trainingData.pcaEigenvectors = pcaComponents.value2;
				trainingData.pcaProjections = pcaComponents.value3;
			}
		}

		// we know we have sufficient training data for each
		// classifier
		switch (SharedPrefs.getRecognitionType(_context)) {
			case KNN:
				trainKnn(trainingdataPerClassifier, SharedPrefs.usePca(_context), SharedPrefs.getAmountOfPcaFeatures(_context));
				break;

			case SVM:
				trainSvm(trainingdataPerClassifier, SharedPrefs.usePca(_context), SharedPrefs.getAmountOfPcaFeatures(_context));

			default:
				break;
		}
	}

	public Map<Integer, SvmClassifier> getSvmClassifiers() {
		return mSvmClassifiers;
	}

	public void setSvmClassifiers(Map<Integer, SvmClassifier> _svmClassifiers) {
		mSvmClassifiers = _svmClassifiers;
	}

	public Map<Integer, KnnClassifier> getKnnClassifiers() {
		return mKnnClassifiers;
	}

	public void setKnnClassifiers(Map<Integer, KnnClassifier> _knnClassifiers) {
		mKnnClassifiers = _knnClassifiers;
	}

	@Override
	public String toString() {
		return "RecognitionModule [mSvmClassifiers=" + mSvmClassifiers + "]";
	}

	private void writeObject(ObjectOutputStream out) throws IOException {
		// out.defaultWriteObject();
		out.writeObject(mSvmClassifiers);
		// TODO knn
	}

	@SuppressWarnings("unchecked")
	private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
		// in.defaultReadObject();
		mSvmClassifiers = (Map<Integer, SvmClassifier>) in.readObject();
		// TODO knn
	}
}
