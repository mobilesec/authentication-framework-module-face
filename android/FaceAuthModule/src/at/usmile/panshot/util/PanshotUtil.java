package at.usmile.panshot.util;

import java.util.HashMap;
import java.util.Map;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

import android.content.Context;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.util.Log;
import at.usmile.tuple.GenericTuple2;

/**
 * Util for performing panshot related operations.
 * 
 * @author Rainhard Findling
 * @date 9 Jan 2014
 * @version 1
 */
public class PanshotUtil {

	/**
	 * Rotate openCV matrix in place by rot degrees clockwise. Only supports 90,
	 * 180, 270 degrees.
	 * 
	 * @param mat
	 * @param rot
	 */
	public static void rotate(Mat mat, int rot) {
		if (rot == 270) {
			// Rotate clockwise 270 degrees
			Core.flip(mat.t(), mat, 0);
		} else if (rot == 180) {
			// Rotate clockwise 180 degrees
			Core.flip(mat, mat, -1);
		} else if (rot == 90) {
			// Rotate clockwise 90 degrees
			Core.flip(mat.t(), mat, 1);
		}
	}

	/**
	 * For given first/last (=min/max) angles: determine which is the axis index
	 * on which the angle values change most (is assumed to be the axis the
	 * device is rotated along) and the normaliser angle for all axis which has
	 * to be added to all angles on this axis in order to normalise them from -N
	 * to N.
	 * 
	 * @param firstAngle
	 * @param lastAngle
	 * @return value1: index on which angles change most. value2: angle
	 *         normaliser for all axis which has to be added to angles on the
	 *         axis index (value1) in order to normalise them from -N to N.
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static GenericTuple2<Integer, Float[]> calculateAngleIndexAndNormalizer(Float[] firstAngle, Float[] lastAngle) {
		if (firstAngle.length != 3 || firstAngle.length != 3) {
			throw new RuntimeException("not implemented for not-3-dim. angles.");
		}
		float[] angleDiffs = new float[] { Math.abs(firstAngle[0] - lastAngle[0]), Math.abs(firstAngle[1] - lastAngle[1]),
				Math.abs(firstAngle[2] - lastAngle[2]) };
		Integer angleIndex = -1;
		if (angleDiffs[0] >= angleDiffs[1] && angleDiffs[0] >= angleDiffs[2]) {
			angleIndex = 0;
		} else if (angleDiffs[1] >= angleDiffs[2]) {
			angleIndex = 1;
		} else {
			angleIndex = 2;
		}
		return new GenericTuple2(angleIndex, new Float[] { (firstAngle[0] + lastAngle[0]) / 2f,
				(firstAngle[1] + lastAngle[1]) / 2f, (firstAngle[2] + lastAngle[2]) / 2f });
	}

	public static void playSoundfile(Context context, int R_raw_fileid) {
		MediaPlayer mp = MediaPlayer.create(context, R_raw_fileid);
		mp.setOnCompletionListener(new OnCompletionListener() {
			@Override
			public void onCompletion(MediaPlayer mp) {
				mp.release();
			}
		});
		mp.start();
	}

	/**
	 * @param origMat
	 * @param kernelWidth
	 * @param kernelHeight
	 * @param dstImageMaxVal
	 *            scaling of destination image's pixel values (e.g. 255.0 for an
	 *            8-bit image or 1.0 if the value range should be [0,1]).
	 * @return (normalisedMat, energyMat)
	 */
	public static GenericTuple2<Mat, Mat> normalizeMatEnergy(Mat origMat, int kernelWidth, int kernelHeight, double dstImageMaxVal) {
		// TODO F: use gaussian kernel? square is not optimal
		Mat kernelEnergyLocal = Mat.ones(kernelWidth, kernelHeight, CvType.CV_32FC1);
		Core.multiply(kernelEnergyLocal, new Scalar(1.0 / kernelEnergyLocal.width() / kernelEnergyLocal.height()),
				kernelEnergyLocal);
		// Log.d(TAG, "origMat size=: " + origMat.width() + "," +
		// origMat.height());
		// Log.d(TAG, "kernelEnergyLocal values are: " +
		// Arrays.toString(kernelEnergyLocal.get(0, 0)));
		Mat energy = new Mat(origMat.rows(), origMat.cols(), origMat.depth());
		Imgproc.filter2D(origMat, energy, -1, kernelEnergyLocal);
		// Log.d(TAG, "energy[0,0]: " + Arrays.toString(energy.get(0, 0)));

		// normalise image with it's energy
		Mat newMat = new Mat(origMat.rows(), origMat.cols(), origMat.type());
		Core.divide(origMat, energy, newMat, dstImageMaxVal);
		// Log.d(TAG, "normalizedFace[0,0]: " + Arrays.toString(newMat.get(0,
		// 0)));
		return new GenericTuple2<Mat, Mat>(newMat, energy);
	}

