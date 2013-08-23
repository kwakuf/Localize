/**
 * 
 */
package com.tracme.localize;

import java.util.ArrayList;

/**
 * The PredictionsObject class provides information about predictions during runs of
 * the localization program
 * 
 * @author Kwaku Farkye
 *
 */
public class PredictionsObject {
	
	/** Localize Application which contains global fields */
	LocalizeApplication thisApp;
	
	/** x coordinate for plotting on the image view */
	protected float xCoord = 0;
	
	/** y coordinate for plotting on the image view */
	protected float yCoord = 0;
	
	/** x coordinate for plotting the original predicted location */
	protected float origX = 0;
	
	/** y coordinate for plotting the original predicted location */
	protected float origY = 0;
	
	/** x coordinate for plotting the error corrected predicted location */
	protected float errX = 0;
	
	/** y coordinate for plotting the error corrected predicted location */
	protected float errY = 0;
	
	/** Previously predicted location */
	protected double[] prevPrediction = new double[2];
	
	/** Previously estimated standard deviation */
	private double stdDevEst = 0;
	
	/** Factor for standard deviation radius of the previous prediction */
	private int stdDevFactor = 3;
	
	/** Coefficient for average estimation location */
	private double avgEstCoeff = 0.7;
	
	/** Coefficient for standard deviation */
	private double stdDevCoeff = 0.8;
	
	/** Predicted value of the location we are currently in */
	double[] prediction = new double[2];
	
	/** Stored predictions used for averaging */
	ArrayList <Double[]> predictions = new ArrayList <Double[]>();
	
	/** Counter for keep track of number of predictions done (used for averaging) */
	protected int predCounter = 1;
	
	/** Flag specifying if the predicted location is within the range of the previous location */
	protected boolean withinRange = true;
	
	/**
	 * 
	 * @param app Global LocalizationApp Object which holds global fields
	 */
	public PredictionsObject(LocalizeApplication app)
	{
		thisApp = app;
	}
	
	/**
	 * Performs error correction on the predicted value and computes new values for error correction/analysis
	 * 
	 * @param prediction The predicted value computed after averaging
	 * 
	 */
	protected void errorCorrect()
	{
		double[] correctedPrediction = new double[2];
		double stdDev;
		double euclTotal; // Actual Euclidean distance
		
		if (thisApp.debugMode)
			thisApp.localizationLog.save("------Running Error Correction------\n");
		
		correctedPrediction = LocalizeMath.computeWeightedPoint(prediction, prevPrediction, avgEstCoeff);
		
		if (thisApp.debugMode)
		{
			thisApp.localizationLog.save("Previous Prediction: " + prevPrediction[0] + "," + prevPrediction[1] + "\n");
			thisApp.localizationLog.save("Corrected Prediction: " + correctedPrediction[0] + "," + correctedPrediction[1] + "\n");
		}
		
		euclTotal = LocalizeMath.computeEuclidean(prediction, correctedPrediction);
		
		if (thisApp.debugMode)
			thisApp.localizationLog.save("Euclidean Total: " + euclTotal + "\n");
		
		// Compute standard deviation
		stdDev = (stdDevCoeff * euclTotal) + ((1 - stdDevCoeff) * stdDevEst);
		
		if (thisApp.debugMode)
			thisApp.localizationLog.save("Standard Deviation: " + stdDev + "\n");
		
		euclTotal = LocalizeMath.computeEuclidean(prevPrediction, correctedPrediction);
		
		if (thisApp.debugMode)
			thisApp.localizationLog.save("Euclidean Distance between corrected and previous: " + euclTotal + "\n");
		
		// Check if we are within a certain range
		if (euclTotal <= (stdDevFactor *stdDev))
		{
			// Only decrease the standard deviation radius factor if it is more than 3
			if (stdDevFactor > 3)
				stdDevFactor /= 2;
			
			// CORRECT VALUE: We are within the range, so adjust the values
			if (thisApp.debugMode)
				thisApp.localizationLog.save("WITHIN RANGE, update previous prediction\n");
			
			prevPrediction[0] = correctedPrediction[0];
			prevPrediction[1] = correctedPrediction[1];
			stdDevEst = stdDev;
			withinRange = true;
		}
		else 
		{
			stdDevFactor *= 2; // Double the standard deviation radius factor
			// THROWAWAY VALUE: Keep predicted value the same as the previous
			if (thisApp.debugMode)
				thisApp.localizationLog.save("NOT WITHIN RANGE, keep previous prediction the same\n");
			
			// Set the corrected Prediction coordinate values
			errX = (float)correctedPrediction[0];
			errY = (float)correctedPrediction[1];
			withinRange = false;
		}
		
		// Set our prediction to the value we deemed to be "Correct"
		prediction[0] = prevPrediction[0];
		prediction[1] = prevPrediction[1];
		
		return;
	}
	
	/**
	 * Average the predictions received from scanning and localizing
	 * 
	 * @return A double array consisting of the averaged prediction value
	 */
	//TODO: Synchronize with message handler so that predCounter will not change
	void averagePredictions()
	{
		double[] predictionAvg = new double[2]; // Returned average prediction
		ArrayList<Double[]> adjustedPredictions;
		double xPred = 0; // Prediction value in X direction
		double yPred = 0; // Prediction value in Y direction
		
		// Only start excluding outliers after the third run of predictions
		if (thisApp.count >= 3)
			adjustedPredictions = LocalizeMath.findPredictionsInRange(predictions, prevPrediction, 
					thisApp.localizationLog, thisApp.debugMode);
		else
			adjustedPredictions = predictions;
		
		if (thisApp.debugMode)
			thisApp.localizationLog.save("------Averaging Predictions------\n");
		
		// Go through each prediction in the adjusted array list and average
		for (int i = 0; i < adjustedPredictions.size(); i++)
		{	
			if (thisApp.debugMode)
			{
				thisApp.localizationLog.save("Prediction " + i + ": " + adjustedPredictions.get(i)[0] + "," 
						+ adjustedPredictions.get(i)[1] + "\n");
			}
			
			// Sum prediction total
			xPred += adjustedPredictions.get(i)[0];
			yPred += adjustedPredictions.get(i)[1];
		}
		
		// Do the averaging
		predictionAvg[0] = xPred / predCounter;
		predictionAvg[1] = yPred / predCounter;
		
		// Reset the predictions array list
		predictions.clear();
		
		if (thisApp.debugMode)
			thisApp.localizationLog.save("Average of prediction " + thisApp.count + ": " + predictionAvg[0] + "," + predictionAvg[1] + "\n");
		
		// Plot the averaged point on the image
		origX = (float)predictionAvg[0];
		origY = (float)predictionAvg[1];
		
		prediction = predictionAvg;
	}
	
	protected void setOrigCoords()
	{
		// TESTING: Plot the prediction location (red icon)
		origX = (float)prediction[0];
		origY = (float)prediction[1];
	}
}
