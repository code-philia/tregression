package tregression.empiricalstudy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.Stack;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;

import japa.parser.ast.Node;
import microbat.instrumentation.instr.aggreplay.shared.ParseData;
import microbat.model.ConcNode;
import microbat.model.trace.ConcurrentTrace;
import microbat.model.trace.ConcurrentTraceNode;
import microbat.model.trace.Trace;
import microbat.model.trace.TraceNode;
import microbat.model.value.VarValue;
import microbat.recommendation.UserFeedback;
import sav.common.core.Pair;
import tregression.SimulationFailException;
import tregression.StepChangeType;
import tregression.StepChangeTypeChecker;
import tregression.model.PairList;
import tregression.model.StepOperationTuple;
import tregression.model.TraceNodePair;
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

	private List<EmpiricalTrial> startSimulationConc(TraceNode observedFaultNode, ConcurrentTrace buggyTrace, ConcurrentTrace correctTrace,
			PairList pairList, DiffMatcher matcher, RootCauseFinder rootCauseFinder) {
		StepChangeTypeChecker typeChecker = new StepChangeTypeChecker(buggyTrace, correctTrace);
		List<EmpiricalTrial> trials = new ArrayList<>();
		TraceNode currentNode = observedFaultNode;
		EmpiricalTrial trial = workSingleTrialConc(buggyTrace, correctTrace, pairList, matcher, 
				rootCauseFinder, typeChecker, currentNode);
		trials.add(trial);
		
		return trials;
	}
	
	private ConcurrentTrace getTrace(boolean isBuggy, ConcurrentTrace trace1, ConcurrentTrace trace2) {
		return isBuggy ? trace1 : trace2;
	}
	
	public StepOperationTuple fromConcNode(ConcNode concNode, ConcurrentTrace buggyTrace,
			ConcurrentTrace correctTrace, PairList pairList, DiffMatcher diffMatcher, StepChangeTypeChecker typeChecker,
			RootCauseFinder rootCauseFinder) {

		ConcurrentTrace trace = buggyTrace;
		if (!concNode.isBefore1()) {
			trace = correctTrace;
		}
		UserFeedback feedback = null;
		switch (concNode.getChangeType()) {
		case StepChangeType.SRC:
			feedback = new UserFeedback(UserFeedback.UNCLEAR);
			break;
		case StepChangeType.DAT:
			// handle the data 
			feedback = new UserFeedback(UserFeedback.WRONG_VARIABLE_VALUE);
			TraceNode currentNode = ((ConcurrentTraceNode)trace.getTraceNode(concNode.getNode1())).getInitialTraceNode();
			StepChangeType changeType = typeChecker.getType(currentNode, concNode.isBefore1(), pairList, matcher);
			VarValue value = changeType.getWrongVariable(currentNode, concNode.isBefore1(), rootCauseFinder);
			return generateDataFeedback(currentNode, changeType, value);
		case StepChangeType.CTL:
			feedback = new UserFeedback(UserFeedback.WRONG_PATH);
			break;
		case -1:
			feedback = new UserFeedback(UserFeedback.UNCLEAR);
			break;
		}
		return new StepOperationTuple((
				(ConcurrentTraceNode) trace.getTraceNode(concNode.getNode1())).getInitialTraceNode(), 
				feedback, 
				null);
	}
	
	protected EmpiricalTrial workSingleTrialConc(ConcurrentTrace buggyTrace, 
			ConcurrentTrace correctTrace, PairList pairList, DiffMatcher matcher,
			RootCauseFinder rootCauseFinder, StepChangeTypeChecker typeChecker,
			TraceNode currentNode) {
		List<StepOperationTuple> checkingList = new ArrayList<>();
		TraceNode rootcauseNode = rootCauseFinder.retrieveRootCause(pairList, matcher, buggyTrace, correctTrace);
		rootCauseFinder.setRootCauseBasedOnDefects4J(pairList, matcher, buggyTrace, correctTrace);
		long startTime = System.currentTimeMillis();
		
		if (rootcauseNode != null) {
			List<TraceNode> regressionList = new LinkedList<>();
			List<TraceNode> correctNodeList = new LinkedList<>();
			/**
			 * BFS to get to the root cause.
			 */
			List<ConcNode> edgeList = rootCauseFinder.getConcNodes();
			// the edge taken that caused this node to be visited in bfs
			HashMap<Pair<Integer, Boolean>, ConcNode> visitedPrevious = new HashMap<>();
			int target = rootcauseNode.getBound().getOrder();
			// use BFS to perform debugging -> find the path to root cause
			HashMap<Pair<Integer, Boolean>, LinkedList<ConcNode>> adjMatrix = new HashMap<>();
			for (ConcNode concNode : edgeList) {
				if (!adjMatrix.containsKey(concNode.getFirst())) {
					adjMatrix.put(concNode.getFirst(), new LinkedList<>());
				}
				adjMatrix.get(concNode.getFirst()).add(concNode);
			}
			// start bfs from current node to rear
			int startNode = currentNode.getBound().getOrder();
			Queue<Pair<Integer, Boolean>> frontier = new LinkedList<>();
			frontier.add(Pair.of(startNode, true));
			while (!frontier.isEmpty()) {
				Pair<Integer, Boolean> frontPair = frontier.poll();
				// we found the target
				if (frontPair.first() == target && frontPair.second() == true) {
					break;
				}
				if (adjMatrix.get(frontPair) != null) {
					for (ConcNode concNode : adjMatrix.get(frontPair)) {
						Pair<Integer, Boolean> currPair = Pair.of(concNode.getNode2(), concNode.isBefore2());
						if (visitedPrevious.containsKey(currPair)) continue;
						frontier.add(currPair);
						visitedPrevious.put(currPair, concNode);
					}
				}
			}
			LinkedList<ConcNode> edgesTakeNodes = new LinkedList<>();
			Pair<Integer, Boolean> currentPair = Pair.of(target, true);
			Pair<Integer, Boolean> rootPair = Pair.of(startNode, true);
			while (!currentPair.equals(rootPair)) {
				ConcNode edgeConcNode = visitedPrevious.get(currentPair);
				if (edgeConcNode == null)  break;
				edgesTakeNodes.add(edgeConcNode);
				currentPair = Pair.of(edgeConcNode.getNode1(), edgeConcNode.isBefore1());
			}
			Iterator<ConcNode> descIterator = edgesTakeNodes.descendingIterator();
			
			while (descIterator.hasNext()) {
				ConcNode node = descIterator.next();
				checkingList.add(fromConcNode(node, buggyTrace, correctTrace, pairList, matcher, typeChecker, rootCauseFinder));
				ConcurrentTrace trace = getTrace(node.isBefore1(), buggyTrace, correctTrace);
				TraceNode traceNode = trace.getTraceNode(node.getNode1()).getBound().getInitialTraceNode();
				if (node.isBefore1()) {
					regressionList.add(traceNode);
				} else {
					correctNodeList.add(traceNode);
				}
			}
			regressionList.add(rootcauseNode);
			rootCauseFinder.setRegressionNodeList(regressionList);
			rootCauseFinder.setCorrectNodeList(correctNodeList);
			checkingList.add(new StepOperationTuple(
					((ConcurrentTraceNode) buggyTrace.getTraceNode(target)).getInitialTraceNode(), 
					new UserFeedback(UserFeedback.UNCLEAR), null));
			
			long endTime = System.currentTimeMillis();
			EmpiricalTrial trial = new EmpiricalTrial(EmpiricalTrial.FIND_BUG, 0, rootcauseNode, 
					checkingList, -1, -1, (int)(endTime-startTime), buggyTrace.size(), correctTrace.size(),
					rootCauseFinder, isMultiThread);
			return trial;
		}
		
		
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
	
	
	
	@Override
	protected List<DeadEndRecord> createDataRecord(TraceNode currentNode, TraceNode buggyNode,
			StepChangeTypeChecker typeChecker, PairList pairList, DiffMatcher matcher,
			RootCauseFinder rootCauseFinder) {
		// TODO Auto-generated method stub
		List<DeadEndRecord> deadEndlist = new ArrayList<>();
		TraceNodePair pair = pairList.findByBeforeNode(buggyNode);
		TraceNode matchingStep = pair.getAfterNode();
		
		TraceNode domOnRef = null;
		StepChangeType matchingStepType = typeChecker.getType(matchingStep, false, pairList, matcher);
		System.currentTimeMillis();
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
		
		VarValue wrongVarOnBuggyTrace = matchingStepType.getWrongVariable(currentNode, true, rootCauseFinder);
		for(TraceNode breakStep: breakSteps){
			DeadEndRecord record = new DeadEndRecord(DeadEndRecord.DATA, buggyNode.getOrder(), 
					currentNode.getOrder(), -1, breakStep.getOrder());
			record.setVarValue(wrongVarOnBuggyTrace);
			if(!deadEndlist.contains(record)) {
				deadEndlist.add(record);						
			}
		}
		
		return deadEndlist;
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
	@Override
	protected List<TraceNode> findTheNearestCorrespondence(TraceNode domOnRef, PairList pairList, Trace buggyTrace,
			Trace correctTrace) {
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
		if (endOrder >= buggyTrace.size()) {
			endOrder = buggyTrace.size();
		}
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
				previousNode = previousNode.getBound();
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
			finder.checkRootCauseConc(observedFault, (ConcurrentTrace) buggyTrace, (ConcurrentTrace) correctTrace, pairList, matcher);
			long end = System.currentTimeMillis();
			int checkTime = (int) (end-start);

			System.out.println("use slice breaker: " + useSliceBreaker);
			if(useSliceBreaker) {
				trials = startSimulationWithCachedState(observedFault, buggyTrace, correctTrace, getPairList(), matcher, finder);
			}
			else {
				trials = startSimulationConc(observedFault, (ConcurrentTrace) buggyTrace, (ConcurrentTrace) correctTrace, getPairList(), matcher, finder);				
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
