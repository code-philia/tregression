package tregression.views;

import java.util.Map;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;

import microbat.model.ClassLocation;
import microbat.model.trace.Trace;
import microbat.model.trace.TraceNode;
import tregression.empiricalstudy.RootCauseFinder;
import tregression.model.TraceNodePair;

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
					
					ConcurrentCorrectTraceView correctTraceView = TregressionViews.getConcCorrectTraceView();
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
	
	@Override
	public void otherViewsBehavior(TraceNode buggyNode) {
		if (this.refreshProgramState) {
			ConcurrentStepPropertyView stepPropertyView = TregressionViews.getConcStepPropertyView();
			TraceNodePair pair = pairList.findByBeforeNode(buggyNode);
			buggyNode.toString();
			TraceNode correctNode = null;
			if(pair != null){
				correctNode = pair.getAfterNode();
				if (correctNode != null) {
					ConcurrentCorrectTraceView concurrentCorrectTraceView = TregressionViews.getConcCorrectTraceView();
					concurrentCorrectTraceView.jumpToNode(correctNode.getTrace(), correctNode.getOrder(), false);
				}
			}
			
			stepPropertyView.refreshConc(correctNode, buggyNode, diffMatcher, pairList);
		}

		markJavaEditor(buggyNode);
	}
	
}
