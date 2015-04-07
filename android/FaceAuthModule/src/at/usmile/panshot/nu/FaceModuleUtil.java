package at.usmile.panshot.nu;

import java.io.IOException;

import android.content.Context;
import android.content.res.Resources.NotFoundException;
import android.widget.Toast;
import at.usmile.auth.module.face.R;

/**
 * General tasks of the face module across Activities including UI interactions.
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
	 * @return true if loading user succeeded.
	 */
	public static boolean loadUsers(Context _context) {
		try {
			// TODO singleton not possible on android. each activity has to
			// write and load all data do the fs or handle is through intents
			DataSingleton.instance().loadUsers(_context);
		} catch (NotFoundException e) {
			e.printStackTrace();
			Toast.makeText(_context, _context.getResources().getString(R.string.media_directory_not_found_exception),
					Toast.LENGTH_LONG).show();
			return false;
		} catch (SdCardNotAvailableException e) {
			e.printStackTrace();
			Toast.makeText(_context, _context.getResources().getString(R.string.sd_card_not_available), Toast.LENGTH_LONG).show();
			return false;
		} catch (IOException e) {
			e.printStackTrace();
			Toast.makeText(_context, _context.getResources().getString(R.string.media_directory_cannot_be_created),
					Toast.LENGTH_LONG).show();
			return false;
		}
		return true;
	}

}
