package tregression.util;

import tregression.model.TregressionTestCase;

/*
 * 
 */
public class ConcurrentTregressionRunner implements Runnable {
	private final TregressionTestCase testCase;
	private String logText;
	
	
	public ConcurrentTregressionRunner(
			TregressionTestCase testCase) {
		this.testCase = testCase;
	}
	
	public void run() {
		
	}
	
	public void deleteLogFile() {
		
	}
	
	private void createLogFile() {
		
	}
	
	public void record() { 
		
	}
}
