package tregression.tracematch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import microbat.algorithm.graphdiff.GraphDiff;
import microbat.algorithm.graphdiff.HierarchyGraphDiffer;
import microbat.model.trace.Trace;
import microbat.model.trace.TraceNode;
import tregression.TraceNodePairReverseOrderComparator;
import tregression.model.PairList;
import tregression.model.TraceNodePair;
import tregression.separatesnapshots.DiffMatcher;

public class ControlPathBasedTraceMatcher{
	
	
	/**
	 * Matches trace nodes using match trace node pair.
	 * If a particular trace doesn't have a map from 1 to another, we assume an
	 * empty trace maps to it.
	 * @param trace1
	 * @param trace2
	 * @param diffMatcher
	 * @param threadIdMap Map from buggy trace id to 
	 * @return
	 */
	public List<PairList> matchConcurrentTraceNodePair(List<Trace> trace1, List<Trace> trace2, DiffMatcher diffMatcher,
			Map<Long, Long> threadIdMap) {
		Map<Long, Trace> trace2Map = new HashMap<>();
		LinkedList<PairList> resultLinkedList = new LinkedList<>();
		for (Trace trace : trace2) {
			trace2Map.put(trace.getThreadId(), trace);
		}
		for (Trace trace: trace1) {
			for (TraceNode traceNode : trace.getExecutionList()) {
				if (traceNode.getBreakPoint().getFullJavaFilePath() == null) {
					throw new RuntimeException("NO full java path");
				}
			}
			if (threadIdMap.containsKey(trace.getThreadId())) {
				Long nextThreadId = threadIdMap.get(trace.getThreadId());
				Objects.requireNonNull(trace2Map.get(nextThreadId));
				resultLinkedList.add(matchTraceNodePair(trace, trace2Map.get(nextThreadId), diffMatcher));
			}
		}
		return resultLinkedList;
	}

	
	public PairList matchTraceNodePair(Trace mutatedTrace, Trace correctTrace, DiffMatcher matcher) {

		IndexTreeNode beforeRoot = initVirtualRootWrapper(mutatedTrace);
		IndexTreeNode afterRoot = initVirtualRootWrapper(correctTrace);

		HierarchyGraphDiffer differ = new HierarchyGraphDiffer();
		differ.diff(beforeRoot, afterRoot, false, new HierarchicalIndexTreeMatcher(matcher), -1);
//		differ.diff(beforeRoot, afterRoot, false, new BucketBasedIndexTreeMatcher(matcher), -1);

		List<GraphDiff> diffList = differ.getDiffs();
		List<TraceNodePair> pList = new ArrayList<>();
		for (GraphDiff diff : diffList) {
			if (diff.getDiffType().equals(GraphDiff.UPDATE)) {
				IndexTreeNode wrapperBefore = (IndexTreeNode) diff.getNodeBefore();
				IndexTreeNode wrapperAfter = (IndexTreeNode) diff.getNodeAfter();

				TraceNodePair pair = new TraceNodePair(wrapperBefore.getTraceNode(), wrapperAfter.getTraceNode());
				pair.setExactSame(false);
				pList.add(pair);
			}
		}

		for (GraphDiff common : differ.getCommons()) {
			IndexTreeNode wrapperBefore = (IndexTreeNode) common.getNodeBefore();
			IndexTreeNode wrapperAfter = (IndexTreeNode) common.getNodeAfter();

			TraceNodePair pair = new TraceNodePair(wrapperBefore.getTraceNode(), wrapperAfter.getTraceNode());
			pair.setExactSame(true);
			pList.add(pair);
		}

		Collections.sort(pList, new TraceNodePairReverseOrderComparator());
		PairList pairList = new PairList(pList);
		return pairList;
	}
	
	private IndexTreeNode initVirtualRootWrapper(Trace trace) {
		TraceNode virtualNode = new TraceNode(null, null, -1, trace, null);
//		List<TraceNode> topList = trace.getTopMethodLevelNodes();
		List<TraceNode> topList = trace.getTopMethodLevelNodes();
		virtualNode.setInvocationChildren(topList);
		
//		Map<TraceNode, IndexTreeNode> linkMap = new TreeMap<>(new Comparator<TraceNode>() {
//
//			@Override
//			public int compare(TraceNode o1, TraceNode o2) {
//				return o1.getOrder()-o2.getOrder();
//			}
//		});

		Map<TraceNode, IndexTreeNode> linkMap = new HashMap<TraceNode, IndexTreeNode>();
		
		IndexTreeNode root = new IndexTreeNode(virtualNode, linkMap);
		
		return root;
	}
}
