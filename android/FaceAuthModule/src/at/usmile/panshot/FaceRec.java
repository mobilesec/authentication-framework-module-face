package at.usmile.auth.module.face;

import static com.googlecode.javacv.cpp.opencv_contrib.createFisherFaceRecognizer;
import static com.googlecode.javacv.cpp.opencv_core.IPL_DEPTH_8U;
import static com.googlecode.javacv.cpp.opencv_highgui.cvLoadImage;
import static com.googlecode.javacv.cpp.opencv_imgproc.CV_BGR2GRAY;
import static com.googlecode.javacv.cpp.opencv_imgproc.cvCvtColor;

import java.io.File;
import java.io.FilenameFilter;

import com.googlecode.javacv.cpp.opencv_contrib.FaceRecognizer;
import com.googlecode.javacv.cpp.opencv_core.IplImage;
import com.googlecode.javacv.cpp.opencv_core.MatVector;

public class FaceRec {
	public static void main(String[] args) {
		String trainingDir = args[0];
		IplImage testImage = cvLoadImage(args[1]);

		File root = new File(trainingDir);

		FilenameFilter pngFilter = new FilenameFilter() {
			public boolean accept(File dir, String name) {
				return name.toLowerCase().endsWith(".png");
			}
		};

		File[] imageFiles = root.listFiles(pngFilter);

		MatVector images = new MatVector(imageFiles.length);

		int[] labels = new int[imageFiles.length];

		int counter = 0;
		int label;

		IplImage img;
		IplImage grayImg;

		for (File image : imageFiles) {
			// Get image and label:
			img = cvLoadImage(image.getAbsolutePath());
			label = Integer.parseInt(image.getName().split("\\-")[0]);
			// Convert image to grayscale:
			grayImg = IplImage.create(img.width(), img.height(), IPL_DEPTH_8U, 1);
			cvCvtColor(img, grayImg, CV_BGR2GRAY);
			// Append it in the image list:
			images.put(counter, grayImg);
			// And in the labels list:
			labels[counter] = label;
			// Increase counter for next image:
			counter++;
		}

		FaceRecognizer faceRecognizer = createFisherFaceRecognizer();
		// FaceRecognizer faceRecognizer = createEigenFaceRecognizer();
		// FaceRecognizer faceRecognizer = createLBPHFaceRecognizer()

		faceRecognizer.train(images, labels);

		// Load the test image:
		IplImage greyTestImage = IplImage.create(testImage.width(), testImage.height(), IPL_DEPTH_8U, 1);
		cvCvtColor(testImage, greyTestImage, CV_BGR2GRAY);

		// And get a prediction:
		int predictedLabel = faceRecognizer.predict(greyTestImage);
		System.out.println("Predicted label: " + predictedLabel);
	}
}