package at.usmile.panshot.recognition.knn;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import at.usmile.panshot.PanshotImage;
import at.usmile.panshot.User;
import at.usmile.panshot.recognition.PCAUtil;
import at.usmile.panshot.recognition.TrainingData;
import at.usmile.tuple.GenericTuple2;

public class KnnClassifier {

	// ================================================================================================================
	// MEMBERS

	private TrainingData mTrainingData = null;

	// ================================================================================================================
	// METHODS

	public void train(TrainingData _trainingData, boolean _usePca, int _pcaAmountOfFeatures) {
		// check all train images contain users
		for (PanshotImage i : _trainingData.images) {
			if (i.rec == null || i.rec.user == null) {
				throw new RuntimeException("Not all training images contain training data (user information).");
			}
		}
		mTrainingData = _trainingData;
	}

	/**
	 * @param _image
	 * @param _k
	 * @param _i
	 * @param _b
	 * @return (mostSelectedUser, votingsPerUser)
	 */
	public GenericTuple2<User, Map<User, Integer>> classify(PanshotImage _image, int _k, DistanceMetric _distanceMetric,
			boolean _usePca, int _pcaAmountOfFeatures) {
		List<GenericTuple2<PanshotImage, Double>> neighbours = getNeighbours(_image, _k, _distanceMetric, _usePca,
				_pcaAmountOfFeatures);
		Map<User, Integer> voting = new HashMap<User, Integer>();
		GenericTuple2<User, Integer> mostVotedUser = null;
		for (GenericTuple2<PanshotImage, Double> n : neighbours) {
			if (!voting.containsKey(n.value1.rec.user)) {
				voting.put(n.value1.rec.user, 0);
			}
			voting.put(n.value1.rec.user, voting.get(n.value1.rec.user) + 1);
			if (mostVotedUser == null || mostVotedUser.value2 < voting.get(n.value1.rec.user)) {
				mostVotedUser = new GenericTuple2<User, Integer>(n.value1.rec.user, voting.get(n.value1.rec.user));
			}
		}
		return new GenericTuple2<User, Map<User, Integer>>(mostVotedUser.value1, voting);
	}

	public List<GenericTuple2<PanshotImage, Double>> getNeighbours(PanshotImage _image, int _k, DistanceMetric _distanceMetric,
			boolean _usePca, int _pcaAmountOfFeatures) {
		if (_k < 1) {
			throw new RuntimeException("_k must be at least of size 1.");
		}
		if (_pcaAmountOfFeatures < 1) {
			throw new RuntimeException("_pcaAmountOfFeatures must be at least of size 1.");
		}

		// (imageIndex,distance)
		List<GenericTuple2<PanshotImage, Double>> neighbours = new ArrayList<GenericTuple2<PanshotImage, Double>>();
		GenericTuple2<PanshotImage, Double> mostDistantNeightbour = null;
		for (PanshotImage image : mTrainingData.images) {
			// calc distance
			double distance = 0;
			if (!_usePca) {
				// measure distance in pixel space
				for (int row = 0; row < image.grayFace.rows(); row++) {
					for (int col = 0; col < image.grayFace.cols(); col++) {
						double mine = image.grayFace.get(row, col)[0];
						double other = _image.grayFace.get(row, col)[0];
						distance += _distanceMetric.distance(mine, other);
					}
				}
			} else {
				// project data into eigenspace
				_image.pcaFace = PCAUtil.pcaProject(_image, mTrainingData.pcaMean, mTrainingData.pcaEigenvectors);
				// measure distance in eigenspace
				for (int row = 0; row < image.pcaFace.rows() && row < _pcaAmountOfFeatures; row++) {
					for (int col = 0; col < image.pcaFace.cols(); col++) {
						double mine = image.pcaFace.get(row, col)[0];
						double other = _image.pcaFace.get(row, col)[0];
						distance += _distanceMetric.distance(mine, other);
					}
				}
			}
			GenericTuple2<PanshotImage, Double> n = new GenericTuple2<PanshotImage, Double>(image, distance);
			if (neighbours.size() < _k) {
				neighbours.add(n);
				if (mostDistantNeightbour == null || mostDistantNeightbour.value2 < n.value2) {
					// as long as we don't have a full neighbourhood: remember most distant neighbour
					mostDistantNeightbour = n;
				}
			} else if (mostDistantNeightbour.value2 > n.value2) {
				// we have full neighbourhood, but this sample is nearer
				neighbours.remove(mostDistantNeightbour);
				neighbours.add(n);
				// search for new most distant neighbour
				mostDistantNeightbour = neighbours.get(0);
				for (int i = 1; i < neighbours.size(); i++) {
					GenericTuple2<PanshotImage, Double> n2 = neighbours.get(i);
					if (n2.value2 > mostDistantNeightbour.value2) {
						mostDistantNeightbour = n2;
					}
				}
			}
		}
		return neighbours;
	}
}
