package com.tracme.training;

import java.io.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Random;
import java.util.Set;


public class Misc {
	   
	
	public static void printArray(double a[]) {
		String tmp = "";
		for (int i = 0; i < a.length; i++) {
			tmp = tmp + " " + a[i];
		}
		System.out.println(tmp);
	}
	public static Set<Integer> getRandomSet(double portion, int maxNumber){
		// select a portion p of random numbers between 0 and maxNumber-1
		// e.g, if portion=0.1, we will generate (10% * maxNumber) numbers
        
		Set<Integer> s = new HashSet<Integer>();
		Random generator = new Random();
		
		for (int i = 0; i < maxNumber; i++) {
			double r = generator.nextDouble();
			if (r <= portion) s.add(i);
		}
		//System.out.println(s);
		return s;
	}
	
	public static double euclideanDist(double[] a, double [] b) {
		assert (a.length != b.length) : "euclideanDist(): different array lengths!";
		double ret = 0;
		int n= a.length;
		for (int i=0; i<n; i++) {
			ret += (a[i]-b[i]) * (a[i]-b[i]);
		}
		return Math.sqrt(ret);
	}
	
	public static double euclideanDist(double x1, double y1, double x2, double y2) {
		return Math.sqrt((x1-x2) * (x1-x2) + (y1-y2) * (y1-y2));
	}
	
	public static double manhattanDist(double[] a, double [] b) {
		assert (a.length != b.length) : "manhattanDist(): different array lengths!";
		double ret = 0;
		int n= a.length;
		for (int i=0; i<n; i++) {
			ret += Math.abs(a[i]-b[i]);
		}
		return ret;
	}
	
	/*
	public static void convertFile_Average(String inputFile, String outputFile) {
		// line input for each file:
		// 2.43892672440343,19.5114137952274#1:-49 2:-71 3:-63 4:-58 5:-95 
		// 2.43892672440343,19.5114137952274#1:-42 2:-71 3:-67 4:-59 5:-95 
		// 2.43892672440343,19.5114137952274#1:-41 2:-71 3:-64 4:-59 5:-95 
		// 4.87785344880686,19.5114137952274#1:-48 2:-64 3:-61 4:-59 5:-95 
		// 4.87785344880686,19.5114137952274#1:-47 2:-60 3:-58 4:-63 5:-95 
		// 4.87785344880686,19.5114137952274#1:-47 2:-59 3:-61 4:-59 5:-95 
		// there may be more than 1 RSSI for each location; we compute the average of this RSSI's for each location in the output file
		
		DataInputStream in1 = new DataInputStream(new FileInputStream(inputFile));
		BufferedReader br1 = new BufferedReader(new InputStreamReader(in1));
		
		DataOutputStream outputT = new DataOutputStream(new FileOutputStream(outputFile));
		
		String strLine;
		// reading input
		while ((strLine = br1.readLine()) != null)   {
			
			
			
			
			
			
		}
	}
		
	*/
	
	public static void convertRawDataBrunato(){
		// this is written to convert Brunato's dataset to our format
		try {
            
            
			FileInputStream fstream = new FileInputStream("brunato_data_original.txt");
			DataInputStream in = new DataInputStream(fstream);
			BufferedReader br = new BufferedReader(new InputStreamReader(in));
			
			FileOutputStream fOutStreamT = new FileOutputStream("brunato_data.txt");
			DataOutputStream outputT = new DataOutputStream(fOutStreamT);
            
			String strLine;
            
			
			outputT.writeBytes("300\n250\n6\n");
			
			while ((strLine = br.readLine()) != null)   {
				String[] str = strLine.trim().split("\\s+");
				outputT.writeBytes("###" +
                                   (int) (new Double(str[0]) * 10.0) + "," +
                                   (int) (new Double(str[1]) * 10.0) + "\n");
				outputT.writeBytes(
                                   "1:" + str[2] + ";" +
                                   "2:" + str[3] + ";" +
                                   "3:" + str[4] + ";" +
                                   "4:" + str[5] + ";" +
                                   "5:" + str[6] + ";" +
                                   "6:" + str[7] + ";\n");
			}
			
			in.close();
			outputT.close();
		} catch(Exception e) {
			System.out.println("Failed convertRawDataBrunato()");
			System.exit(-1);
		}
		
		
	}
	
	public static void computeBestNumClasses(String dimension, String trainFile) {
    	// find the best number of classes for use in eTrack
    	// resulting in minimum location error
    	double minError = 1;
    	int ret = -1;
    	
    	try {
    		// save error info in a file
    		FileOutputStream fOutStreamT = new FileOutputStream(trainFile + ".summary." + dimension + ".csv");
			DataOutputStream outputT = new DataOutputStream(fOutStreamT);
			
			outputT.writeBytes("Num Classes; Location Error Bound\n");
			
    		for (int numClasses = 10; numClasses <= 100; numClasses += 10) {    	
    			String summaryFile = trainFile + "_dir/" + dimension + numClasses + "/summary.csv";
    			DataInputStream in = new DataInputStream(new FileInputStream(summaryFile));
    			BufferedReader br = new BufferedReader(new InputStreamReader(in));
    			
    			double E = new Double(br.readLine().split("=")[1]);
    			if (minError > E) {
    				minError = E;
    				ret = numClasses;
    			}
     			outputT.writeBytes(numClasses + ";" + E + "\n");
    			in.close();
    		}
 			outputT.writeBytes("Best #classes for dimension " + dimension + "=" + ret);
			outputT.close();
    		
    	} 
    	catch(Exception e) {
    		System.out.println("Failed getBestNumClasses()");
    		System.exit(-1);
    	}
    }
	
	public static double errBound(double m, double eps) {
		return eps*(1+2*eps)/2/(1+eps) + (1-eps)/Math.pow(2,m) - Math.pow(1-eps, m)/Math.pow(2, m+1)/(1+eps);
	}
	
}

