package at.usmile.panshot;

import java.util.Observable;
import java.util.Observer;

/**
 * Special listener for a {@link SensorComponent}. Determines if it is time to take the next user photo out of the camera image
 * stream, depending on the phone's rotation.
 * 
 * @author Rainhard Findling
 * @date 12.01.2012
 * @version 1
 */
public class PhotoGyroListener implements Observer {

	// ================================================================================================================
	// MEMBERS

	/** "angle gaps" in between photos in rad. */
	private Float mRotationForNextImage = null;
	/** last degree a photo was taken on */
	private Float[] mLastPhotoDegree = null;
	/** the last known gyro values */
	private Float[] mLastGyroValues = null;

	// ================================================================================================================
	// METHODS
	/**
	 * @param _rotationForNextImage
	 *            {@link #mRotationForNextImage}.
	 */
	public PhotoGyroListener(float _rotationForNextImage) {
		mRotationForNextImage = _rotationForNextImage;
	}

	@Override
	public void update(Observable _arg0, Object _arg1) {
		SensorValues v = (SensorValues) _arg1;
		if (v.getRotationValues() == null) {
			mLastGyroValues = null;
		} else {
			mLastGyroValues = v.getRotationValues().value;
		}
	}

	/**
	 * @return true if the next photo should be taken <b>now</b> (gets calculated out of rotation of phone).
	 */
	public boolean isNextPhoto() {
		if (mLastGyroValues == null) {
			// we have no gyro values by now
			return false;
		}

		if (mLastPhotoDegree == null) {
			// first photo, take wherever we are
			mLastPhotoDegree = new Float[mLastGyroValues.length];
			memorizeLastPhotoPosition();
			return true;
		}

		for (int i = 0; i < mLastGyroValues.length; i++) {
			// check if difference of at least 1 axis is bigger than threshold
			if (Math.abs(mLastGyroValues[i] - mLastPhotoDegree[i]) > mRotationForNextImage) {
				memorizeLastPhotoPosition();
				return true;
			}
		}
		return false;
	}

	/**
	 * remember gyro values of last photo.
	 */
	private void memorizeLastPhotoPosition() {
		for (int i = 0; i < mLastGyroValues.length; i++) {
			mLastPhotoDegree[i] = mLastGyroValues[i];
		}
	}

	/**
	 * Reset the cached values of the gyro sensor and the last taken photo. does not change the degree gap, at which photos should
	 * be taken.
	 */
	public void reset() {
		mLastGyroValues = null;
		mLastPhotoDegree = null;
	}
}
