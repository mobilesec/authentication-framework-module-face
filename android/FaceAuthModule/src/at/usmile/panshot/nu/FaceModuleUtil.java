package at.usmile.panshot.nu;

import java.io.IOException;
import java.util.List;

import android.content.Context;
import android.content.res.Resources.NotFoundException;
import android.widget.Toast;
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
	 * @return the list of users. null if the loading failed.
	 */
	public static List<User> loadExistingUsers(Context _context) {
		try {
			return DataUtil.loadExistingUsers(_context);
		} catch (NotFoundException e) {
			e.printStackTrace();
			Toast.makeText(_context, _context.getResources().getString(R.string.media_directory_not_found_exception),
					Toast.LENGTH_LONG).show();
			return null;
		} catch (SdCardNotAvailableException e) {
			e.printStackTrace();
			Toast.makeText(_context, _context.getResources().getString(R.string.sd_card_not_available), Toast.LENGTH_LONG).show();
			return null;
		} catch (IOException e) {
			e.printStackTrace();
			Toast.makeText(_context, _context.getResources().getString(R.string.media_directory_cannot_be_created),
					Toast.LENGTH_LONG).show();
			return null;
		}
	}

}
