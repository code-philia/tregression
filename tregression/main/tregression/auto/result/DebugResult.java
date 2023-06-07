package tregression.auto.result;

public class DebugResult extends RunResult {
	public double avgPropTime = -1;
	public double avgPathFindingTime = -1;
	public double avgTotalTime = -1;
	
	public int correctFeedbackCount = -1;
	public int totalFeedbackCount = -1;
	
	public boolean locatedRootCause = false;
	public boolean debugSuccess = false;
	
	public DebugResult() {
		
	}
	
	public DebugResult(final RunResult result) {
		super(result);
	}
	
	public DebugResult(final DebugResult result) {
		super(result);
		this.avgPropTime = result.avgPropTime;
		this.avgPathFindingTime = result.avgPathFindingTime;
		this.avgTotalTime = result.avgTotalTime;
		this.correctFeedbackCount = result.correctFeedbackCount;
		this.totalFeedbackCount = result.totalFeedbackCount;
		this.debugSuccess = result.debugSuccess;
	}
	
	public static DebugResult parseString(final String string) {
		DebugResult result = new DebugResult();
		
		String[] tokens = string.split(RunResult.DELIMITER);
		
		final String projName = tokens[0];
		result.projectName = projName == " " ? null : projName;
		
		final String bugID_str = tokens[1];
		result.bugID = Integer.valueOf(bugID_str);
		
		final String traceLen_str = tokens[2];
		result.traceLen = Integer.valueOf(traceLen_str);
		
		final String rootCauseOrder_str = tokens[3];
		result.rootCauseOrder = Integer.valueOf(rootCauseOrder_str);
		
		final String isOmissionBug_str = tokens[4];
		result.isOmissionBug = Boolean.valueOf(isOmissionBug_str);
		
		final String solutionName = tokens[5];
		result.solutionName = solutionName == " " ? null : solutionName;
		
		final String errMsg = tokens[6];
		result.errorMessage = errMsg == " " ? null : errMsg;
		
		final String avgPropTime_str = tokens[7];
		result.avgPropTime = Double.valueOf(avgPropTime_str);
		
		final String avgPathFindingTime_str = tokens[8];
		result.avgPathFindingTime = Double.valueOf(avgPathFindingTime_str);
		
		
		final String avgTotalTime_str = tokens[9];
		result.avgTotalTime = Double.valueOf(avgTotalTime_str);
		
		final String correctFeedbackCount_str = tokens[9];
		result.correctFeedbackCount = Integer.valueOf(correctFeedbackCount_str);
		
		final String totalFeedbackCount_str = tokens[10];
		result.totalFeedbackCount = Integer.valueOf(totalFeedbackCount_str);
		
		final String locateRootCause_str = tokens[11];
		result.locatedRootCause = Boolean.valueOf(locateRootCause_str);
		
		final String debugSuccess_str = tokens[12];
		result.debugSuccess = Boolean.valueOf(debugSuccess_str);
		return result;
	}
	
	@Override
	public String toString() {
		String string = super.toString();
		StringBuilder strBuilder = new StringBuilder();
		this.appendStr(strBuilder, string);
		this.appendStr(strBuilder, String.valueOf(this.avgPropTime));
		this.appendStr(strBuilder, String.valueOf(this.avgPathFindingTime));
		this.appendStr(strBuilder, String.valueOf(this.avgTotalTime));
		this.appendStr(strBuilder, String.valueOf(this.correctFeedbackCount));
		this.appendStr(strBuilder, String.valueOf(this.totalFeedbackCount));
		this.appendStr(strBuilder, String.valueOf(this.locatedRootCause));
		this.appendStr(strBuilder, String.valueOf(this.debugSuccess));
		return strBuilder.toString();
	}
	
	public String getFormattedInfo() {
		StringBuilder builder = new StringBuilder();
		builder.append("--------------------------------\n");
		builder.append("ProjectName: " + this.projectName + "\n");
		builder.append("Bug ID: " + this.bugID + "\n");
		builder.append("Length: " + this.traceLen + "\n");
		builder.append("Root Cause Order: " + this.rootCauseOrder + "\n");
		builder.append("isOmissionBug: " + this.isOmissionBug + "\n");
		builder.append("SolutationName: " + this.solutionName + "\n");
		builder.append("Error Message: " + this.errorMessage + "\n");
		builder.append("avgPropTime: " + this.avgPropTime + "\n");
		builder.append("avgPathFindingTime: " + this.avgPathFindingTime + "\n");
		builder.append("avgTotalTime: " + this.avgTotalTime + "\n");
		builder.append("correctFeedbackCount: " + this.correctFeedbackCount + "\n");
		builder.append("totalFeedbackCount: " + this.totalFeedbackCount + "\n");
		builder.append("locateRootCause: " + this.locatedRootCause + "\n");
		builder.append("debugSuccess: " + this.debugSuccess + "\n");
		builder.append("--------------------------------\n");
		return builder.toString();
	}
}