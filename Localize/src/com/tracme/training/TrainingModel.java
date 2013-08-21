package com.tracme.training;

// read model information for a single class file (libsvm format)

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.Queue;
import com.tracme.util.*;


	
public	class TrainingModel implements Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = -3955216371610525127L;
	public int total_sv; // number of support vectors	
	public double training_accuracy; // accuracy of SVM training
	
	private String trainFile; // name of training file, given by constructor method
	private double[][] sv_list; // store list of support vectors
	private int numAnchors;		
	private double gamma;
	private double rho;
	private String label; // list of labels in sv_list; label can be "-1" (when all samples are negative), "1" (all samples are positive), or "1 -1" (mix)
	private double lo, hi; // low and high bounds for scaling
	private double [][] attrRange; // range of each attribute in a sample
	
		
	TrainingModel(String trainFile1, int numAnchors1){
		trainFile = trainFile1;
		numAnchors = numAnchors1;	
		
		getScaleParameters();
		getModelParameters();
		getTrainingAccuracy();
	}
		
	private void getScaleParameters(){
		lo=hi=0;
		attrRange=new double[numAnchors][2];
		
		// Create Android Log for each trainfile
		AndroidLog trainFileLog = new AndroidLog(trainFile + ".range", true);
		
		try{
			//CHANGED FOR ANDROID: FileInputStream fstream = new FileInputStream(trainFile + ".range");
			FileInputStream fstream = trainFileLog.inputStream;
			DataInputStream in = new DataInputStream(fstream);
			BufferedReader br = new BufferedReader(new InputStreamReader(in));
				
			String strLine;
			strLine=br.readLine(); // skip 1st line
				
			// read low and hi
			strLine=br.readLine();
			lo=Double.parseDouble(strLine.split(" ")[0]);
			hi=Double.parseDouble(strLine.split(" ")[1]);
				//System.out.println("Hello\n");
			// read range for each attribute
			while ((strLine = br.readLine()) != null) {
				//System.out.println(strLine);
				String[] str = strLine.split(" ");
				int i = Integer.parseInt(str[0])-1; // index starts at zero
				attrRange[i][0]=Double.parseDouble(str[1]);
				attrRange[i][1]=Double.parseDouble(str[2]);
			}
		}
		catch(Exception e){
			System.err.println("Failed getScaleParameters()" + e.getMessage());
			e.printStackTrace();
		}
	}
		
	private void getTrainingAccuracy() {
		// Create Android Log for each trainfile
		AndroidLog trainLog = new AndroidLog(trainFile + ".log", true);
		
		try{
			//CHANGED FOR ANDROID: FileInputStream fstream = new FileInputStream(trainFile + ".log");
			FileInputStream fstream = trainLog.inputStream;
			DataInputStream in = new DataInputStream(fstream);
			BufferedReader br = new BufferedReader(new InputStreamReader(in));
				
			String strLine;
				
			strLine=br.readLine();
			strLine=br.readLine();
			strLine=br.readLine();
				
			training_accuracy = Double.parseDouble(strLine.split("rate=")[1]);
			in.close();
		}
		catch(Exception e){
			System.out.println("Failed getTrainingAccuracy()");
			e.printStackTrace();
			System.exit(-1);
		}
	
	}
	
	private void getModelParameters(){
		// Create Android Log for each trainfile
		AndroidLog modelLog = new AndroidLog(trainFile + ".model", true);
		
		try{
			//System.out.println("get model from " + trainFile + ".model");
			// CHANGED FOR ANDROID: FileInputStream fstream = new FileInputStream(trainFile + ".model");
			FileInputStream fstream = modelLog.inputStream;
			DataInputStream in = new DataInputStream(fstream);
			BufferedReader br = new BufferedReader(new InputStreamReader(in));
				
			String strLine;
				
			strLine=br.readLine(); // skip 1st line
			strLine=br.readLine(); // skip kernel_type line
			
			strLine=br.readLine();
			gamma=Double.parseDouble(strLine.split(" ")[1]);
				
			strLine=br.readLine(); // nr_class line
			int nr_class = Integer.parseInt(strLine.split(" ")[1]);
			
			
			strLine=br.readLine(); // total_sv line
			total_sv=Integer.parseInt(strLine.split(" ")[1]);
			
			if (total_sv > 0) {
				sv_list=new double[total_sv][numAnchors+1];

				strLine=br.readLine(); // read "rho" line
				rho=Double.parseDouble(strLine.split(" ")[1]);	
			
				strLine=br.readLine(); // read "label" line
				label = strLine.split(" ")[1];
				
				
				strLine=br.readLine(); // read "nr_sv" line
				strLine=br.readLine(); // read "SV" line
			
				for (int i=0;i<total_sv;i++){
					strLine=br.readLine();
					String[] vals=strLine.split(" ");
					
					// the first (nr_class-1) values are for coefficient "alpha" in SVM formula
					// since were are interested in binary classification, only need to get the first value
					sv_list[i][0]=Double.parseDouble(vals[0]); // "alpha" value in our SVM formula
					
					// read support vector
					for (int j=nr_class-1;j<vals.length;j++) {
						sv_list[i][Integer.parseInt(vals[j].split(":")[0])]=Double.parseDouble(vals[j].split(":")[1]);
					}
				}
			}
			else {
				// there is zero support vector
				strLine=br.readLine(); // read "rho" line
				strLine=br.readLine(); // read "label" line
				label = strLine.split(" ")[1];
				// no need to read the rest
			}
			in.close();
		}
		catch(Exception e){
			System.out.println("Failed getModelParameters() " + e.getMessage());
			e.printStackTrace();
			System.exit(-1);
		}
	}
	
	
	
	private double kernelFunction(double[] a, double[] b) {
		// kernel function for SVM
		// here, we use RBF
		double r = Misc.euclideanDist(a, b);
		return Math.exp((0-gamma) * r * r);
	}
	
	private double decisionFunction(double[] a) {
		// NOTE: this decision function only applies to the case of binary classification, not for multi-classification 
		// sign (negative/positive) of this decision function tells if a given vector a is in the class or not
		
		if (total_sv == 0) {
			// special case where there is no SV
			if (label.equals("1")) return 1;
			else return -1;
		}
		
		double sum=0;
		for (int i=0;i<total_sv;i++){
			double[] b=new double[numAnchors];
			for (int j=1;j<=numAnchors;j++){
				b[j-1]=sv_list[i][j];
			}
			sum+=sv_list[i][0] * kernelFunction(a, b);
		}
		return (new Double(label)) * (sum-rho);
	}
	
	public boolean contains(double[] newSample) {
		// return if a new sample is in the class or not
		
		// need to scale vector newSample using the .range info
		double [] newSample1 = new double[numAnchors];
		for (int i=0; i < numAnchors; i++) {
			double val = newSample[i];
			if (attrRange[i][1]-attrRange[i][0] != 0)
				newSample1[i] = ((val-attrRange[i][0])/(attrRange[i][1]-attrRange[i][0]))*(hi-lo)+lo;
			else
				newSample1[i] = 0; 
		}
		//Misc.printArray(newSample1);
		if (decisionFunction(newSample1) > 0) return true;
		return false;
	}
	
	private void scaleFile(String testFile, String testFileScaled) {
		// scale test file (libsvm file format) based on the training model
		
		try {
			DataInputStream in = new DataInputStream(new FileInputStream(testFile));
			BufferedReader br = new BufferedReader(new InputStreamReader(in));
		
			DataOutputStream output = new DataOutputStream(new FileOutputStream(testFileScaled));
			String strLine;
			
			while ((strLine = br.readLine()) != null)   {
				String[] str = strLine.trim().split(" ");
				output.writeBytes(str[0]); // write label
				
				for (int i=0; i < numAnchors; i++) {
					double val = new Double(str[i+1].split(":")[1]);
					if (attrRange[i][1]-attrRange[i][0] != 0) {
						val = ((val-attrRange[i][0])/(attrRange[i][1]-attrRange[i][0]))*(hi-lo)+lo;
						output.writeBytes(" " + (i+1) + ":" + val);
					}
					else {
						// in this case, the respective feature plays no role in the prediction
						// its value will be zero, hence ignored in the libsvm input format
					}
				}
				output.writeBytes("\n");			
			}
			in.close();
			output.close();
		}
		catch(Exception e){
			System.out.println("Failed scaleFile()");
			System.exit(-1);
		}
			
	}
	
	public void predictFile(String testFile, String predictFile) {
		// first we use ./svm-scale to scale test file according to model
		// then we use ./svm-predict to predict scaled test file
		
		scaleFile(testFile, testFile + ".scale");
		// now, predict		
		try {
			String cmd = "./svm-predict " + testFile + ".scale" + " " + trainFile + ".model "  + predictFile; 
			//System.out.println(cmd);
			Process pr = Runtime.getRuntime().exec(cmd);
			pr.waitFor();
			
			
			// delete the scaled file to avoid mess
			new File(testFile + ".scale").delete();	
		}
		catch(Exception e){
			System.out.println("Failed scaleFile()");
			System.exit(-1);
		}
	}
	
		
	
}
	
