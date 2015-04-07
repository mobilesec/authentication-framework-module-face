package at.usmile.panshot.recognition;

import java.util.List;

import org.opencv.core.Mat;

/**
 * Util for recognition tasks.
 * 
 * @author Rainhard Findling
 * @date 4 Apr 2014
 * @version 1
 */
public class RecUtil {

	// ================================================================================================================
	// MEMBERS

	// ================================================================================================================
	// METHODS

	/**
	 * Get the pixel data from an image as double array.
	 * 
	 * @param _grayFace
	 *            The pixel data as Mat
	 * @return
	 */
	public static double[] transformImageToFeatureVector(Mat _grayFace) {
		int cols = _grayFace.cols();
		int rows = _grayFace.rows();
		double[] pixelData = new double[cols * rows];

		for (int row = 0; row < rows; row++) {
			for (int col = 0; col < cols; col++) {
				double[] gray = _grayFace.get(row, col);
				pixelData[row * cols + col] = gray[0];
			}
		}
		return pixelData;
	}

	/**
	 * Takes K samples, each represented by MxN matrizes (e.g. image). Transforms each sample into a single row of features and
	 * concatenates all samples to a single matrix. Returns a mat with K rows (each row represents a sample) and MxN columns (MxN
	 * features per sample).
	 * 
	 * @param _mats
	 * @return
	 */
	public static Mat transformImagesToFeatureMatrix(List<Mat> _mats) {
		Mat firstMat = _mats.get(0);
		int cols = firstMat.cols();
		int rows = firstMat.rows();
		Mat resultMat = new Mat(_mats.size(), firstMat.rows() * firstMat.cols(), firstMat.type());
		for (int mNr = 0; mNr < _mats.size(); mNr++) {
			Mat m = _mats.get(mNr);
			if (m.rows() != rows || m.cols() != cols) {
				throw new RuntimeException("Cannot concatenate features of matrizes of different size into one matrix.");
			}
			for (int row = 0; row < rows; row++) {
				m.submat(row, row + 1, 0, cols).copyTo(resultMat.submat(mNr, mNr + 1, row * cols, (row + 1) * cols));
			}
		}
		return resultMat;
	}
}
