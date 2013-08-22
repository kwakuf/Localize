/**
 * 
 */
package com.tracme.data;

/**
 * This class is an object that stores information about a specific floor that has been sampled.
 * The FloorData object relates to a BuildingData object, and a floor contains information about
 * the localization models to use for that specific floor.
 * 
 * 
 * @author Kwaku Farkye
 *
 */
//TODO: Make this class and BuildingData class accessible from a database.
public class FloorData {

	/** Name of this floor (Example: Lower Level, UL, FirstFloor, etc..) */
	private String floorName;
	
	/** Number of this floor (example: 1 for first floor, 2 for second floor, etc..) */
	private int floorNumber;
	
	/** File name of the localization file that will be loaded */
	private String localizationFileName;
	
	/** Name of the building this floor is in */
	private String buildingName;
	
	/** Access point file associated with this floor */
	private String apFileName;
	
	/**
	 * Constructor for Floor Data that initializes all fields
	 * @param name The name of the floor
	 * @param building The name of the building this floor is in
	 * @param fileName The name of the localization model file to load for this floor
	 * @param apFile The name of the access point file to load for this floor
	 * @param floorNum The floor number of this floor
	 */
	public FloorData(String name, String building, String fileName, String apFile, int floorNum)
	{
		floorName = name;
		buildingName = building;
		localizationFileName = fileName;
		floorNumber = floorNum;
		apFileName = apFile;
	}
	
	/**
	 * Get the name of this floor.
	 * 
	 * @return The name of this floor
	 */
	public String getFloorName()
	{
		return floorName;
	}
	
	/**
	 * Get the name of the building this floor is in
	 * 
	 * @return Building name this floor is in
	 */
	public String getBuildingName()
	{
		return buildingName;
	}
	
	/**
	 * Get the name of the localization file that should be loaded
	 * 
	 * @return Name of the localization file to load for this floor
	 */
	public String getFileName()
	{
		return localizationFileName;
	}
	
	/**
	 * Get the floor number of this floor (what level it is on)
	 * 
	 * @return The floor number of this floor
	 */
	public int getFloorNumber()
	{
		return floorNumber;
	}
	
	/**
	 * Get the access point file name associated with this floor
	 * 
	 * @return name of the access point file
	 */
	public String getAPFileName()
	{
		return apFileName;
	}
	
}
