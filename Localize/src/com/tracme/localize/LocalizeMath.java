package com.tracme.localize;

import java.util.ArrayList;
import com.tracme.util.AndroidLog;

/**
 * The LocalizeMath class provides methods for doing error correction and averaging on predictions.
 * This class comes as a supplement to MainActivity
 * 
 * @author Kwaku Farkye
 *
 */
public class LocalizeMath {

	/**
	 * Finds the predictions/points that fall within the range of a reference prediction/point
	 * 
	 * @param rawPredictions The list of original predictions
	 * @param prevPrediction Previous prediction value used for computing euclidean distance. This prediction
	 * 	value will be used as the initial reference point for finding the closest prediction
	 * @param localizationLog Log File for debug information
	 * @param debugMode Specifies whether we are in debug mode. If in debug mode, localizationLog will be written to.
	 * 
	 * @return A list of predictions that fall within the computed range
	 */
	protected static ArrayList<Double[]> findPredictionsInRange(ArrayList<Double[]> rawPredictions, double[] prevPrediction,
			AndroidLog localizationLog, boolean debugMode)
	{
		double refPtEuclDistance = -1, compEuclDistance = 0;
		double[] thisPrediction = new double[2];
		double[] refPrediction = new double[2];
		ArrayList<Double[]> newPredictionsList = new ArrayList<Double[]>();
		int refPtIndex = 0; // Index in the array list of our reference point/prediction
		
		if (debugMode)
			localizationLog.save("--- Removing Extraneous Predictions ---\n");
		
		for (int i = 0; i < rawPredictions.size(); i++)
		{
			// Turn into primitive double array
			thisPrediction[0] = rawPredictions.get(i)[0].doubleValue();
			thisPrediction[1] = rawPredictions.get(i)[1].doubleValue();
			
			if (debugMode)
				localizationLog.save("Prediction " + i + ": " + thisPrediction[0] + "," + thisPrediction[1] + "\n");
			
			// Find the euclidean distance between this point and previous predicted point
			compEuclDistance = computeEuclidean(thisPrediction, prevPrediction);
			
			// Find the point/prediction with the shortest distance from the previously predicted location
			if (refPtEuclDistance == -1 || compEuclDistance < refPtEuclDistance)
			{
				refPtEuclDistance = compEuclDistance;
				refPtIndex = i; // Record this index for later
			}
		}
		
		if (debugMode)
			localizationLog.save("Prediction using for reference point: " + refPtIndex + ", Euclidean Distance: " + refPtEuclDistance + "\n");
		
		// Set our reference point/prediction
		refPrediction[0] = rawPredictions.get(refPtIndex)[0].doubleValue();
		refPrediction[1] = rawPredictions.get(refPtIndex)[1].doubleValue();
		
		// Now lets pass through and see what predictions can be excluded
		for (int i = 0; i < rawPredictions.size(); i++)
		{
			thisPrediction[0] = rawPredictions.get(i)[0].doubleValue();
			thisPrediction[1] = rawPredictions.get(i)[1].doubleValue();
			
			compEuclDistance = computeEuclidean(thisPrediction, refPrediction);
			
			// If the distance between this point and our reference point
			// is less than the twice the distance between the reference point and the previous prediction
			// then add the point to our new predictions list
			if (compEuclDistance <= (2 * refPtEuclDistance))
			{
				newPredictionsList.add(rawPredictions.get(i));
			}
		}
		
		return newPredictionsList;
	}
	
	/**
	 * Computes the euclidean distance between two points
	 * 
	 * @param point1 The first point used in the calculation
	 * @param point2 The second point used in the calculation
	 * 
	 * @return The euclidean distance between point 1 and point 2
	 */
	protected static double computeEuclidean(double[] point1, double[] point2)
	{
		double euclX, euclY, euclTotal;
		
		euclX = point2[0] - point1[0];
		euclY = point2[1] - point1[1];
		
		euclX *= euclX;
		euclY *= euclY;
		
		euclTotal = Math.sqrt(euclX + euclY);
		
		return euclTotal;
	}
	
	/**
	 * Computes a corrected point based off of two points in the coordinate system
	 * 
	 * @param heavyPoint The point that will get the most weight
	 * @param lightWeight The point that will get the least amount of weight
	 * @param weight The weight that the heavy point will receive
	 * 
	 * @return The weighted point
	 */
	protected static double[] computeWeightedPoint(double[] heavyPoint, double[] lightPoint, double weight)
	{
		double[] weightedPrediction = new double[2];
		
		// Compute the adjusted/corrected prediction value
		weightedPrediction[0] = (weight * heavyPoint[0]) + ((1 - weight) * lightPoint[0]);
		weightedPrediction[1] = (weight * heavyPoint[1]) + ((1 - weight) * lightPoint[1]);
		
		return weightedPrediction;
	}
	
	/**
	 * Takes all of the raw predictions and, based on how close the prediction is to the previous prediction,
	 * computes a weighted average of all the predictions.
	 * 
	 * @param rawPredictions The list of original predictions
	 * @param prevPrediction Previous prediction value used for computing euclidean distance. This prediction
	 * 	value will be used as the initial reference point for finding the closest prediction
	 * @param localizationLog Log File for debug information
	 * @param debugMode Specifies whether we are in debug mode. If in debug mode, localizationLog will be written to.
	 * 
	 * @return A double array of the weighted average of raw predictions
	 */
	protected static double[] weightRawPredictions(ArrayList<Double[]> rawPredictions, double[] prevPrediction,
			AndroidLog localizationLog, boolean debugMode)
	{
		
		double[] resPredictions = new double[2];
		double[] tmpPrediction = new double[2];
		double thisWeight = 0; // Weight for the current prediction
		double summedWeight = 0; // Sum of all the weights
		double euclDistance = 0;
		
		if (debugMode)
			localizationLog.save("--- Averaging The Predictions with Weight ---\n");
		
		for (int i = 0; i < rawPredictions.size(); i++)
		{
			if (debugMode)
				localizationLog.save("Prediction " + i + ": " + tmpPrediction[0] + "," + tmpPrediction[1] + "\n");
			
			tmpPrediction[0] = rawPredictions.get(i)[0].doubleValue();
			tmpPrediction[1] = rawPredictions.get(i)[1].doubleValue();
			
			euclDistance = computeEuclidean(tmpPrediction, prevPrediction);
			
			thisWeight = Math.exp(-1 * euclDistance);
			summedWeight += thisWeight;
			
			if (debugMode)
				localizationLog.save("Weight for Prediction " + i + ": " + thisWeight + "\n");
			
			// Sum the new weighted predictions
			resPredictions[0] = resPredictions[0] + (thisWeight * tmpPrediction[0]);
			resPredictions[1] = resPredictions[1] + (thisWeight * tmpPrediction[1]);
		}
		
		// Now normalize the weighted average
		resPredictions[0] = resPredictions[0] / summedWeight;
		resPredictions[1] = resPredictions[1] / summedWeight;
		
		return resPredictions;
	}
}
