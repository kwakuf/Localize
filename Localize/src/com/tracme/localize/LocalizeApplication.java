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
	
	/** Width of floor-plan image */
	protected float dimX = 1291;
	
	/** Height of floor-plan image */
	protected float dimY = 754;
	
	/** Counter that records the number of predictions done */
	protected int count = 1;
	
	/** Multiple to compute seconds from nanoseconds */
	protected long nanoMult = 1000000000;
	
	/** Number of classes in x dimension */
	protected int nX = 100;
	
	/** Number of classes in y dimension */
	protected int nY = 100;
	
	/** Number of scans/predictions done before outputting prediction on image */
	protected int numScans = 5;

}
