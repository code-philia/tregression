package tregression.views;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.viewers.IStructuredSelection;

import microbat.model.ClassLocation;
import microbat.model.trace.TraceNode;
import tregression.empiricalstudy.RootCauseFinder;

public class ConcurrentBuggyTraceView extends ConcurrentTregressionTraceView {
	public static final String ID = "tregression.evalView.buggyConcTraceView";

	@Override
	protected Action createControlMendingAction() {
		Action action = new Action() {
			public void run() {
				if (listViewer.getSelection().isEmpty()) {
					return;
				}

				if (listViewer.getSelection() instanceof IStructuredSelection) {
					IStructuredSelection selection = (IStructuredSelection) listViewer.getSelection();
					TraceNode node = (TraceNode) selection.getFirstElement();
					
					CorrectTraceView correctTraceView = TregressionViews.getCorrectTraceView();
					ClassLocation correspondingLocation = diffMatcher.findCorrespondingLocation(node.getBreakPoint(), false);
					TraceNode otherControlDom = new RootCauseFinder().findControlMendingNodeOnOtherTrace(node, pairList, 
							correctTraceView.getTrace(), false, correspondingLocation, diffMatcher);
					
					if (otherControlDom != null) {
						correctTraceView.otherViewsBehavior(otherControlDom);
						correctTraceView.jumpToNode(correctTraceView.getTrace(), otherControlDom.getOrder(), refreshProgramState);
					}
					
				}
				
			}
			
			public String getText() {
				return "control mend";
			}
		};
		
		return action;
	}
	
}
