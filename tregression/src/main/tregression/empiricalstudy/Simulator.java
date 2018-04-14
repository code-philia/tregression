package tregression.empiricalstudy;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Stack;

import microbat.model.trace.Trace;
import microbat.model.trace.TraceNode;
import microbat.model.value.VarValue;
import microbat.recommendation.ChosenVariableOption;
import microbat.recommendation.UserFeedback;
import sav.common.core.SavException;
import tregression.SimulationFailException;
import tregression.StepChangeType;
import tregression.StepChangeTypeChecker;
import tregression.empiricalstudy.recommendation.BreakerRecommender;
import tregression.empiricalstudy.training.DED;
import tregression.empiricalstudy.training.TrainingDataTransfer;
import tregression.model.PairList;
import tregression.model.StepOperationTuple;
import tregression.model.TraceNodePair;
import tregression.separatesnapshots.DiffMatcher;

/**
 * This class is for empirical study. I will check (1) whether and when a
 * miss-alignment bug happens and (2) what is the possible fix for that bug.
 * 
 * @author linyun
 *
 */
public class Simulator  {

	protected PairList pairList;
	protected DiffMatcher matcher;
	private TraceNode observedFault;
	
	private boolean useSliceBreaker;
	private int breakerTrialLimit;
	public Simulator(boolean useSlicerBreaker, int breakerTrialLimit){
		this.useSliceBreaker = useSlicerBreaker;
		this.breakerTrialLimit = breakerTrialLimit;
	}
	
	
	public PairList getPairList() {
		return pairList;
	}

	public void setPairList(PairList pairList, DiffMatcher matcher) {
		this.pairList = pairList;
	}
	
	protected TraceNode findObservedFault(TraceNode node, Trace buggyTrace, Trace correctTrace){
		StepChangeTypeChecker checker = new StepChangeTypeChecker(buggyTrace, correctTrace);
		boolean everInvokedByTearDownMethod = previousNodeInvokedByTearDown(node);
		while(node != null) {
			StepChangeType changeType = checker.getType(node, true, pairList, matcher);
			if (everInvokedByTearDownMethod && isInvokedByTearDownMethod(node)) {
				node = node.getStepInPrevious();
				continue;
			}
			else if(everInvokedByTearDownMethod && previousNodeInvokedByTearDown(node)){
				node = node.getStepInPrevious();
				continue;
			}
			else if(changeType.getType()==StepChangeType.CTL) {
				TraceNode cDom = node.getControlDominator();
				if(cDom==null){
					if(node.isException()) {
						return node;
					}	
					else{
						node = node.getStepInPrevious();
						continue;
					}
				}
				
				StepChangeType cDomType = checker.getType(cDom, true, pairList, matcher);
				if(cDomType.getType()==StepChangeType.IDT){
					TraceNode stepOverPrev = node.getStepOverPrevious();
					if(stepOverPrev!=null){
						if(stepOverPrev.equals(cDom) && stepOverPrev.isBranch() && !stepOverPrev.isConditional()){
							node = node.getStepInPrevious();
							continue;
						}
					}
				}
				
				return node;
			}
			else if(changeType.getType()!=StepChangeType.IDT){
				return node;
			}
			
			node = node.getStepInPrevious();
		}
		
		return null;
	}
	
	private boolean previousNodeInvokedByTearDown(TraceNode node) {
		TraceNode prev = node.getStepInPrevious();
		if(prev==null) {
			return false;
		}
		
		boolean isInvoked = isInvokedByTearDownMethod(prev);
		if(isInvoked){
			return true;
		}
		
		while(!isInvoked){
			prev = prev.getStepInPrevious();
			if(prev==null){
				return false;
			}
			
			isInvoked = isInvokedByTearDownMethod(prev);
			if(isInvoked){
				return true;
			}
		}
		
		
		return false;
	}

	private boolean isInvokedByTearDownMethod(TraceNode node) {
		TraceNode n = node;
		while(n!=null) {
			if(n.getMethodSign()!=null && n.getMethodSign().contains("tearDown()V")) {
				return true;
			}
			else {
				n = n.getInvocationParent();
			}
		}
		
		return false;
	}

