package at.usmile.panshot.nu;

import java.io.IOException;
import java.util.List;

import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Resources.NotFoundException;
import at.usmile.auth.module.face.R;
import at.usmile.panshot.User;

/**
 * General tasks of the face module across Activities including UI interactions,
 * in general requiring the context.
 * 
 * @author Rainhard Findling
 * @date 7 Apr 2015
 * @version 1
 */
public class FaceModuleUtil {

	/**
	 * {@link DataSingleton} loads existing users and displays according error
	 * messages if necessary.
	 * 
	 * @param _context
	 *            the calling context
	 * @param _onKeyListener
	 * @return the list of users. null if the loading failed.
	 */
	public static List<User> loadExistingUsers(Context _context, DialogInterface.OnClickListener _listener,
			Dialog.OnKeyListener _onKeyListener) {
		// the dialog
		Builder builder = new AlertDialog.Builder(_context).setTitle(_context.getResources().getString(R.string.error)).setIcon(
				android.R.drawable.ic_dialog_alert);
		// default listener if we
		if (_listener == null) {
			_listener = new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int which) {
				}
			};
		}
		// add back listener if needed
		if (_onKeyListener != null) {
			builder.setOnKeyListener(_onKeyListener);
		}
		try {
			// throw new SdCardNotAvailableException();
			return DataUtil.loadExistingUsers(_context);
		} catch (NotFoundException e) {
			e.printStackTrace();
			builder.setMessage(_context.getResources().getString(R.string.media_directory_not_found_exception))
					.setPositiveButton(android.R.string.yes, _listener).show();
			return null;
		} catch (SdCardNotAvailableException e) {
			e.printStackTrace();
			builder.setMessage(_context.getResources().getString(R.string.sd_card_not_available))
					.setPositiveButton(android.R.string.yes, _listener).show();
			return null;
		} catch (IOException e) {
			e.printStackTrace();
			builder.setMessage(_context.getResources().getString(R.string.media_directory_cannot_be_created))
					.setPositiveButton(android.R.string.yes, _listener).show();
			return null;
		}
	}
}
