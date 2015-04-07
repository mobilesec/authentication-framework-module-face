package at.usmile.panshot;

import java.util.Arrays;

/**
 * Holds all information about a picture was taken (angle usw.)
 * 
 * @author Rainhard Findling
 * @date 12.01.2012
 * @version 1
 */
public class Pose {

	// ================================================================================================================
	// MEMBERS

	/** angle at which the picture was taken */
	private float[]	mRotationValues	= null;

	// ================================================================================================================
	// METHODS

	public Pose(float[] _gyroValues) {
		mRotationValues = _gyroValues;
	}

	public Pose(Pose _p) {
		mRotationValues = new float[_p.mRotationValues.length];
		mRotationValues[0] = _p.getGyroValues()[0];
		mRotationValues[1] = _p.getGyroValues()[1];
		mRotationValues[2] = _p.getGyroValues()[2];
	}

	@Override
	public Pose clone() {
		return new Pose(this);
	}

	@Override
	public String toString() {
		return "Pose [mGyroValues=" + Arrays.toString(mRotationValues) + "]";
	}

	/**
	 * @return {@link #angleValues}.
	 */
	public float[] getGyroValues() {
		return mRotationValues;
	}

	/**
	 * @param _gyroValues
	 *            sets {@link #angleValues} to _gyroValues.
	 */
	public void setGyroValues(float[] _gyroValues) {
		mRotationValues = _gyroValues;
	}

}
