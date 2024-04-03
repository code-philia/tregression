package tregression.empiricalstudy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

import microbat.instrumentation.instr.aggreplay.shared.ParseData;
import microbat.model.trace.ConcurrentTrace;
import microbat.model.trace.Trace;
import microbat.model.trace.TraceNode;
import microbat.model.value.VarValue;
import microbat.recommendation.UserFeedback;
import tregression.SimulationFailException;
import tregression.StepChangeType;
import tregression.StepChangeTypeChecker;
import tregression.model.PairList;
import tregression.model.StepOperationTuple;
import tregression.separatesnapshots.DiffMatcher;

/**
 * Class containing logic for simulating debugging in concurrent programs
 */
public class ConcurrentSimulator extends Simulator {

	boolean isMultiThread = false;
	public ConcurrentSimulator(boolean useSlicerBreaker, boolean enableRandom, int breakerTrialLimit) {
		super(useSlicerBreaker, enableRandom, breakerTrialLimit);
	}
	
	public boolean isMultiThread() {
		return this.isMultiThread;
	}

	public void prepareConc(List<Trace> buggyTraces, 
			List<Trace> correctTraces, 
			PairList combinedPairList,
			Map<Long, Long> threadIdMap,
			DiffMatcher matcher) {
		Map<Long, Trace> correctTraceMap = new HashMap<>();
		this.isMultiThread = correctTraces.size() > 1 || buggyTraces.size() > 1;
		for (Trace trace : correctTraces) {
			correctTraceMap.put(trace.getThreadId(), trace);
		}
		for (Trace trace : buggyTraces) {
			if (!threadIdMap.containsKey(trace.getThreadId())) {
				continue;
			}
			Trace correctTrace = correctTraceMap.get(threadIdMap.get(trace.getThreadId()));
			this.prepare(trace, correctTrace, combinedPairList, matcher);
		}
	}

	private List<EmpiricalTrial> startSimulationConc(TraceNode observedFaultNode, Trace buggyTrace, Trace correctTrace,
			PairList pairList, DiffMatcher matcher, RootCauseFinder rootCauseFinder) {
		StepChangeTypeChecker typeChecker = new StepChangeTypeChecker(buggyTrace, correctTrace);
		List<EmpiricalTrial> trials = new ArrayList<>();
		TraceNode currentNode = observedFaultNode;
		EmpiricalTrial trial = workSingleTrialConc(buggyTrace, correctTrace, pairList, matcher, 
				rootCauseFinder, typeChecker, currentNode);
		trials.add(trial);
		
		return trials;
	}
	
