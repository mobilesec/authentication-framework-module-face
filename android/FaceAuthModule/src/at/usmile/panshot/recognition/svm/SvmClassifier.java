package at.usmile.panshot.recognition.svm;

import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import libsvm.svm;
import libsvm.svm_model;
import libsvm.svm_node;
import libsvm.svm_parameter;
import libsvm.svm_problem;

import org.opencv.core.Mat;

import android.util.Log;
import at.usmile.panshot.PanshotImage;
import at.usmile.panshot.User;
import at.usmile.panshot.recognition.FaceClassifier;
import at.usmile.panshot.recognition.PCAUtil;
import at.usmile.panshot.recognition.RecUtil;
import at.usmile.panshot.recognition.TrainingData;
import at.usmile.panshot.util.PanshotUtil;
import at.usmile.tuple.GenericTuple2;

/**
 * Documentation of the used LIBSVM library for Android:
 * http://grepcode.com/snapshot
 * /repo1.maven.org/maven2/tw.edu.ntu.csie/libsvm/3.1/
 * 
 * @author Rainhard Findling
 * @date 7 Apr 2014
 * @version 1
 */
public class SvmClassifier implements FaceClassifier, Serializable {
	private static final long serialVersionUID = 1L;

	// ================================================================================================================
	// MEMBERS

	private static final String FILE_PREFIX = "svm";

	/** The angle index this classifier covers (e.g. 0 = frontal) */
	private Integer mClassifierIndex = null;

	/**
	 * list of users so that classifier can state which user he recognised
	 * during classification.
	 */
	private List<User> mUsers = new ArrayList<User>();

	/** the actual SVM classifier. null if not trained yet. */
	private svm_model mSvmModel = null;

	/** PCA: the mean of all pixels of training images. null if PCA is not used. */
	private Mat mPcaMean = null;

	/** PCA: the eigenvectors of the training images. null if PCA is not used. */
	private Mat mPcaEigenvectors = null;

	// ================================================================================================================
	// METHODS

	public SvmClassifier(Integer _classifierIndex) {
		mClassifierIndex = _classifierIndex;
	}

	/**
	 * 
	 * @param _trainingData
	 * @param _usePca
	 *            if PCA should be used to transform features before training
	 *            the classifier.
	 * @param _amountOfPcaFeatures
	 *            the amount N of transformed features to be used for training
	 *            and classification. only the N most important features will be
	 *            used. all features are used if set to -1.
	 */
	@Override
	public void train(TrainingData _trainingData, boolean _usePca, int _pcaAmountOfFeatures) {
		if (_pcaAmountOfFeatures < 1) {
			throw new RuntimeException("_pcaAmountOfFeatures must be at least of size 1.");
		} // check all train images contain users
		for (PanshotImage i : _trainingData.images) {
			if (i.rec == null || i.rec.user == null) {
				throw new RuntimeException("Not all training images contain training data (user information).");
			}
		}
		// PCA
		if (_usePca) {
			mPcaMean = _trainingData.pcaMean;
			mPcaEigenvectors = _trainingData.pcaEigenvectors;
		}

		// create a new svm-problem for all images of the current angel
		svm_problem svmProblem = new svm_problem();
		svmProblem.x = new svm_node[_trainingData.images.size()][];
		svmProblem.y = new double[_trainingData.images.size()];
		svmProblem.l = _trainingData.images.size();

		// assign images to classifier
		for (int imageNr = 0; imageNr < _trainingData.images.size(); imageNr++) {
			PanshotImage panshotImage = _trainingData.images.get(imageNr);
			User user = panshotImage.rec.user;
			if (!mUsers.contains(user)) {
				mUsers.add(user);
			}
			Log.i("SVM", "train next image - userId: " + user.getId() + " name: " + user.getName());

			if (!_usePca) {
				// use pixel features directly
				// get the pixel data from the detected face data
				double[] features = RecUtil.transformImageToFeatureVector(panshotImage.grayFace);
				// create a node value for every pixel
				svmProblem.x[imageNr] = new svm_node[features.length];
				for (int featureNr = 0; featureNr < features.length; featureNr++) {
					svm_node node = new svm_node();
					node.index = featureNr;
					node.value = features[featureNr];
					svmProblem.x[imageNr][featureNr] = node;
				}
			} else {
				// use PCA transformed values of pixel values
				// create a node value for every pixel
				svmProblem.x[imageNr] = new svm_node[Math.min(panshotImage.pcaFace.cols(), _pcaAmountOfFeatures)];
				for (int featureNr = 0; featureNr < panshotImage.pcaFace.cols() && featureNr < _pcaAmountOfFeatures; featureNr++) {
					svm_node node = new svm_node();
					node.index = featureNr;
					double[] d = panshotImage.pcaFace.get(0, featureNr);
					node.value = d[0];
					svmProblem.x[imageNr][featureNr] = node;
				}
			}
			// save the classId from the current user to be able to
			// identify the user in the prediction results
			int userIndex = mUsers.indexOf(user);
			svmProblem.y[imageNr] = userIndex;
		}

		// train the classifier with all the images from the current angel
		mSvmModel = svm.svm_train(svmProblem, getSvmParams());
	}

