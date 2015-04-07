package at.usmile.panshot.util;

import java.util.Observer;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.util.Log;
import android.widget.EditText;
import android.widget.Toast;

/**
 * Derivate of the JOptionPanel in Swing.
 * 
 * @author Rainhard Findling
 * @date 08.02.2012
 * @version 1
 */
public class AndroidOptionPane {

	// ================================================================================================================
	// MEMBERS

	// ================================================================================================================
	// METHODS

	/**
	 * Promts user for text input. Is a popup that has an textfield + an OK button. The listener (observer) gets informed as soon
	 * as the user clicked the OK button, the string input gets delivered via
	 * {@link Observer#update(java.util.Observable, Object)}.
	 * 
	 * @param _context
	 * @param _message
	 * @param _title
	 * @return
	 */
	public static void showStringInputDialog(final Context _context, String _title, String _message, final Observer _listener) {
		AlertDialog.Builder alert = new AlertDialog.Builder(_context);

		alert.setTitle(_title);
		alert.setMessage(_message);

		// Set an EditText view to get user input
		final EditText input = new EditText(_context);
		alert.setView(input);
		final StringBuilder sb = new StringBuilder();
		alert.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int whichButton) {
				Log.d(AndroidOptionPane.class.getSimpleName(), "OK clicked.");
				sb.append(input.getText().toString());
				_listener.update(null, input.getText().toString());
			}
		});

		alert.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int whichButton) {
				// Cancelled
			}
		});
		alert.show();
	}

	public static void showToast(Context _context, String _msg) {
		Toast.makeText(_context, _msg, Toast.LENGTH_LONG).show();
	}
}
