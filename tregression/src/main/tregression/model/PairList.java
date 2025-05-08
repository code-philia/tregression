package tregression.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import microbat.model.trace.TraceNode;

public class PairList {
	private List<TraceNodePair> pairList = new ArrayList<>();
	
	// map from before node thread + order -> trace node
	private Map<Long, Map<Integer, TraceNodePair>> beforeNodeToThreadPairMap = new HashMap<>();
	private Map<Long, Map<Integer, TraceNodePair>> afterNodeToThreadPairMap = new HashMap<>();
	
	private Map<Integer, TraceNodePair> assertThreadExists(Map<Long, Map<Integer, TraceNodePair>> map, long threadId) {
		if (map.containsKey(threadId)) {
			return map.get(threadId);
		}
		Map<Integer, TraceNodePair> result = new HashMap<>();
		map.put(threadId, result);
		return result;
	}
	
	public PairList(List<TraceNodePair> pairList) {
		super();
		this.pairList = pairList;
		for(TraceNodePair pair: pairList){
			TraceNode beforeNode = pair.getBeforeNode();
			TraceNode afterNode = pair.getAfterNode();
			
			if(beforeNode!=null){
				Map<Integer, TraceNodePair> beforeNodeToPairMap = 
						assertThreadExists(beforeNodeToThreadPairMap, beforeNode.getTrace().getThreadId());
				beforeNodeToPairMap.put(beforeNode.getOrder(), pair);
			}
			
			if(afterNode!=null){
				Map<Integer, TraceNodePair> afterNodeToPairMap = 
						assertThreadExists(afterNodeToThreadPairMap, afterNode.getTrace().getThreadId());
				afterNodeToPairMap.put(afterNode.getOrder(), pair);
			}
		}
	}

	public List<TraceNodePair> getPairList() {
		return pairList;
	}

	public void setPairList(List<TraceNodePair> pairList) {
		this.pairList = pairList;
	}
	
	public void add(TraceNodePair pair){
		this.pairList.add(pair);
	}

	private TraceNodePair getFromThreadTraceNodePairMap(Map<Long, Map<Integer, TraceNodePair>> map, TraceNode node) {
		return map.getOrDefault(node.getTrace().getThreadId(), new HashMap<>()).get(node.getOrder());
	}
	
	public TraceNodePair findByAfterNode(TraceNode node) {
//		for(TraceNodePair pair: pairList){
//			if(pair.getAfterNode().equals(node)){
//				return pair;
//			}
//		}
		if(node==null){
			return null;
		}
		return getFromThreadTraceNodePairMap(afterNodeToThreadPairMap, node);
	}
	
	public TraceNodePair findByBeforeNode(TraceNode node) {
//		for(TraceNodePair pair: pairList){
//			if(pair.getBeforeNode().equals(node)){
//				return pair;
//			}
//		}
		if(node==null){
			return null;
		}
		return getFromThreadTraceNodePairMap(beforeNodeToThreadPairMap, node);
	}
	
	public int size(){
		return pairList.size();
	}
	
	public boolean isPair(TraceNode node1, TraceNode node2, boolean isNode1Before) {
		if(isNode1Before) {
			return isPair(node1, node2);
		}
		else {
			return isPair(node2, node1);
		}
	}
	
	public boolean isPair(TraceNode beforeNode, TraceNode afterNode) {
		if(beforeNode==null || afterNode==null) {
			return false;
		}
		
		TraceNodePair pair = findByBeforeNode(beforeNode);
		if(pair!=null) {
			TraceNode n = pair.getAfterNode();
			if(n != null) {
				return n.getOrder()==afterNode.getOrder() 
						&& n.getTrace().getThreadId() == afterNode.getTrace().getThreadId();
			}
		}
		
		return false;
	}
}
