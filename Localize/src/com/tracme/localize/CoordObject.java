/**
 * 
 */
package com.tracme.localize;

/**
 * The Coordinate Object stores the ID of a sampled point on a floorplan and the interpolated pixel coordinates of that point.
 * 
 * @author Kwaku Farkye
 * @author Ken Ugo
 *
 */
public class CoordObject {
	
	/* The point on the floorplan */
	private int point;
	
	/* The pixel coordinate of this point on the map */
	private int xCoord;
	
	/* The y pixel coordinate of this point on the map */
	private int yCoord;
	
	public CoordObject() {
		
	}

	/**
	 * 
	 * @param id
	 */
	private void setPoint(int id)
	{
		
	}
	
	/**
	 * Set the x pixel coordinate of this point on the floorplan
	 * 
	 * @param x
	 *  The value of the pixel coordinate in x direction
	 */
	private void setXCoord(int x)
	{
		xCoord = x;
	}
	
	/**
	 * Set the y pixel coordinate of this point on the floorplan
	 * 
	 * @param y
	 *  The value of the pixel coordinate in y direction
	 */
	private void setYCoord(int y)
	{
		yCoord = y;
	}
	
	/**
	 * Get the Point on the floorplan image
	 * 
	 * @return
	 *   The value of the point on the floorplan image
	 */
	public int getPoint()
	{
		return point;
	}
	
	/**
	 * Get the pixel coordinates of the point on the floorplan image
	 * 
	 * @return
	 *  The value of the pixel coordinate
	 */
	public int getX()
	{
		return xCoord;
	}
	
	/**
	 * Get the y pixel point on the floorplan image
	 * 
	 * @return
	 *  The value of the coordinate in the y direction
	 */
	public int getY()
	{
		return yCoord;
	}
	
}
