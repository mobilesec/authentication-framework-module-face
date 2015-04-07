package at.usmile.panshot;

import java.util.Observable;
import java.util.Observer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

/**
 * Manages all sensor stuff we need. Current sensor values can be read out via {@link #getSensorValues()}, listeners to sensor
 * updates can be attached via {@link #addObserver(java.util.Observer)}, their {@link Observer#update(Observable, Object)} gets
 * passed an instance of {@link SensorValues}.
 * 
 * @author Rainhard Findling
 * @date 12.01.2012
 * @version 1
 */
public class SensorComponent extends Observable implements SensorEventListener {
	private Logger LOGGER = LoggerFactory.getLogger(SensorComponent.class);

	// ================================================================================================================
	// MEMBERS
	private SensorManager mSensorManager = null;
	/** gyro sensor */
	private Sensor mSensorGyroscope = null;
	/** last known sensor values */
	private SensorValues mSensorValues = new SensorValues();

	private Sensor mSensorAcceleration;

	private Sensor mSensorLight;

	// ================================================================================================================
	// SINGLETON with init()

	private static SensorComponent mInstance = null;

	public static void init(Context _context) {
		if (mInstance != null) {
			return;
		}
		mInstance = new SensorComponent(_context);
	}

	private SensorComponent(Context _context) {
		mSensorManager = (SensorManager) _context.getSystemService(Context.SENSOR_SERVICE);

		// get wanted sensors
		mSensorGyroscope = mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
		mSensorAcceleration = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
		mSensorLight = mSensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
	}

	public static SensorComponent instance() {
		if (mInstance == null) {
			throw new RuntimeException("The SensorComponent must get initialized via SensorComponent.init(Context) before usage!");
		}
		return mInstance;
	}

	// ================================================================================================================
	// METHODS

	/**
	 * Tells the SensorComponent to listen for hw-sensor updates from now on. You will most certainly like to call this in
	 * Context.onResume().
	 * 
	 * @return true, if starting to listening for hw-sensor-updates was okay.
	 */
	public void start() {
		// reset sensor values
		mSensorValues = new SensorValues();
		// register listener
		LOGGER.debug("starting listening for sensor changes...");
		if (!mSensorManager.registerListener(this, mSensorGyroscope, SensorManager.SENSOR_DELAY_GAME)) {
			throw new RuntimeException("failed to start gyroscope sensor updates");
		}
		if (!mSensorManager.registerListener(this, mSensorAcceleration, SensorManager.SENSOR_DELAY_GAME)) {
			throw new RuntimeException("failed to start acceleration sensor updates");
		}
		if (!mSensorManager.registerListener(this, mSensorLight, SensorManager.SENSOR_DELAY_GAME)) {
			throw new RuntimeException("failed to start light sensor updates");
		}
	}

	/**
	 * Tells the SensorComponent to stop listening for hw-sensor-updates from now on. You will most certainly like to call this in
	 * Context.onPause().
	 */
	public void stop() {
		LOGGER.debug("stopping listening for sensor changes...");
		// unregister listener
		mSensorManager.unregisterListener(this);
	}

	private void notifyObserversNow(Object _o) {
		setChanged();
		notifyObservers(_o);
	}

	// ================================================================================================================
	// CALLBACKS SensorEventListener

	@Override
	public void onAccuracyChanged(Sensor _sensor, int _accuracy) {
		LOGGER.debug("onAccuracyChanged()");
	}

	@Override
	public void onSensorChanged(SensorEvent _event) {
		switch (_event.sensor.getType()) {
			case Sensor.TYPE_GYROSCOPE: {
				mSensorValues.addNewGyroValues(_event.values, System.currentTimeMillis());
				break;
			}
			case Sensor.TYPE_ACCELEROMETER: {
				mSensorValues.addNewAccValues(_event.values, System.currentTimeMillis());
				break;
			}
			case Sensor.TYPE_LIGHT: {
				mSensorValues.addNewLightValues(_event.values[0], System.currentTimeMillis());
				break;
			}
		}
		notifyObserversNow(mSensorValues);
	}

	// ================================================================================================================
	// GETTER / SETTER

	/**
	 * If the corresponding {@link SensorComponent} is still active, the SensorValues will be updated periodically (until the
	 * {@link SensorComponent} gets stopped and started again, which causes the SensorValues will be decoupled).
	 * 
	 * @return {@link #mSensorValues}.
	 */
	public SensorValues getSensorValues() {
		return mSensorValues;
	}
}
