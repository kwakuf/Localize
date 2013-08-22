package com.tracme.localize;

import java.util.ArrayList;

import com.tracme.R;
import com.tracme.data.*;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Spinner;
import android.widget.Button;
import android.widget.Toast;

/**
 * This activity handles loading the correct location for localization.
 * The user is prompted to select their location from a list of locations currently accounted for.
 * Once the user has selected their current location, the location is passed to the main activity,
 * which will proceed to load the location data for localizing.
 * 
 * @author Kwaku Farkye
 *
 */
public class LoadActivity extends Activity {

	public static final String LOAD_MESSAGE = "com.tracme.localize.LoadActivity.Message";
	public static final String AP_FILE = "com.tracme.localize.LoadActivity.AP_FILE";
	public static final String LOCALIZE_FILE = "com.tracme.localize.LoadActivity.Localize_File";
	
	/** Drop-down list used for selecting location/building */
	private Spinner locationSpinner;
	
	/** Drop-down list used for selecting floor in the building */
	private Spinner floorSpinner;
	
	/** Button to start the main activity */
	private Button goButton;
	
	/** Name of the localization file we need to load */
	private String locFileName = null;
	
	/** Name of the access point file we need to load */
	private String apFileName = null;
	
	/** List of buildings to load from */
	private ArrayList<BuildingData> buildings;
	
	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
	    super.onCreate(savedInstanceState);
	
	    setContentView(R.layout.activity_load);
	    goButton = (Button)findViewById(R.id.button1);
	    
	    populateList();
	}
	
	public void addActivityListener()
	{
	}
	
	/**
	 * Prepares to and loads the main activity
	 * 
	 * @param v The View that was clicked
	 */
	public void loadMainActivity(View v)
	{
		if ( apFileName != null && locFileName != null)
		{
			Intent intent = new Intent(this, MainActivity.class);
			intent.putExtra(LOCALIZE_FILE, locFileName);
			intent.putExtra(AP_FILE, apFileName);
			startActivity(intent);
		}
		else
		{
			Toast.makeText(this, "Please select your location", Toast.LENGTH_SHORT).show();
			apFileName = "apcc1_76_nexus";
			locFileName = "cc1_81_nexus.txt";
		}
	}

	public void populateList()
	{
		buildings = new ArrayList<BuildingData>();
	}
}
