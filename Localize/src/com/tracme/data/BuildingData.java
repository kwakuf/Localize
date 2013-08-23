/**
 * 
 */
package com.tracme.data;

import java.util.ArrayList;
import java.util.List;

/**
 * This class is an object that holds information on a specific building.
 * This information can include things such as GPS location, campus location,
 * number of floors, etc...
 * 
 * The purpose of this class is to relate a building with floors that have been sampled, to streamline
 * the process of a user choosing the location they are in.
 * 
 * @author Kwaku Farkye
 *
 */
// TODO: Make this class and FloorData class accessible from a database.
public class BuildingData {

	/** The name of this building */
	private String buildingName;
	
	/** Number of floors this building has */
	private int numberOfFloors;
	
	/** List of FloorData objects representing the floors that are in this building */
	private List<FloorData> floors;
	
	public BuildingData(String name, int numFloors)
	{
		buildingName = name;
		numberOfFloors = numFloors;
		floors = new ArrayList<FloorData>();
	}
	
	public BuildingData(String name)
	{
		buildingName = name;
		floors = new ArrayList<FloorData>();
	}
	
	/**
	 * Get the name of this building
	 * 
	 * @return String representing the name of this building
	 */
	public String getBuildingName()
	{
		return buildingName;
	}
	
	/**
	 * Get the number of floors this building has
	 * 
	 * @return The number of floors this building has
	 */
	public int getNumberOfFloors()
	{
		return numberOfFloors;
	}
	
	/**
	 * Add a floor to the list of floors in this building
	 * 
	 * @param floor The floor to add to this building
	 */
	public void addFloor(FloorData floor)
	{
		floors.add(floor);
	}
	
	/**
	 * Get the list of floors in this building
	 * 
	 * @return An array list of floors in this building
	 */
	public ArrayList<FloorData> getFloors()
	{
		return (ArrayList<FloorData>)floors;
	}
	
	public ArrayList<String> getFloorNames()
	{
		ArrayList<String>list = new ArrayList<String>();
		for (int i = 0; i < floors.size(); i++)
		{
			list.add(floors.get(i).getFloorName());
		}
		
		return list;
	}
}
