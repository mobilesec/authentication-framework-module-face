package at.fhooe.facelogin.recognition;

import static com.googlecode.javacv.cpp.opencv_core.CV_32FC1;
import static com.googlecode.javacv.cpp.opencv_core.CV_L1;
import static com.googlecode.javacv.cpp.opencv_core.CV_TERMCRIT_ITER;
import static com.googlecode.javacv.cpp.opencv_core.IPL_DEPTH_32F;
import static com.googlecode.javacv.cpp.opencv_core.cvCreateImage;
import static com.googlecode.javacv.cpp.opencv_core.cvCreateMat;
import static com.googlecode.javacv.cpp.opencv_core.cvNormalize;
import static com.googlecode.javacv.cpp.opencv_core.cvTermCriteria;
import static com.googlecode.javacv.cpp.opencv_legacy.CV_EIGOBJ_NO_CALLBACK;
import static com.googlecode.javacv.cpp.opencv_legacy.cvCalcEigenObjects;
import static com.googlecode.javacv.cpp.opencv_legacy.cvEigenDecomposite;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import at.fhooe.facelogin.recognition.api.Face;
import at.fhooe.facelogin.recognition.api.RecognitionProbabiliy;
import at.fhooe.facelogin.recognition.api.TrainingFace;
import at.fhooe.facelogin.recognition.api.User;
import at.fhooe.util.JavaCvConversionUtil;

import com.googlecode.javacpp.FloatPointer;
import com.googlecode.javacpp.PointerPointer;
import com.googlecode.javacv.cpp.opencv_core.CvMat;
import com.googlecode.javacv.cpp.opencv_core.CvSize;
import com.googlecode.javacv.cpp.opencv_core.CvTermCriteria;
import com.googlecode.javacv.cpp.opencv_core.IplImage;

/**
 * Face recognition, based on Eigenfaces. Must be trained with a set of
 * {@link TrainingFace}s using {@link #train()}, then new {@link Face}s can be
 * classified using {@link #classify(Face)}. Based on the implementation from
 * Shervin Emami, online at http://www.shervinemami.info/faceRecognition.html.
 * 
 * @author Rainhard Findling
 * @date 06.02.2012
 * @version 1
 */
public class FaceRecognizer {
	private static final Logger	LOGGER							= LoggerFactory.getLogger(FaceRecognizer.class);

	// ================================================================================================================
	// MEMBERS

	/**
	 * list of trainingfaces: trainingfaces get added to this via
	 * {@link #add(TrainingFace)} over time, afterwards the classifier gets
	 * trainged via {@link #train()} with this data.
	 */
	private List<TrainingFace>	mTrainingFaces					= new ArrayList<TrainingFace>();

	// ================================================================================================================
	// OPENCV SPECIFIC MEMBERS
	/**
	 * how much the spatial difference between two images can be for one
	 * feature. as we are using gray valued images, this is 255.
	 */
	private static final float	MAX_EIGENVALUE_SPATIAL_DISTANCE	= 255f;
	/** the projected training faces */
	private CvMat				mProjectedTrainFaceMat;
	/** the number of eigenvalues */
	private int					mAmountOfEigens					= 0;
	/** the training face image array */
	private IplImage[]			mTrainingFaceImgArr;
	/** the number of training faces */
	private int					mAmountOfTrainFaces				= 0;
	/** eigenvectors */
	private IplImage[]			mEigenVectArr;
	/** eigenvalues */
	private CvMat				mEigenValMat;
	/** the average image */
	private IplImage			mAvgTrainImg;

	// ================================================================================================================
	// METHODS

	/**
	 * Add a trainingface to {@link #mTrainingFaces}.
	 * 
	 * @param _tf
	 */
	public void add(TrainingFace _tf) {
		mTrainingFaces.add(_tf);
	}

