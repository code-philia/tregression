package tregression;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import microbat.model.trace.Trace;
import microbat.model.trace.TraceNode;
import tregression.model.PairList;
import tregression.model.TraceNodePair;

public abstract class Simulator {
	
	protected PairList pairList;
	protected TraceNode observedFaultNode;
	
	public abstract void prepare(Trace mutatedTrace, Trace correctTrace, PairList pairList, Object sourceDiffInfo);
	
	
	public PairList getPairList() {
		return pairList;
	}

	public void setPairList(PairList pairList) {
		this.pairList = pairList;
	}
	
	protected Map<Integer, TraceNode> findAllWrongNodes(PairList pairList, Trace buggyTrace){
		Map<Integer, TraceNode> actualWrongNodes = new HashMap<>();
		for(TraceNode buggyTraceNode: buggyTrace.getExectionList()){
			TraceNodePair foundPair = pairList.findByBeforeNode(buggyTraceNode);
			if(foundPair != null){
				if(!foundPair.isExactSame()){
					TraceNode mutatedNode = foundPair.getBeforeNode();
					actualWrongNodes.put(mutatedNode.getOrder(), mutatedNode);
				}
			}
			else{
				actualWrongNodes.put(buggyTraceNode.getOrder(), buggyTraceNode);
			}
		}
		return actualWrongNodes;
	}
	
	protected TraceNode findObservedFault(List<TraceNode> wrongNodeList, PairList pairList){
		TraceNode exceptionNode = findExceptionNode(wrongNodeList);
		if(exceptionNode!=null) {
			return exceptionNode;
		}
		
		for(TraceNode node: wrongNodeList) {
			if(isObservedFaultWrongPath(node, pairList) && node.getControlDominator()==null) {
				continue;
			}
			else {
				return node;
			}
		}
		
		return null;
	}
	
	private TraceNode findExceptionNode(List<TraceNode> wrongNodeList) {
		for(TraceNode node: wrongNodeList) {
			if(node.isException()) {
				return node;
			}
		}
		return null;
	}


	protected boolean isObservedFaultWrongPath(TraceNode observableNode, PairList pairList){
		TraceNodePair pair = pairList.findByAfterNode(observableNode);
		if(pair == null){
			return true;
		}
		
		if(pair.getBeforeNode() == null){
			return true;
		}
		
		return false;
	}
}
