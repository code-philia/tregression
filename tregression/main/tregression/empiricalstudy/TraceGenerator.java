package tregression.empiricalstudy;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import microbat.model.trace.Trace;
import microbat.preference.AnalysisScopePreference;
import tregression.empiricalstudy.config.ProjectConfig;
import tregression.separatesnapshots.RunningResult;
import tregression.separatesnapshots.TraceCollector0;

public class TraceGenerator {
	
	public static final int NORMAL = 0;

	public TraceGenerator() {
		
	}
	
	public Trace generateTrace(String buggyPath, String fixPath, ProjectConfig config, String testcase, boolean isOnBuggy){
		List<TestCase> tcList;
		RunningResult runningResult = null;

		try {
			tcList = config.retrieveFailingTestCase(buggyPath);
			
			if(testcase != null) {
				tcList = filterSpecificTestCase(testcase,tcList);
			}
			
			for(TestCase tc: tcList) {
				runningResult = analyzeTestCase(buggyPath,fixPath,tc,config,isOnBuggy);
				if(runningResult!=null) {
					return runningResult.getRunningTrace();
				}
			}
			
		} catch (IOException e) {
			e.printStackTrace();
		}

		return null;
	}
	
	private List<TestCase> filterSpecificTestCase(String testcase, List<TestCase> tcList) {
		List<TestCase> filteredList = new ArrayList<>();
		for(TestCase tc: tcList){
			String tcName = tc.testClass + "#" + tc.testMethod;
			if(tcName.equals(testcase)){
				filteredList.add(tc);
			}
		}
		
		if(filteredList.isEmpty()){
			filteredList = tcList;
		}
		
		return filteredList;
	}
	
	private RunningResult analyzeTestCase(String buggyPath, String fixPath,TestCase tc, ProjectConfig config, boolean isOnBuggy){
		TraceCollector0 traceCollector = new TraceCollector0(isOnBuggy);
		RunningResult runningResult = null;

		List<String> includedClassNames = AnalysisScopePreference.getIncludedLibList();
		List<String> excludedClassNames = AnalysisScopePreference.getExcludedLibList();
			
		config.includeLibs = includedClassNames;
		config.excludeLibs = excludedClassNames;
		
		String path = isOnBuggy?buggyPath:fixPath;
		
		runningResult = traceCollector.run(path, tc, config, true, true);
		if (runningResult.getRunningType() != NORMAL) {
			return null;
		}
		else {
			return runningResult;
		}
	}
}
