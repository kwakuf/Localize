package com.tracme.localize;

import java.util.ArrayList;
import java.util.concurrent.ExecutionException;

import com.tracme.R;
import com.tracme.training.TestingTask;
import com.tracme.util.*;

import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.OvalShape;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.SeekBar.OnSeekBarChangeListener;

/**
 * Main Activity for TracMe localization. 
 * This activity will handle user interaction with the floorplan/image,
 * as well as Message/Messenger/Message Handling for the intent service that will handle scanning for signals
 *  
 * @author Kwaku Farkye
 * @author Ken Ugo
 *
 */
public class MainActivity extends Activity implements OnTouchListener {
	
	/* String passed to main thread specifying that load is complete */
	public static final String LOAD_COMPLETE = "LoadComplete";
	
	/* String passed to main thread specifying that prediction thread is finished running */
	public static final String PREDICTION_COMPLETE = "PredictComplete";

	/* TAG Given to log on error of initial thread */
	public static final String INITLOAD_TAG = "INITLOAD_THREAD";
	
	/* TAG Given to log on error of prediction thread */
	public static final String PREDICT_THREAD_TAG = "PREDICT_THREAD";
	
	/* Access point table object that holds all of the access points */
	APTable apTable;
	
	/******** TODO: HOW ARE WE GOING TO SET THESE? *******/
	/* Name of the access point file */
	String apfilename = "apcc1_76_nexus";
	
	/* Options for localization (set these in settings) */
	LocalizeOptions options;
	
	/* Intent to start the LocalizeService */
	Intent localizeIntent;
	
	private String rawFile = "cc1_76_nexus.txt"; // Name of the rawfile
	private String trainFile = "train_p0.0.txt_sub_1.0.1.txt"; // Name of the training file
	private int nX = 100; // Number of classes in x dimension
	private int nY = 100; // Number of classes in y dimension
	
	/* Coefficient for average estimation location */
	private double avgEstCoeff = 0.7;
	
	/* Coefficient for standard deviation */
	private double stdDevCoeff = 0.8;
	
	/* Localization log that will record our results */
	AndroidLog localizationLog = null;
	
	/* Name of the localization log file */
	String locLog = "locAug19_class100_0.7";//_correcttomaxrange_1stddev0.9";
	
	/*********************END********************************/
	
	/* Progress Bar used to show initial loading of localization classes */
	public ProgressBar initialProgBar;
	
	/* Counter that records the number of predictions done */
	public int count = 1;
	
	/* Predicted value of the location we are currently in */
	double[] prediction = new double[2];
	
	/* Stored predictions used for averaging */
	ArrayList <Double[]> predictions = new ArrayList <Double[]>();
	
	/* Counter for keep track of number of predictions done (used for averaging) */
	private int predCounter = 1;
	
	/* RSSI values received from LocalizeService */
	double[] rssis; 
	
	/* Interface to localization classes provided by Dr. Tran */
	private TestingTask localize;
	
	/* Previously predicted location */
	private double[] prevPrediction = new double[2];
	
	/* Previously estimated standard deviation */
	private double stdDevEst = 0;
	
	/* Factor for standard deviation radius of the previous prediction */
	private int stdDevFactor = 3;
	
	/***********************************************
	 * Variables for the Image Manipulation aspect *
	 *                                             *
	 *                                             *
	 ***********************************************/
	
	// Views for the Background Image and positioning Icon
	private ImageView imgView;
	private MyDrawableView myDView;
	private MyDrawableView errView;
	private MyDrawableView origView;
	
	private LocalizeDisplay ld;
	
	private int numScans = 5;
	private int numScansPending;
	
	/****************** END *************************/
	
	/* x coordinate for plotting on the image view */
	protected float xCoord = 0;
	
	/* y coordinate for plotting on the image view */
	protected float yCoord = 0;
	
	/* x coordinate for plotting the original predicted location */
	protected float origX = 0;
	
