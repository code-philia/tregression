package tregression.empiricalstudy;

import microbat.model.trace.TraceNode;
import tregression.model.ConcurrentTraceNode;

public class RootCauseNode {

	private TraceNode root;
	private boolean isOnBefore;

	public RootCauseNode(TraceNode root, boolean isOnBefore) {
		super();
		this.root = root;
		this.isOnBefore = isOnBefore;
	}

	public TraceNode getRoot() {
		return root;
	}

	public void setRoot(TraceNode root) {
		this.root = root;
	}

	public boolean isOnBefore() {
		return isOnBefore;
	}

	public void setOnBefore(boolean isOnBefore) {
		this.isOnBefore = isOnBefore;
	}
	
	@Override
	public String toString(){
		int order = root.getOrder();
		if (root instanceof ConcurrentTraceNode) {
			order = ((ConcurrentTraceNode) root).getInitialOrder(); 
		}
		
		StringBuffer buffer = new StringBuffer();
		String trace = isOnBefore?"buggy":"correct";
		buffer.append("On " + trace + " trace, order: ");
		buffer.append(order);
		buffer.append(" On thread: ");
		buffer.append(root.getTrace().getThreadName());
		return buffer.toString();
	}

}