	protected EmpiricalTrial workSingleTrialConc(Trace buggyTrace, Trace correctTrace, PairList pairList, DiffMatcher matcher,
			RootCauseFinder rootCauseFinder, StepChangeTypeChecker typeChecker,
			TraceNode currentNode) {
		List<StepOperationTuple> checkingList = new ArrayList<>();
		TraceNode rootcauseNode = rootCauseFinder.retrieveRootCause(pairList, matcher, buggyTrace, correctTrace);
		rootCauseFinder.setRootCauseBasedOnDefects4J(pairList, matcher, buggyTrace, correctTrace);
		
		long startTime = System.currentTimeMillis();
		
		/**
		 * start debugging
		 */
		while (true) {
			TraceNode previousNode = null;
			if(!checkingList.isEmpty()){
				StepOperationTuple lastTuple = checkingList.get(checkingList.size()-1);
				previousNode = lastTuple.getNode();
			}
			
			if(currentNode==null || (previousNode!=null && currentNode.getOrder()==previousNode.getOrder())){
				long endTime = System.currentTimeMillis();
				EmpiricalTrial trial = new EmpiricalTrial(EmpiricalTrial.OVER_SKIP, -1, rootcauseNode, 
						checkingList, -1, -1, (int)(endTime-startTime), buggyTrace.size(), correctTrace.size(),
						rootCauseFinder, isMultiThread);
				return trial;
			}
			
			StepChangeType changeType = typeChecker.getType(currentNode, true, pairList, matcher);

			if (changeType.getType() == StepChangeType.SRC) {
				StepOperationTuple operation = new StepOperationTuple(currentNode,
						new UserFeedback(UserFeedback.UNCLEAR), null);
				checkingList.add(operation);
				
				long endTime = System.currentTimeMillis();
				EmpiricalTrial trial = new EmpiricalTrial(EmpiricalTrial.FIND_BUG, 0, rootcauseNode, 
						checkingList, -1, -1, (int)(endTime-startTime), buggyTrace.size(), correctTrace.size(),
						rootCauseFinder, isMultiThread);
				return trial;
			} else if (changeType.getType() == StepChangeType.DAT) {
				VarValue readVar = changeType.getWrongVariable(currentNode, true, rootCauseFinder);
				StepOperationTuple operation = generateDataFeedback(currentNode, changeType, readVar);
				checkingList.add(operation);
				
				TraceNode dataDom = buggyTrace.findDataDependency(currentNode, readVar);
				
				currentNode = dataDom;
			} else if (changeType.getType() == StepChangeType.CTL) {
				TraceNode controlDom = null;
				if(currentNode.insideException()){
					controlDom = currentNode.getStepInPrevious();
				}
				else{
					controlDom = currentNode.getInvocationMethodOrDominator();
					//indicate the control flow is caused by try-catch
					if(controlDom!=null && !controlDom.isConditional() && controlDom.isBranch()
							&& !controlDom.equals(currentNode.getInvocationParent())){
						StepChangeType t = typeChecker.getType(controlDom, true, pairList, matcher);
						if(t.getType()==StepChangeType.IDT){
							controlDom = findLatestControlDifferent(currentNode, controlDom, 
									typeChecker, pairList, matcher);
						}
					}
					
					if(controlDom==null) {
						controlDom = currentNode.getStepInPrevious();
					}	
				}

				StepOperationTuple operation = new StepOperationTuple(currentNode,
						new UserFeedback(UserFeedback.WRONG_PATH), null);
				checkingList.add(operation);
				
				currentNode = controlDom;
			}
			/**
			 * when it is a correct node
			 */
			else {
				StepOperationTuple operation = new StepOperationTuple(currentNode,
						new UserFeedback(UserFeedback.CORRECT), null);
				checkingList.add(operation);
				
				if(currentNode.isException()){
					currentNode = currentNode.getStepInPrevious();
					continue;
				}

				int overskipLen = checkOverskipLength(pairList, matcher, buggyTrace, rootcauseNode, checkingList);
				if(overskipLen<0 && checkingList.size()>=2){
					int size = checkingList.size();
					if(checkingList.get(size-2).getUserFeedback().getFeedbackType().equals(UserFeedback.WRONG_PATH)){
						overskipLen = 1;
					}
				}

				long endTime = System.currentTimeMillis();
				EmpiricalTrial trial = new EmpiricalTrial(EmpiricalTrial.OVER_SKIP, overskipLen, rootcauseNode, 
						checkingList, -1, -1, (int)(endTime-startTime), buggyTrace.size(), correctTrace.size(),
						rootCauseFinder, isMultiThread);
				
				if(previousNode!=null){
					StepChangeType prevChangeType = typeChecker.getType(previousNode, true, pairList, matcher);
					List<DeadEndRecord> list = null;
					if(prevChangeType.getType()==StepChangeType.CTL){
						list = createControlRecord(currentNode, previousNode, typeChecker, pairList, matcher);
						trial.setDeadEndRecordList(list);
					}
					else if(prevChangeType.getType()==StepChangeType.DAT){
						list = createDataRecord(currentNode, previousNode, typeChecker, pairList, matcher, rootCauseFinder);
						trial.setDeadEndRecordList(list);
					}
					
					if(trial.getBugType()==EmpiricalTrial.OVER_SKIP && trial.getOverskipLength()==0){
						if(list != null && !list.isEmpty()){
							DeadEndRecord record = list.get(0);
							int len = currentNode.getOrder() - record.getBreakStepOrder();
							trial.setOverskipLength(len);
						}
					}
				}
				
				return trial;	
			}

		}
		
	}
	
