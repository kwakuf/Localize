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
	
	/** The minimum value that the deviation radius factor (devFactor) may be */
	private final int devFactorMin = 3;
	
	/** Localize Application which contains global fields */
	private LocalizeApplication thisApp;
	
	/** x coordinate for plotting on the image view */
	protected float xCoord = 0;
	
	/** y coordinate for plotting on the image view */
	protected float yCoord = 0;
	
	/** x coordinate for plotting the raw/averaged predicted location */
	protected float origX = 0;
	
	/** y coordinate for plotting the raw/averaged predicted location */
	protected float origY = 0;
	
	/** x coordinate for plotting the error corrected predicted location */
	protected float errX = 0;
	
	/** y coordinate for plotting the error corrected predicted location */
	protected float errY = 0;
	
	/** Previously predicted location */
	protected double[] prevPrediction = new double[2];
	
	/** Previously estimated deviation */
	private double devEst = 0;
	
	/** Factor for deviation radius of the previous prediction */
	private int devFactor;
	
	/** Coefficient for average estimation location */
	private double avgEstCoeff = 0.8;
	
	/** Coefficient for deviation */
	private double devCoeff = 0.8;
	
	/** Predicted value of the location we are currently in */
	double[] prediction = new double[2];
	
	/** Stored predictions used for averaging */
	ArrayList <Double[]> predictions = new ArrayList <Double[]>();
	
	/** Counter for keep track of number of predictions done (used for averaging) */
	protected int predCounter = 1;
	
	/** Flag specifying if the predicted location is within the range of the previous location */
	protected boolean withinRange = true;
	
	/**
	 * Constructor for Prediction Object, initializing the Global fields of the LocalizeApplication
	 * Initializes the deviation to be equal to the minimum value
	 * 
	 * @param app Global LocalizationApp Object which holds global fields
	 */
	public PredictionsObject(LocalizeApplication app)
	{
		thisApp = app;
		devFactor = devFactorMin;
	}
	
	/**
	 * Performs error correction on the predicted value and computes new values for error correction/analysis
	 * The point of this method is to keep the predicted value within a certain range, so that the predicted
	 * value does not jump around. It is meant to remove inconsistencies.
	 *  
	 */
	protected void errorCorrect()
	{
		double[] correctedPrediction = new double[2];
		double euclTotal;
		
		if (thisApp.debugMode)
			thisApp.localizationLog.save("------Running Error Correction------\n");
		
		// Get the "corrected" prediction (a weighted value based on this prediction and previous prediction)
		correctedPrediction = LocalizeMath.computeWeightedPoint(prediction, prevPrediction, avgEstCoeff);
		
		if (thisApp.debugMode)
		{
			thisApp.localizationLog.save("Previous Prediction: " + prevPrediction[0] + "," + prevPrediction[1] + "\n");
			thisApp.localizationLog.save("Corrected Prediction: " + correctedPrediction[0] + "," + correctedPrediction[1] + "\n");
		}
				
		// Get the Euclidean distance between the previous prediction and the corrected prediction
		euclTotal = LocalizeMath.computeEuclidean(prevPrediction, correctedPrediction);
		
		if (thisApp.debugMode)
			thisApp.localizationLog.save("Euclidean Distance between corrected and previous: " + euclTotal + "\n");
		
		// Check if we are within a certain range
		if (euclTotal <= (devFactor * devEst))
		{
			// Only decrease the deviation radius factor if it is more than 3
			if (devFactor > 3)
				devFactor /= 2;
			
			// Compute and set the new deviation factor 
			devEst = (devCoeff * euclTotal) + ((1 - devCoeff) * devEst);
			
			// KEEP VALUE: We are within the range, so adjust the values
			if (thisApp.debugMode)
				thisApp.localizationLog.save("WITHIN RANGE, update previous prediction\n");
			
			prevPrediction[0] = correctedPrediction[0];
			prevPrediction[1] = correctedPrediction[1];
			
			// Mark that we were within range (for plotting the error corrected point in debug mode)
			withinRange = true;
		}
		else 
		{
			devFactor *= 2; // Double the deviation radius factor
			// THROWAWAY VALUE: Keep predicted value the same as the previous
			if (thisApp.debugMode)
				thisApp.localizationLog.save("NOT WITHIN RANGE, keep previous prediction the same\n");
			
			// Set the corrected Prediction coordinate values
			errX = (float)correctedPrediction[0];
			errY = (float)correctedPrediction[1];
			
			// Mark that we were *not* within range (for plotting the error corrected point in debug mode)
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
	protected void averagePredictions()
	{
		double[] predictionAvg = new double[2]; // Returned average prediction
		double xPred = 0; // Prediction value in X direction
		double yPred = 0; // Prediction value in Y direction
		
		// Start computing the weighted average on the third run
		if (thisApp.count >= 3)
			predictionAvg = LocalizeMath.weightRawPredictions(predictions, prevPrediction,
					 thisApp.localizationLog, thisApp.debugMode);
		else
		{
			// Average the predictions we already have
			for (int i = 0; i < predictions.size(); i++)
			{
				if (thisApp.debugMode)
				{
					thisApp.localizationLog.save("Prediction " + i + ": " + predictions.get(i)[0] + "," 
							+ predictions.get(i)[1] + "\n");
				}
				
				// Sum prediction total
				xPred += predictions.get(i)[0];
				yPred += predictions.get(i)[1];
			}
			
			// Do the averaging
			predictionAvg[0] = xPred / predCounter;
			predictionAvg[1] = yPred / predCounter;
			
			// Once we have our first and second prediction, compute the euclidean distance
			// so that we can get the deviation
			if (thisApp.count == 2)
			{
				double[] firstPrediction = prediction;
				double[] secondPrediction = predictionAvg;
				double euclTotal = LocalizeMath.computeEuclidean(firstPrediction, secondPrediction);
				
				// Compute deviation
				devEst = (devCoeff * euclTotal) + ((1 - devCoeff) * devEst);
				
				if (thisApp.debugMode)
					thisApp.localizationLog.save("Initial Deviation: " + devEst + "\n");
			}
			
		}
		
		/**
		 *********** USE THIS BLOCK WHEN ELIMINATING OUTLIERS USING (LocalizeMath.findPredictionsInRange()...
		 * REPLACE THE IF/ELSE Block ABOVE if using the findPredictionsInRange Method
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
		
		*****************************************************************/
		
		// Reset the predictions array list
		predictions.clear();
		
		if (thisApp.debugMode)
			thisApp.localizationLog.save("Average of prediction " + thisApp.count + ": " + predictionAvg[0] + "," + predictionAvg[1] + "\n");
		
		// Plot the averaged point on the image
		origX = (float)predictionAvg[0];
		origY = (float)predictionAvg[1];
		
		prediction = predictionAvg;
	}
	
	/**
	 * Sets the coordinate values for the original prediction
	 */
	protected void setOrigCoords()
	{
		origX = (float)prediction[0];
		origY = (float)prediction[1];
	}
}
