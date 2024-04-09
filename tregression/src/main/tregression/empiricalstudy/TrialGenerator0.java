package tregression.empiricalstudy;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.Stack;

import microbat.codeanalysis.runtime.InstrumentationExecutor;
import microbat.handler.CancelThread;
import microbat.instrumentation.instr.aggreplay.TimeoutThread;
import microbat.instrumentation.output.RunningInfo;
import microbat.model.trace.ConcurrentTrace;
import microbat.model.trace.ConcurrentTraceNode;
import microbat.model.trace.Trace;
import microbat.model.trace.TraceNode;
import microbat.preference.AnalysisScopePreference;
import microbat.recommendation.DebugState;
import microbat.recommendation.UserFeedback;
import microbat.util.Settings;
import sav.common.core.utils.SingleTimer;
import sav.strategies.dto.AppJavaClassPath;
import tregression.SimulationFailException;
import tregression.empiricalstudy.config.Defects4jProjectConfig;
import tregression.empiricalstudy.config.ProjectConfig;
import tregression.empiricalstudy.solutionpattern.PatternIdentifier;
import tregression.io.RegressionRecorder;
import tregression.model.PairList;
import tregression.model.StepOperationTuple;
import tregression.model.TraceNodePair;
import tregression.separatesnapshots.AppClassPathInitializer;
import tregression.separatesnapshots.BuggyRnRTraceCollector;
import tregression.separatesnapshots.BuggyTraceCollector;
import tregression.separatesnapshots.DiffMatcher;
import tregression.separatesnapshots.RunningResult;
import tregression.separatesnapshots.TraceCollector0;
import tregression.tracematch.ControlPathBasedTraceMatcher;
import tregression.util.ConcurrentTraceMatcher;
import tregression.views.ConcurrentVisualiser;
import tregression.views.Visualizer;

public class TrialGenerator0 {
	public static final int NORMAL = 0;
	public static final int OVER_LONG = 1;
	public static final int MULTI_THREAD = 2;
	public static final int INSUFFICIENT_TRACE = 3;
	public static final int SAME_LENGTH = 4;
	public static final int OVER_LONG_INSTRUMENTATION_METHOD = 5;
	public static final int EXPECTED_STEP_NOT_MET = 6;
	public static final int UNDETERMINISTIC = 7;
	public static final int NOT_MULTI_THREAD = 8;
	public static final int NO_TRACE = 9;

	private RunningResult cachedBuggyRS;
	private RunningResult cachedCorrectRS;

	private DiffMatcher cachedDiffMatcher;
	private PairList cachedPairList;
	private List<PairList> cachedPairLists;
	private CancelThread cancelThread = null;
	
	public void setCancelThread(CancelThread cThread) {
		this.cancelThread = cThread;
	}
	
	public static String getProblemType(int type) {
		switch (type) {
		case OVER_LONG:
			return "some trace is over long";
		case MULTI_THREAD:
			return "it's a multi-thread program";
		case INSUFFICIENT_TRACE:
			return "the trace is insufficient";
		case SAME_LENGTH:
			return "two traces are of the same length";
		case OVER_LONG_INSTRUMENTATION_METHOD:
			return "over long instrumented byte code method";
		case EXPECTED_STEP_NOT_MET:
			return "expected steps are not met";
		case UNDETERMINISTIC:
			return "this is undeterministic testcase";
		case NOT_MULTI_THREAD:
			return "this is not multi threaded";
		case NO_TRACE:
			return "main trace has no recording";
		default:
			break;
		}
		return "I don't know";
	}
	

	public List<EmpiricalTrial> findConcurrent(String buggyPath, String fixPath, boolean isReuse, boolean useSliceBreaker,
			boolean enableRandom, int breakLimit, boolean requireVisualization, ProjectConfig config, String testcase) {
		List<TestCase> tcList;
		EmpiricalTrial trial = null;
		TestCase workingTC = null;
		LinkedList<EmpiricalTrial> result = new LinkedList<>();
		try {
			tcList = retrieveD4jFailingTestCase(buggyPath);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return new LinkedList<>();
		}
		
		if(testcase!=null){
			tcList = filterSpecificTestCase(testcase, tcList);
		}
		
		for (TestCase tc : tcList) {
			if (cancelThread != null && cancelThread.stopped) break;
			System.out.println("#####working on test case " + tc.testClass + "#" + tc.testMethod);
			workingTC = tc;
			SingleTimer timer = SingleTimer.start("generateTrial");

			List<String> includedClassNames = AnalysisScopePreference.getIncludedLibList();
			List<String> excludedClassNames = AnalysisScopePreference.getExcludedLibList();
			try {	
				InstrumentationExecutor executor = TraceCollector0.generateExecutor(buggyPath, tc, config, true, includedClassNames, excludedClassNames, true);
				executor.runPrecheck(null, Settings.stepLimit);
				
				trial = EmpiricalTrial.createDumpTrial("");
				trial.setTestcase(tc.getName());
				trial.setMultiThread(executor.getPrecheckInfo().getThreadNum() > 1);
				result.add(trial);
			} catch (Exception e) {
				trial = EmpiricalTrial.createDumpTrial("Runtime exception occurs " + e);
				trial.setTestcase(workingTC.testClass + "::" + workingTC.testMethod);
				trial.setExecutionTime(timer.getExecutionTime());
				result.add(trial);
				e.printStackTrace();
			}
			
//				if(!trial.isDump()){
//					break;
//				}
		}

		

//		if (trial == null) {
//			trial = EmpiricalTrial.createDumpTrial("runtime exception occurs");
//			trial.setTestcase(workingTC.testClass + "::" + workingTC.testMethod);
//		}
//		List<EmpiricalTrial> list = new ArrayList<>();
//		list.add(trial);
		return result;
	}
	