	/**
	 * Train the classifier with the data in {@link #mTrainingFaces}.
	 */
	public void train() {
		// create data in the structure opencv needs it:
		// 1 iplimage array
		// 1 list of ids to which person a face belongs
		mAmountOfTrainFaces = mTrainingFaces.size();
		mTrainingFaceImgArr = new IplImage[mAmountOfTrainFaces];
		for (int i = 0; i < mAmountOfTrainFaces; i++) {
			IplImage face = mTrainingFaces.get(i).getFace();
			mTrainingFaceImgArr[i] = face;
		}

		// do Principal Component Analysis on the training faces
		doPCA();

		LOGGER.info("projecting the training images onto the PCA subspace");
		// project the training images onto the PCA subspace
		mProjectedTrainFaceMat = cvCreateMat(mAmountOfTrainFaces, // rows
				mAmountOfEigens, // cols
				CV_32FC1); // type, 32-bit float, 1 channel

		// initialize the training face matrix - for ease of debugging
		for (int i1 = 0; i1 < mAmountOfTrainFaces; i1++) {
			for (int j1 = 0; j1 < mAmountOfEigens; j1++) {
				mProjectedTrainFaceMat.put(i1, j1, 0.0);
			}
		}

		LOGGER.info("created projectedTrainFaceMat with " + mAmountOfTrainFaces + " (nTrainFaces) rows and " + mAmountOfEigens
				+ " (nEigens) columns");
		if (mAmountOfTrainFaces < 5) {
			LOGGER.info("projectedTrainFaceMat contents:\n"
					+ JavaCvConversionUtil.oneChannelCvMatToString(mProjectedTrainFaceMat));
		}

		final FloatPointer floatPointer = new FloatPointer(mAmountOfEigens);
		for (int i = 0; i < mAmountOfTrainFaces; i++) {
			cvEigenDecomposite(mTrainingFaceImgArr[i], // obj
					mAmountOfEigens, // nEigObjs
					new PointerPointer(mEigenVectArr), // eigInput (Pointer)
					0, // ioFlags
					null, // userData (Pointer)
					mAvgTrainImg, // avg
					floatPointer); // coeffs (FloatPointer)

			if (mAmountOfTrainFaces < 5) {
				LOGGER.info("floatPointer: " + JavaCvConversionUtil.floatPointerToString(floatPointer));
			}
			for (int j1 = 0; j1 < mAmountOfEigens; j1++) {
				mProjectedTrainFaceMat.put(i, j1, floatPointer.get(j1));
			}
		}
		if (mAmountOfTrainFaces < 5) {
			LOGGER.info("projectedTrainFaceMat after cvEigenDecomposite:\n" + mProjectedTrainFaceMat);
		}
	}

	public void train(int _classifierNr) {
		train();
		storeEigenfaceImagesToFs(_classifierNr);
	}

	/** Saves all the eigenvectors as images, so that they can be checked. */
	private void storeEigenfaceImagesToFs(int _classifierNr) {
		// LOGGER.info("Saving the image of the average face as 'data/out_averageImage.bmp'");
		// Bitmap b = ImageUtil.createBitmapOutOf1ChannelIplImage(mAvgTrainImg);
		// SaveMediaUtil.saveBitmapToFs(b, "/mnt/sdcard/f/facelogin/eigenfaces",
		// "classifierNr_" + _classifierNr + "_avg_face"
		// + ".png");
		// b.recycle();
		// LOGGER.info("Saving the " + mAmountOfEigens + " eigenvector images");
		// for (int i = 0; i < mAmountOfEigens; i++) {
		// // Get the eigenface image.
		// IplImage i2 =
		// ImageUtil.convertFloatImageToUcharImage(mEigenVectArr[i]);
		// b = ImageUtil.createBitmapOutOf1ChannelIplImage(i2);
		// SaveMediaUtil.saveBitmapToFs(b, "/mnt/sdcard/f/facelogin/eigenfaces",
		// "classifierNr_" + _classifierNr
		// + "_eigenfaceNr_" + i + ".png");
		// b.recycle();
		// }
	}

