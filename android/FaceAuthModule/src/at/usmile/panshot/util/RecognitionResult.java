package at.usmile.panshot.util;

import java.util.ArrayList;

import android.util.Log;


/**
 * Helper class to save the important user data for the face recognition via SVM and the prediction results.
 * @author Chris Gabler
 *
 */
public class RecognitionResult {

	private ArrayList<UserProbability> users = new ArrayList<UserProbability>();

	
	public ArrayList<UserProbability> getUsers() {
		return users;
	}

	public void setUsers(ArrayList<UserProbability> users) {
		this.users = users;
	}

	public void addUser(String userId, String name) {
		users.add(new UserProbability(userId, users.size(), name));
	}

	public boolean containsUser(String userId) {
		if (getUser(userId) != null) {
			return true;
		}
		return false;
	}

	public double getClassId(String userId) {
		return getUser(userId).getClassId();
	}

	public UserProbability getUser(String userId) {
		for (UserProbability user : users) {
			if (user.getUserId().equals(userId)) {
				return user;
			}
		}
		return null;
	}

	public void addProbabilityToUser(int classId, double prob) {
		for (UserProbability user : users) {
			if (user.getClassId() == classId) {
				user.addProbability(prob);
			}
		}

	}

	/**
	 * finds the user with the highest average probability
	 * @return the user id of the user with the highest average probability of all angels
	 */
	public String getUserIdWithHighestProbability() {
		String maxId = "";
		double maxVal = Double.MIN_VALUE;

		for (UserProbability user : users) {
			double val = user.getAverageProbability();
			Log.i("SVM", "user: " + user.getUserName()	+ " average probability: " + val);
			if (val > maxVal) {
				maxVal = val;
				maxId = user.getUserId();
			}
		}
		return maxId;
	}

	public UserProbability getUserForClassId(double v) {
		for (UserProbability user : users) {
			if (user.getClassId() == v) {
				return user;
			}
		}
		return null;
	}

}