	public List<EmpiricalTrial> generateTrialsConcurrent(String buggyPath, String fixPath, boolean isReuse, boolean useSliceBreaker,
			boolean enableRandom, int breakLimit, boolean requireVisualization, ProjectConfig config, String testcase) {
		List<TestCase> tcList;
		EmpiricalTrial trial = null;
		TestCase workingTC = null;
		LinkedList<EmpiricalTrial> result = new LinkedList<>();
		try {
			tcList = retrieveD4jFailingTestCase(buggyPath);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return new LinkedList<>();
		}
		
		if(testcase!=null){
			tcList = filterSpecificTestCase(testcase, tcList);
		}
		
		for (TestCase tc : tcList) {
			if (cancelThread != null && cancelThread.stopped) break;
			System.out.println("#####working on test case " + tc.testClass + "#" + tc.testMethod);
			workingTC = tc;
			SingleTimer timer = SingleTimer.start("generateTrial");
			try {
				trial = analyzeConcurrentTestCase(buggyPath, fixPath, isReuse,
						tc, config, requireVisualization, true, useSliceBreaker, enableRandom, breakLimit);
				trial.setExecutionTime(timer.getExecutionTime());
				result.add(trial);
			} catch (Exception e) {
				trial = EmpiricalTrial.createDumpTrial("Runtime exception occurs " + e);
				trial.setTestcase(workingTC.testClass + "::" + workingTC.testMethod);
				trial.setExecutionTime(timer.getExecutionTime());
				result.add(trial);
				e.printStackTrace();
			}
			
//				if(!trial.isDump()){
//					break;
//				}
		}

		

//		if (trial == null) {
//			trial = EmpiricalTrial.createDumpTrial("runtime exception occurs");
//			trial.setTestcase(workingTC.testClass + "::" + workingTC.testMethod);
//		}
//		List<EmpiricalTrial> list = new ArrayList<>();
//		list.add(trial);
		return result;
	}
	