	// get the SVM parameters
	public svm_parameter getSvmParams() {
		svm_parameter param = new svm_parameter();
		param.probability = 1;
		param.gamma = 0.5;
		param.nu = 0.5;
		param.C = 1;
		param.svm_type = svm_parameter.C_SVC;
		param.kernel_type = svm_parameter.LINEAR;
		param.cache_size = 20000;
		param.eps = 0.001;
		return param;
	}

	/**
	 * @param _image
	 * @return (mostSelectedUser, probabilityPerUser)
	 */
	public GenericTuple2<User, Map<User, Double>> classify(PanshotImage _image, boolean _usePca, int _pcaAmountOfFeatures) {
		Log.d("SVM", "classify, usePCA=" + _usePca + ", pcaFeat=" + _pcaAmountOfFeatures);
		svm_node[] nodes = null;
		if (!_usePca) {
			// get the pixel data from the recognized face
			double[] features = RecUtil.transformImageToFeatureVector(_image.grayFace);
			// creates nodes from features
			nodes = new svm_node[features.length];
			for (int featureNr = 0; featureNr < features.length; featureNr++) {
				svm_node node = new svm_node();
				node.index = featureNr;
				node.value = features[featureNr];
				nodes[featureNr] = node;
			}
		} else {
			if (mPcaMean == null || mPcaEigenvectors == null) {
				throw new RuntimeException("Cannot project data into Eigenspace if the mean or eigenvalue matrix are null.");
			}
			// project data into eigenspace
			_image.pcaFace = PCAUtil.pcaProject(_image, mPcaMean, mPcaEigenvectors);
			// creates nodes from features
			nodes = new svm_node[Math.min(_image.pcaFace.cols(), _pcaAmountOfFeatures)];
			for (int featureNr = 0; featureNr < _image.pcaFace.cols() && featureNr < _pcaAmountOfFeatures; featureNr++) {
				svm_node node = new svm_node();
				node.index = featureNr;
				double[] d = _image.pcaFace.get(0, featureNr);
				node.value = d[0];
				nodes[featureNr] = node;
			}
		}

		// get probability per user
		double[] probResults = new double[mUsers.size()];
		double v = svm.svm_predict_probability(mSvmModel, nodes, probResults);
		// DEBUG
		int[] labels = mSvmModel.label;

		// assign probability to user
		Map<User, Double> probabilities = new HashMap<User, Double>();
		GenericTuple2<User, Double> highestProbUser = null;
		for (int labelIndex = 0; labelIndex < mSvmModel.label.length; labelIndex++) {
			int label = mSvmModel.label[labelIndex];
			User u = mUsers.get(label);
			probabilities.put(u, probResults[label]);
			if (highestProbUser == null || highestProbUser.value2 < probResults[label]) {
				highestProbUser = new GenericTuple2<User, Double>(u, probResults[label]);
			}
		}
		return new GenericTuple2<User, Map<User, Double>>(highestProbUser.value1, probabilities);
	}

