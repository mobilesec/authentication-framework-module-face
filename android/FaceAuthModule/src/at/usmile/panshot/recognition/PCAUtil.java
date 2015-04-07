package at.usmile.panshot.recognition;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;

import android.util.Log;
import at.usmile.functional.Fun;
import at.usmile.functional.FunUtil;
import at.usmile.panshot.PanshotImage;
import at.usmile.tuple.GenericTuple3;

public class PCAUtil {

	// ================================================================================================================
	// MEMBERS

	// ================================================================================================================
	// METHODS

	/**
	 * PCA transformation of pan shot images. the indexing of images and pca transformed features is the same (e.g. image nr 1 in
	 * _images corresponds to the transformed features of row 1).
	 * 
	 * @param _images
	 * @return in this order: mean of data, eigenvectors of data, projections (transformations) of data into eigenspace.
	 */
	public static GenericTuple3<Mat, Mat, Mat> pcaCompute(List<PanshotImage> _images) {
		// concatenate all samples to rows of a single Mat
		List<Mat> samplesList = FunUtil.map(_images, new Fun<PanshotImage, Mat>() {
			@Override
			public Mat apply(PanshotImage _t) {
				return _t.grayFace;
			}
		});
		Mat samplesMat = RecUtil.transformImagesToFeatureMatrix(samplesList);
		// // DEBUG
		// try {
		// MediaSaveUtil.saveMatToJpgFile(new File("/mnt/sdcard/DCIM/PanshotRecognition/pcaSamplesMat.jpg"), samplesMat);
		// } catch (IOException e) {
		// e.printStackTrace();
		// }
		// do PCA
		GenericTuple3<Mat, Mat, Mat> pcaCompute = pcaCompute(samplesMat);
		// set PCA projections in images
		Mat pcaProjections = pcaCompute.value3;
		for (int imageNr = 0; imageNr < pcaProjections.rows(); imageNr++) {
			_images.get(imageNr).pcaFace = pcaProjections.submat(imageNr, imageNr + 1, 0, pcaProjections.cols());
		}
		return pcaCompute;
	}

	public static GenericTuple3<Mat, Mat, Mat> pcaCompute(Mat _samplesMat) {
		// calculate mean and vectors
		Mat mean = new Mat();
		Mat eigenvectors = new Mat();
		Core.PCACompute(_samplesMat, mean, eigenvectors);
		// project data into pc space
		Mat result = new Mat();
		Core.PCAProject(_samplesMat, mean, eigenvectors, result);
		// // DEBUG
		// try {
		// mean.convertTo(mean, CvType.CV_8UC1);
		// eigenvectors.convertTo(eigenvectors, CvType.CV_8UC1);
		// result.convertTo(result, CvType.CV_8UC1);
		// MediaSaveUtil.saveMatToJpgFile(new File("/mnt/sdcard/DCIM/PanshotRecognition/pcaMean.jpg"), mean);
		// MediaSaveUtil.saveMatToJpgFile(new File("/mnt/sdcard/DCIM/PanshotRecognition/pcaEigenvectors.jpg"), eigenvectors);
		// MediaSaveUtil.saveMatToJpgFile(new File("/mnt/sdcard/DCIM/PanshotRecognition/pcaProjections.jpg"), result);
		// // ATTENTION cannot proceed further now as image have been converted
		// } catch (IOException e) {
		// e.printStackTrace();
		// }
		// int meanRows = mean.rows();
		// int meanCols = mean.cols();
		// int resultRows = result.rows();
		// int resultCols = result.cols();
		return new GenericTuple3<Mat, Mat, Mat>(mean, eigenvectors, result);
	}

	public static Mat pcaProject(List<PanshotImage> _images, Mat _mean, Mat _eigenvectors) {
		// concatenate all samples to rows of a single Mat
		List<Mat> samplesList = FunUtil.map(_images, new Fun<PanshotImage, Mat>() {
			@Override
			public Mat apply(PanshotImage _t) {
				return _t.grayFace;
			}
		});
		Mat samplesMat = RecUtil.transformImagesToFeatureMatrix(samplesList);
		// project data into pc space
		Mat result = new Mat();
		Core.PCAProject(samplesMat, _mean, _eigenvectors, result);
		// int meanRows = mean.rows();
		// int meanCols = mean.cols();
		// int resultRows = result.rows();
		// int resultCols = result.cols();
		return result;
	}

	public static Mat pcaProject(final PanshotImage _image, Mat _mean, Mat _eigenvectors) {
		return pcaProject(new ArrayList<PanshotImage>() {
			{
				add(_image);
			}
		}, _mean, _eigenvectors);
	}

	public static void testPCAComputeMatMatMat() {
		// test data
		Mat data = new Mat(3, 6, CvType.CV_32F) {
			{
				put(0, 0, new double[] { -1, 0, 1, 2, 2, 4 });
				put(1, 0, new double[] { +2, 0, 2, 4, 4, 8 });
				put(2, 0, new double[] { -1, 0, 3, 6, 6, 12 });
			}
		};
		// calculate mean and vectors
		Mat mean = new Mat();
		Mat eigenvectors = new Mat();
		Core.PCACompute(data, mean, eigenvectors);
		// project data into pc space
		Mat result = new Mat();
		Core.PCAProject(data, mean, eigenvectors, result);
		// debug
		// mean should be 0, 0, 2, 4, 4, 8
		for (int row = 0; row < mean.rows(); row++) {
			for (int col = 0; col < mean.cols(); col++) {
				double[] d = mean.get(row, col);
				Log.d("PCA", "mean " + d[0]);
			}
		}
		// vectors should be ?, ?, 0.2, 0.4, 0.4, 0.8
		for (int row = 0; row < eigenvectors.rows(); row++) {
			for (int col = 0; col < eigenvectors.cols(); col++) {
				double[] d = eigenvectors.get(row, col);
				Log.d("PCA", "vectors " + Arrays.toString(d));
			}
		}
		for (int row = 0; row < result.rows(); row++) {
			for (int col = 0; col < result.cols(); col++) {
				double[] d = eigenvectors.get(row, col);
				Log.d("PCA", "vectors " + Arrays.toString(d));
			}
		}
		double EPS = 10E-6;
	}
}