	protected boolean isObservedFaultWrongPath(TraceNode observableNode, PairList pairList){
		TraceNodePair pair = pairList.findByBeforeNode(observableNode);
		if(pair == null){
			return true;
		}
		
		if(pair.getBeforeNode() == null){
			return true;
		}
		
		return false;
	}
	
//	List<TraceNode> rootCauseNodes;
	public void prepare(Trace buggyTrace, Trace correctTrace, PairList pairList, DiffMatcher matcher) {
		this.pairList = pairList;
		this.matcher = matcher;
		TraceNode initialStep = buggyTrace.getLatestNode();
		observedFault = findObservedFault(initialStep, buggyTrace, correctTrace);
	}

	public List<EmpiricalTrial> detectMutatedBug(Trace buggyTrace, Trace correctTrace, DiffMatcher matcher,
			int optionSearchLimit) throws SimulationFailException {
		if (observedFault != null) {
			RootCauseFinder finder = new RootCauseFinder();
			
			long start = System.currentTimeMillis();
			finder.checkRootCause(observedFault, buggyTrace, correctTrace, pairList, matcher);
			long end = System.currentTimeMillis();
			int checkTime = (int) (end-start);

			List<EmpiricalTrial> trials = null;
			if(useSliceBreaker) {
				trials = startSimulationWithCachedState(observedFault, buggyTrace, correctTrace, getPairList(), matcher, finder);
			}
			else {
				trials = startSimulation(observedFault, buggyTrace, correctTrace, getPairList(), matcher, finder);				
			}
			
			if(trials!=null) {
				for(EmpiricalTrial trial: trials) {
					trial.setSimulationTime(checkTime);
				}
			}
			
			return trials;
		}

		return null;
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
		} 
		
