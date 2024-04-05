package tregression.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import microbat.instrumentation.model.id.ThreadId;
import microbat.model.BreakPoint;
import microbat.model.trace.Trace;
import sav.common.core.Pair;
import tregression.model.BipGraph;
import tregression.model.MCMF;
import tregression.separatesnapshots.DiffMatcher;

public class ConcurrentTraceMatcher implements TraceMatcher {
	
	private DiffMatcher diffMatcher;
	
	public ConcurrentTraceMatcher(DiffMatcher diffMatcher) {
		this.diffMatcher = diffMatcher;
	}
	
	/**
	 * Matches the traces
	 * @param trace1
	 * @param trace2
	 * @return
	 */
	public Map<Long, Long> matchTraces(List<Trace> trace1, List<Trace> trace2) {
		Map<Long, Long> resultMap = new HashMap<>();
		HashSet<Long> notMatchedT1 = new HashSet<>();
		HashSet<Long> notMatchedT2 = new HashSet<>();
		// trying matching without threadId
		HashMap<ThreadId, Long> traceThreadIdHashMap = new HashMap<ThreadId, Long>();
		for (Trace trace: trace1) {
			notMatchedT1.add(trace.getThreadId());
			if (trace.getInnerThreadId() == null) continue; 
			traceThreadIdHashMap.put(trace.getInnerThreadId(), trace.getThreadId());
		}
		for (Trace trace: trace2) {
//			if (false) {
			if (trace.getInnerThreadId() != null && traceThreadIdHashMap.containsKey(trace.getInnerThreadId())) {
				Long prevTrace = traceThreadIdHashMap.get(trace.getInnerThreadId());
				notMatchedT1.remove(prevTrace);
				resultMap.put(prevTrace, trace.getThreadId());
			} else {
				notMatchedT2.add(trace.getThreadId());
			}
		}
		// early return.
		if (resultMap.size() == Math.min(trace1.size(), trace2.size())) {
			return resultMap;
		}
		List<Trace> filteredTrace1 = trace1.stream().filter(t1 -> notMatchedT1.contains(t1.getThreadId())).toList();
		List<Trace> filteredTrace2 = trace2.stream().filter(t2 -> notMatchedT2.contains(t2.getThreadId())).toList();
		Map<Long, Long> heuristicMatchedMap = heuristicMatcher(filteredTrace1, filteredTrace2);
		resultMap.putAll(heuristicMatchedMap);
		return resultMap;
	}
	
	/**
	 * Constructs the similarity for each trace
	 * use mcm
	 * @param trace1
	 * @param trace2
	 * @return
	 */
	private Map<Long, Long> heuristicMatcher(List<Trace> traces1, List<Trace> traces2) {
		Map<Long, Long> resultMap = new HashMap<>();
		MCMF mcmf = new MCMF();
		int s = traces1.size() + traces2.size() + 2;
		int[][] cap = new int[s][s];
		int[][] cost = new int[s][s];
		initCapCostForMCMF(traces1, traces2, s, cap, cost);
		
		mcmf.getMaxFlow(cap, cost, s-2, s-1);
		List<Pair<Integer, Integer>> matchEdgeList = mcmf.getSTEdgeList(0, traces1.size(), traces1.size(), traces2.size() + traces1.size());
	 	
		ArrayList<Trace> tempTraces1 = new ArrayList<>(traces1);
		ArrayList<Trace> tempTraces2 = new ArrayList<>(traces2);
		for (Pair<Integer, Integer> pair : matchEdgeList) {
			Trace t1 = tempTraces1.get(pair.first());
			Trace t2 = tempTraces2.get(pair.second() - traces1.size());
			resultMap.put(t1.getThreadId(), t2.getThreadId());
		}
		return resultMap;
	}

	/**
	 * Generates the capacity and costs for mcmf
	 * @param traces1
	 * @param traces2
	 * @param s
	 * @param cap
	 * @param cost
	 */
	private void initCapCostForMCMF(List<Trace> traces1, List<Trace> traces2, int s, int[][] cap, int[][] cost) {
		for (int i = 0; i < traces1.size(); ++i) {
			cap[s-2][i] = 1;
		}
		for (int i = traces1.size(); i < traces1.size() + traces2.size(); ++i) {
			cap[i][s-1] = 1;
		}
		int max_trace = 0;
		for (Trace trace: traces1) {
			max_trace = Math.max(trace.size(), max_trace);
		}
		for (Trace trace: traces2) {
			max_trace = Math.max(trace.size(), max_trace);
		}
		
		int i = 0;
		int m_weight = 0;
		for (Trace trace : traces1) {
			int j = traces1.size();
			for (Trace trace2: traces2) {
//				int weight = computeWeight(trace, trace2);
				int weight = computeWeight2(trace, trace2);
				// use this weight to differentiate
				// between matching small to superset and small to equal set
				double score = weight / Math.max(Math.max(trace.size(), trace2.size()), 1);
				int arbitraryMult = max_trace;
				int k_weight = (int) (score * arbitraryMult);
				if (weight > traces1.size() / 2) {
					m_weight = Math.max(weight, k_weight);
					cap[i][j] = 1;
					cost[i][j] = weight;
				}
				j++;
			}
			i++;
		}
		
		for (i = 0; i < traces1.size(); ++i) {
			for (int j = traces1.size(); j < traces1.size() + traces2.size(); ++j) {
				if (cap[i][j] == 0) continue;
				cost[i][j] = m_weight - cost[i][j];
			}
		}
	}
	
	private int computeWeight2(Trace t1, Trace t2) {
		int sameLined = 0;
		int i = 1;
		int j = 1;
		while (i <= t1.size() && j <= t2.size()) {
			BreakPoint bp1 = t1.getTraceNode(j).getBreakPoint();
			BreakPoint bp2 = t2.getTraceNode(j).getBreakPoint();
			if (diffMatcher.isMatch(bp1, bp2)) {
				sameLined++;
				i++; j++;
			} else {
				i++;
			}
		}
		return sameLined;
	}
	
	/**
	 * Should be larger for a larger similarity of the trace.
	 * Compute similarity using bipartite matching
	 * @param t1
	 * @param t2
	 * @return
	 */
	private int computeWeight(Trace t1, Trace t2) {
		BipGraph bipGraph = new BipGraph(t1.size(), t2.size());
		int sameLines = 0;
		for (int i = 1; i <= t1.size(); ++i) {
			BreakPoint bp1 = t1.getTraceNode(i).getBreakPoint();
			for (int j = 1; j <= t2.size(); ++j) {
				BreakPoint bp2 = t2.getTraceNode(j).getBreakPoint();
				if (diffMatcher.isMatch(bp1, bp2)) {
					bipGraph.addEdge(i, j);
				}
			}
		}
		return bipGraph.hopcroftKarp();
	}
}
