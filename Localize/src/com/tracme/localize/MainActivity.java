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
	private int nX = 70; // Number of classes in x dimension
	private int nY = 70; // Number of classes in y dimension
	
	/* Coefficient for average estimation location */
	private double avgEstCoeff = 0.9;
	
	/* Coefficient for standard deviation */
	private double stdDevCoeff = 0.9;
	
	/* Localization log that will record our results */
	AndroidLog localizationLog;
	
	/* Name of the localization log file */
	String locLog = "loc_Aug16_70";//_correcttomaxrange_1stddev0.9";
	
	/*********************END********************************/
	
	/* Progress Bar used to show initial loading of localization classes */
	public ProgressBar initialProgBar;
	public int count = 1;
	
	double[] prediction = new double[2]; // Prediction of the corresponding point
	
	/* Stored predictions used for averaging */
	ArrayList <Double[]> predictions = new ArrayList <Double[]>();
	
	/* Counter for keep track of number of predictions done (used for averaging) */
	private int predCounter = 1;
	
	/* Rssi values received from LocalizeService */
	double[] rssis; 
	
	/* Interface to localization classes provided by Dr. Tran */
	private TestingTask localize;
	
	/* Previously predicted location */
	private double[] prevPrediction = new double[2];
	
	/* Previously estimated standard deviation */
	private double stdDevEst = 0;
	
	/***********************************************
	 * Variables for the Image Manipulation aspect *
	 *                                             *
	 *                                             *
	 ***********************************************/
	
	// Views for the Background Image and positioning Icon
	private ImageView imgView;
	private MyDrawableView myDView;
	
	private LocalizeDisplay ld;
	
	private int numScans = 3;
	private int numScansPending;
	
	/****************** END *************************/
	
	/* x coordinate for plotting on the image view */
	protected float xCoord = 0;
	
	/* y coordinate for plotting on the image view */
	protected float yCoord = 0;
	
	/* Runnable Thread used for initial loading of models and classes */
	private InitialLoadRunnable loadRunnable;
	
	/* Instance of our scan handler to handle incoming messages */
	private ScanHandler sHandler = new ScanHandler();
	
	/* Messenger for receiving messages from other threads */
	private Messenger messenger;
	
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
				Log.e("PREDICT_THREAD", "Error while predicting");
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
			try {
				// Setup the model classes
				localize.setNumClasses(nX, nY);
				// Once models are loaded, send a message to the main thread
				Message msg = Message.obtain();
				// Tell the main thread that we are done loading
				String loadResult = LOAD_COMPLETE;
				msg.obj = loadResult;
				// Send the message and end our run
				messenger.send(msg);
			} catch (Exception e)
			{
				Log.e("INITLOAD_THREAD", "Error while loading classees");
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
		double euclX; // Euclidean distance x value
		double euclY; // Euclidean distance y value
		double euclTotal; // Actual Euclidean distance
		
		localizationLog.save("------Running Error Correction------\n");
		
		// Compute the adjusted/corrected prediction value
		correctedPrediction[0] = (avgEstCoeff * tstprediction[0]) + ((1 - avgEstCoeff) * prevPrediction[0]);
		correctedPrediction[1] = (avgEstCoeff * tstprediction[1]) + ((1 - avgEstCoeff) * prevPrediction[1]);
		
		localizationLog.save("Previous Prediction: " + prevPrediction[0] + "," + prevPrediction[1] + "\n");
		localizationLog.save("Corrected Prediction: " + correctedPrediction[0] + "," + correctedPrediction[1] + "\n");
		
		// Compute Euclidean distance information
		euclX = tstprediction[0] - correctedPrediction[0];
		euclY = tstprediction[1] - correctedPrediction[1];
		euclX *= euclX;
		euclY *= euclY;
		
		localizationLog.save("Euclid Distance X: " + euclX + "\n");
		localizationLog.save("Euclid Distance Y: " + euclY + "\n");
		
		euclTotal = Math.sqrt(euclY + euclX);
		
		localizationLog.save("Euclidean Total: " + euclTotal + "\n");
		
		// Compute standard deviation
		stdDev = (stdDevCoeff * euclTotal) + ((1 - stdDevCoeff) * stdDevEst);
		
		localizationLog.save("Standard Deviation: " + stdDev + "\n");
		
		localizationLog.save("Values to Compare:\ncorrectedPrediction: " + correctedPrediction[0] + "," + correctedPrediction[1] + "\n");
		localizationLog.save("Range Prediction: " + (prevPrediction[0] + (3 *stdDev)) + "," + (prevPrediction[1] + (3 *stdDev)) + "\n");
		// Check if we are within a certain range
		if (correctedPrediction[0] <= prevPrediction[0] + (3 *stdDev)
				&& correctedPrediction[1] <= prevPrediction[1] + (3 *stdDev))
		{
			// CORRECT VALUE: We are within the range, so adjust the values
			localizationLog.save("WITHIN RANGE, update previous prediction\n");
			prevPrediction[0] = correctedPrediction[0];
			prevPrediction[1] = correctedPrediction[1];
			stdDevEst = stdDev;
		}
		else 
		{
			// THROWAWAY VALUE: Keep predicted value the same as the previous
			localizationLog.save("NOT WITHIN RANGE, keep previous prediction the same\n");
			prevPrediction[0] = prevPrediction[0] + (stdDev);
			prevPrediction[1] = prevPrediction[1] + (stdDev);
		}
		
		// Set our prediction to the value we deemed to be "Correct"
		prediction[0] = prevPrediction[0];
		prediction[1] = prevPrediction[1];
		
		return;
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
		//Toast.makeText(this, "IntentService", Toast.LENGTH_SHORT)
		//.show();
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
		String res = "Predicted Location for run " + count++ + ": "+ prediction[0] + "," + prediction[1];
		localizationLog.save(res + "\n");
		// Set the coord values to the predicted values
		xCoord = (float)prediction[0];
		yCoord = (float)prediction[1];
		plotPoint(xCoord, yCoord);
		//Toast.makeText(MainActivity.this,
		//		res, Toast.LENGTH_SHORT)
		//		.show();
		localizationLog.save("-------- STARTING PREDICTION NUMBER " + count + " --------\n");
	}
	
	/**
	 * Instantiate and set the initial values for objects/variables
	 * used
	 */
	private void setInitialValues()
	{
		// Set up a localization log for testing/recording results
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
	 * Average the predictions received from scanning and localizing
	 * 
	 */
	//TODO: Synchronize with message handler so that predCounter will not change
	private double[] averagePredictions()
	{
		double[] predictionAvg = new double[2];
		double xPred = 0; // Prediction value in X direction
		double yPred = 0; // Prediction value in Y direction
		
		localizationLog.save("------Averaging Predictions------\n");
		
		// Go through each prediction in the array list and average
		for (int i = 0; i < numScans; i++)
		{
			// Write to log file
			localizationLog.save("Prediction " + i + ": " + predictions.get(i)[0] + "," + predictions.get(i)[1] + "\n");
			// Sum prediction total
			xPred += predictions.get(i)[0];
			yPred += predictions.get(i)[1];
		}
		
		// Do the averaging
		predictionAvg[0] = xPred / predCounter;
		predictionAvg[1] = yPred / predCounter;
		
		// Reset the predictions array list
		predictions.clear();
		
		// Write to log file
		localizationLog.save("Average of prediction " + count + ": " + predictionAvg[0] + "," + predictionAvg[1] + "\n");
		
		return predictionAvg;
	}
	
	/**
	 * Initialize the training interface and all that is necessary to predict a location
	 *
	 * 
	 */
	private void initTraining()
	{
		localize = new TestingTask(rawFile, trainFile);
		localize.setProgBar(initialProgBar);
		
		final Thread initialLoadThread = new Thread(loadRunnable);
		initialLoadThread.start();
	}
	
	/*
	 * Function that will plot the point correctly regardless of scale or position
	 */
	private void plotPoint(float x, float y) {
		// TODO adjust for rotation

		myDView.setVisibility(View.INVISIBLE);
		myDView.setX(ld.getAdjustedX(x));
		myDView.setY(ld.getAdjustedY(y));
		myDView.setVisibility(View.VISIBLE);
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

	protected void onDraw(Canvas canvas) {
		mDrawable.draw(canvas);
	}
}
