/**
 * 
 */
package com.tracme.localize;

import com.tracme.util.AndroidLog;

import android.app.Application;

/**
 * This class holds all of the global variables of this application that multiple classes
 * may need to use.
 * 
 * @author Kwaku Farkye
 *
 */
public class LocalizeApplication extends Application {
	
	/** Localization log that will record our results */
	protected AndroidLog localizationLog = null;

	/** Flag specifying whether we are in debug mode */
	protected boolean debugMode = false;
	
	/** Counter that records the number of predictions done */
	protected int count = 1;
	
	/** Multiple to compute seconds from nanoseconds */
	protected long nanoMult = 1000000000;

}
