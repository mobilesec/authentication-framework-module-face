package at.usmile.panshot.util;

import java.util.ArrayList;
import java.util.List;

import android.util.Log;

/**
 * Holds the information and prediction results of one user
 * @author Chris Gabler
 *
 */
public class UserProbability{
	private String userId;
	private double classId;
	private String name;
	private List<Double> probabilities = new ArrayList<Double>();
	
	public UserProbability(String userId, double classId, String name){
		this.userId = userId;
		this.classId = classId;
		this.name = name;
	}
	
	public double getAverageProbability() {
		double sum = 0;
		Log.i("SVM", "user: "+getUserName());
		for(double d : probabilities){
			Log.i("SVM", "probability: " + d);
			sum += d;
		}
		return sum / (double) probabilities.size();
	}

	public void addProbability(double probability){
		probabilities.add(probability);
	}

	public String getUserId() {
		return userId;
	}

	public double getClassId() {
		return classId;
	}

	public List<Double> getProbabilities() {
		return probabilities;
	}

	public String getUserName() {
		return name;
	}

	
}