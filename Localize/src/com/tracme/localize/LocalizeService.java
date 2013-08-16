/**
 * 
 */
package com.tracme.localize;

import com.tracme.util.*;
import com.tracme.training.*;
import android.app.Activity;
import android.app.IntentService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Message;
import android.os.Messenger;
import android.util.Log;
import java.util.List;

/**
 * The localize service runs the wifi scans and predictions for the main activity.
 * It gives the main activity the predicted location and allows the main thred (UI Thread)
 * to just handle user interaction with the image
 * 
 * @author Kwaku Farkye
 *
 */
public class LocalizeService extends IntentService {
	/* These are the keys that will be used for bundles
	 *  passed between this service and the calling activity via the messenger
	 */
	public static final String MESSENGER_KEY = "MESSENGER";
	public static final String OPTIONS_KEY = "OPTIONS";
	public static final String APTABLE_KEY = "APTABLE";
	public static final String SCANARRAY_KEY = "SCANARRAY";
	public static final String COUNT_KEY = "COUNT";
	
	private APTable apTable;
	public LocalizeOptions options;
	private Messenger messenger;
	private int count;
	
	//public WifiManager myWifiManager;
	//public WiFiScanReceiver myReceiver = null;
	private LocalizeBroadcastReceiver myReceiver;
	private WifiManager myWifiManager;
	
	double[] rssis;// = {33,32,34,25,26,23,22,19,16,17,15,16,12,9,13,11,11,12,33,26,26,25,24,18,19,16,84,17,16,15,14,11,14,10,11,9,16,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}; //List of rssi values for each access point in the ap list
	
	double[] prediction = new double[2]; // Prediction of the corresponding point
	
	/*** TODO: These fields should probably be passed to us by the activity in an object ***/
	private String rawFile = "cc1_76_nexus.txt"; // Name of the rawfile
	private String trainFile = "train_p0.0.txt_sub_1.0.1.txt"; // Name of the training file
	private int nX = 100; // Number of classes in x dimension
	private int nY = 100; // Number of classes in y dimension
	/*** END ****/
	
	/**
	 * Class for Receiving WiFi scan results from the WifiManager
	 * 
	 * @author Kwaku Farkye
	 *
	 */
	private class LocalizeBroadcastReceiver extends BroadcastReceiver {
		@Override
		public void onReceive(Context context, Intent intent)
		{
			int apId = 0;
			
			// Make sure we received signal scans..
			if (intent.getAction().equals("android.net.wifi.SCAN_RESULTS"))
			{
				// Get each scan result and do an ap table lookup
				List<ScanResult> results = myWifiManager.getScanResults();
				for (ScanResult result : results)
				{	
					// Access point was found, so register the value in our double array
					if ((apId = apTable.lookupAP(result, false, 0)) != 0)
					{
						// Set the value in our double array
						setRSSIArrayValue(apId, result.level + 100);
					}
					
				}				
			}
		}
	}
	
	public LocalizeService()
	{
		super("LocalizeService");
	}
	
	public void setRSSIArrayValue(int apID, int rssival)
	{
		// Since the access point IDs in the table start at 1, we must decrement the array id by 1
		int arrayID = apID - 1;
		
		// Because we may average the scans, just keep adding the new rssi value to the value alreay there.
		// If we end up only scanning once, then it wont even matter.
		rssis[arrayID] += (double)rssival;
		
		return;
	}
	
	@Override
	protected void onHandleIntent(Intent intent)
	{
		// Recreate the objects that were passed in the intent
		if( receiveParcels(intent.getExtras()) ) 
		{	
			// Initialize our double array to the amount of access points in the access point table
			rssis = new double[apTable.getAPTable().size()];
			
			// Reset array values to zero
			resetArray();
			
			// Instance of WifiManager to handle scanning. Final because we dont want it to change
			myWifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
			
			myReceiver = new LocalizeBroadcastReceiver();
							
			// Register the receiver so the wifi manager knows where to go once its done.
			this.registerReceiver(myReceiver, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));	
		
			//while (true)
			//{
			//resetArray();
			/***  TODO: Here is where we can loop multiple times in the case where the user wants more accuracy ****/ 
			
			// Start scanning and predicting
			myWifiManager.startScan();
			
			//TODO: Get rid of this sleep somehow.. (BroadcastReceiver or AsyncTask??)
			try {
				Thread.sleep(2000);
			} catch (InterruptedException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
			
			/*** TODO: End of that loooop ***/
			
			//Obtain a message from the pool
			Message msg = Message.obtain();
			
			msg.arg1 = Activity.RESULT_OK;
			
			// After receiving scans and storing in a double array,
			// bundle the results up and send back to activity via a message/messenger
			Bundle outData = new Bundle();
			outData.putDoubleArray(LocalizeService.SCANARRAY_KEY, rssis);
			
			msg.setData(outData);
			//msg.obj = outString;
			try {
				messenger.send(msg);
			} catch (android.os.RemoteException e)
			{
				Log.w(getClass().getName(), "Exception sending message", e);
			}
			//}
		}
	}
	
	
	@Override
	public void onDestroy()
	{
		super.onDestroy();
		this.unregisterReceiver(myReceiver);
		this.stopSelf();
		
	}
	
	
	@Override
	public void onCreate()
	{
		super.onCreate();
	}
	
	/**
	 * Receive the objects passed to us from the object.
	 * 
	 * @param b The bundle in the intent that holds the extra data.
	 * 
	 * @return True if the bundle was not null, false otherwise
	 */
	private boolean receiveParcels(Bundle b)
	{
		if (b != null)
		{
			messenger = (Messenger)b.get(MESSENGER_KEY);
			apTable = (APTable)b.getParcelable(APTABLE_KEY);
			options = (LocalizeOptions)b.get(OPTIONS_KEY);
			count = (int)b.getInt(COUNT_KEY);

			return true;
		}
		
		return false;
	}
	
	/**
	 * Resets the values in the array to zero
	 * 
	 */
	private void resetArray()
	{
		for (int i = 0; i < rssis.length; i++)
		{
			rssis[i] = 0;
		}
		
		return;
	}

}
