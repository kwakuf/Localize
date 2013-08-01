package com.tracme.localize;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * The LocalizeOptions class is a supplement class for LocalizeService.
 * An instance of this object will contain options that
 * LocalizeService will use while doing its work.
 * These options may effect speed, accuracy, etc...
 * 
 * @author Kwaku Farkye
 *
 */
public class LocalizeOptions implements Parcelable {
	
	// The number of scans to do for each localization attempt
	private int numScans;
	
	public LocalizeOptions()
	{
		//Default number of scans
		numScans = 1;
	}
	
	/**
	 * Constructor for re-constructing the object
	 * 
	 * @param in A parcel to read the object.
	 */
	public LocalizeOptions(Parcel in)
	{
		readFromParcel(in);
	}
	
	/**
	 * Set the number of scans for each localization attempt
	 * 
	 * @param val
	 *  The amount of scans to do.
	 *  
	 */
	public void setNumScans(int val)
	{
		numScans = val;
	}
	
	/**
	 * Get the number of scans
	 * 
	 * @return
	 *   The number of scans being done.
	 */
	public int getNumScans()
	{
		return numScans;
	}
	
	/**
	 * Reconstruct the Object via a parcel.
	 * 
	 * @param in The parcel to reconstruct the object from
	 */
	private void readFromParcel(Parcel in)
	{
		numScans = in.readInt();
	}

	@Override
	public int describeContents() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public void writeToParcel(Parcel dest, int flags) {
		// TODO Auto-generated method stub
		dest.writeInt(numScans);
	}

	@SuppressWarnings("rawtypes")
	public static final Parcelable.Creator CREATOR = 
			new Parcelable.Creator() {
				public LocalizeOptions createFromParcel(Parcel in)
				{
					return new LocalizeOptions(in);
				}
				
				public LocalizeOptions[] newArray(int size)
				{
					return new LocalizeOptions[size];
				}
			};
	
}
