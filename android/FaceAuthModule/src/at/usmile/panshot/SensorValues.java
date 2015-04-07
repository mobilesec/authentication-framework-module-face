package at.usmile.panshot;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * All least known sensor values.
 * 
 * @author Rainhard Findling
 * @date 12.01.2012
 * @version 1
 */
public class SensorValues {
	private static final Logger LOGGER = LoggerFactory.getLogger(SensorValues.class);

	// ================================================================================================================
	// MEMBERS

	/**
	 * list of past gyro values. needed to integrate over time to get the current rotation within {@link #mCurrentRotation}.
	 */
	private List<Observation<Float[]>> mGyroValueHistory = new ArrayList<Observation<Float[]>>();
	/**
	 * list of past gyro values. needed to integrate over time to get the current rotation within {@link #mCurrentRotation}.
	 */
	private List<Observation<Float[]>> mRotationHistory = new ArrayList<Observation<Float[]>>();

	/** history of acceleration values. may contain only the least values. */
	private List<Observation<Float[]>> mAccValueHistory = new ArrayList<Observation<Float[]>>();

	/** history of light values. may contain only the least values. */
	private List<Observation<Float>> mLightValueHistory = new ArrayList<SensorValues.Observation<Float>>();

	private long mLastGyroUpdateTime = -1L;

	// ================================================================================================================
	// METHODS

	@Override
	public String toString() {
		return "SensorValues [mGyroValues=" + Arrays.toString(mGyroValueHistory.get(mGyroValueHistory.size() - 1).value) + "]";
	}

	/**
	 * clear gyro values
	 */
	public void reset() {
		// do not clear lists as somebody else is possibly still using them - create new ones
		mGyroValueHistory = new ArrayList<Observation<Float[]>>();
		mRotationHistory = new ArrayList<Observation<Float[]>>();

		mAccValueHistory = new ArrayList<Observation<Float[]>>();

		mLightValueHistory = new ArrayList<SensorValues.Observation<Float>>();
	}

	/**
	 * @return {@link #angleValues}.
	 */
	public Observation<Float[]> getRotationValues() {
		return getLastObservation(mRotationHistory);
	}

	public void addNewAccValues(float[] _values, long timestamp) {
		Float[] accValues = new Float[_values.length];
		for (int i = 0; i < accValues.length; i++) {
			accValues[i] = new Float(_values[i]);
		}
		mAccValueHistory.add(new Observation<Float[]>(accValues, timestamp));
	}

	/**
	 * @param _values
	 *            the current raw Android gyroscope values.
	 */
	public void addNewGyroValues(float[] _values, long timestamp) {
		// store this gyro values
		Float[] gyroValues = new Float[_values.length];
		for (int i = 0; i < gyroValues.length; i++) {
			gyroValues[i] = new Float(_values[i]);
		}
		synchronized (this) {
			mGyroValueHistory.add(new Observation<Float[]>(gyroValues, timestamp));
			// integration of gyro values to cur. rotation value
			if (mGyroValueHistory.size() < 2) {
				// yet too less values to do a calculation
				return;
			}

			// - // check if list is too long - we only need 2 values currently
			// - while (mGyroValueHistory.size() > 2) {
			// - mGyroValueHistory.remove(0);
			// - }
			// - // integrate values to current rotation
			// - float[] newRotation = new float[mCurrentRotation.length];
			// - for (int i = 0; i < _values.length; i++) {
			// - newRotation[i] = mCurrentRotation[i] + (mGyroValueHistory.get(0).value[i] + mGyroValueHistory.get(1).value[i]) /
			// 2.0f
			// - * (timestamp - mLastGyroscopeUpdateTime) / 1000.0f;
			// - }
			// - mCurrentRotation = newRotation;
			// - mLastGyroscopeUpdateTime = timestamp;

			// check if list is too long - we only need 2 values currently
			while (mGyroValueHistory.size() > 2) {
				mGyroValueHistory.remove(0);
			}
			// integrate values to current rotation
			Float[] newRotation = new Float[_values.length];
			Observation<Float[]> gyroLast = mGyroValueHistory.get(mGyroValueHistory.size() - 1);
			Observation<Float[]> gyroSecondLast = mGyroValueHistory.get(mGyroValueHistory.size() - 2);
			// if there is no previous rotation, our last rotation is "initial device rotation", which is 0,0,0
			Float[] rotationLast = (mRotationHistory.size() == 0) ? new Float[] { 0f, 0f, 0f } : mRotationHistory
					.get(mRotationHistory.size() - 1).value;
			for (int i = 0; i < _values.length; i++) {
				newRotation[i] = rotationLast[i] + (gyroSecondLast.value[i] + gyroLast.value[i]) / 2.0f
						* (gyroLast.timestamp - gyroSecondLast.timestamp) / 1000.0f;
			}
			mRotationHistory.add(new Observation<Float[]>(newRotation, timestamp));
		}
	}

	public List<Observation<Float[]>> getGyroValueHistory() {
		return mGyroValueHistory;
	}

	public List<Observation<Float[]>> getAccValueHistory() {
		return mAccValueHistory;
	}

	public List<Observation<Float[]>> getRotationHistory() {
		return mRotationHistory;
	}

	public void addNewLightValues(float _f, long _timestamp) {
		mLightValueHistory.add(new Observation<Float>(_f, _timestamp));
	}

	public Observation<Float> getLightValue() {
		return getLastObservation(mLightValueHistory);
	}

	public Observation<Float[]> getAccelerationValues() {
		return getLastObservation(mAccValueHistory);
	}

	private <T> Observation<T> getLastObservation(List<Observation<T>> _l) {
		if (_l.size() == 0) {
			return null;
		}
		return _l.get(_l.size() - 1);
	}

	public class Observation<T> {
		public T value;
		public long timestamp;

		public Observation(T _value, long _timestamp) {
			value = _value;
			timestamp = _timestamp;
		}

		@Override
		public String toString() {
			return "Observation [value=" + value + ", timestamp=" + timestamp + "]";
		}
	}

}