	/* y coordinate for plotting the original predicted location */
	protected float origY = 0;
	
	/* x coordinate for plotting the error corrected predicted location */
	protected float errX = 0;
	
	/* y coordinate for plotting the error corrected predicted location */
	protected float errY = 0;
	
	/* Flag specifying if the predicted location is within the range of the previous location */
	private boolean withinRange = true;
	
	/* Runnable Thread used for initial loading of models and classes */
	private InitialLoadRunnable loadRunnable;
	
	/* Instance of our scan handler to handle incoming messages */
	private ScanHandler sHandler = new ScanHandler();
	
	/* Messenger for receiving messages from other threads */
	private Messenger messenger;
	
	/* Flag specifying whether we are in debug mode */
	private boolean debugMode = true;
	
	/* Start time for measuring the time a task takes */
	//private long startTime;
	
	/* End time for measuring the time a task takes */
	//private long endTime;
	
	/* Multiple to compute seconds from nanoseconds */
	private long nanoMult = 1000000000;
	
	/**
	 * Nested runnable class that handles predicting the user's location and performing error
	 * analysis/correction
	 * 
	 * @author Kwaku Farkye
	 * 
	 */
	private class PredictionRunnable implements Runnable {
		@Override
		public void run()
		{
			try {
				prediction = averagePredictions();
				
				// Dont error correct the first couple of runs
				if (count >= 3)
					errorCorrect(prediction); // Perform error correction
				
				// All done predicting, so lets send a message to the main thread
				Message msg = Message.obtain();
				// Tell the main thread we are done predicting
				String predictResult = PREDICTION_COMPLETE;
				msg.obj = predictResult;
				// Send message and end run
				messenger.send(msg);
				
			} catch (Exception ex)
			{
				Log.e(PREDICT_THREAD_TAG, "Error while predicting");
				ex.printStackTrace();
			}
		}
	}
	
	/**
	 * Nested runnable class that loads the localization classes/models and updates
	 * the progress bar on the UI thread while doing so.
	 * 
	 * @author Kwaku Farkye
	 *
	 */
	private class InitialLoadRunnable implements Runnable {
		@Override
		public void run()
		{
			long startTime = 0;
			long endTime = 0;
			
			try {
				if (debugMode)
					startTime = System.nanoTime();
				
				// Setup the model classes
				localize.setNumClasses(nX, nY);
				// Once models are loaded, send a message to the main thread
				
				if (debugMode)
					endTime = System.nanoTime();
				
				Message msg = Message.obtain();
				// Tell the main thread that we are done loading
				String loadResult = LOAD_COMPLETE;
				msg.obj = loadResult;
				// Send the message and end our run
				messenger.send(msg);
				
				if (debugMode)
					localizationLog.save("Time taken to load X" + nX + ", Y" + nY + " classes: " +
							((endTime - startTime) / nanoMult) + "." + ((endTime - startTime) % nanoMult) + " seconds\n" );
				
			} catch (Exception e)
			{
				Log.e(INITLOAD_TAG, "Error while loading classees");
				e.printStackTrace();
			}
		}
	}
	
	/**
	 *  Nested Handler class for message communication between main activity and other threads/services
	 *  started by the activity
	 *  
	 */
	private class ScanHandler extends Handler {	
		
