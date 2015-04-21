package at.usmile.panshot.nu;

import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.Uri;
import android.util.Log;
import android.widget.Toast;
import at.usmile.auth.module.face.R;

/**
 * Util for package operations, like checking if certain Apps are installed,
 * installing them if they are not and opening them. Adapted from {@url
 * https://github.com/zxing/zxing/blob/master/android-integration/src/main/java/
 * com/google/zxing/integration/android/IntentIntegrator.java}
 * 
 * @author Rainhard Findling
 * @date 20 Apr 2015
 * @version 1
 */
public class PackageUtil {
	protected static final String TAG = PackageUtil.class.getSimpleName();

	public static final int REQUEST_CODE = 0x0000fff0;

	/**
	 * Checks if a certain app is installed.
	 * 
	 * @param _context
	 * @param _packagename
	 * @return
	 */
	public static boolean isPackageInstalled(Context _context, String _packagename) {
		PackageManager pm = _context.getPackageManager();
		try {
			PackageInfo packageInfo = pm.getPackageInfo(_packagename, PackageManager.GET_ACTIVITIES);
			Log.d(TAG, packageInfo.toString());
			return true;
		} catch (NameNotFoundException e) {
			return false;
		}
	}

	/**
	 * Notifies user that a certain App is not installed and that it needs to be
	 * downloaded. User can chose to let the dialog open Google Play to install
	 * that App then.
	 * 
	 * @param _context
	 * @param _packagename
	 * @return
	 */
	public static void installPackage(Context _context, String _packagename) {
		showDownloadDialog(_context, _packagename);
	}

	/**
	 * Opens the specified path in a file browser. Requires one to be installed
	 * to work correctly! (Tested with OI File Browser)
	 * 
	 * @param _context
	 * @param _path
	 */
	public static void openFolderInFileBrowser(Context _context, String _path) {
		Intent intent = new Intent(Intent.ACTION_VIEW);
		// intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		intent.setData(Uri.parse("file://" + _path));
		_context.startActivity(intent);
	}

	private static AlertDialog showDownloadDialog(final Context _context, final String _packagename) {
		AlertDialog.Builder downloadDialog = new AlertDialog.Builder(_context);
		downloadDialog.setTitle(_context.getResources().getString(R.string.install_application_title));
		downloadDialog.setMessage(_context.getResources().getString(R.string.install_application_message, _packagename));
		downloadDialog.setPositiveButton(_context.getResources().getString(R.string.yes), new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialogInterface, int i) {
				Uri uri = Uri.parse("market://details?id=" + _packagename);
				Intent intent = new Intent(Intent.ACTION_VIEW, uri);
				try {
					_context.startActivity(intent);
				} catch (ActivityNotFoundException e) {
					// Hmm, market is not installed
					Log.w(TAG, "Google Play is not installed; cannot install " + _packagename);
					Toast.makeText(_context, _context.getResources().getString(R.string.market_not_installed), Toast.LENGTH_LONG)
							.show();
				}
			}
		});
		downloadDialog.setNegativeButton(_context.getResources().getString(R.string.no), null);
		downloadDialog.setCancelable(true);
		return downloadDialog.show();
	}

}