	/**
	 * Convert a OpenCV matrix to a Map of it's attributes (e.g. for
	 * serialization). To be used in combination with
	 * {@link PanshotUtil#matFromMap(Map)}
	 * 
	 * @param mat
	 * @return
	 */
	public static <T> Map<String, Object> matToMap(Mat mat, MatDataGetter<T> _gen) {
		Map<String, Object> map = new HashMap<String, Object>();

		if (!mat.isContinuous()) {
			Log.e(DataUtil.class.getSimpleName(), "Mat not continuous.");
			return null;
		}

		int cols = mat.cols();
		int rows = mat.rows();
		int elemSize = (int) mat.elemSize();

		// float[] data = new float[cols * rows * elemSize];
		// mat.get(0, 0, data);
		T data = _gen.getData(mat, cols * rows * elemSize);

		map.put("rows", mat.rows());
		map.put("cols", mat.cols());
		map.put("type", mat.type());
		map.put("data", data);

		return map;
	}

	/**
	 * See {@link PanshotUtil#matToMap(Mat, MatDataExtractor)}.
	 * 
	 * @param _mat
	 * @return
	 */
	public static Map<String, Object> matToMapFloat(Mat _mat) {
		return matToMap(_mat, new MatDataGetter<float[]>() {
			@Override
			public float[] getData(Mat _mat, int _length) {
				float[] data = new float[_length];
				_mat.get(0, 0, data);
				return data;
			}
		});
	}

	/**
	 * See {@link PanshotUtil#matToMap(Mat, MatDataExtractor)}.
	 * 
	 * @param _mat
	 * @return
	 */
	public static Map<String, Object> matToMapByte(Mat _mat) {
		return matToMap(_mat, new MatDataGetter<byte[]>() {
			@Override
			public byte[] getData(Mat _mat, int _length) {
				byte[] data = new byte[_length];
				_mat.get(0, 0, data);
				return data;
			}
		});
	}

	/**
	 * * Convert a Map of a OpenCV matrix's attributes to the original map (e.g.
	 * for deserialization). To be used in combination with
	 * {@link PanshotUtil#matToMap(Mat)}
	 * 
	 * @param _map
	 * @return
	 */
	@SuppressWarnings("unchecked")
	public static <T> Mat matFromMap(Map<String, Object> _map, MatDataSetter<T> _t) {

		int rows = (Integer) _map.get("rows");
		int cols = (Integer) _map.get("cols");
		int type = (Integer) _map.get("type");
		Mat mat = new Mat(rows, cols, type);

		// T[] data = (T[]) map.get("data");
		// mat.put(0, 0, data);
		_t.setData(mat, (T) _map.get("data"));

		return mat;
	}

	/**
	 * See {@link PanshotUtil#matFromMap(Map, MatDataSetter)}.
	 * 
	 * @param _map
	 * @return
	 */
	public static Mat matFromMapFloat(Map<String, Object> _map) {
		return matFromMap(_map, new MatDataSetter<float[]>() {
			@Override
			public void setData(Mat _mat, float[] _data) {
				_mat.put(0, 0, _data);
			}
		});
	}

	/**
	 * See {@link PanshotUtil#matFromMap(Map, MatDataSetter)}.
	 * 
	 * @param _map
	 * @return
	 */
	public static Mat matFromMapByte(Map<String, Object> _map) {
		return matFromMap(_map, new MatDataSetter<byte[]>() {
			@Override
			public void setData(Mat _mat, byte[] _data) {
				_mat.put(0, 0, _data);
			}
		});
	}

	public interface MatDataGetter<T> {
		public T getData(Mat _mat, int length);
	}

	public interface MatDataSetter<T> {
		public void setData(Mat _mat, T _data);
	}

}
