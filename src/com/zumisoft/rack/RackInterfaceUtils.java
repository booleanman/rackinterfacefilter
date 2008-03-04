package com.zumisoft.rack;

import java.io.IOException;
import java.io.InputStream;

/**
 * Collection of utility methods
 * 
 * @author Fred McCann
 */
public class RackInterfaceUtils {
	private static final int BUFFER_SIZE = 16384;

	/**
	 * Reads some input stream and returns a String. Closes stream when finished.
	 */
	public static String readInputStream(InputStream stream) throws IOException {
		StringBuffer str = new StringBuffer();
		try {
		    byte[] buffer = new byte[BUFFER_SIZE];			
		    int len = 0;
		    while ( (len = stream.read(buffer)) > 0 )
		    	str.append(new String(buffer, 0, len));			
		}
		catch (IOException ioe) {
			throw ioe;
		}
		finally {
			stream.close();
		}
		
		return str.toString();
	}
}