		return trials;
	}
	
	private List<EmpiricalTrial> startSimulation(TraceNode observedFaultNode, Trace buggyTrace, Trace correctTrace,
			PairList pairList, DiffMatcher matcher, RootCauseFinder rootCauseFinder) {

		StepChangeTypeChecker typeChecker = new StepChangeTypeChecker(buggyTrace, correctTrace);
		List<EmpiricalTrial> trials = new ArrayList<>();
		TraceNode currentNode = observedFaultNode;
		
		EmpiricalTrial trial = workSingleTrial(buggyTrace, correctTrace, pairList, matcher, 
				rootCauseFinder, typeChecker, currentNode);
		trials.add(trial);
		
		return trials;
	}
	
	/**
	 * This method returns a debugging trial, and backup all the new debugging state in the input stack.
	 * 
	 * visitedStates records all the backed up debugging state so that we do not repetitively debug the same step with
	 * the same wrong variable twice.
	 * 
	 * stack is used to backup the new debugging state.
	 * 
	 * @param buggyTrace
	 * @param pairList
	 * @param matcher
	 * @param rootCauseFinder
	 * @param typeChecker
	 * @param currentNode
	 * @param stack
	 * @param visitedStates
	 * @param state
	 * @return
	 */
	private EmpiricalTrial workSingleTrial(Trace buggyTrace, Trace correctTrace, PairList pairList, DiffMatcher matcher,
			RootCauseFinder rootCauseFinder, StepChangeTypeChecker typeChecker,
			TraceNode currentNode) {
		
		List<StepOperationTuple> checkingList = new ArrayList<>();
		
		TraceNode rootcauseNode = rootCauseFinder.retrieveRootCause(pairList, matcher, buggyTrace, correctTrace);
		rootCauseFinder.setRootCauseBasedOnDefects4J(pairList, matcher, buggyTrace, correctTrace);
		
		boolean isMultiThread = buggyTrace.isMultiThread() || correctTrace.isMultiThread();
		
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
				
				TraceNode dataDom = buggyTrace.findDataDominator(currentNode, readVar);
				
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
				else{
					return trial;					
				}
			}

		}
		
	}

	/**
	 * This method returns a debugging trial, and backup all the new debugging state in the input stack.
	 * 
	 * visitedStates records all the backed up debugging state so that we do not repetitively debug the same step with
	 * the same wrong variable twice.
	 * 
	 * stack is used to backup the new debugging state.
	 * 
	 * @param buggyTrace
	 * @param pairList
	 * @param matcher
	 * @param rootCauseFinder
	 * @param typeChecker
	 * @param currentNode
	 * @param stack
	 * @param visitedStates
	 * @param state
	 * @return
	 */
	private EmpiricalTrial workSingleTrialWithCachedState(Trace buggyTrace, Trace correctTrace, PairList pairList, DiffMatcher matcher,
			RootCauseFinder rootCauseFinder, StepChangeTypeChecker typeChecker,
			TraceNode currentNode, Stack<DebuggingState> stack, Set<DebuggingState> visitedStates,
			DebuggingState state) {
		/**
		 * recover the debugging state
		 */
		List<StepOperationTuple> checkingList = state.checkingList;
		currentNode = state.currentNode;
		
		TraceNode rootcauseNode = rootCauseFinder.retrieveRootCause(pairList, matcher, buggyTrace, correctTrace);
		rootCauseFinder.setRootCauseBasedOnDefects4J(pairList, matcher, buggyTrace, correctTrace);
		
		boolean isMultiThread = buggyTrace.isMultiThread() || correctTrace.isMultiThread();
		
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
				
				TraceNode dataDom = buggyTrace.findDataDominator(currentNode, readVar);
				
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
				
				List<DeadEndRecord> list = null;
				if(previousNode!=null){
					StepChangeType prevChangeType = typeChecker.getType(previousNode, true, pairList, matcher);
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
				
				List<TraceNode> sliceBreakers = findBreaker(list, breakerTrialLimit, buggyTrace, rootCauseFinder);
				if(!sliceBreakers.isEmpty()){
					if(includeRootCause(sliceBreakers, rootCauseFinder, buggyTrace, correctTrace)){
						trial.setBreakSlice(true);
						return trial;	
					}
					else{
						currentNode = sliceBreakers.get(0);
						for(int i=1; i<sliceBreakers.size(); i++){
							backupDebuggingState(sliceBreakers.get(i), stack, visitedStates, checkingList, null);							
						}
					}
				}
				else{
					return trial;					
				}
			}
		}
		
	}
	
	private boolean includeRootCause(List<TraceNode> sliceBreakers, RootCauseFinder rootCauseFinder, 
			Trace buggyTrace, Trace correctTrace) {
		List<TraceNode> roots = rootCauseFinder.retrieveAllRootCause(pairList, matcher, buggyTrace, correctTrace);
		for(TraceNode breaker: sliceBreakers){
			for(TraceNode root: roots){
				if(breaker.getBreakPoint().equals(root.getBreakPoint())){
					return true;
				}
			}
		}
		
		return false;
	}


	private List<TraceNode> findBreaker(List<DeadEndRecord> list, int breakerTrialLimit, 
			Trace buggyTrace, RootCauseFinder rootCauseFinder) {
		for(DeadEndRecord record: list){
			DED ded = new TrainingDataTransfer().transfer(record, buggyTrace);
			try {
				List<TraceNode> breakerCandidates = new BreakerRecommender().
						recommend(ded.getAllData(), buggyTrace, breakerTrialLimit); 
				return breakerCandidates;
			} catch (SavException | IOException e) {
				e.printStackTrace();
			} 
		}
		
		return new ArrayList<>();
	}


	private TraceNode findLatestControlDifferent(TraceNode currentNode, TraceNode controlDom, 
			StepChangeTypeChecker checker, PairList pairList, DiffMatcher matcher) {
		TraceNode n = currentNode.getStepInPrevious();
		StepChangeType t = checker.getType(n, true, pairList, matcher);
		while((t.getType()==StepChangeType.CTL || t.getType()==StepChangeType.SRC) && n.getOrder()>controlDom.getOrder()){
			TraceNode dom = n.getInvocationMethodOrDominator();
			if(dom.getMethodSign().equals(n.getMethodSign())){
				return n;
			}
			
			n = n.getStepInPrevious();
			t = checker.getType(n, true, pairList, matcher);
		}
		return controlDom;
	}

	private List<DeadEndRecord> createControlRecord(TraceNode currentNode, TraceNode latestBugNode, StepChangeTypeChecker typeChecker,
			PairList pairList, DiffMatcher matcher) {
		List<DeadEndRecord> deadEndRecords = new ArrayList<>();
		
		Trace trace = currentNode.getTrace();
		for(int i=currentNode.getOrder()+1; i<=latestBugNode.getOrder(); i++){
			TraceNode node = trace.getTraceNode(i);
			StepChangeType changeType = typeChecker.getType(node, true, pairList, matcher);
			if(changeType.getType()==StepChangeType.CTL){
				DeadEndRecord record = new DeadEndRecord(DeadEndRecord.CONTROL, 
						latestBugNode.getOrder(), currentNode.getOrder(), -1, node.getOrder());
				deadEndRecords.add(record);
				
				TraceNode equivalentNode = node.getStepOverNext();
				while(equivalentNode!=null && equivalentNode.getBreakPoint().equals(node.getBreakPoint())){
					DeadEndRecord addRecord = new DeadEndRecord(DeadEndRecord.CONTROL, 
							latestBugNode.getOrder(), currentNode.getOrder(), -1, equivalentNode.getOrder());
					deadEndRecords.add(addRecord);
					equivalentNode = equivalentNode.getStepOverNext();
				}
				
				break;
			}
		}
		
		return deadEndRecords;
	}

	private List<TraceNode> findTheNearestCorrespondence(TraceNode domOnRef, PairList pairList, Trace buggyTrace, Trace correctTrace) {
		List<TraceNode> list = new ArrayList<>();
		
		List<TraceNode> sameLineSteps = findSameLineSteps(domOnRef);
		for(TraceNode sameLineStep: sameLineSteps){
			TraceNodePair pair = pairList.findByAfterNode(sameLineStep);
			if(pair!=null){
				TraceNode beforeNode = pair.getBeforeNode();
				if(beforeNode!=null){
					list.add(beforeNode);
				}
			}
		}
		if(!list.isEmpty()){
			return list;
		}
		
		int endOrder = new RootCauseFinder().findEndOrderInOtherTrace(domOnRef, pairList, false, correctTrace);
		TraceNode startNode = buggyTrace.getTraceNode(endOrder);
		list.add(startNode);
		while(startNode.getStepOverPrevious()!=null && 
				startNode.getStepOverPrevious().getBreakPoint().equals(startNode.getBreakPoint())){
			startNode = startNode.getStepOverPrevious();
			list.add(startNode);
		}
		
//		TraceNode end = buggyTrace.getTraceNode(endOrder);
//		TraceNode n = end.getStepOverNext();
//		while(n!=null && (n.getLineNumber()==end.getLineNumber())){
//			list.add(n);
//			n = n.getStepOverNext();
//		}
		
		return list;
	}
	
	private List<TraceNode> findSameLineSteps(TraceNode domOnRef) {
		List<TraceNode> list = new ArrayList<>();
		list.add(domOnRef);
		
		TraceNode node = domOnRef.getStepOverPrevious();
		while(node!=null && node.getLineNumber()==domOnRef.getLineNumber()){
			list.add(node);
			node = node.getStepOverPrevious();
		}
		
		node = domOnRef.getStepOverNext();
		while(node!=null && node.getLineNumber()==domOnRef.getLineNumber()){
			list.add(node);
			node = node.getStepOverNext();
		}
		
		return list;
	}

	private List<DeadEndRecord> createDataRecord(TraceNode currentNode, TraceNode buggyNode,
			StepChangeTypeChecker typeChecker, PairList pairList, DiffMatcher matcher, RootCauseFinder rootCauseFinder) {
		
		List<DeadEndRecord> deadEndlist = new ArrayList<>();
		TraceNodePair pair = pairList.findByBeforeNode(buggyNode);
		TraceNode matchingStep = pair.getAfterNode();
		
		TraceNode domOnRef = null;
		StepChangeType matchingStepType = typeChecker.getType(matchingStep, false, pairList, matcher);
		if(matchingStepType.getWrongVariableList()==null) {
			return deadEndlist;
		}
		
		VarValue wrongVar = matchingStepType.getWrongVariable(currentNode, false, rootCauseFinder);
		domOnRef = matchingStep.getDataDominator(wrongVar);
		
		List<TraceNode> breakSteps = new ArrayList<>();
		 
		while(domOnRef != null){
			StepChangeType changeType = typeChecker.getType(domOnRef, false, pairList, matcher);
			if(changeType.getType()==StepChangeType.SRC){
				breakSteps = findTheNearestCorrespondence(domOnRef, pairList, buggyNode.getTrace(), matchingStep.getTrace());
				break;
			}
			else{
				TraceNodePair conPair = pairList.findByAfterNode(domOnRef);
				if(conPair != null && conPair.getBeforeNode() != null){
					/**
					 * if we find a matched step on buggy trace, then we find the first incorrect step starting at the matched
					 * step as the break step.
					 */
					TraceNode matchingPoint = conPair.getBeforeNode();
					for(int order=matchingPoint.getOrder(); order<=matchingPoint.getTrace().size(); order++){
						TraceNode potentialPoint = matchingPoint.getTrace().getTraceNode(order);
						StepChangeType ct = typeChecker.getType(potentialPoint, true, pairList, matcher);
						if(ct.getType()!=StepChangeType.IDT){
							breakSteps.add(potentialPoint);
							break;
						}
					}
					
					break;
				}
				else{
					domOnRef = domOnRef.getInvocationMethodOrDominator();
				}
			}
		}
		
		for(TraceNode breakStep: breakSteps){
			DeadEndRecord record = new DeadEndRecord(DeadEndRecord.DATA, buggyNode.getOrder(), 
					currentNode.getOrder(), -1, breakStep.getOrder());
			record.setVarValue(wrongVar);
			if(!deadEndlist.contains(record)) {
				deadEndlist.add(record);						
			}
		}
		
		return deadEndlist;
	}
	
	
	private void backupDebuggingState(TraceNode currentNode, Stack<DebuggingState> stack,
			Set<DebuggingState> visitedStates, List<StepOperationTuple> checkingList, VarValue readVar) {
		List<StepOperationTuple> clonedCheckingList = cloneList(checkingList);
		DebuggingState backupState = new DebuggingState(currentNode, clonedCheckingList, readVar);
		if(!visitedStates.contains(backupState)) {
			stack.push(backupState);
			visitedStates.add(backupState);
		}
	}

	private List<StepOperationTuple> cloneList(List<StepOperationTuple> checkingList) {
		List<StepOperationTuple> list = new ArrayList<>();
		for(StepOperationTuple t: checkingList) {
			list.add(t);
		}
		return list;
	}

	private StepOperationTuple generateDataFeedback(TraceNode currentNode, StepChangeType changeType,
			VarValue readVar) {
		UserFeedback feedback = new UserFeedback(UserFeedback.WRONG_VARIABLE_VALUE);
		ChosenVariableOption option = new ChosenVariableOption(readVar, null);
		feedback.setOption(option);
		StepOperationTuple operation = new StepOperationTuple(currentNode, feedback, changeType.getMatchingStep());
		return operation;
	}

	private int checkOverskipLength(PairList pairList, DiffMatcher matcher, Trace buggyTrace, TraceNode rootcauseNode,
			 List<StepOperationTuple> checkingList) {
		TraceNode latestNode = checkingList.get(checkingList.size() - 1).getNode();

		if (rootcauseNode != null) {
			return rootcauseNode.getOrder() - latestNode.getOrder();
		}

		return 0;
	}

	public TraceNode getObservedFault() {
		return observedFault;
	}

	public void setObservedFault(TraceNode observedFault) {
		this.observedFault = observedFault;
	}

}
