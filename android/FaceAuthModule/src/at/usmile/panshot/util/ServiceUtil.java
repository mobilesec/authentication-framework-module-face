package at.usmile.panshot.util;

import android.app.ActivityManager;
import android.app.ActivityManager.RunningServiceInfo;
import android.content.Context;
import android.util.Log;

/**
 * Util for services.
 * 
 * @author Rainhard Findling
 * @date 20 Apr 2015
 * @version 1
 */
public class ServiceUtil {
	private static final String TAG = ServiceUtil.class.getSimpleName();

	/**
	 * Check if a service is (still) running.
	 * 
	 * @param _context
	 * @param _fullQualifiedClassName
	 *            package and class name of service to check, e.g.
	 *            "com.example.MyService"
	 * @return true if service is running.
	 */
	public static boolean isServiceRunning(Context _context, String _fullQualifiedClassName) {
		ActivityManager manager = (ActivityManager) _context.getSystemService(Context.ACTIVITY_SERVICE);
		for (RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
			if (_fullQualifiedClassName.equals(service.service.getClassName())) {
				Log.d(TAG, "Service " + _fullQualifiedClassName + " is running.");
				return true;
			}
		}
		Log.d(TAG, "Service " + _fullQualifiedClassName + " is not running.");
		return false;
	}

	/**
	 * Check if a service is (still) running.
	 * 
	 * @param _context
	 * @param _service
	 *            The service class.
	 * @return true if service is running.
	 */
	public static boolean isServiceRunning(Context _context, @SuppressWarnings("rawtypes") Class _service) {
		return isServiceRunning(_context, _service.getName());
	}

}