	protected EmpiricalTrial workSingleTrial(Trace buggyTrace, Trace correctTrace, PairList pairList, DiffMatcher matcher,
			RootCauseFinder rootCauseFinder, StepChangeTypeChecker typeChecker,
			TraceNode currentNode) {
		
		List<StepOperationTuple> checkingList = new ArrayList<>();
		
		TraceNode rootcauseNode = rootCauseFinder.retrieveRootCause(pairList, matcher, buggyTrace, correctTrace);
		rootCauseFinder.setRootCauseBasedOnDefects4J(pairList, matcher, buggyTrace, correctTrace);
		
		boolean isMultiThread = false;
		
		long startTime = System.currentTimeMillis();
		
		/**
		 * start debugging
		 */
		while (true) {
			TraceNode previousNode = null;
			if(!checkingList.isEmpty()){
				StepOperationTuple lastTuple = checkingList.get(checkingList.size()-1);
				previousNode = lastTuple.getNode();
			}
			
			if(currentNode==null || (previousNode!=null && currentNode.getOrder()==previousNode.getOrder())){
				long endTime = System.currentTimeMillis();
				EmpiricalTrial trial = new EmpiricalTrial(EmpiricalTrial.OVER_SKIP, -1, rootcauseNode, 
						checkingList, -1, -1, (int)(endTime-startTime), buggyTrace.size(), correctTrace.size(),
						rootCauseFinder, isMultiThread);
				return trial;
			}
			
			StepChangeType changeType = typeChecker.getType(currentNode, true, pairList, matcher);

			if (changeType.getType() == StepChangeType.SRC) {
				StepOperationTuple operation = new StepOperationTuple(currentNode,
						new UserFeedback(UserFeedback.UNCLEAR), null);
				checkingList.add(operation);
				
				long endTime = System.currentTimeMillis();
				EmpiricalTrial trial = new EmpiricalTrial(EmpiricalTrial.FIND_BUG, 0, rootcauseNode, 
						checkingList, -1, -1, (int)(endTime-startTime), buggyTrace.size(), correctTrace.size(),
						rootCauseFinder, isMultiThread);
				return trial;
			} else if (changeType.getType() == StepChangeType.DAT) {
				VarValue readVar = changeType.getWrongVariable(currentNode, true, rootCauseFinder);
				StepOperationTuple operation = generateDataFeedback(currentNode, changeType, readVar);
				checkingList.add(operation);
				
				TraceNode dataDom = buggyTrace.findDataDependency(currentNode, readVar);
				
				currentNode = dataDom;
			} else if (changeType.getType() == StepChangeType.CTL) {
				TraceNode controlDom = null;
				if(currentNode.insideException()){
					controlDom = currentNode.getStepInPrevious();
				}
				else{
					controlDom = currentNode.getInvocationMethodOrDominator();
					//indicate the control flow is caused by try-catch
					if(controlDom!=null && !controlDom.isConditional() && controlDom.isBranch()
							&& !controlDom.equals(currentNode.getInvocationParent())){
						StepChangeType t = typeChecker.getType(controlDom, true, pairList, matcher);
						if(t.getType()==StepChangeType.IDT){
							controlDom = findLatestControlDifferent(currentNode, controlDom, 
									typeChecker, pairList, matcher);
						}
					}
					
					if(controlDom==null) {
						controlDom = currentNode.getStepInPrevious();
					}	
				}

				StepOperationTuple operation = new StepOperationTuple(currentNode,
						new UserFeedback(UserFeedback.WRONG_PATH), null);
				checkingList.add(operation);
				
				currentNode = controlDom;
			}
			/**
			 * when it is a correct node
			 */
			else {
				StepOperationTuple operation = new StepOperationTuple(currentNode,
						new UserFeedback(UserFeedback.CORRECT), null);
				checkingList.add(operation);
				
				if(currentNode.isException()){
					currentNode = currentNode.getStepInPrevious();
					continue;
				}

				int overskipLen = checkOverskipLength(pairList, matcher, buggyTrace, rootcauseNode, checkingList);
				if(overskipLen<0 && checkingList.size()>=2){
					int size = checkingList.size();
					if(checkingList.get(size-2).getUserFeedback().getFeedbackType().equals(UserFeedback.WRONG_PATH)){
						overskipLen = 1;
					}
				}

				long endTime = System.currentTimeMillis();
				EmpiricalTrial trial = new EmpiricalTrial(EmpiricalTrial.OVER_SKIP, overskipLen, rootcauseNode, 
						checkingList, -1, -1, (int)(endTime-startTime), buggyTrace.size(), correctTrace.size(),
						rootCauseFinder, isMultiThread);
				
				if(previousNode!=null){
					StepChangeType prevChangeType = typeChecker.getType(previousNode, true, pairList, matcher);
					List<DeadEndRecord> list = null;
					if(prevChangeType.getType()==StepChangeType.CTL){
						list = createControlRecord(currentNode, previousNode, typeChecker, pairList, matcher);
						trial.setDeadEndRecordList(list);
					}
					else if(prevChangeType.getType()==StepChangeType.DAT){
						list = createDataRecord(currentNode, previousNode, typeChecker, pairList, matcher, rootCauseFinder);
						trial.setDeadEndRecordList(list);
					}
					
					if(trial.getBugType()==EmpiricalTrial.OVER_SKIP && trial.getOverskipLength()==0){
						if(list != null && !list.isEmpty()){
							DeadEndRecord record = list.get(0);
							int len = currentNode.getOrder() - record.getBreakStepOrder();
							trial.setOverskipLength(len);
						}
					}
				}
				
				return trial;	
			}

		}
		
	}

