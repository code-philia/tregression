package tregression.model;

import microbat.model.trace.ConcurrentTraceNode;
import microbat.model.trace.TraceNode;
import microbat.model.value.VarValue;
import microbat.recommendation.ChosenVariableOption;
import microbat.recommendation.UserFeedback;

public class StepOperationTuple {
	private TraceNode node;
	private UserFeedback userFeedback;
	
	/**
	 * the corresponding node in correct trace.
	 */
	private TraceNode referenceNode;
	
	/**
	 * the debugging state before the user feedback
	 */
	private int debugState;
	
	public StepOperationTuple(TraceNode node, UserFeedback userFeedback, TraceNode referenceNode, int debugState) {
		super();
		this.node = node;
		this.userFeedback = userFeedback;
		this.referenceNode = referenceNode;
		this.debugState = debugState;
	}
	
	public StepOperationTuple(TraceNode node, UserFeedback userFeedback, TraceNode referenceNode) {
		super();
		this.node = node;
		this.userFeedback = userFeedback;
		this.referenceNode = referenceNode;
	}
	
	public TraceNode getNode() {
		return node;
	}
	public void setNode(TraceNode node) {
		this.node = node;
	}
	public UserFeedback getUserFeedback() {
		return userFeedback;
	}
	public void setUserFeedback(UserFeedback userFeedback) {
		this.userFeedback = userFeedback;
	}

	@Override
	public String toString() {
		StringBuffer buffer = new StringBuffer();
		
		buffer.append(userFeedback.getFeedbackType() + ": ");

		if(node==null){
			return buffer.toString();
		}
		
		int order = node.getOrder();
		int lineNumber = node.getBreakPoint().getLineNumber();
		String threadName = ConcurrentTraceNode.getIniTraceNode(node).getTrace().getThreadName();
		buffer.append("order " + order + ", ");
		buffer.append("threadName: " + threadName + ",");
		buffer.append("line " + lineNumber + ", ");
		
		ChosenVariableOption option = userFeedback.getOption();
		if(option!=null && option.getReadVar()!=null) {
			VarValue var = option.getReadVar();
			buffer.append(var + ", ");
		}
		
		if(getReferenceNode() != null){
			buffer.append("reference node order " + getReferenceNode().getOrder() + " reference thread name " + getReferenceNode().getTrace().getThreadName());
		}
		
		return buffer.toString();
	}

	public TraceNode getReferenceNode() {
		TraceNode refNode = ConcurrentTraceNode.getIniTraceNode(referenceNode);
		return refNode;
	}

	public void setReferenceNode(TraceNode referenceNode) {
		this.referenceNode = referenceNode;
	}

	public int getDebugState() {
		return debugState;
	}

	public void setDebugState(int debugState) {
		this.debugState = debugState;
	}
	
	
}
