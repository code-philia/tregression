package tregression.model;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import microbat.instrumentation.output.RunningInfo;
import microbat.model.trace.ConcurrentTrace;
import microbat.model.trace.ConcurrentTraceNode;
import microbat.model.trace.Trace;
import microbat.model.trace.TraceNode;
import microbat.recommendation.DebugState;
import microbat.recommendation.UserFeedback;
import microbat.trace.Reader;
import microbat.trace.TraceReader;
import sav.common.core.utils.SingleTimer;
import sav.strategies.dto.AppJavaClassPath;
import tregression.SimulationFailException;
import tregression.empiricalstudy.ConcurrentSimulator;
import tregression.empiricalstudy.EmpiricalTrial;
import tregression.empiricalstudy.RegressionUtil;
import tregression.empiricalstudy.RootCauseFinder;
import tregression.empiricalstudy.TestCase;
import tregression.empiricalstudy.config.ProjectConfig;
import tregression.empiricalstudy.solutionpattern.PatternIdentifier;
import tregression.separatesnapshots.DiffMatcher;
import tregression.tracematch.ControlPathBasedTraceMatcher;
import tregression.util.ConcurrentTraceMatcher;

public class TregressionConcurrentContext {
	private AppJavaClassPath buggyAppJavaClassPath;
	private AppJavaClassPath correctAppJavaClassPath;
	private List<Trace> buggyTraces;
	private List<Trace> correctTraces;
	private ProjectConfig projectConfig;
	private int numOfTrials = 0;
	private long executionTime = -1;
	/**
	 * The path to the buggy project.
	 */
	private String buggyPath;
	/**
	 * The path to the fixed project.
	 */
	private String fixPath;
	private TestCase testCase;
	
	private DiffMatcher diffMatcher = null;
	private PairList pairList = null;
	private Map<Long, Long> threadIdMap = null;
	private List<PairList> basePairLists = null;
	private SingleTimer singleTimer = null;
	private TregressionConcurrentContext() {
		
	}
	
	private TregressionConcurrentContext(AppJavaClassPath buggyAppJavaClassPath,
			AppJavaClassPath correctAppJavaClassPath) {
		this.buggyAppJavaClassPath = buggyAppJavaClassPath;
		this.correctAppJavaClassPath = correctAppJavaClassPath;
	}
	
	public static TregressionConcurrentContext fromFile(String buggyFilePath, String correctFilePath, AppJavaClassPath appJavaClassPath) {
		TregressionConcurrentContext result = new TregressionConcurrentContext(appJavaClassPath, 
				appJavaClassPath);
		
		return result;
	}
	
	public static TregressionConcurrentContext fromReader(TraceReader buggyReader, TraceReader correctReader,
			String buggyDumpFile, String correctDumpFile, ProjectConfig config, TestCase testCase) {
		RunningInfo correctRun = correctReader.read(null, correctDumpFile);
		RunningInfo buggyRun = buggyReader.read(null, buggyDumpFile);
		TregressionConcurrentContext result = new TregressionConcurrentContext();
		result.buggyTraces = buggyRun.getTraceList();
		result.correctTraces = correctRun.getTraceList();
		result.testCase = testCase;
		result.projectConfig = config;
		return result;
	}
	
	public EmpiricalTrial runTrial() {
		ConcurrentSimulator concurrentSimulator = new ConcurrentSimulator(false, false, 3);
		this.singleTimer = SingleTimer.start("run trial");
		SingleTimer matchTimer = SingleTimer.start("matching");
		generateCacheData();
		long matchTime = matchTimer.getExecutionTime();
		RootCauseFinder rootCauseFinder = createRootCauseFinder();
		concurrentSimulator.prepareConc(buggyTraces, correctTraces, pairList, threadIdMap, diffMatcher);
		if (!rootCauseFinder.hasRealRootCause()) {
			EmpiricalTrial result = EmpiricalTrial.createDumpTrial("Cannot find real root cause");
			StepOperationTuple tuple = new StepOperationTuple(concurrentSimulator.getObservedFault(), 
					new UserFeedback(UserFeedback.UNCLEAR), concurrentSimulator.getObservedFault(), DebugState.UNCLEAR);
			result.getCheckList().add(tuple);
			return result;
		}
		if (concurrentSimulator.getObservedFault() == null) {
			return EmpiricalTrial.createDumpTrial("Cannot find observable fault");
		}
		ConcurrentTrace buggyTrace = ConcurrentTrace.fromTimeStampOrder(buggyTraces);
		ConcurrentTrace correctTrace = ConcurrentTrace.fromTimeStampOrder(correctTraces);
		rootCauseFinder.checkRootCause(
				concurrentSimulator.getObservedFault(), buggyTrace, correctTrace, pairList, diffMatcher);
		TraceNode rootCause = rootCauseFinder.retrieveRootCause(pairList, diffMatcher, buggyTrace, correctTrace);
		numOfTrials++;
		if (rootCause==null) {
			return handleMissingRootCause(10, rootCauseFinder);
		}
		return runSimulation(concurrentSimulator, buggyTrace, correctTrace, (int) matchTime, diffMatcher);
		
	}
	