	/**
	 * Does the Principal Component Analysis, finding the average image and the
	 * eigenfaces that represent any image in the given dataset.
	 */
	private void doPCA() {
		int i;
		CvTermCriteria calcLimit;
		CvSize faceImgSize = new CvSize();

		// set the number of eigenvalues to use
		mAmountOfEigens = mAmountOfTrainFaces - 1;

		LOGGER.info("allocating images for principal component analysis, using " + mAmountOfEigens
				+ (mAmountOfEigens == 1 ? " eigenvalue" : " eigenvalues"));

		// allocate the eigenvector images
		faceImgSize.width(mTrainingFaceImgArr[0].width());
		faceImgSize.height(mTrainingFaceImgArr[0].height());
		mEigenVectArr = new IplImage[mAmountOfEigens];
		for (i = 0; i < mAmountOfEigens; i++) {
			mEigenVectArr[i] = cvCreateImage(faceImgSize, // size
					IPL_DEPTH_32F, // depth
					1); // channels
		}

		// allocate the eigenvalue array
		mEigenValMat = cvCreateMat(1, // rows
				mAmountOfEigens, // cols
				CV_32FC1); // type, 32-bit float, 1 channel

		// allocate the averaged image
		mAvgTrainImg = cvCreateImage(faceImgSize, // size
				IPL_DEPTH_32F, // depth
				1); // channels

		// set the PCA termination criterion
		calcLimit = cvTermCriteria(CV_TERMCRIT_ITER, // type
				mAmountOfEigens, // max_iter
				1); // epsilon

		LOGGER.info("computing average image, eigenvalues and eigenvectors");
		// compute average image, eigenvalues, and eigenvectors
		cvCalcEigenObjects(mAmountOfTrainFaces, // nObjects
				new PointerPointer(mTrainingFaceImgArr), // input
				new PointerPointer(mEigenVectArr), // output
				CV_EIGOBJ_NO_CALLBACK, // ioFlags
				0, // ioBufSize
				null, // userData
				calcLimit, mAvgTrainImg, // avg
				mEigenValMat.data_fl()); // eigVals

		LOGGER.info("normalizing the eigenvectors");
		cvNormalize(mEigenValMat, // src (CvArr)
				mEigenValMat, // dst (CvArr)
				1, // a
				0, // b
				CV_L1, // norm_type
				null); // mask
	}

	/**
	 * Recognizes the face in each of the test images given, and compares the
	 * results with the truth. <br>
	 * <br>
	 * About the recognition probabilities: an exact match produces 1.0 as a
	 * result.
	 * 
	 * @param szFileTest
	 *            the index file of test images
	 */
	public List<RecognitionProbabiliy> classify(Face _f) {
		// project the test images onto the PCA subspace
		float[] projectedTestFace = new float[mAmountOfEigens];
		cvEigenDecomposite(_f.getFace(), // obj
				mAmountOfEigens, // nEigObjs
				new PointerPointer(mEigenVectArr), // eigInput (Pointer)
				0, // ioFlags
				null, // userData
				mAvgTrainImg, // avg
				projectedTestFace); // coeffs
		// calculate distance in eigenspace of this face to all training faces
		List<RecognitionProbabiliy> recognitionList = calculateDistancesToSubjects(projectedTestFace);
		// debug output
		User nearestPerson = recognitionList.get(0).getPerson();
		LOGGER.info("Classification of 1 picture done, best match (name=" + nearestPerson.getName() + "), confidence="
				+ recognitionList.get(0).getProbability());
		return recognitionList;
	}

