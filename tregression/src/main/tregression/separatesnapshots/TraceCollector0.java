package tregression.separatesnapshots;

import java.util.List;
import java.util.concurrent.Executor;

import microbat.codeanalysis.runtime.InstrumentationExecutor;
import microbat.codeanalysis.runtime.PreCheckInformation;
import microbat.codeanalysis.runtime.StepLimitException;
import microbat.instrumentation.output.RunningInfo;
import microbat.model.trace.Trace;
import microbat.util.MicroBatUtil;
import sav.strategies.dto.AppJavaClassPath;
import tregression.empiricalstudy.TestCase;
import tregression.empiricalstudy.TrialGenerator0;
import tregression.empiricalstudy.config.ProjectConfig;

public class TraceCollector0 {
	private boolean isBuggy;
	protected boolean isForceJunit3Or4 = false;
	public TraceCollector0(boolean buggy) {
		this.isBuggy = buggy;
	}
	
	protected InstrumentationExecutor generateExecutor(String workingDir, TestCase tc, 
			ProjectConfig config, boolean isRunInTestCaseMode, List<String> includeLibs, List<String> excludeLibs) {
		AppJavaClassPath appClassPath = AppClassPathInitializer.initialize(workingDir, tc, config);
		if(!isRunInTestCaseMode) {
			appClassPath.setLaunchClass(appClassPath.getOptionalTestClass());
		}
		
		String traceDir = MicroBatUtil.generateTraceDir(config.projectName, config.regressionID);
		String traceName = isBuggy ? "bug" : "fix";
		return new InstrumentationExecutor(appClassPath,
				traceDir, traceName, includeLibs, excludeLibs);
		
	}
	

	public static InstrumentationExecutor generateExecutor(String workingDir, TestCase tc, 
			ProjectConfig config, boolean isRunInTestCaseMode, List<String> includeLibs, List<String> excludeLibs, boolean isBuggy) {
		AppJavaClassPath appClassPath = AppClassPathInitializer.initialize(workingDir, tc, config);
		if(!isRunInTestCaseMode) {
			appClassPath.setLaunchClass(appClassPath.getOptionalTestClass());
		}
		
		String traceDir = MicroBatUtil.generateTraceDir(config.projectName, config.regressionID);
		String traceName = isBuggy ? "bug" : "fix";
		return new InstrumentationExecutor(appClassPath,
				traceDir, traceName, includeLibs, excludeLibs);
		
	}
	
	

	public RunningResult runInner(String workingDir, TestCase tc, 
			ProjectConfig config, boolean isRunInTestCaseMode, boolean mustBeMultiThread,
			boolean allowMultiThread,
			List<String> includeLibs, List<String> excludeLibs){
		
		InstrumentationExecutor executor = generateExecutor(workingDir, tc, config, isRunInTestCaseMode, includeLibs, excludeLibs);
		executor.setIsForceJunit3Or4(this.isForceJunit3Or4);
		RunningInfo info = null;
		try {
			info = executor.run();
		} catch (StepLimitException e) {
			e.printStackTrace();
		}
		
		PreCheckInformation precheckInfo = executor.getPrecheckInfo();
		System.out.println("There are " + precheckInfo.getStepNum() + " steps in this trace");
		if(precheckInfo.isOverLong()) {
			System.out.println("The trace is over long!");
			RunningResult rs = new RunningResult();
			rs.setFailureType(TrialGenerator0.OVER_LONG);
			return rs;
		}
		
//		if(!precheckInfo.getOverLongMethods().isEmpty()) {
//			String method = precheckInfo.getOverLongMethods().get(0);
//			System.out.println("Method " + method + " is over long after instrumentation!");
//			RunningResult rs = new RunningResult();
//			rs.setFailureType(TrialGenerator0.OVER_LONG_INSTRUMENTATION_METHOD);
//			return rs;
//		}
		
		if(precheckInfo.isUndeterministic()){
			System.out.println("This is undeterministic testcase!");
			RunningResult rs = new RunningResult();
			rs.setFailureType(TrialGenerator0.UNDETERMINISTIC);
			return rs;
		}
		
		
		boolean isMultiThread = precheckInfo.getThreadNum()!=1;
		
		if(isMultiThread && !allowMultiThread) {
			System.out.println("It is multi-thread program!");
			RunningResult rs = new RunningResult();
			rs.setFailureType(TrialGenerator0.MULTI_THREAD);
			return rs;
		}
		
		if (info.getTraceList().size() == 0) {
			RunningResult rs = new RunningResult();
			System.out.println("No trace");
			rs.setFailureType(TrialGenerator0.NO_TRACE);
			return rs;
		}
		
//		if(!info.isExpectedStepsMet()){
//			System.out.println("The expected steps are not met by normal run");
//			RunningResult rs = new RunningResult();
//			rs.setFailureType(TrialGenerator0.EXPECTED_STEP_NOT_MET);
//			return rs;
//		}
		updateTraceInfo(info);
		Trace mainTrace = info.getMainTrace();
		
		RunningResult rs = new RunningResult(mainTrace, null, null, precheckInfo, executor.getAppPath());
		rs.setRunningTrace(mainTrace);
		rs.setRunningInfo(info);
		return rs;
	}
	
	protected void updateTraceInfo(RunningInfo runningInfo) {
		List<Trace> traces = runningInfo.getTraceList();
		for (Trace trace : traces) {
			trace.constructLoopParentRelation();
			trace.setSourceVersion(isBuggy);	
		}
	}
	
	
	public RunningResult runForceMultithreaded(String workingDir, TestCase tc, 
			ProjectConfig config, boolean isRunInTestCaseMode,
			List<String> includeLibs, List<String> excludeLibs) {
		return runInner(workingDir, tc, config, isRunInTestCaseMode, true, true, includeLibs, excludeLibs);
	}
	
	public RunningResult run(String workingDir, TestCase tc, 
			ProjectConfig config, boolean isRunInTestCaseMode, boolean allowMultiThread, 
			List<String> includeLibs, List<String> excludeLibs){
		return runInner(workingDir, tc, config, isRunInTestCaseMode, false, allowMultiThread, includeLibs, excludeLibs);
	}
	
}
