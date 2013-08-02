package com.tracme.localize;

import java.util.concurrent.ExecutionException;

import com.tracme.training.TestingTask;
import com.tracme.util.*;

import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Messenger;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;

/**
 * Main Activity for TracMe localization. 
 * This activity will handle user interaction with the floorplan/image,
 * as well as Message/Messenger/Message Handling for the intent service that will handle scanning for signals
 *  
 * @author Kwaku Farkye
 *
 */
public class MainActivity extends Activity {
	
	/* Access Point Table */
	APTable apTable;
	
	/******** TODO: HOW ARE WE GOING TO SET THESE? *******/
	/* Access point file to load (must be in downloads folder right now) */
	String apfilename;
	
	/* Options for localization (set these in settings) */
	LocalizeOptions options;
	
	/* Intent to start the LocalizeService */
	Intent localizeIntent;
	
	private String rawFile = "cc1_76_nexus.txt"; // Name of the rawfile
	private String trainFile = "train_p0.0.txt_sub_1.0.1.txt"; // Name of the training file
	private int nX = 30; // Number of classes in x dimension
	private int nY = 30; // Number of classes in y dimension
	
	AndroidLog localizationLog;
	
	/*********************END********************************/
	
	/* Progress Bar used to show initial loading of localization classes */
	public ProgressBar initialProgBar;
	public boolean doneLoading = false;
	public int count = 1;
	
	double[] prediction = new double[2]; // Prediction of the corresponding point
	double[] rssis; // RSSIs values received from the LocalizeService IntentService
	
	// Interface to localization classes provided by Dr. Tran
	private TestingTask localize;
	
	/* Messenger for the IntentService so that we can communicate with it */
	private Messenger serviceMessenger = null;
	int k = 1;
	
	/**
	 *  Handler for message communication between main activity and signal scanning intent service 
	 */
	private class ScanHandler extends Handler {	
		
		/**
		 * Method that is called when a message is received from a Messenger
		 */
		@Override
		public void handleMessage(Message msg)
		{
			
			//Receive the message and, using the information 
			//received from the message, update the location of the user on the map 
			if (msg.arg1 == RESULT_OK)
			{
				// Receive the bundle that was passed by the message
				Bundle inData = msg.getData();
				
				if (serviceMessenger == null)
				{
					// Receive the messenger for the intent service
					serviceMessenger = (Messenger)inData.getParcelable(LocalizeService.SERVICE_MESSENGER_KEY);
					Toast.makeText(MainActivity.this, "Received messenger!", Toast.LENGTH_LONG).show();
				}
				
				// Receive the double array in the bundle, representing the results of the scan
				rssis = inData.getDoubleArray(LocalizeService.SCANARRAY_KEY);
				
				Toast.makeText(MainActivity.this, "First RSSI Received: " + rssis[0], Toast.LENGTH_LONG).show();
				
				// Call to Training interface: Predict the location
				//prediction = localize.getEstLocation(rssis);
				
				//translatePoint(prediction);
				
				// Restart the service
				//initIntentService();
				sendMessageToService(k++);
						
			}
			else {
				Toast.makeText(MainActivity.this, "Didnt receive anything back",
						Toast.LENGTH_LONG).show();
			}
			return;
		}
	}
	
	private ScanHandler sHandler = new ScanHandler();
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		
		initialProgBar = (ProgressBar) findViewById(R.id.progressBar1);
		
		// Set the max value of the progress bar to the number of classes that we must load
		initialProgBar.setMax(100);
		
		setInitialValues();
		
		initTraining();
		
		Toast.makeText(this, "Localize", Toast.LENGTH_LONG)
		.show();
		
		initIntentService();
		
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.activity_main, menu);
		return true;
	}
	
	@Override
	public void onDestroy()
	{
		super.onDestroy();
		stopService(localizeIntent);
	}
	
	/**
	 * Initialize the information that will be sent to the service.
	 * Once the data is bundled within the intent, start the service.
	 */
	public void initIntentService() {
		// Create a Messenger for communication back and forth
		Messenger messenger = new Messenger(sHandler);
		// Add the Messenger info to the intent, so the
		// intent service knows how who to give the message to
		localizeIntent.putExtra(LocalizeService.MESSENGER_KEY, messenger);
		localizeIntent.putExtra(LocalizeService.OPTIONS_KEY, options);
		localizeIntent.putExtra(LocalizeService.APTABLE_KEY, apTable);
		localizeIntent.putExtra(LocalizeService.COUNT_KEY, count++);
		localizeIntent.putExtra("PATH", "Hello World");
		Toast.makeText(this, "IntentService", Toast.LENGTH_LONG)
		.show();
		startService(localizeIntent);
	}

	private void translatePoint(double[] prediction)
	{
		String res = "Predicted Location: " + prediction[0] + "," + prediction[1];
		localizationLog.save(res + "\n");
		Toast.makeText(MainActivity.this,
				res, Toast.LENGTH_LONG)
				.show();
	}
	
	/**
	 * Instantiate and set the initial values for objects/variables
	 * used
	 */
	private void setInitialValues()
	{
		localizationLog = new AndroidLog("loc_first_run" + ".txt");
		// Load AP Table
		apfilename = "apcc1_76_nexus";
		apTable = new APTable(apfilename);
		apTable.loadTable();
		
		Toast.makeText(this, "AP Table Loaded " + Integer.valueOf(apTable.getAPTable().size()).toString(), Toast.LENGTH_LONG).show();
		
		//Initialize options
		options = new LocalizeOptions();
		localizeIntent = new Intent(this, LocalizeService.class);
		
	}
	
	/**
	 * Initialize the training interface and all that is necessary to predict a location
	 * 
	 * @throws ExecutionException 
	 * @throws InterruptedException 
	 * 
	 */
	private void initTraining()
	{
		initialProgBar.setVisibility(View.VISIBLE);
		System.out.println("GOING TO ESTIMATE LOCATION...");
		localize = new TestingTask(rawFile, trainFile);
		localize.setProgBar(initialProgBar);
		//System.out.println("SETTING CLASSES");
		//localize.execute(nX, nY, this.initialProgBar).get();
		localize.setNumClasses(nX, nY);
	}
	
	private void sendMessageToService(int k)
	{
		Message msg = Message.obtain();
		msg.arg1 = Activity.RESULT_OK;
		msg.arg2 = k;
		
		try {
			serviceMessenger.send(msg);
		} catch (Exception e)
		{
			Log.w(getClass().getName(), "Exception sending message", e);
		}
	}
}