	// @Override
	// public String toString() {
	// return "SvmClassifier [mClassifierIndex=" + mClassifierIndex +
	// ", mUsers=" + mUsers + ", mSvmModel="
	// + (mSvmModel == null ? "null" : "present") + ", mPcaMean=" + (mPcaMean ==
	// null ? "null" : "present")
	// + ", mPcaEigenvectors=" + (mPcaEigenvectors == null ? "null" : "present")
	// + "]";
	// }

	@Override
	public String toString() {
		return "SvmClassifier [mClassifierIndex=" + mClassifierIndex + ", mUsers=" + mUsers + ", mSvmModel=" + mSvmModel
				+ ", mPcaMean=" + mPcaMean + ", mPcaEigenvectors=" + mPcaEigenvectors + "]";
	}

	// ========================================================================================================================
	// SERIALIZATION

	/**
	 * 
	 * @param classifierIndex
	 * @return
	 */
	protected static String getSerializationClassifierName(int classifierIndex) {
		return "index_" + classifierIndex;
	}

	/**
	 * We need custom serialization as we can't serialize opencv members.
	 */
	private void writeObject(ObjectOutputStream out) throws IOException {
		// out.defaultWriteObject();
		out.writeObject(mUsers);
		out.writeInt(mClassifierIndex);
		if (mPcaMean == null) {
			out.writeObject(null);
		} else {
			out.writeObject(PanshotUtil.matToMapFloat(mPcaMean));
		}
		if (mPcaEigenvectors == null) {
			out.writeObject(null);
		} else {
			out.writeObject(PanshotUtil.matToMapFloat(mPcaEigenvectors));
		}
	}

	/**
	 * We need custom serialization as we can't serialize opencv members.
	 */
	@SuppressWarnings("unchecked")
	private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
		// in.defaultReadObject();
		mUsers = (List<User>) in.readObject();
		mClassifierIndex = in.readInt();
		Object tmp = in.readObject();
		if (tmp != null) {
			mPcaMean = PanshotUtil.matFromMapFloat((Map<String, Object>) tmp);
		}
		tmp = in.readObject();
		if (tmp != null) {
			mPcaEigenvectors = PanshotUtil.matFromMapFloat((Map<String, Object>) tmp);
		}
	}

	/**
	 * Stores native data (OpenCV data) to the filesystem. This is intended to
	 * be called during deserialization to load those parts of classifiers that
	 * can't be persisted through Java serialization.
	 * 
	 * @throws IOException
	 */
	public void storeNativeData(File _folder) throws IOException {
		svm.svm_save_model(
				new File(_folder, FILE_PREFIX + "_model_" + getSerializationClassifierName(mClassifierIndex)).getAbsolutePath()
						+ ".bin", mSvmModel);
	}

	/**
	 * Loads native data (OpenCV data) from the filesystem. This is intended to
	 * be called during deserialization to load those parts of classifiers that
	 * can't be persisted through Java serialization.
	 * 
	 * @throws IOException
	 */
	public void loadNativeData(File _folder) throws IOException {
		mSvmModel = svm.svm_load_model(new File(_folder, FILE_PREFIX + "_model_"
				+ getSerializationClassifierName(mClassifierIndex)).getAbsolutePath()
				+ ".bin");
	}

	public Integer getClassifierIndex() {
		return mClassifierIndex;
	}

	public void setClassifierIndex(Integer classifierIndex) {
		this.mClassifierIndex = classifierIndex;
	}
}
