package at.usmile.auth.module.face;

import android.content.Context;
import at.usmile.auth.module.face.activity.OldMainActivity.RecognitionType;

/**
 * Provides access to shared preferences
 * 
 * @author Rainhard Findling
 * @date 9 Apr 2014
 * @version 1
 */
public abstract class SharedPrefs {

	// LOAD DATA - EXAMPLE
	// getActivity().getSharedPreferences(SharedPrefs.SHARED_PREFENCES_ID,
	// Context.MODE_PRIVATE).getBoolean(SharedPrefs.USE_ENERGY_NORMALIZATION,
	// false);

	// WRITE DATA - EXAMPLE
	// getActivity().getSharedPreferences(SharedPrefs.SHARED_PREFENCES_ID,
	// Context.MODE_PRIVATE).edit()
	// .putBoolean(SharedPrefs.USE_ENERGY_NORMALIZATION, _isChecked).commit();

	// ==============================================================================================================
	// SHARED PREFERENCE STRINGS

	public static final String SHARED_PREFENCES_ID = "at.usmile.panshot.recognition";
	public static final String USE_ENERGY_NORMALIZATION = SHARED_PREFENCES_ID + ".use_energy_normalization";
	/**
	 * used within image energy normalisation. the kernel subsampling factor is
	 * the factor the kernel is SMALLER than the image it gets applied to in
	 * width and height. therefore: the bigger the subsampling, the smaller the
	 * kernel, the more local the energy normalisation.
	 */
	public static final String ENERGY_NORMALIZATION_KERNEL_SUBSAMPLING_FACTOR = SHARED_PREFENCES_ID
			+ ".energy_normalization_kernel_subsampling_factor";
	/** the K parameter of the KNN classifier. */
	public static final String KNN_K = SHARED_PREFENCES_ID + ".knn_k";
	/**
	 * if PCA should be applied to transform and reduce the amount of features
	 * before training / classification.
	 */
	public static final String USE_PCA = SHARED_PREFENCES_ID + ".use_pca";
	/** if the classifier used is a KNN. */
	public static final String USE_CLASSIFIER_TYPE_KNN = SHARED_PREFENCES_ID + ".use_classifier_type_knn";
	/**
	 * size to which faces get resized before applying classifiers as KNN or
	 * SVM.
	 */
	public static final String FACE_SIZE = SHARED_PREFENCES_ID + ".face_size";
	/**
	 * amount of PCA features used when applying PCA before
	 * training/classification.
	 */
	public static final String PCA_AMOUNT_OF_FEATURES = SHARED_PREFENCES_ID + ".pca_amount_of_features";

	// ==============================================================================================================
	// READ ACCESS

	public static float getImageEnergyNormalizationSubsamplingFactor(Context _context) {
		return _context.getSharedPreferences(SharedPrefs.SHARED_PREFENCES_ID, Context.MODE_PRIVATE).getFloat(
				SharedPrefs.ENERGY_NORMALIZATION_KERNEL_SUBSAMPLING_FACTOR, 4f);
	}

	public static boolean useImageEnergyNormlization(Context _context) {
		return _context.getSharedPreferences(SharedPrefs.SHARED_PREFENCES_ID, Context.MODE_PRIVATE).getBoolean(
				SharedPrefs.USE_ENERGY_NORMALIZATION, false);
	}

	public static int getFaceWidth(Context _context) {
		// currently using single shared pref: face_size
		return _context.getSharedPreferences(SharedPrefs.SHARED_PREFENCES_ID, Context.MODE_PRIVATE).getInt(SharedPrefs.FACE_SIZE,
				50);
	}

	public static int getFaceHeight(Context _context) {
		// currently using single shared pref: face_size
		return _context.getSharedPreferences(SharedPrefs.SHARED_PREFENCES_ID, Context.MODE_PRIVATE).getInt(SharedPrefs.FACE_SIZE,
				50);
	}

	public static boolean usePca(Context _context) {
		return _context.getSharedPreferences(SharedPrefs.SHARED_PREFENCES_ID, Context.MODE_PRIVATE).getBoolean(
				SharedPrefs.USE_PCA, false);
	}

	public static int getAmountOfPcaFeatures(Context _context) {
		return _context.getSharedPreferences(SharedPrefs.SHARED_PREFENCES_ID, Context.MODE_PRIVATE).getInt(
				SharedPrefs.PCA_AMOUNT_OF_FEATURES, 20);
	}

	public static RecognitionType getRecognitionType(Context _context) {
		if (SharedPrefs.useKnn(_context)) {
			return RecognitionType.KNN;
		}
		return RecognitionType.SVM;
	}

	public static boolean useKnn(Context _context) {
		return _context.getSharedPreferences(SharedPrefs.SHARED_PREFENCES_ID, Context.MODE_PRIVATE).getBoolean(
				SharedPrefs.USE_CLASSIFIER_TYPE_KNN, false);
	}

	public static int getKnnK(Context _context) {
		return _context.getSharedPreferences(SharedPrefs.SHARED_PREFENCES_ID, Context.MODE_PRIVATE).getInt(SharedPrefs.KNN_K, 3);
	}
}
