package tregression.empiricalstudy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

import microbat.model.trace.Trace;
import microbat.model.trace.TraceNode;
import tregression.SimulationFailException;
import tregression.StepChangeTypeChecker;
import tregression.model.ConcurrentTrace;
import tregression.model.PairList;
import tregression.model.StepOperationTuple;
import tregression.separatesnapshots.DiffMatcher;

/**
 * Class containing logic for running erase
 * on concurrent programs.
 */
public class ConcurrentSimulator extends Simulator {

	public ConcurrentSimulator(boolean useSlicerBreaker, boolean enableRandom, int breakerTrialLimit) {
		super(useSlicerBreaker, enableRandom, breakerTrialLimit);
	}

	public void prepareConc(List<Trace> buggyTraces, 
			List<Trace> correctTraces, 
			PairList combinedPairList, 
			Map<Long, Long> threadIdMap,
			DiffMatcher matcher) {
		Map<Long, Trace> correctTraceMap = new HashMap<>();
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
		
		EmpiricalTrial trial = workSingleTrial(buggyTrace, correctTrace, pairList, matcher, 
				rootCauseFinder, typeChecker, currentNode);
		trials.add(trial);
		
		return trials;
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
					if(!rootcauseFind && trial.getRootcauseNode()!=null){
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