		/**
		 * Method that is called when a message is received from a Messenger
		 */
		@Override
		public void handleMessage(Message msg)
		{
			// Check if this is an initial load message from the initial load thread
			if (msg.obj == LOAD_COMPLETE)
			{
				initImageView();
				if (debugMode)
					localizationLog.save("-------- STARTING PREDICTION NUMBER " + count + " --------\n");
				
				return;
			}
			
			// Check if this is a message from the prediction thread
			if (msg.obj == PREDICTION_COMPLETE)
			{	
				// Translate the prediction to a coordinate
				translatePoint(prediction);
				
				// Reset prediction counter
				predCounter = 1;
				
				// Restart the service
				initIntentService();
				return;
			}
			
			//Receive the message and, using the information 
			//received from the message, update the location of the user on the map 
			if (msg.arg1 == RESULT_OK)
			{
				// Receive the bundle that was passed by the message
				Bundle inData = msg.getData();
				
				// Receive the double array in the bundle, representing the results of the scan
				rssis = inData.getDoubleArray(LocalizeService.SCANARRAY_KEY);
				
				// Call to Training interface: Predict the location
				prediction = localize.getEstLocation(rssis);
				
				if (debugMode)
				{
					// TESTING: Plot the prediction location (blue icon)
					//origX = (float)prediction[0];
					//origY = (float)prediction[1];
					//plotOrigPoint(origX, origY);
					
					long totalScanTime = inData.getLong(LocalizeService.TIME_KEY);
					localizationLog.save("Time taken for scan: " + (totalScanTime / nanoMult) + "." + ((totalScanTime) % nanoMult) + " seconds\n");
				}
				
				// Translate primitive double array to Double instance array
				Double[] predToDouble = new Double[2];
				predToDouble[0] = Double.valueOf(prediction[0]);
				predToDouble[1] = Double.valueOf(prediction[1]);
				
				
				//Toast.makeText(MainActivity.this, "Predicted Vals: " + predToDouble[0] + ", " + predToDouble[1], Toast.LENGTH_SHORT)
				//.show();
				// Add the translated prediction to the array list
				predictions.add(predToDouble);
							
				if (predCounter >= numScans)
				{ // If number of predictions is equal to the number of scans, lets average
					/*
					prediction = averagePredictions();
					
					// Dont error correct the first couple of runs
					if (count >= 3)
						errorCorrect(prediction); // Perform error correction
					
					// Translate the prediction to a coordinate
					translatePoint(prediction);
					
					// Reset prediction counter
					predCounter = 1;
					*/
					PredictionRunnable prun = new PredictionRunnable();
					Thread predictThread = new Thread(prun);
					predictThread.start();
					
					return;
				}
				else if (predCounter < numScans)
				{
				//	Toast.makeText(MainActivity.this, "Add to average buffer", Toast.LENGTH_SHORT)
				//	.show();
					predCounter++;
				}
				
				// Restart the service
				initIntentService();
						
			}
			else {
				Toast.makeText(MainActivity.this, "Didnt receive anything back",
						Toast.LENGTH_LONG).show();
			}
			return;
		}
	}
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);		
		setContentView(R.layout.activity_main);
		
		if (debugMode && localizationLog != null)
		{
			localizationLog.save("Creating Activity again\n");
			return;
		}
		
		initialProgBar = (ProgressBar) findViewById(R.id.initProgBar);
		
		// Set the max value of the progress bar to the number of classes that we must load
		initialProgBar.setMax(nX+nY);
		
		// Make instance of runnable class for initial load of models..
		loadRunnable = new InitialLoadRunnable();

		// Set the initial values needed for this run
		setInitialValues();
		
		// Initialize loading of the model classes (starts a new thread)
		initTraining();
		
		Toast.makeText(this, "Localize", Toast.LENGTH_SHORT)
		.show();	
	}
	
	@Override
	public void onSaveInstanceState(Bundle savedInstanceState)
	{
		System.out.println("Saving Instance state\n");
		if (debugMode)
			localizationLog.save("Save Instance State called\n");
	}
	
	/**
	 * Initializes all of the components necessary for the localization image view.
	 * Also sets the view to the localization image view. This method is called after the models
	 * are loaded into their objects.
	 * 
	 */
	private void initImageView()
	{
		setContentView(R.layout.activity_two);
		imgView = (ImageView) findViewById(R.id.imageView1);

		myDView = (MyDrawableView) findViewById(R.id.circleView1);
		myDView.setVisibility(View.INVISIBLE);

		
		errView = (MyDrawableView) findViewById(R.id.circleViewErr);
		errView.setVisibility(View.INVISIBLE);
		errView.setDrawColor("Blue");
		
		origView = (MyDrawableView) findViewById(R.id.circleViewOrig);
		origView.setVisibility(View.INVISIBLE);
		origView.setDrawColor("Red");
		
		imgView.setOnTouchListener(this);
		
		// Initialize the first intent service and start it
		initIntentService();
		
		ld = new LocalizeDisplay();
		ld.drawable = getResources().getDrawable(R.drawable.cc_1);
		ld.calcInitScale();
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}
	
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.action_settings:
			showSeek();
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}
	}

	public void showSeek() {
		final TextView tvBetVal;
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		LayoutInflater inflater = this.getLayoutInflater();
		View v = inflater.inflate(R.layout.dialog, null);
		builder
				.setView(v)
				.setTitle(
						"Enter the sampling speed (Note: Higher speeds may reduce accuracy)")
				.setPositiveButton("Confirm", new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						// confirmValues();
						// SAVE THE PROGRESS BAR VALUE SOMEWHERE
						numScans = (numScansPending == 0) ? (1) : (numScansPending);
						// Set the option for the next intent
						options.setNumScans(numScans);
						dialog.dismiss();
					}
				}).setNeutralButton("Cancel", null).show();
		SeekBar sbBetVal = (SeekBar) v.findViewById(R.id.sbBetVal);
		tvBetVal = (TextView) v.findViewById(R.id.tvBetVal);
		sbBetVal.setMax(10);
		sbBetVal.setProgress(numScans);
		sbBetVal.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {

			@Override
			public void onStopTrackingTouch(SeekBar seekBar) {
				// TODO Auto-generated method stub

			}

			@Override
			public void onStartTrackingTouch(SeekBar seekBar) {
				// TODO Auto-generated method stub

			}

			@Override
			public void onProgressChanged(SeekBar seekBar, int progress,
					boolean fromUser) {
				// TODO Auto-generated method stub
				tvBetVal.setText(String.valueOf(progress));
				numScansPending = progress;
			}
		});
	}
	
	@Override
	public void onDestroy()
	{
		super.onDestroy();
		stopService(localizeIntent);
	}
	
	public boolean onTouch(View v, MotionEvent event) {
		DisplayMetrics metrics = new DisplayMetrics();
		getWindowManager().getDefaultDisplay().getMetrics(metrics);

		// handle touch events here
		ImageView view = (ImageView) v;

		switch (event.getAction() & MotionEvent.ACTION_MASK) {
		case MotionEvent.ACTION_DOWN:
			ld.actionDown(event);
			ld.mode = LocalizeDisplay.DRAG;
			break;
		case MotionEvent.ACTION_POINTER_DOWN:
			System.out.println("Subsequent presses");
			ld.pointerDown(event);
			
			ld.oldDist = ld.spacing(event);
			if (ld.oldDist > 10f) {
				ld.savedMatrix.set(ld.matrix);
				ld.midPoint(ld.mid, event);
				ld.mode = LocalizeDisplay.ZOOM;
			}
			break;
		case MotionEvent.ACTION_UP:
		case MotionEvent.ACTION_POINTER_UP:
//			ld.checkEdgeCases(view);

			ld.mode = LocalizeDisplay.NONE;
			break;
		case MotionEvent.ACTION_MOVE:
			if (ld.mode == LocalizeDisplay.DRAG) {
				ld.drag(event);
			}
			else if (ld.mode == LocalizeDisplay.ZOOM) {
        ld.zoomAndRotate(event, view);
			}
			break;
		}

		ld.matrix.getValues(ld.eventMatrix);

		/* The point specified will be given by the localization function */
		plotPoint(xCoord, yCoord);
		
		if (debugMode)
		{
			plotOrigPoint(origX, origY);
			if (!withinRange)
				plotErrPoint(errX, errY);
		}
		
		view.setImageMatrix(ld.matrix);
		return true;
	}
	
	/**
	 * Initialize the information that will be sent to the service.
	 * Once the data is bundled within the intent, start the service.
	 */
	public void initIntentService() {
		// Add the Messenger info to the intent, so the
		// intent service knows how who to give the message to
		localizeIntent.putExtra(LocalizeService.MESSENGER_KEY, messenger);
		localizeIntent.putExtra(LocalizeService.OPTIONS_KEY, options);
		localizeIntent.putExtra(LocalizeService.APTABLE_KEY, apTable);
		localizeIntent.putExtra(LocalizeService.COUNT_KEY, count);
		localizeIntent.putExtra(LocalizeService.DEBUG_KEY, debugMode);
		
		startService(localizeIntent);
	}

	/**
	 * Translates the returned points from getEstLocation into coordinates.
	 * The coordinate values will then be plotted via plotPoint()
	 * 
	 * @param prediction The predicted values from getEstLocation()
	 */
	private void translatePoint(double[] prediction)
	{
		
		if (count < 3)
		{
			prevPrediction[0] = prediction[0];
			prevPrediction[1] = prediction[1];
		}
		
		if (debugMode)
		{
			String res = "Predicted Location for run " + count++ + ": "+ prediction[0] + "," + prediction[1];
			localizationLog.save(res + "\n");
		}
		
		// Set the coord values to the predicted values
		xCoord = (float)prediction[0];
		yCoord = (float)prediction[1];
		
		// Plot the point on the image
		plotPoint(xCoord, yCoord);
		
		if (debugMode)
			plotOrigPoint(origX, origY);
		
		if (!withinRange && debugMode)
			plotErrPoint(errX, errY);

		if (debugMode)
			localizationLog.save("-------- STARTING PREDICTION NUMBER " + count + " --------\n");
	}
	
	/**
	 * Instantiate and set the initial values for objects/variables
	 * used
	 */
	private void setInitialValues()
	{
		// Set up a localization log for testing/recording results
		if (debugMode)
			localizationLog = new AndroidLog(locLog + ".txt");
		
		// Load AP Table
		apTable = new APTable(apfilename);
		apTable.loadTable();
		
		//Initialize options
		options = new LocalizeOptions();
		localizeIntent = new Intent(this, LocalizeService.class);
		
		// Create a Messenger for communication back and forth
		messenger = new Messenger(sHandler);
		
	}
	
	/**
	 * Performs error correction on the predicted value and computes new values for error correction/analysis
	 * 
	 * @param prediction The predicted value computed after averaging
	 * 
	 */
	private void errorCorrect(double[] tstprediction)
	{
		double[] correctedPrediction = new double[2];
		double stdDev;
		//double euclX; // Euclidean distance x value
		//double euclY; // Euclidean distance y value
		double euclTotal; // Actual Euclidean distance
		
		if (debugMode)
			localizationLog.save("------Running Error Correction------\n");
		
		// Compute the adjusted/corrected prediction value
		//correctedPrediction[0] = (avgEstCoeff * tstprediction[0]) + ((1 - avgEstCoeff) * prevPrediction[0]);
		//correctedPrediction[1] = (avgEstCoeff * tstprediction[1]) + ((1 - avgEstCoeff) * prevPrediction[1]);
		
		correctedPrediction = computeWeightedPoint(tstprediction, prevPrediction, avgEstCoeff);
		
		if (debugMode)
		{
			localizationLog.save("Previous Prediction: " + prevPrediction[0] + "," + prevPrediction[1] + "\n");
			localizationLog.save("Corrected Prediction: " + correctedPrediction[0] + "," + correctedPrediction[1] + "\n");
		}
		
		// Compute Euclidean distance information
		//euclX = tstprediction[0] - correctedPrediction[0];
		//euclY = tstprediction[1] - correctedPrediction[1];
		//euclX *= euclX;
		//euclY *= euclY;
		
		euclTotal = computeEuclidean(tstprediction, correctedPrediction);
		
		if (debugMode)
			localizationLog.save("Euclidean Total: " + euclTotal + "\n");
		
		// Compute standard deviation
		stdDev = (stdDevCoeff * euclTotal) + ((1 - stdDevCoeff) * stdDevEst);
		
		if (debugMode)
			localizationLog.save("Standard Deviation: " + stdDev + "\n");
		
		//localizationLog.save("Values to Compare:\ncorrectedPrediction: " + correctedPrediction[0] + "," + correctedPrediction[1] + "\n");
		//localizationLog.save("Range Prediction: " + (prevPrediction[0] + (3 *stdDev)) + "," + (prevPrediction[1] + (3 *stdDev)) + "\n");
		
		// Compute euclidean distance between previous prediction and corrected prediction
		//euclX = (prevPrediction[0] - correctedPrediction[0]);
		//euclY = (prevPrediction[1] - correctedPrediction[1]);
		//euclX *= euclX;
		//euclY *= euclY;
		
		euclTotal = computeEuclidean(prevPrediction, correctedPrediction);
		
		if (debugMode)
			localizationLog.save("Euclidean Distance between corrected and previous: " + euclTotal + "\n");
		
		// Check if we are within a certain range
		if (euclTotal <= (stdDevFactor *stdDev))
		{
			// Only decrease the standard deviation radius factor if it is more than 3
			if (stdDevFactor > 3)
				stdDevFactor /= 2;
			
			// CORRECT VALUE: We are within the range, so adjust the values
			if (debugMode)
				localizationLog.save("WITHIN RANGE, update previous prediction\n");
			
			prevPrediction[0] = correctedPrediction[0];
			prevPrediction[1] = correctedPrediction[1];
			stdDevEst = stdDev;
			withinRange = true;
		}
		else 
		{
			stdDevFactor *= 2; // Double the standard deviation radius factor
			// THROWAWAY VALUE: Keep predicted value the same as the previous
			if (debugMode)
				localizationLog.save("NOT WITHIN RANGE, keep previous prediction the same\n");
			
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
	 */
	//TODO: Synchronize with message handler so that predCounter will not change
	private double[] averagePredictions()
	{
		double[] predictionAvg = new double[2]; // Returned average prediction
		ArrayList<Double[]> adjustedPredictions;
		double xPred = 0; // Prediction value in X direction
		double yPred = 0; // Prediction value in Y direction
		
		// Only start excluding outliers after the third run of predictions
		if (count >= 3)
			adjustedPredictions = findPredictionsInRange(predictions);
		else
			adjustedPredictions = predictions;
		
		if (debugMode)
			localizationLog.save("------Averaging Predictions------\n");
		
		// Go through each prediction in the adjusted array list and average
		for (int i = 0; i < adjustedPredictions.size(); i++)
		{	
			if (debugMode)
			{
				localizationLog.save("Prediction " + i + ": " + adjustedPredictions.get(i)[0] + "," 
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
		
		if (debugMode)
			localizationLog.save("Average of prediction " + count + ": " + predictionAvg[0] + "," + predictionAvg[1] + "\n");
		
		// Plot the averaged point on the image
		origX = (float)predictionAvg[0];
		origY = (float)predictionAvg[1];
		
		return predictionAvg;
	}
	
	/**
	 * Finds the predictions/points that fall within the range of a reference prediction/point
	 * 
	 * @param rawPredictions The list of original predictions
	 * 
	 * @return A list of predictions that fall within the computed range
	 */
	private ArrayList<Double[]> findPredictionsInRange(ArrayList<Double[]> rawPredictions)
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
	 * Computes a corrected point based off of two points in the coordinate system
	 * 
	 * @param heavyPoint The point that will get the most weight
	 * @param lightWeight The point that will get the least amount of weight
	 * @param weight The weight that the heavy point will receive
	 * 
	 * @return The weighted point
	 */
	private static double[] computeWeightedPoint(double[] heavyPoint, double[] lightPoint, double weight)
	{
		double[] weightedPrediction = new double[2];
		
		// Compute the adjusted/corrected prediction value
		weightedPrediction[0] = (weight * heavyPoint[0]) + ((1 - weight) * lightPoint[0]);
		weightedPrediction[1] = (weight * heavyPoint[1]) + ((1 - weight) * lightPoint[1]);
		
		return weightedPrediction;
	}
	
	/**
	 * Computes the euclidean distance between two points
	 * 
	 * @param point1 The first point used in the calculation
	 * @param point2 The second point used in the calculation
	 * 
	 * @return The euclidean distance between point 1 and point 2
	 */
	private static double computeEuclidean(double[] point1, double[] point2)
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
	 * Initialize the training interface and all that is necessary to predict a location
	 * Spawns a new thread to handle the loading of the classes/models
	 * 
	 */
	private void initTraining()
	{
		localize = new TestingTask(rawFile, trainFile);
		localize.setProgBar(initialProgBar);
		
		final Thread initialLoadThread = new Thread(loadRunnable);
		initialLoadThread.start();
	}
	
	/**
	 * Plots the predicted point correctly on the image. Takes into consideration the scaling
	 * and positioning of the image
	 * 
	 * @param x The predicted x coordinate
	 * @param y The predicted y coordinate
	 */
	private void plotPoint(float x, float y) {
		// TODO adjust for rotation

		myDView.setVisibility(View.INVISIBLE);
		myDView.setX(ld.getAdjustedX(x));
		myDView.setY(ld.getAdjustedY(y));
		myDView.setVisibility(View.VISIBLE);
		return;
	}
	
	
	/**
	 * Plots the adjusted/corrected predicted point correctly on the image. Takes into consideration the scaling
	 * and positioning of the image. This method is only called when in debug mode.
	 * 
	 * @param x The predicted x coordinate
	 * @param y The predicted y coordinate
	 */
	private void plotErrPoint(float x, float y) {
		// TODO adjust for rotation

		errView.setVisibility(View.INVISIBLE);
		errView.setX(ld.getAdjustedX(x));
		errView.setY(ld.getAdjustedY(y));
		errView.setVisibility(View.VISIBLE);
		return;
	}
	
	/**
	 * Plots the originally predicted point correctly on the image. Takes into consideration the scaling
	 * and positioning of the image. This method is only called when in debug mode.
	 * 
	 * @param x The predicted x coordinate
	 * @param y The predicted y coordinate
	 */
	private void plotOrigPoint(float x, float y) {
		// TODO adjust for rotation

		origView.setVisibility(View.INVISIBLE);
		origView.setX(ld.getAdjustedX(x));
		origView.setY(ld.getAdjustedY(y));
		origView.setVisibility(View.VISIBLE);
		return;
	}
}

/*
 * Tracking circle
 */
class MyDrawableView extends View {
	private ShapeDrawable mDrawable;

	public MyDrawableView(Context context) {
		super(context);

		int x = 10;
		int y = 10;
		int width = 300;
		int height = 50;

		mDrawable = new ShapeDrawable(new OvalShape());
		mDrawable.getPaint().setColor(0xff74AC23);
		mDrawable.setBounds(x, y, x + width, y + height);
	}

	public MyDrawableView(Context context, AttributeSet attrs) {
		super(context, attrs);

		int x = 0;
		int y = 0;
		int width = 50;
		int height = 50;

		mDrawable = new ShapeDrawable(new OvalShape());
		mDrawable.getPaint().setColor(0xff74AC23);
		mDrawable.setBounds(x, y, x + width, y + height);
	}
	
	public void setDrawColor(String color) {
		if (color == "Blue")
			mDrawable.getPaint().setColor(Color.BLUE);
		else if (color == "Red")
			mDrawable.getPaint().setColor(Color.RED);
		
		return;
	}

	protected void onDraw(Canvas canvas) {
		mDrawable.draw(canvas);
	}
}
