package tregression.views;

import java.io.File;
import java.util.Map;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;

import microbat.model.BreakPoint;
import microbat.model.ClassLocation;
import microbat.model.trace.Trace;
import microbat.model.trace.TraceNode;
import tregression.editors.CompareEditor;
import tregression.editors.CompareTextEditorInput;
import tregression.empiricalstudy.RootCauseFinder;
import tregression.model.TraceNodePair;
import tregression.separatesnapshots.DiffMatcher;
import tregression.separatesnapshots.diff.FilePairWithDiff;
import tregression.views.BuggyTraceView.CompareFileName;

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
	

	class CompareFileName {
		String buggyFileName;
		String fixFileName;

		public CompareFileName(String buggyFileName, String fixFileName) {
			super();
			this.buggyFileName = buggyFileName;
			this.fixFileName = fixFileName;
		}

	}
	
	private CompareFileName generateCompareFile(BreakPoint breakPoint, DiffMatcher matcher) {
		
		String fixPath = "null";
		String buggyPath = breakPoint.getFullJavaFilePath();
		
		FilePairWithDiff fileDiff = diffMatcher.findDiffBySourceFile(breakPoint);
		if (this.diffMatcher == null || fileDiff == null) {
			String bugBase = diffMatcher.getBuggyPath();
			String content = buggyPath.substring(bugBase.length(), buggyPath.length());
			fixPath = diffMatcher.getFixPath() + content;				
			if(!new File(fixPath).exists()){
				fixPath = buggyPath;
			}
		} else {
			fixPath = fileDiff.getTargetFile();
		}
		
		CompareFileName cfn = new CompareFileName(buggyPath, fixPath);
		return cfn;
	}
	private void openInCompare(CompareTextEditorInput input, TraceNode node) {
		IWorkbench wb = PlatformUI.getWorkbench();
		IWorkbenchWindow win = wb.getActiveWorkbenchWindow();
		IWorkbenchPage workBenchPage = win.getActivePage();

		IEditorPart editPart = workBenchPage.findEditor(input);
		if(editPart != null){
			workBenchPage.activate(editPart);
			CompareEditor editor = (CompareEditor)editPart;
			editor.highLight(node);
		}
		else{
			try {
				workBenchPage.openEditor(input, CompareEditor.ID);
			} catch (PartInitException e) {
				e.printStackTrace();
			}
		}
		
	}
	
	@Override
	protected void markJavaEditor(TraceNode node) {
		BreakPoint breakPoint = node.getBreakPoint();
		
		CompareFileName cfn = generateCompareFile(breakPoint, diffMatcher);

		CompareTextEditorInput input = new CompareTextEditorInput(node, this.pairList, 
				cfn.buggyFileName, cfn.fixFileName, diffMatcher);

		openInCompare(input, node);

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
