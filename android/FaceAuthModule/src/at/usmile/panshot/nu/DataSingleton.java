package at.usmile.panshot.nu;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import android.content.Context;
import android.content.res.Resources.NotFoundException;
import at.usmile.panshot.User;
import at.usmile.panshot.util.MediaSaveUtil;

// TODO singleton not possible on android
// TODO extract all context stuff out of this class
// TODO comments

public class DataSingleton {

	// ================================================================================================================
	// MEMBERS

	private List<User> users = new ArrayList<User>();

	// ================================================================================================================
	// METHODS

	public void loadUsers(Context _context) throws NotFoundException, IOException {
		if (!MediaSaveUtil.isSdCardAvailableRW()) {
			throw new SdCardNotAvailableException();
		}
		users = DataUtil.loadExistingUsers(_context);
	}

	public User getUserForName(String name) {
		for (User u : users) {
			if (u.getName().equalsIgnoreCase(name)) {
				return u;
			}
		}
		throw new RuntimeException(name + ": no such user.");
	}

	public User createNewUser(String name) {
		String id = Long.toString(Math.abs(new Random(System.currentTimeMillis()).nextLong()));
		return new User(id, name);
	}
}
