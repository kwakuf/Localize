package com.tracme.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;

import android.app.Activity;
import android.content.Context;
import android.os.Environment;
import android.widget.Toast;

public class AndroidLog {
  
  public File file;
  public File path;
  public FileInputStream inputStream;
  
  public AndroidLog(String filename) {
    path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);    
    file = new File(path, filename);
  }
  
  /**
   * Constructor called when Log file is needed for prediction
   * 
   * @param filename
   * 	The name of the file being loaded
   * 
   * @param predict
   *  Flag specifying whether this file is used for prediction/localization
   *  
   */
  public AndroidLog(String filename, boolean predict)
  {
	  path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);    
	  file = new File(path, filename);
	  if (predict == true)
	  {
		  try {
			  inputStream = new FileInputStream(file);
		  } catch (Exception e)
		  {
			  System.out.println("Error loading file: " + e.getLocalizedMessage());
			  System.exit(-1);
		  }
	  }
  }
  
  public void save(String data)
  {
    try
    {
      // Make sure the Downloads directory exists.
      path.mkdirs();
      FileOutputStream fos = new FileOutputStream(file, true);
      fos.write(data.getBytes());
      fos.close();
      System.out.println("File successfully saved.");
    }
    catch (Exception ex)
    {
      System.out.println("Error saving file: " + ex.getLocalizedMessage());
    }
  }

  public String load()
  {
    try
    {
      FileInputStream fis = new FileInputStream(file);
      BufferedReader reader = new BufferedReader(new InputStreamReader(fis));
      String line = null, input="";
      while ((line = reader.readLine()) != null)
          input += line + "\n";
      reader.close();
      fis.close();
      System.out.println("File successfully loaded.");
      return input;
    }
    catch (Exception ex)
    {
      System.out.println("Error loading file: " + ex.getLocalizedMessage());
      return "";
    }
  } 
}