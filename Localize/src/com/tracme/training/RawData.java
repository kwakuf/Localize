package com.tracme.training;

import java.io.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Random;
import java.util.Set;


public	class RawData {
	private String rawDataFileName;
	public int maxX; // max x-coordinate in the resolution of the area map
	public int maxY; // max y-coordinate in the resolution of the area map
	public int numAnchors; // number of anchors; e.g., number of reference nodes, access points
	ArrayList<ArrayList<String>> dataList;
	
	
	RawData(String rawDataFileName1) {
		// load all measurements into dataList
		// duplicates are possible during sample collection; we need to remove duplicates
		// remove all duplicates by adding to a set to avoid duplicates and then converting to an array for later use
		
		rawDataFileName = rawDataFileName1;
		
		
		// dataSet: set of readings without duplicates
		// dataList: list of readings without duplicates
		ArrayList<Set<String>> dataSet = new ArrayList<Set<String>>();
		dataList = new ArrayList<ArrayList<String>>();
		
		
    	// insert values to dataSet
		try {
            FileInputStream fstream = new FileInputStream(rawDataFileName);
			DataInputStream in = new DataInputStream(fstream);
			BufferedReader br = new BufferedReader(new InputStreamReader(in));
			
			String strLine;
            
			// 1st line of file -> read parameter maxX
			strLine = br.readLine();
			maxX = Integer.parseInt(strLine);
        
			
			// 2nd line of file -> read parameter maxY
			strLine = br.readLine();
			maxY = Integer.parseInt(strLine);
			
			// 3rd line of file -> read numAnchors
			strLine = br.readLine();
			numAnchors = Integer.parseInt(strLine);
	       
			
			
			System.out.println(rawDataFileName + ": maxX = " + maxX + " : maxY = " + maxY + " : numAnchors= " + numAnchors);
			
			// initialization
			for (int i=0;i<maxX;i++)
    			for (int j=0;j<maxY;j++) {
    				Set<String> arrStr=new HashSet<String>();
    				ArrayList<String> arrLStr=new ArrayList<String>();
    				dataSet.add(arrStr);
    				dataList.add(arrLStr);
    			}
            
            
            
			int index = 0;
			String loc = "";
			while ((strLine = br.readLine()) != null)   {
                
    			//System.out.println(strLine);
				if (strLine.contains("###")){
					loc = strLine.split("###")[1];
					
					index = (int) Math.round(Float.parseFloat(loc.split(",")[0]))*maxY + Math.round(Float.parseFloat(loc.split(",")[1]));
					continue;
				}
				// convert strLine to libsvm training file format featureID:valueID separated by white space
				dataSet.get(index).add(loc+"#" + strLine.replace(';', ' '));
			}
			
			in.close();
            
		} catch(Exception e) {
			System.out.println("Failed RawData()");
			System.exit(-1);
		}
		
		// copy values of dataSet to dataList
    	for (int i=0;i<maxX;i++)
    		for (int j=0;j<maxY;j++) {
    			int index1=i*maxY+j;
    			Iterator<String> it = dataSet.get(index1).iterator();
    			while (it.hasNext()) {
    			    dataList.get(index1).add(it.next());
    			}
    		}
        
	}
	
	public void printParameters(){
		System.out.println("Raw data file: " + rawDataFileName);
		System.out.println("#anchors=" + numAnchors + ";maxX=" + maxX + ";maxY=" + maxY);	
	}
	
}