	/**
	 * Find the most likely person based on a detection. Returns the index, and
	 * stores the confidence value into pConfidence.
	 * 
	 * @param projectedTestFace
	 *            the projected test face
	 * @param pConfidencePointer
	 *            a pointer containing the confidence value
	 * @param iTestFace
	 *            the test face index
	 * @return the index
	 */
	private List<RecognitionProbabiliy> calculateDistancesToSubjects(float projectedTestFace[]) {
		// smallest distances of given face to training faces of all users
		Map<User, Double> squaredDistances = new HashMap<User, Double>();
		User trainingFaceUserWithLeastSquareDistance = null;

		LOGGER.info("find nearest neighbor from " + mAmountOfTrainFaces + " training faces");
		for (int iTrain = 0; iTrain < mAmountOfTrainFaces; iTrain++) {
			// get user and initial distance for this training face
			User trainingFaceOwer = mTrainingFaces.get(iTrain).getPerson();
			// LOGGER.debug("considering training face " + (iTrain + 1) +
			// " which is from user " + trainingFaceOwer.getName());
			if (squaredDistances.get(trainingFaceOwer) == null) {
				squaredDistances.put(trainingFaceOwer, Double.MAX_VALUE);
			}

			// calculate distance of given face to this training face
			double distSq = 0;
			for (int i = 0; i < mAmountOfEigens; i++) {
				double d_i = projectedTestFace[i] - mProjectedTrainFaceMat.get(iTrain, i);
				// LOGGER.debug("distance in dimension " + i + " is " + d_i);
				// EUCLEADIAN DISTANCE
				distSq += d_i * d_i;
				// MAHALANOBIS DISTANCE (might give
				// better results than Eucalidean
				// distance)
				// distSq += d_i*d_i / eigenValMat.data_fl().get(i);
			}
			// check if this distance is smaller than the smallest distance for
			// this user
			if (distSq < squaredDistances.get(trainingFaceOwer)) {
				squaredDistances.put(trainingFaceOwer, distSq);
			}
			// keep track of user with smallest distance
			if (trainingFaceUserWithLeastSquareDistance == null
					|| distSq < squaredDistances.get(trainingFaceUserWithLeastSquareDistance)) {
				trainingFaceUserWithLeastSquareDistance = trainingFaceOwer;
				LOGGER.info("  training face " + (iTrain + 1) + " from user " + trainingFaceOwer.getName()
						+ " is the new best match, least squared distance: "
						+ squaredDistances.get(trainingFaceUserWithLeastSquareDistance));
			}
		}

		// normalize all distances to a confidence [0,1]
		// FIXME this still does not work as expected, as there are values <0
		// possible (which does not have a major impact by default, as the best
		// value is 1 for sure -- but it can lower the recognition rate, as the
		// collection of negative value can lead to lowering the confidence. of
		// course this will only increase the false negative rate and not allow
		// attackers to break the recognition.
		List<RecognitionProbabiliy> recognitionProbabilities = new ArrayList<RecognitionProbabiliy>();
		for (User u : squaredDistances.keySet()) {
			double confidence = 1.0f - Math.sqrt(squaredDistances.get(u) / (double) (mAmountOfTrainFaces * mAmountOfEigens))
					/ (double) MAX_EIGENVALUE_SPATIAL_DISTANCE;
			recognitionProbabilities.add(new RecognitionProbabiliy(u, confidence));
		}
		// sort probabilities ascending
		Collections.sort(recognitionProbabilities, new Comparator<RecognitionProbabiliy>() {
			@Override
			public int compare(RecognitionProbabiliy _lhs, RecognitionProbabiliy _rhs) {
				if (_lhs.getProbability() == _rhs.getProbability()) {
					return 0;
				}
				if (_lhs.getProbability() < _rhs.getProbability()) {
					return 1;
				}
				return -1;
			}
		});
		// DEBUG print
		for (RecognitionProbabiliy r : recognitionProbabilities) {
			LOGGER.debug(r.toString());
		}
		return recognitionProbabilities;
	}

	/**
	 * @return {@link #trainingFaces}.
	 */
	public List<TrainingFace> getTrainingFaces() {
		return mTrainingFaces;
	}

	/**
	 * Releases the saved training faces in {@link #mTrainingFaces}.
	 */
	public void clear() {
		for (TrainingFace f : mTrainingFaces) {
			if (f.getBitmap() != null) {
				f.getBitmap().recycle();
				f.setBitmap(null);
			}
			if (f.getFace() != null && !f.getFace().isNull()) {
				f.getFace().release();
				f.setFace(null);
			}
		}
		mTrainingFaces.clear();
	}
}
