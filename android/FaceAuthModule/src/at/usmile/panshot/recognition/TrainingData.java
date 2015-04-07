package at.usmile.panshot.recognition;

import java.util.ArrayList;
import java.util.List;

import org.opencv.core.Mat;

import at.usmile.panshot.PanshotImage;

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