	public List<EmpiricalTrial> generateTrials(String buggyPath, String fixPath, boolean isReuse, boolean useSliceBreaker,
			boolean enableRandom, int breakLimit, boolean requireVisualization, 
			boolean allowMultiThread, ProjectConfig config, String testcase) {
		SingleTimer timer = SingleTimer.start("generateTrial");
		List<TestCase> tcList;
		EmpiricalTrial trial = null;
		TestCase workingTC = null;
		try {
			tcList = retrieveD4jFailingTestCase(buggyPath);
			
			if(testcase!=null){
				tcList = filterSpecificTestCase(testcase, tcList);
			}
			
			for (TestCase tc : tcList) {
				System.out.println("#####working on test case " + tc.testClass + "#" + tc.testMethod);
				workingTC = tc;

				trial = analyzeTestCase(buggyPath, fixPath, isReuse, allowMultiThread,
						tc, config, requireVisualization, true, useSliceBreaker, enableRandom, breakLimit);
				if(!trial.isDump()){
					break;
				}
			}

		} catch (Exception e) {
			e.printStackTrace();
		}

		if (trial == null) {
			trial = EmpiricalTrial.createDumpTrial("runtime exception occurs");
			trial.setTestcase(workingTC.testClass + "::" + workingTC.testMethod);
		}
		trial.setExecutionTime(timer.getExecutionTime());
		List<EmpiricalTrial> list = new ArrayList<>();
		list.add(trial);
		return list;
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

	private List<EmpiricalTrial> runMainMethodVersion(String buggyPath, String fixPath, boolean isReuse, boolean allowMultiThread,
			boolean requireVisualization, Defects4jProjectConfig config, TestCase tc) throws SimulationFailException {
		List<EmpiricalTrial> trials;
		generateMainMethod(buggyPath, tc, config);
		recompileD4J(buggyPath, config);
		generateMainMethod(fixPath, tc, config);
		recompileD4J(fixPath, config);
		
		trials = new ArrayList<>();
		EmpiricalTrial trial = analyzeTestCase(buggyPath, fixPath, isReuse, allowMultiThread, 
				tc, config, requireVisualization, false, false, false, -1);
		trials.add(trial);
		return trials;
	}

	private void recompileD4J(String workingPath, Defects4jProjectConfig config) {
		File pathToExecutable = new File(config.rootPath);
		ProcessBuilder builder = new ProcessBuilder(pathToExecutable.getAbsolutePath(), "compile");
		builder.directory(new File(workingPath).getAbsoluteFile() ); // this is where you set the root folder for the executable to run with
		builder.redirectErrorStream(true);
		Process process;
		try {
			process = builder.start();
			Scanner s = new Scanner(process.getInputStream());
			StringBuilder text = new StringBuilder();
			while (s.hasNextLine()) {
				text.append(s.nextLine());
				text.append("\n");
			}
			s.close();
			
			int result = process.waitFor();
			
			System.out.printf( "Process exited with result %d and output %s%n", result, text );
		} catch (IOException | InterruptedException e) {
			e.printStackTrace();
		}

		
	}

	private void generateMainMethod(String workingPath, TestCase tc, Defects4jProjectConfig config) {
		MainMethodGenerator generator = new MainMethodGenerator();
		AppJavaClassPath appCP = AppClassPathInitializer.initialize(workingPath, tc, config);
		String relativePath = tc.testClass.replace(".", File.separator) + ".java";
		String sourcePath = appCP.getTestCodePath() + File.separator + relativePath;
		
		generator.generateMainMethod(sourcePath, tc);
		System.currentTimeMillis();
	}
	
	/**
	 * 
	 * @param traces The list of traces to check for dead lock
	 * @return The set of threads that are involved in the deadlock.
	 */
	private Set<Long> hasDeadlock(List<Trace> traces) {
		HashMap<Long, Long> waitingForMap  = new HashMap<Long, Long>();
		HashSet<Long> visited = new HashSet<>();
		HashMap<Long, Long> lockedOnMap = new HashMap<Long, Long>();
		for (Trace trace : traces) {
			for (Long object : trace.getAcquiredLocks()) {
				lockedOnMap.put(object, trace.getThreadId());
			}
		}
		for (Trace trace : traces) {
			if (lockedOnMap.containsKey(trace.getAcquiringLock())) {
				waitingForMap.put(trace.getThreadId(), lockedOnMap.get(trace.getAcquiringLock()));
			}
		}
		long cycleNode = -1L;
		for (Trace trace : traces) {
			if (visited.contains(trace.getThreadId())) continue;
			long currentNode = trace.getThreadId();
			while (true) {
				if (visited.contains(currentNode)) {
					// detected a cycle.
					cycleNode = currentNode;
					break;
				}
				visited.add(currentNode);
				if (!waitingForMap.containsKey(currentNode)) {
					break;
				}
				currentNode = waitingForMap.get(currentNode);
			}
			if (cycleNode != -1) break;
		}
		// no cycles found
		if (cycleNode == -1) return Collections.emptySet();
		HashSet<Long> cycleNodes = new HashSet<Long>();
		long currentNode = cycleNode;
		while (true) {
			if (cycleNodes.contains(currentNode)) break;
			cycleNodes.add(currentNode);
			currentNode = waitingForMap.get(currentNode);
		}
		
		return cycleNodes;
	}
	
	/**
	 * Used to run erased on concurrent program
	 */
	private EmpiricalTrial analyzeConcurrentTestCase(String buggyPath, String fixPath, boolean isReuse, 
			TestCase tc, ProjectConfig config, boolean requireVisualization, 
			boolean isRunInTestCaseMode, boolean useSliceBreaker, boolean enableRandom, int breakLimit) throws SimulationFailException {
		TraceCollector0 buggyCollector = new BuggyTraceCollector(100);
		TraceCollector0 correctCollector = new TraceCollector0(false);
		long time1 = 0;
		long time2 = 0;

		RunningResult buggyRS = null;
		RunningResult correctRs = null;

		DiffMatcher diffMatcher = null;
		PairList pairList = null;
		
		List<PairList> basePairLists = null;

		int matchTime = -1;
		if (cachedBuggyRS != null && cachedCorrectRS != null && cachedPairList != null && isReuse) {
			buggyRS = cachedBuggyRS;
			correctRs = cachedCorrectRS;

//			System.out.println("start matching trace..., buggy trace length: " + buggyRS.getRunningTrace().size()
//					+ ", correct trace length: " + correctRs.getRunningTrace().size());
//			time1 = System.currentTimeMillis();
//			diffMatcher = new DiffMatcher(config.srcSourceFolder, config.srcTestFolder, buggyPath, fixPath);
//			diffMatcher.matchCode();
//
//			ControlPathBasedTraceMatcher traceMatcher = new ControlPathBasedTraceMatcher();
//			pairList = traceMatcher.matchTraceNodePair(buggyRS.getRunningTrace(), correctRs.getRunningTrace(),
//					diffMatcher);
//			time2 = System.currentTimeMillis();
//			matchTime = (int) (time2 - time1);
//			System.out.println("finish matching trace, taking " + matchTime + "ms");
//			cachedDiffMatcher = diffMatcher;
//			cachedPairList = pairList;

			diffMatcher = cachedDiffMatcher;
			pairList = cachedPairList;
			EmpiricalTrial trial = simulateDebuggingWithCatchedObjects(buggyRS.getRunningTrace(), 
					correctRs.getRunningTrace(), pairList, diffMatcher, requireVisualization,
					useSliceBreaker, enableRandom, breakLimit);
			return trial;
		} else {
			int trialLimit = 10;
			int trialNum = 0;
			boolean isDataFlowComplete = false;
			EmpiricalTrial trial = null;
			List<String> includedClassNames = AnalysisScopePreference.getIncludedLibList();
			List<String> excludedClassNames = AnalysisScopePreference.getExcludedLibList();
			
			while(!isDataFlowComplete && trialNum<trialLimit){
				trialNum++;
				
				Settings.compilationUnitMap.clear();
				Settings.iCompilationUnitMap.clear();
				buggyRS = buggyCollector.run(buggyPath, tc, config, isRunInTestCaseMode, true, includedClassNames, excludedClassNames);
//				buggyRS = buggyCollector.runForceMultithreaded(buggyPath, tc, config, isRunInTestCaseMode, includedClassNames, excludedClassNames);
				if (buggyRS.getRunningType() != NORMAL) {
					trial = EmpiricalTrial.createDumpTrial(getProblemType(buggyRS.getRunningType()));
					trial.setTestcase(tc.testClass + "#" + tc.testMethod);
					return trial;
				}

				Settings.compilationUnitMap.clear();
				Settings.iCompilationUnitMap.clear();
				correctRs = correctCollector.runForceMultithreaded(fixPath, tc, config, isRunInTestCaseMode, includedClassNames, excludedClassNames);
				if (correctRs.getRunningType() != NORMAL) {
					trial = EmpiricalTrial.createDumpTrial(getProblemType(correctRs.getRunningType()));
					trial.setTestcase(tc.toString());
					return trial;
				}

				List<Trace> buggyTraces = buggyRS.getRunningInfo().getTraceList();
				List<Trace> correctTraces = correctRs.getRunningInfo().getTraceList();
				
				AppJavaClassPath buggyAppJavaClassPath = buggyRS.getRunningInfo().getMainTrace().getAppJavaClassPath();
				AppJavaClassPath correctAppJavaClassPath = correctRs.getRunningInfo().getMainTrace().getAppJavaClassPath();
				for (Trace buggyTrace : buggyTraces) {
					if (buggyTrace.getAppJavaClassPath() == null) {
						buggyTrace.setAppJavaClassPath(buggyAppJavaClassPath);
					}
				}
				for (Trace correctTrace : correctTraces) {
					if (correctTrace.getAppJavaClassPath() == null) {
						correctTrace.setAppJavaClassPath(correctAppJavaClassPath);
					}
				}
				boolean isTimeout = buggyRS.getRunningInfo().getProgramMsg().equals(TimeoutThread.TIMEOUT_MSG);
				boolean isCorrectTimeout = correctRs.getRunningInfo().getProgramMsg().equals(TimeoutThread.TIMEOUT_MSG);
				
				Set<Long> deadLockThreads = new HashSet<Long>();
				if (isTimeout) {
					deadLockThreads = hasDeadlock(buggyTraces);
				}
				
				Map<Long, Long> threadIdMap = new HashMap<>();
				if (buggyRS != null && correctRs != null) {
					cachedBuggyRS = buggyRS;
					cachedCorrectRS = correctRs;

					System.out.println("start matching trace..., buggy trace length: " + buggyRS.getRunningTrace().size()
							+ ", correct trace length: " + correctRs.getRunningTrace().size());
					time1 = System.currentTimeMillis();
					diffMatcher = new DiffMatcher(config.srcSourceFolder, config.srcTestFolder, buggyPath, fixPath);
					diffMatcher.matchCode();

					threadIdMap = new ConcurrentTraceMatcher(diffMatcher).matchTraces(buggyTraces, correctTraces);
					ControlPathBasedTraceMatcher traceMatcher = new ControlPathBasedTraceMatcher();
					basePairLists = traceMatcher.matchConcurrentTraceNodePair(buggyTraces, correctTraces, diffMatcher, threadIdMap);
					LinkedList<TraceNodePair> pairs = new LinkedList<>();
					for (PairList pList : basePairLists) {
						pairs.addAll(pList.getPairList());
					}
					pairList = new PairList(pairs);
					
					time2 = System.currentTimeMillis();
					matchTime = (int) (time2 - time1);
					System.out.println("finish matching trace, taking " + matchTime + "ms");
					System.out.println("Finish matching concurrent trace");
					cachedDiffMatcher = diffMatcher;
					cachedPairLists = basePairLists;
					cachedPairList = pairList;
				}
				
				System.out.println("Wrong traces");
				
				for (Trace trace : buggyTraces) {
					if (trace.getInnerThreadId() == null) {
						System.out.println("null");
						continue;
					}
					System.out.println(trace.getInnerThreadId().printRootListNode());
				}
				
				System.out.println("Correct traces");
				for (Trace trace : correctTraces) {
					if (trace.getInnerThreadId() == null) {
						System.out.println("null");
						continue;
					}
					System.out.println(trace.getInnerThreadId().printRootListNode());
				}
				for (Trace trace : buggyTraces) {
					if (trace.getAppJavaClassPath() == null) {
						System.out.println("Null app java path");
						throw new RuntimeException("missing app java path");
					}
				}
				for (Trace trace : correctTraces) {
					if (trace.getAppJavaClassPath() == null) {
						throw new RuntimeException("Missing app java path");
					}
				}
				
				ConcurrentTrace buggyTrace = ConcurrentTrace.fromTimeStampOrder(buggyTraces);
				ConcurrentTrace correctTrace = ConcurrentTrace.fromTimeStampOrder(correctTraces);
				
				
				if (requireVisualization) {
					ConcurrentVisualiser visualizer = 
							new ConcurrentVisualiser(correctTraces, buggyTraces, buggyTrace, correctTrace, pairList, diffMatcher);
					visualizer.visualise();
				}
				
				RootCauseFinder rootcauseFinder = new RootCauseFinder();
				rootcauseFinder.setRootCauseBasedOnDefects4JConc(basePairLists, diffMatcher, buggyTraces, correctTraces);
				
				
				
				
				
				ConcurrentSimulator simulator = new ConcurrentSimulator(useSliceBreaker, enableRandom, breakLimit);
				
				simulator.prepareConc(buggyTraces, correctTraces, pairList, threadIdMap, diffMatcher);
				if (!simulator.isMultiThread()) {
					EmpiricalTrial trial0 = EmpiricalTrial.createDumpTrial("is not multi thread");
					trial0.setTestcase(tc.getName());
					trial0.setBugType(NOT_MULTI_THREAD);
					return trial0;
				}
				if(rootcauseFinder.getRealRootCaseList().isEmpty()) {
					trial = EmpiricalTrial.createDumpTrial("cannot find real root cause");
					StepOperationTuple tuple = new StepOperationTuple(simulator.getObservedFault(), 
							new UserFeedback(UserFeedback.UNCLEAR), simulator.getObservedFault(), DebugState.UNCLEAR);
					trial.getCheckList().add(tuple);
					return trial;
				}
				
				if(simulator.getObservedFault()==null){
					trial = EmpiricalTrial.createDumpTrial("cannot find observable fault");
					return trial;
				}

				rootcauseFinder.checkRootCauseConc(simulator.getObservedFault(), buggyTrace, correctTrace, pairList, diffMatcher);
				TraceNode rootCause = rootcauseFinder.retrieveRootCause(pairList, diffMatcher, buggyTrace, correctTrace);
				
				if(rootCause==null){
					
					System.out.println("[Search Lib Class] Cannot find the root cause, I am searching for library classes...");
					
					List<TraceNode> buggySteps = rootcauseFinder.getStopStepsOnBuggyTrace();
					List<TraceNode> correctSteps = rootcauseFinder.getStopStepsOnCorrectTrace();
					
					List<String> newIncludedClassNames = new ArrayList<>();

					List<TraceNode> convertedBuggySteps = ConcurrentTraceNode.convert(buggySteps);
					List<TraceNode> convertedConcurrentSteps = ConcurrentTraceNode.convert(correctSteps);
					List<TraceNode> convertedRegressionNodes = ConcurrentTraceNode.convert(rootcauseFinder.getRegressionNodeList());
					List<TraceNode> convertedCorrectNodeList = ConcurrentTraceNode.convert(rootcauseFinder.getCorrectNodeList());
					
					
					List<String> newIncludedBuggyClassNames = RegressionUtil.identifyIncludedClassNames(convertedBuggySteps, buggyRS.getPrecheckInfo(), convertedRegressionNodes);
					List<String> newIncludedCorrectClassNames = RegressionUtil.identifyIncludedClassNames(convertedConcurrentSteps, correctRs.getPrecheckInfo(), convertedCorrectNodeList);
					
					newIncludedClassNames.addAll(newIncludedBuggyClassNames);
					newIncludedClassNames.addAll(newIncludedCorrectClassNames);
					boolean includedClassChanged = false;
					for(String name: newIncludedClassNames){
						if(!includedClassNames.contains(name)){
							includedClassNames.add(name);
							includedClassChanged = true;
						}
					}
					
					if(!includedClassChanged) {
						trialNum = trialLimit + 1;
					}
					else {
						continue;						
					}
				}
				
				isDataFlowComplete = true;
				
				System.out.println("start simulating debugging...");
				time1 = System.currentTimeMillis();
				List<EmpiricalTrial> trials0 = simulator.detectMutatedBug(buggyTrace, correctTrace, diffMatcher, 0);
				time2 = System.currentTimeMillis();
				int simulationTime = (int) (time2 - time1);
				System.out.println("finish simulating debugging, taking " + simulationTime / 1000 + "s");
				
				for (EmpiricalTrial t : trials0) {
					t.setTestcase(tc.testClass + "#" + tc.testMethod);
					t.setTraceCollectionTime(buggyTrace.getConstructTime() + correctTrace.getConstructTime());
					t.setTraceMatchTime(matchTime);
					t.setBuggyTrace(buggyTrace);
					t.setFixedTrace(correctTrace);
					t.setPairList(pairList);
					t.setDiffMatcher(diffMatcher);
					t.setDeadLock(deadLockThreads.size() > 0);
					PatternIdentifier identifier = new PatternIdentifier();
					identifier.identifyPattern(t);
				}

				trial = trials0.get(0);
				return trial;
			}

		}

		return null;
		
	}
	
	
	private EmpiricalTrial analyzeTestCase(String buggyPath, String fixPath, boolean isReuse, boolean allowMultiThread, 
			TestCase tc, ProjectConfig config, boolean requireVisualization, 
			boolean isRunInTestCaseMode, boolean useSliceBreaker, boolean enableRandom, int breakLimit) throws SimulationFailException {
		TraceCollector0 buggyCollector = new TraceCollector0(true);
		TraceCollector0 correctCollector = new TraceCollector0(false);
		long time1 = 0;
		long time2 = 0;

		RunningResult buggyRS = null;
		RunningResult correctRs = null;

		DiffMatcher diffMatcher = null;
		PairList pairList = null;

		int matchTime = -1;
		if (cachedBuggyRS != null && cachedCorrectRS != null && cachedPairList != null && isReuse) {
			buggyRS = cachedBuggyRS;
			correctRs = cachedCorrectRS;

//			System.out.println("start matching trace..., buggy trace length: " + buggyRS.getRunningTrace().size()
//					+ ", correct trace length: " + correctRs.getRunningTrace().size());
//			time1 = System.currentTimeMillis();
//			diffMatcher = new DiffMatcher(config.srcSourceFolder, config.srcTestFolder, buggyPath, fixPath);
//			diffMatcher.matchCode();
//
//			ControlPathBasedTraceMatcher traceMatcher = new ControlPathBasedTraceMatcher();
//			pairList = traceMatcher.matchTraceNodePair(buggyRS.getRunningTrace(), correctRs.getRunningTrace(),
//					diffMatcher);
//			time2 = System.currentTimeMillis();
//			matchTime = (int) (time2 - time1);
//			System.out.println("finish matching trace, taking " + matchTime + "ms");
//			cachedDiffMatcher = diffMatcher;
//			cachedPairList = pairList;

			diffMatcher = cachedDiffMatcher;
			pairList = cachedPairList;
			EmpiricalTrial trial = simulateDebuggingWithCatchedObjects(buggyRS.getRunningTrace(), 
					correctRs.getRunningTrace(), pairList, diffMatcher, requireVisualization,
					useSliceBreaker, enableRandom, breakLimit);
			return trial;
		} else {
			int trialLimit = 10;
			int trialNum = 0;
			boolean isDataFlowComplete = false;
			EmpiricalTrial trial = null;
			List<String> includedClassNames = AnalysisScopePreference.getIncludedLibList();
			List<String> excludedClassNames = AnalysisScopePreference.getExcludedLibList();
			
			while(!isDataFlowComplete && trialNum<trialLimit){
				trialNum++;
				
				Settings.compilationUnitMap.clear();
				Settings.iCompilationUnitMap.clear();
				buggyRS = buggyCollector.run(buggyPath, tc, config, isRunInTestCaseMode, 
						allowMultiThread, includedClassNames, excludedClassNames);
				if (buggyRS.getRunningType() != NORMAL) {
					trial = EmpiricalTrial.createDumpTrial(getProblemType(buggyRS.getRunningType()));
					return trial;
				}

				Settings.compilationUnitMap.clear();
				Settings.iCompilationUnitMap.clear();
				correctRs = correctCollector.run(fixPath, tc, config, isRunInTestCaseMode, 
						allowMultiThread, includedClassNames, excludedClassNames);
				if (correctRs.getRunningType() != NORMAL) {
					trial = EmpiricalTrial.createDumpTrial(getProblemType(correctRs.getRunningType()));
					return trial;
				}
				
				if (buggyRS != null && correctRs != null) {
					cachedBuggyRS = buggyRS;
					cachedCorrectRS = correctRs;

					System.out.println("start matching trace..., buggy trace length: " + buggyRS.getRunningTrace().size()
							+ ", correct trace length: " + correctRs.getRunningTrace().size());
					time1 = System.currentTimeMillis();
					diffMatcher = new DiffMatcher(config.srcSourceFolder, config.srcTestFolder, buggyPath, fixPath);
					diffMatcher.matchCode();

					ControlPathBasedTraceMatcher traceMatcher = new ControlPathBasedTraceMatcher();
					pairList = traceMatcher.matchTraceNodePair(buggyRS.getRunningTrace(), correctRs.getRunningTrace(),
							diffMatcher);
					time2 = System.currentTimeMillis();
					matchTime = (int) (time2 - time1);
					System.out.println("finish matching trace, taking " + matchTime + "ms");
					System.out.println("Finish matching concurrent trace");
					cachedDiffMatcher = diffMatcher;
					cachedPairList = pairList;
				}
				
				Trace buggyTrace = buggyRS.getRunningTrace();
				Trace correctTrace = correctRs.getRunningTrace();
				
				if (requireVisualization) {
					Visualizer visualizer = new Visualizer();
					visualizer.visualize(buggyTrace, correctTrace, pairList, diffMatcher);
				}
				
				RootCauseFinder rootcauseFinder = new RootCauseFinder();
				rootcauseFinder.setRootCauseBasedOnDefects4J(pairList, diffMatcher, buggyTrace, correctTrace);
				
				Simulator simulator = new Simulator(useSliceBreaker, enableRandom, breakLimit);
				simulator.prepare(buggyTrace, correctTrace, pairList, diffMatcher);
				if(rootcauseFinder.getRealRootCaseList().isEmpty()) {
					trial = EmpiricalTrial.createDumpTrial("cannot find real root cause");
					StepOperationTuple tuple = new StepOperationTuple(simulator.getObservedFault(), 
							new UserFeedback(UserFeedback.UNCLEAR), simulator.getObservedFault(), DebugState.UNCLEAR);
					trial.getCheckList().add(tuple);
					return trial;
				}
				
				if(simulator.getObservedFault()==null){
					trial = EmpiricalTrial.createDumpTrial("cannot find observable fault");
					return trial;
				}
				
				rootcauseFinder.checkRootCause(simulator.getObservedFault(), buggyTrace, correctTrace, pairList, diffMatcher);
				TraceNode rootCause = rootcauseFinder.retrieveRootCause(pairList, diffMatcher, buggyTrace, correctTrace);
				
				if(rootCause==null){
					
					System.out.println("[Search Lib Class] Cannot find the root cause, I am searching for library classes...");
					
					List<TraceNode> buggySteps = rootcauseFinder.getStopStepsOnBuggyTrace();
					List<TraceNode> correctSteps = rootcauseFinder.getStopStepsOnCorrectTrace();
					
					List<String> newIncludedClassNames = new ArrayList<>();
					List<String> newIncludedBuggyClassNames = RegressionUtil.identifyIncludedClassNames(buggySteps, buggyRS.getPrecheckInfo(), rootcauseFinder.getRegressionNodeList());
					List<String> newIncludedCorrectClassNames = RegressionUtil.identifyIncludedClassNames(correctSteps, correctRs.getPrecheckInfo(), rootcauseFinder.getCorrectNodeList());
					newIncludedClassNames.addAll(newIncludedBuggyClassNames);
					newIncludedClassNames.addAll(newIncludedCorrectClassNames);
					boolean includedClassChanged = false;
					for(String name: newIncludedClassNames){
						if(!includedClassNames.contains(name)){
							includedClassNames.add(name);
							includedClassChanged = true;
						}
					}
					
					if(!includedClassChanged) {
						trialNum = trialLimit + 1;
					}
					else {
						continue;						
					}
				}
				
				isDataFlowComplete = true;
				System.out.println("start simulating debugging...");
				time1 = System.currentTimeMillis();
				List<EmpiricalTrial> trials0 = simulator.detectMutatedBug(buggyTrace, correctTrace, diffMatcher, 0);
				time2 = System.currentTimeMillis();
				int simulationTime = (int) (time2 - time1);
				System.out.println("finish simulating debugging, taking " + simulationTime / 1000 + "s");
				
				for (EmpiricalTrial t : trials0) {
					t.setTestcase(tc.testClass + "#" + tc.testMethod);
					t.setTraceCollectionTime(buggyTrace.getConstructTime() + correctTrace.getConstructTime());
					t.setTraceMatchTime(matchTime);
					t.setBuggyTrace(buggyTrace);
					t.setFixedTrace(correctTrace);
					t.setPairList(pairList);
					t.setDiffMatcher(diffMatcher);
					
					PatternIdentifier identifier = new PatternIdentifier();
					identifier.identifyPattern(t);
				}

				trial = trials0.get(0);
				return trial;
			}

		}

		return null;
	}
	
	private EmpiricalTrial simulateDebuggingWithCatchedObjects(Trace buggyTrace, Trace correctTrace, PairList pairList,
			DiffMatcher diffMatcher, boolean requireVisualization, 
			boolean useSliceBreaker, boolean enableRandom, int breakerLimit) throws SimulationFailException {
		Simulator simulator = new Simulator(useSliceBreaker, enableRandom, breakerLimit);
		simulator.prepare(buggyTrace, correctTrace, pairList, diffMatcher);
		RootCauseFinder rootcauseFinder = new RootCauseFinder();
		rootcauseFinder.setRootCauseBasedOnDefects4J(pairList, diffMatcher, buggyTrace, correctTrace);
		if(rootcauseFinder.getRealRootCaseList().isEmpty()){
			EmpiricalTrial trial = EmpiricalTrial.createDumpTrial("cannot find real root cause");
			StepOperationTuple tuple = new StepOperationTuple(simulator.getObservedFault(), 
					new UserFeedback(UserFeedback.UNCLEAR), simulator.getObservedFault(), DebugState.UNCLEAR);
			trial.getCheckList().add(tuple);
			
			return trial;
		}
		
		if(simulator.getObservedFault()==null){
			EmpiricalTrial trial = EmpiricalTrial.createDumpTrial("cannot find observable fault");
			return trial;
		}
		
		System.out.println("start simulating debugging...");
		long time1 = System.currentTimeMillis();
		List<EmpiricalTrial> trials0 = simulator.detectMutatedBug(buggyTrace, correctTrace, diffMatcher, 0);
		long time2 = System.currentTimeMillis();
		int simulationTime = (int) (time2 - time1);
		System.out.println("finish simulating debugging, taking " + simulationTime / 1000 + "s");
		
		if (requireVisualization) {
			Visualizer visualizer = new Visualizer();
			visualizer.visualize(buggyTrace, correctTrace, pairList, diffMatcher);
		}
		
		for (EmpiricalTrial t : trials0) {
			t.setTraceCollectionTime(buggyTrace.getConstructTime() + correctTrace.getConstructTime());
			t.setBuggyTrace(buggyTrace);
			t.setFixedTrace(correctTrace);
			t.setPairList(pairList);
			t.setDiffMatcher(diffMatcher);
			
			PatternIdentifier identifier = new PatternIdentifier();
			identifier.identifyPattern(t);
		}

		EmpiricalTrial trial = trials0.get(0);
		return trial;
	}

	public class DBRecording implements Runnable{

		EmpiricalTrial trial;
		Trace buggyTrace;
		Trace correctTrace;
		DiffMatcher diffMatcher;
		PairList pairList;
		Defects4jProjectConfig config;
		
		public DBRecording(EmpiricalTrial trial, Trace buggyTrace, Trace correctTrace, DiffMatcher diffMatcher,
				PairList pairList, Defects4jProjectConfig config) {
			super();
			this.trial = trial;
			this.buggyTrace = buggyTrace;
			this.correctTrace = correctTrace;
			this.diffMatcher = diffMatcher;
			this.pairList = pairList;
			this.config = config;
		}



		@Override
		public void run() {
			try {
				new RegressionRecorder().record(trial, buggyTrace, correctTrace, pairList, config.projectName, 
						config.regressionID);
			} catch (SQLException e) {
				e.printStackTrace();
			}	
			
		}
		
	}

	public List<TestCase> retrieveD4jFailingTestCase(String buggyVersionPath) throws IOException {
		return Defects4jProjectConfig.retrieveFailingTestCase(buggyVersionPath);
	}
}
