package tregression.empiricalstudy;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import microbat.model.trace.Trace;
import microbat.preference.AnalysisScopePreference;
import microbat.util.Settings;
import tregression.empiricalstudy.config.ProjectConfig;
import tregression.model.PairList;
import tregression.model.TraceNodePair;
import tregression.separatesnapshots.DiffMatcher;
import tregression.separatesnapshots.RunningResult;
import tregression.separatesnapshots.TraceCollector;
import tregression.separatesnapshots.TraceCollector0;
import tregression.tracematch.ControlPathBasedTraceMatcher;
import tregression.util.ConcurrentTraceMatcher;
import tregression.views.ConcurrentVisualiser;

/**
 * Trial generator used for concurrent programs.
 * Used to perform tregression on concurrent programs
 */
public class ConcurrentTrialGenerator {
	
	protected EmpiricalTrial analyzeTestCase(String buggyPath, String fixPath,
			TestCase tc, ProjectConfig config, boolean isRunInTestMode,
			boolean useSliceBreaker, boolean enableRandom, int breakLimit) {
		TraceCollector0 buggyCollector = new TraceCollector0(true);
		TraceCollector0 correctCollector0 = new TraceCollector0(false);
		RunningResult buggyRsResult = null;
		RunningResult correctRs = null;
		
		DiffMatcher diffMatcher = null;
		List<PairList> pairLists = null;
		
		int trialLimit = 10;
		int trialNum = 0;
		boolean isDataFlowComplete = false;
		EmpiricalTrial trial = null;
		List<String> includedClassNames = AnalysisScopePreference.getIncludedLibList();
		List<String> excludedClassNames = AnalysisScopePreference.getExcludedLibList();
		
		List<Trace> buggyTraces = buggyRsResult.getRunningInfo().getTraceList();
		List<Trace> correctTraces = correctRs.getRunningInfo().getTraceList();
		diffMatcher = new DiffMatcher(config.srcSourceFolder, config.srcTestFolder, buggyPath, fixPath);
		diffMatcher.matchCode();
		
		ControlPathBasedTraceMatcher traceMatcher = new ControlPathBasedTraceMatcher();
		
		while (!isDataFlowComplete && trialNum < trialLimit) {
			trialNum++;
			Settings.compilationUnitMap.clear();
			Settings.iCompilationUnitMap.clear();
			
			buggyRsResult = buggyCollector.run(buggyPath, tc, config, isRunInTestMode, true, 
					includedClassNames, excludedClassNames);
			correctRs = correctCollector0.run(fixPath, tc, config, isRunInTestMode, true, includedClassNames, excludedClassNames);
			
			ConcurrentVisualiser vizConcurrentVisualiser = 
					new ConcurrentVisualiser(buggyTraces, correctTraces, pairLists, diffMatcher);
			vizConcurrentVisualiser.visualise();
			
			Map<Long, Long> traceMap = new ConcurrentTraceMatcher(diffMatcher).matchTraces(buggyTraces, correctTraces);
			pairLists = traceMatcher.matchConcurrentTraceNodePair(buggyTraces, correctTraces, diffMatcher, traceMap);
			List<TraceNodePair> tnPair = new LinkedList<>();
			
			for (PairList pairList : pairLists) {
				tnPair.addAll(pairList.getPairList());
			}
			PairList basePairList = new PairList(tnPair);
			
			
			RootCauseFinder rootCauseFinder = new RootCauseFinder();
			rootCauseFinder.setRootCauseBasedOnDefects4JConc(pairLists, diffMatcher, buggyTraces, correctTraces);
			ConcurrentSimulator simulator = new ConcurrentSimulator(useSliceBreaker, enableRandom, breakLimit);
			simulator.prepareConc(buggyTraces, correctTraces, basePairList, traceMap, diffMatcher);
		}
		
		return null;
	}
}
