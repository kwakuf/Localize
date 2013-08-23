package com.tracme.localize;

import java.io.*;
import java.util.ArrayList;
import java.util.Calendar;
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
import android.graphics.PointF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.TransitionDrawable;
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
import android.view.animation.Animation;
import android.view.animation.Animation.AnimationListener;
import android.view.animation.TranslateAnimation;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.ToggleButton;

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
	
	/** String passed to main thread specifying that load is complete */
	public static final String LOAD_COMPLETE = "LoadComplete";
	
	/** String passed to main thread specifying that prediction thread is finished running */
	public static final String PREDICTION_COMPLETE = "PredictComplete";

	/** String passed to main thread specifying that the TestingTask Object has been successfully loaded */
	public static final String PERSIST_LOAD_SUCCESS = "LoadSuccess";
	
	/** String passed to main thread specifying that the TestingTask Object failed to load */
	public static final String PERSIST_LOAD_FAIL = "LoadFail";
	
	/** TAG Given to log on error of initial thread */
	public static final String INITLOAD_TAG = "INITLOAD_THREAD";
	
	/** TAG Given to log on error of prediction thread */
	public static final String PREDICT_THREAD_TAG = "PREDICT_THREAD";
	
	/** TAG Given to log on error of persistence load thread */
	public static final String PERSIST_LOAD_TAG = "PERSIST_LOAD_THREAD";
	
	/** Localize Application instance for getting global objects/fields */
	LocalizeApplication thisApp;
	
	/** Predictions object that holds information about predictions */
	PredictionsObject predObj;
	
	/** Access point table object that holds all of the access points */
	APTable apTable;
	
	/** Name of the localization log file */
	String locLog;
	
	/** Name of the access point file */
	String apfilename;
	
	/** Intent to start the LocalizeService */
	Intent localizeIntent;
	
	/** Name of the raw file. Used by TestingTask */
	private String rawFile;

	/******** TODO: HOW ARE WE GOING TO SET THESE? *******/
	
	/** Options for localization (set these in settings) */
	LocalizeOptions options;
		
	/** Name of the training file. Used by TestingTask */
	private String trainFile = "train_p0.0.txt_sub_1.0.1.txt";
	
	/** Number of classes in x dimension */
	private int nX = 100;
	
	/** Number of classes in y dimension */
	private int nY = 100;
	
	/*********************END********************************/
	
	/** Progress Bar used to show initial loading of localization classes */
	public ProgressBar initialProgBar;
		
	/** RSSI values received from LocalizeService */
	double[] rssis; 
	
	/** Interface to localization classes provided by Dr. Tran */
	private TestingTask localize;
	
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
	private TrailView tview;
	
	private LocalizeDisplay ld;
	
	private PointF center = new PointF();
	public PointF transPoint = new PointF();
	
	private int numScans = 5;
	private int numScansPending;
	public int trailNdx = 0;

	public static final boolean POINT = true;
	public static final boolean FOLLOW = false;

	private boolean mapState = POINT;
	private boolean tempMap;	
	public boolean animDone = true;

	
	/****************** END *************************/
		
	/** Instance of our scan handler to handle incoming messages */
	private ScanHandler sHandler = new ScanHandler();
	
	/** Messenger for receiving messages from other threads */
	private Messenger messenger;
	
	/** Flag specifying whether our localization data has been written to storage yet */
	private boolean writtenToStorage = false;
	
	/** Flag specifying that initial loading is complete. This flag is used to let us know that
	 * the localization data can be stored
	 */
	private boolean finishedLoading = false;
	
	/**
	 * Nested runnable class that handles predicting the user's location and performing error
	 * analysis/correction
	 * 
	 * @author Kwaku Farkye
	 * 
	 */
	private class PredictionRunnable implements Runnable 
	{
		@Override
		public void run()
		{
			try {
				predObj.averagePredictions();
				
				// Don't error correct the first couple of runs
				if (thisApp.count >= 3)
					predObj.errorCorrect(); // Perform error correction
				
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
	 * Nested runnable class that saves localization data, cutting down load times on future
	 * runs of the application
	 * 
	 * @author Kwaku Farkye
	 *
	 */
	private class PersistenceRunnable implements Runnable
	{
		@Override
		public void run()
		{
			long startTime = System.nanoTime();
			
			if (saveLocalizeData())
			{
				if (thisApp.debugMode)
				{
					long endTime = System.nanoTime();
					thisApp.localizationLog.save("Saved localization data via persistence runnable \n");
					thisApp.localizationLog.save("Time taken to save log: " + 
							((endTime - startTime) / thisApp.nanoMult) + "." + ((endTime - startTime) % thisApp.nanoMult) + " seconds\n\n" );	
				}
			}
			else
			{
				if (thisApp.debugMode)
					thisApp.localizationLog.save("Unable to save localization data\n");
			}
		}
	}
	
	/**
	 * Nested runnable class that loads the stored TestingTask Object
	 * 
	 * @author Kwaku Farkye
	 *
	 */
	private class LoadPersistenceRunnable implements Runnable
	{
		@Override
		public void run()
		{
			long startTime = 0;
			long endTime = 0;
			if (thisApp.debugMode)
			{
				startTime = System.nanoTime();
				thisApp.localizationLog.save("NEW RUN: LOADING Localization MODELS...\n");
			}
						
			// Attempt to load localization data from internal storage
			if (loadLocalizeData())
			{
				if (thisApp.debugMode)
				{
					endTime = System.nanoTime();
					thisApp.localizationLog.save("Time taken to load localization info: " + 
							((endTime - startTime) / thisApp.nanoMult) + "." + ((endTime - startTime) % thisApp.nanoMult) + " seconds\n\n" );
				}
				
				try {
					Message msg = Message.obtain();
					// Tell the main thread that we loaded the localization data
					String loadResult = PERSIST_LOAD_SUCCESS;
					msg.obj = loadResult;
					// Send the message and end our run
					messenger.send(msg);
				} catch (Exception ex)
				{
					Log.e(PERSIST_LOAD_TAG, "Unable to Load Localization Data\n");
					ex.printStackTrace();
				}
			}
			else 
			{ // Unable to load the localization data
				// Send a failure Message to Main Thread
				try {
					Message msg = Message.obtain();
					// Tell the main thread that we failed to load the localization data
					String loadResult = PERSIST_LOAD_FAIL;
					msg.obj = loadResult;
					// Send the message and end our run
					messenger.send(msg);
				} catch (Exception e)
				{
					Log.e(PERSIST_LOAD_TAG, "Error Sending Failure Message\n");
					e.printStackTrace();
					return;
				}
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
	private class InitialLoadRunnable implements Runnable
	{
		@Override
		public void run()
		{
			long startTime = 0;
			long endTime = 0;
			
			try {
				if (thisApp.debugMode)
					startTime = System.nanoTime();
				
				// Setup the model classes
				localize.setNumClasses(nX, nY, initialProgBar);
				// Once models are loaded, send a message to the main thread
				
				if (thisApp.debugMode)
					endTime = System.nanoTime();
				
				finishedLoading = true;
				
				Message msg = Message.obtain();
				// Tell the main thread that we are done loading
				String loadResult = LOAD_COMPLETE;
				msg.obj = loadResult;
				// Send the message and end our run
				messenger.send(msg);
				
				if (thisApp.debugMode)
					thisApp.localizationLog.save("Time taken to load X" + nX + ", Y" + nY + " classes: " +
							((endTime - startTime) / thisApp.nanoMult) + "." + ((endTime - startTime) % thisApp.nanoMult) + " seconds\n" );
				
			} catch (Exception e)
			{
				Log.e(INITLOAD_TAG, "Error while loading classees");
				e.printStackTrace();
			}
		}
	}
	
	/**
	 * Nested Handler class for message communication between main activity and other threads/services
	 * started by the activity
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
				if (thisApp.debugMode)
					thisApp.localizationLog.save("-------- STARTING PREDICTION NUMBER " + thisApp.count + " --------\n");
				
				// Save the localization data
				PersistenceRunnable pr = new PersistenceRunnable();
				Thread storeThread = new Thread(pr);
				storeThread.start();
				
				return;
			}
			
			// Check if this is a message from the prediction thread
			if (msg.obj == PREDICTION_COMPLETE)
			{	
				// Translate the prediction to a coordinate
				translatePoint(predObj.prediction);
				
				// Reset prediction counter
				predObj.predCounter = 1;
				
				// Restart the service
				initIntentService();
				return;
			}
			
			// Check if we successfully loaded the persistent TestingTask Object
			if (msg.obj == PERSIST_LOAD_SUCCESS)
			{
				initImageView();
				
				if (thisApp.debugMode)
					thisApp.localizationLog.save("-------- STARTING PREDICTION NUMBER " + thisApp.count + " --------\n");
				
				return;
			}
			
			// Check if loading the TestingTask Object Failed
			if (msg.obj == PERSIST_LOAD_FAIL)
			{
				// Since we failed, we have to load from external storage (Downloads Folder)
				
				//setContentView(R.layout.activity_main);
				initialProgBar = (ProgressBar) findViewById(R.id.initProgBar);
				
				// Set the max value of the progress bar to the number of classes that we must load
				initialProgBar.setMax(nX+nY);
				
				// Initialize loading of the model classes (starts a new thread)
				initTraining();
				
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
				predObj.prediction = localize.getEstLocation(rssis);
				
				if (thisApp.debugMode)
				{
					predObj.setOrigCoords();
					plotOrigPoint(predObj.origX, predObj.origY);
					
					long totalScanTime = inData.getLong(LocalizeService.TIME_KEY);
					thisApp.localizationLog.save("Time taken for scan: " + (totalScanTime / thisApp.nanoMult) + "." + ((totalScanTime) % thisApp.nanoMult) + " seconds\n");
				}
				
				// Translate primitive double array to Double instance array
				Double[] predToDouble = new Double[2];
				predToDouble[0] = Double.valueOf(predObj.prediction[0]);
				predToDouble[1] = Double.valueOf(predObj.prediction[1]);
				
				// Add the translated prediction to the array list
				predObj.predictions.add(predToDouble);
							
				if (predObj.predCounter >= numScans)
				{ // If number of predictions is equal to the number of scans, lets average
					PredictionRunnable prun = new PredictionRunnable();
					Thread predictThread = new Thread(prun);
					predictThread.start();
					
					return;
				}
				else if (predObj.predCounter < numScans)
				{
					predObj.predCounter++;
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
		
		// Receive and parse the intent from the load activity
		Intent intent = getIntent();
		apfilename = intent.getStringExtra(LoadActivity.AP_FILE);
		rawFile = intent.getStringExtra(LoadActivity.LOCALIZE_FILE);
		
		// Get instance of this application
		thisApp = (LocalizeApplication)this.getApplicationContext();
		
		// Initialize a new Prediction Object for use during this run of the application
		predObj = new PredictionsObject(thisApp);
		
		// Set the initial values needed for this run
		//(Needs to be called before writing to localization log in debug mode)
		setInitialValues();
		
		setContentView(R.layout.activity_main);
		
		
		// Attempt to load the localization data (start a new thread to do so)
		LoadPersistenceRunnable lpr = new LoadPersistenceRunnable();
		final Thread loadLocThread = new Thread(lpr);
		loadLocThread.start();
		
	}
	
	@Override
	public void onSaveInstanceState(Bundle savedInstanceState)
	{
		System.out.println("Saving Instance state\n");
		if (thisApp.debugMode)
			thisApp.localizationLog.save("Save Instance State called\n");
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

		// Setup the actual drawable circle
		myDView = (MyDrawableView) findViewById(R.id.circleView1);
		myDView.setVisibility(View.INVISIBLE);

		// Setup the error corrected drawable circle (For DebugMode)
		errView = (MyDrawableView) findViewById(R.id.circleViewErr);
		errView.setVisibility(View.INVISIBLE);
		errView.setDrawColor("Blue");
		
		// Setup the original predicted drawable circle (For DebugMode)
		origView = (MyDrawableView) findViewById(R.id.circleViewOrig);
		origView.setVisibility(View.INVISIBLE);
		origView.setDrawColor("Red");
		
		tview = (TrailView) findViewById(R.id.trailView1);
		
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

						mapState = tempMap;
						dialog.dismiss();
					}
				}).setNeutralButton("Cancel", null).show();
		SeekBar sbBetVal = (SeekBar) v.findViewById(R.id.sbBetVal);
		tvBetVal = (TextView) v.findViewById(R.id.tvBetVal);

		ToggleButton tBut = (ToggleButton) v.findViewById(R.id.toggleButton1);
		tBut.setChecked(mapState);
		tBut.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				if (isChecked) {
					// The toggle is enabled
					System.out.println("This is the first choice");
					// THIS IS THE POINT OPTION
					tempMap = POINT;

				}
				else {
					// The toggle is disabled
					System.out.println("This is the second choice");
					// THIS IS THE FOLLOW OPTION
					tempMap = FOLLOW;
				}
			}
		});

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
	protected void onStop()
	{
		super.onStop();
	}
	
	@Override
	protected void onDestroy()
	{
		super.onDestroy();
		stopService(localizeIntent);
	}
	
	public boolean onTouch(View v, MotionEvent event) {
		DisplayMetrics metrics = new DisplayMetrics();
		getWindowManager().getDefaultDisplay().getMetrics(metrics);

		// handle touch events here
		ImageView view = (ImageView) v;

		// TODO find a better place to initialize the center
		center.set(v.getWidth() / 2, v.getHeight() / 2);
		
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

		if (!animDone) {
			System.out.println("moving");
			return true;
		}
		
		if (mapState == POINT) {
			/* The point specified will be given by the localization function */
			plotPoint(predObj.xCoord, predObj.yCoord);
			view.setImageMatrix(ld.matrix);
		}
		else
		{
			view.setImageMatrix(plotImage(predObj.xCoord, predObj.yCoord, ld.matrix));
		}
		
		if (thisApp.debugMode)
		{
			plotOrigPoint(predObj.origX, predObj.origY);
			if (!predObj.withinRange)
				plotErrPoint(predObj.errX, predObj.errY);
		}
		
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
		localizeIntent.putExtra(LocalizeService.COUNT_KEY, thisApp.count);
		localizeIntent.putExtra(LocalizeService.DEBUG_KEY, thisApp.debugMode);
		
		startService(localizeIntent);
	}

	/**
	 * Translates the returned points from getEstLocation into coordinates.
	 * The coordinate values will then be plotted via plotPoint() functions.
	 * This method is called before anything is drawn onto the image view
	 * 
	 * @param prediction The predicted values from getEstLocation()
	 */
	private void translatePoint(double[] prediction)
	{
		
		if (thisApp.count < 3)
		{
			predObj.prevPrediction[0] = prediction[0];
			predObj.prevPrediction[1] = prediction[1];
		}
		
		if (thisApp.debugMode)
		{
			String res = "Predicted Location for run " + thisApp.count++ + ": "+ prediction[0] + "," + prediction[1];
			thisApp.localizationLog.save(res + "\n");
		}
		
		// Set the coord values to the predicted values
		predObj.xCoord = (float)prediction[0];
		predObj.yCoord = (float)prediction[1];
		
		if (mapState == POINT) {
			  // plotPoint(xCoord, yCoord);
				movePoint(predObj.xCoord, predObj.yCoord);
		}
		else {
				imgView.setImageMatrix(moveImage(predObj.xCoord, predObj.yCoord, ld.matrix));
		}
		
		if (!predObj.withinRange && thisApp.debugMode)
			plotErrPoint(predObj.errX, predObj.errY);

		if (thisApp.debugMode)
			thisApp.localizationLog.save("-------- STARTING PREDICTION NUMBER " + thisApp.count + " --------\n");
	}
	
	/**
	 * Instantiate and set the initial values for objects/variables
	 * used
	 */
	private void setInitialValues()
	{
		// Set up a localization log for testing/recording results
		if (thisApp.debugMode)
		{
			locLog = "LocLog_" + Calendar.getInstance().getTime().toString();
			thisApp.localizationLog = new AndroidLog(locLog + ".txt");
		}
		
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
	 * Initialize the training interface and all that is necessary to predict a location
	 * Spawns a new thread to handle the loading of the classes/models
	 * 
	 */
	private void initTraining()
	{
		localize = new TestingTask(rawFile, trainFile);
		
		// Make instance of runnable class for initial load of models..
		InitialLoadRunnable loadRunnable = new InitialLoadRunnable();
		
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
		
		// Move the trail view as the map moves
		tview.setX(ld.eventMatrix[Matrix.MTRANS_X]);
		tview.setY(ld.eventMatrix[Matrix.MTRANS_Y]);

		tview.setPivotX(ld.mid.x);
		tview.setPivotY(ld.mid.y);
		tview.setScaleX(ld.eventMatrix[Matrix.MSCALE_X]);
		tview.setScaleY(ld.eventMatrix[Matrix.MSCALE_Y]);
		
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
	
	/**
	 * Centers a point on the map (image view)
	 * @param x
	 * @param y
	 * @param m
	 */
	private Matrix plotImage(float x, float y, Matrix m) {
		float[] mtxArr = new float[9];
		PointF scale = ld.getInitScale();

		myDView.setX(center.x);
		myDView.setY(center.y);
		m.getValues(mtxArr);

		mtxArr[Matrix.MTRANS_X] = center.x - (x * scale.x) + 25;
		mtxArr[Matrix.MTRANS_Y] = center.y - (y * scale.y) + 25;

		m.setValues(mtxArr);

		tview.setX(mtxArr[Matrix.MTRANS_X]);
		tview.setY(mtxArr[Matrix.MTRANS_Y]);
		return m;
	}
	
	/**
	 * Functions that animates the movement of marker from one point to the next
	 * @param x
	 * @param y
	 */
	private void movePoint(float x, float y) {

		float prevX = myDView.getX();
		float prevY = myDView.getY();
		tview.trail(trailNdx, prevX, prevY);

		float calcX = ld.getAdjustedX(x);

		float calcY = ld.getAdjustedY(y);

		transPoint.set(calcX, calcY);

		TranslateAnimation anim = new TranslateAnimation(0, calcX - prevX, 0, calcY
				- prevY);
		anim.setFillAfter(true);
		anim.setDuration(1000);
		anim.setAnimationListener(new AnimationListener() {
			@Override
			public void onAnimationEnd(Animation animation) {
				animation.setFillAfter(false);
				myDView.setX(transPoint.x);
				myDView.setY(transPoint.y);

				animDone = true;
				trailNdx++;

				System.out.println("trail ndx is " + trailNdx);
			}

			@Override
			public void onAnimationRepeat(Animation animation) {
				// TODO Auto-generated method stub

			}

			@Override
			public void onAnimationStart(Animation animation) {
				// TODO Auto-generated method stub

			}
		});
		myDView.startAnimation(anim);
	}
	
	/**
	 * Responsible for adding the trail in FOLLOW MODE
	 */
	private Matrix moveImage(float x, float y, Matrix m) {
		PointF scale = ld.getInitScale();

		// Calculate the previous point for the trail
		float calcX = (predObj.xCoord * scale.x) - 10;
		float calcY = (predObj.yCoord * scale.y) - 10;

		// Add a trail point
		tview.trail(trailNdx, calcX, calcY);
		trailNdx++;

		animDone = true;
		return plotImage(x, y, m);
	}
	
	/**
	 * Saves the localization data that we may use for the next run.
	 * This will eliminate initial load times
	 * 
	 * @return True if save was successful, false otherwise
	 */
	private boolean saveLocalizeData()
	{
		try {
			FileOutputStream fos = openFileOutput(rawFile, Context.MODE_PRIVATE);
			ObjectOutputStream os = new ObjectOutputStream(fos);
			os.writeObject(localize);
			writtenToStorage = true; // Mark that we have written something to storage
			os.writeBoolean(writtenToStorage); // Save marker that we have written to storage
			
			if (thisApp.debugMode)
				thisApp.localizationLog.save("SAVED LOCALIZATION OBJECT:\n");
			
			os.close();
			fos.close();
			return true;
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
	}
	
	/**
	 * Loads the localization data that was saved from the last run
	 * 
	 * @return True if loading was successful, false otherwise
	 */
	private boolean loadLocalizeData()
	{
		try {
			FileInputStream fis = openFileInput(rawFile);
			ObjectInputStream ois = new ObjectInputStream(fis);
			localize = (TestingTask)ois.readObject();
			writtenToStorage = (boolean)ois.readBoolean();
			finishedLoading = true;
			ois.close();
			fis.close();
			return true;
		} catch (Exception e)
		{
			e.printStackTrace();
			return false;
		}
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

/*
 * Trailing shapes
 */
class TrailView extends View {
	private ShapeDrawable[] trail = new ShapeDrawable[20];
	private int plotCnt = 0;

	private int diameter = 20;

	public TrailView(Context context) {
		super(context);

	}

	public TrailView(Context context, AttributeSet attrs) {
		super(context, attrs);

	}

	protected void onDraw(Canvas canvas) {
		plotCnt = (plotCnt > 20) ? (20) : (plotCnt);
		for (int i = 0; i < plotCnt; i++) {
			trail[i].draw(canvas);
		}
	}

	public void trail(int ndx, float x, float y) {

		// fix for some duplicating issue
		if (plotCnt != ndx + 1) {
			System.out.println("TRAILING");
			plotCnt = ndx + 1;
			if (ndx >= 20) {
				ndx %= 20;
			}
			else {
				trail[ndx] = new ShapeDrawable(new OvalShape());
				trail[ndx].getPaint().setColor(0xff74AC23);
			}
			System.out.println("x and y: " + x + ", " + y);
			trail[ndx].setBounds((int) x + 10, (int) y + 10, (int) x + diameter + 10,
					(int) y + diameter + 10);

			invalidate();
		}
	}
}