	private EmpiricalTrial runSimulation(
			ConcurrentSimulator concurrentSimulator,
			ConcurrentTrace buggyTrace, 
			ConcurrentTrace correctTrace,
			int matchTime,
			DiffMatcher diffMatcher) {
		try {
			List<EmpiricalTrial> trials = concurrentSimulator.detectMutatedBug(buggyTrace, correctTrace, diffMatcher, 0);
			for (EmpiricalTrial t : trials) {
				t.setTestcase(this.testCase.testClass + "#" + this.testCase.testMethod);
				t.setTraceCollectionTime(buggyTrace.getConstructTime() + correctTrace.getConstructTime());
				t.setTraceMatchTime(matchTime);
				t.setBuggyTrace(buggyTrace);
				t.setFixedTrace(correctTrace);
				t.setPairList(pairList);
				t.setDiffMatcher(diffMatcher);
				
				PatternIdentifier identifier = new PatternIdentifier();
				identifier.identifyPattern(t);
			}

			this.executionTime = this.singleTimer.getExecutionTime();
			EmpiricalTrial trial = trials.get(0);
			return trial;
		} catch (SimulationFailException e) {
			
			e.printStackTrace();
			return null;
		}
		
	}
	
	/**
	 * Handles the case where no root cause can be found by looking through the
	 * libraries and adding new classes.
	 * @param maxTrials
	 * @return
	 */
	private EmpiricalTrial handleMissingRootCause(int maxTrials, RootCauseFinder rootCauseFinder) {
		System.out.println("[Search Lib Class] Cannot find the root cause, I am searching for library classes...");
		
		List<TraceNode> buggySteps = rootCauseFinder.getStopStepsOnBuggyTrace();
		List<TraceNode> correctSteps = rootCauseFinder.getStopStepsOnCorrectTrace();
		
		List<String> newIncludedClassNames = new ArrayList<>();

		List<TraceNode> convertedBuggySteps = ConcurrentTraceNode.convert(buggySteps);
		List<TraceNode> convertedConcurrentSteps = ConcurrentTraceNode.convert(correctSteps);
		List<TraceNode> convertedRegressionNodes = ConcurrentTraceNode.convert(rootCauseFinder.getRegressionNodeList());
		List<TraceNode> convertedCorrectNodeList = ConcurrentTraceNode.convert(rootCauseFinder.getCorrectNodeList());
		
		
//		List<String> newIncludedBuggyClassNames = RegressionUtil.identifyIncludedClassNames(convertedBuggySteps, buggyRS.getPrecheckInfo(), convertedRegressionNodes);
//		List<String> newIncludedCorrectClassNames = RegressionUtil.identifyIncludedClassNames(convertedConcurrentSteps, correctRs.getPrecheckInfo(), convertedCorrectNodeList);
//		
//		newIncludedClassNames.addAll(newIncludedBuggyClassNames);
//		newIncludedClassNames.addAll(newIncludedCorrectClassNames);
		boolean includedClassChanged = false;
//		for(String name: newIncludedClassNames){
//			if(!includedClassNames.contains(name)){
//				includedClassNames.add(name);
//				includedClassChanged = true;
//			}
//		}
		return null;
		
		
	}
	
	private RootCauseFinder createRootCauseFinder() {
		RootCauseFinder result = new RootCauseFinder();
		result.setRootCauseBasedOnDefects4JConc(basePairLists, diffMatcher, buggyTraces, correctTraces);
		return result;
	}

	private void generateCacheData() {
		if (diffMatcher == null || basePairLists == null || pairList == null || threadIdMap == null) {
			diffMatcher = new DiffMatcher(projectConfig.srcSourceFolder, projectConfig.srcTestFolder, 
					buggyPath, fixPath);
			diffMatcher.matchCode();
			ControlPathBasedTraceMatcher traceMatcher = new ControlPathBasedTraceMatcher();
			Map<Long, Long> threadIdMap = new ConcurrentTraceMatcher(diffMatcher).matchTraces(buggyTraces, correctTraces);
			this.basePairLists = traceMatcher.matchConcurrentTraceNodePair(correctTraces, buggyTraces, diffMatcher, threadIdMap);
			this.pairList = combinePairLists(basePairLists);
		}
	}

	private PairList combinePairLists(List<PairList> basePairLists) {
		LinkedList<TraceNodePair> pairs = new LinkedList<>();
		for (PairList pList : basePairLists) {
			pairs.addAll(pList.getPairList());
		}
		return new PairList(pairs);
	}
}
