package at.usmile.panshot.recognition;


/**
 * Common interface for all face classifiers.
 * 
 * @author Rainhard Findling
 * @date 13 Apr 2015
 * @version 1
 */
public interface FaceClassifier {
	public void train(TrainingData _trainingData, boolean _usePca, int _pcaAmountOfFeatures);

}