	private List<EmpiricalTrial> startSimulationWithCachedState(TraceNode observedFaultNode, Trace buggyTrace, Trace correctTrace,
			PairList pairList, DiffMatcher matcher, RootCauseFinder rootCauseFinder) {
		StepChangeTypeChecker typeChecker = new StepChangeTypeChecker(buggyTrace, correctTrace);
		List<EmpiricalTrial> trials = new ArrayList<>();
		TraceNode currentNode = observedFaultNode;
		
		Stack<DebuggingState> stack = new Stack<>();
		stack.push(new DebuggingState(currentNode, new ArrayList<StepOperationTuple>(), null));
		Set<DebuggingState> visitedStates = new HashSet<>();
		
		while (!stack.isEmpty()){
			DebuggingState state = stack.pop();
			
			EmpiricalTrial trial = workSingleTrialWithCachedState(buggyTrace, correctTrace, pairList, matcher, 
					rootCauseFinder, typeChecker, currentNode, stack, visitedStates, state);
			trials.add(trial);
			
			if(trial.isBreakSlice()){
				break;
			}
		} 
		
		return trials;
	}

	
	/**
	 * Method to perform ERASE without relying on merging the trace into a single trace list
	 * 
	 * @param buggyTrace
	 * @param correctTrace
	 * @param matcher
	 * @param optionSearchLimit
	 * @return
	 * @throws SimulationFailException
	 */
	public List<EmpiricalTrial> detectMutatedBugAlt(List<Trace> buggyTrace, List<Trace> correctTrace, 
			DiffMatcher matcher,
			PairList overallPairList,
			Map<Long, Long> threadIdMap) throws SimulationFailException {
		List<EmpiricalTrial> trials = null;
		return null;
	}
	

	public List<EmpiricalTrial> detectMutatedBug(Trace buggyTrace, Trace correctTrace, DiffMatcher matcher,
			int optionSearchLimit) throws SimulationFailException {
		List<EmpiricalTrial> trials = null;
		for (TraceNode observedFault: observedFaultList) {
			RootCauseFinder finder = new RootCauseFinder();
			
			long start = System.currentTimeMillis();
			finder.checkRootCause(observedFault, buggyTrace, correctTrace, pairList, matcher);
			long end = System.currentTimeMillis();
			int checkTime = (int) (end-start);

			System.out.println("use slice breaker: " + useSliceBreaker);
			if(useSliceBreaker) {
				trials = startSimulationWithCachedState(observedFault, buggyTrace, correctTrace, getPairList(), matcher, finder);
			}
			else {
				trials = startSimulationConc(observedFault, buggyTrace, correctTrace, getPairList(), matcher, finder);				
			}
			
			if(trials!=null) {
				boolean rootcauseFind = false;
				for(EmpiricalTrial trial: trials) {
					if(!rootcauseFind && trial.getRootcauseNode()!=null) {
						rootcauseFind = true;
					}
					trial.setSimulationTime(checkTime);
				}
				
				if(rootcauseFind){
					observedFaultList.clear();
					observedFaultList.add(observedFault);
					return trials;
				}
			}
		}

		return trials;
	}
	
	
	
	
}
