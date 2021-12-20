package org.xtext.example.mydsl.validation;

/*
 * 
 * Utility file 
 *   -> Upper and lower bounds taking confidence value into account
 *   -> Number of samples in a period of time
 *   -> Assertion condition checks
 * 	
 * @author Maialen Otaegi
 * 
 * */

public class Utils {

	// TODO: configurable sampling frequency  
	public static int getNSamples(int samples, String timeUnit) {
		if (samples <= 0) return samples;
		
		// 1 sample = 1 second
		int nSamples = samples;
		switch (timeUnit) {
		case "milliseconds":
			nSamples = (int) nSamples / 1000;
			if (nSamples < 1)
				nSamples = 1;
			break;
		case "minutes":
			nSamples = nSamples * 60;
			break;
		case "hours":
			nSamples = nSamples * 60 * 60;
			break;
		default: // seconds
		}
		return nSamples;
	}


}
