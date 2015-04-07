package at.usmile.panshot.recognition.knn;

public interface DistanceMetric {

	// ================================================================================================================
	// MEMBERS

	// ================================================================================================================
	// METHODS

	/**
	 * Calculates the distance between the same feature of two different samples. Could e.g. be an Euclidean or squared distance.
	 * 
	 * @param _sample1Feat
	 * @param _sample2Feat
	 * @return
	 */
	public double distance(double _sample1Feat, double _sample2Feat);
}
