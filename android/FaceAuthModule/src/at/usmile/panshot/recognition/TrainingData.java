package at.usmile.panshot.recognition;

import java.util.ArrayList;
import java.util.List;

import org.opencv.core.Mat;

import at.usmile.panshot.PanshotImage;

/**
 * Aggregated training data.
 * 
 * @author Rainhard Findling
 * @date 13 Apr 2015
 * @version 1
 */
public class TrainingData {

	// ================================================================================================================
	// MEMBERS

	public List<PanshotImage> images = new ArrayList<PanshotImage>();
	public Mat pcaMean = null;
	public Mat pcaEigenvectors = null;
	public Mat pcaProjections = null;

	// ================================================================================================================
	// METHODS

}
