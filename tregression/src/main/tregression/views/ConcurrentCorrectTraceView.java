package tregression.views;

import java.io.File;

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
import microbat.model.trace.TraceNode;
import tregression.editors.CompareEditor;
import tregression.editors.CompareTextEditorInput;
import tregression.empiricalstudy.RootCauseFinder;
import tregression.model.PairList;
import tregression.model.TraceNodePair;
import tregression.separatesnapshots.DiffMatcher;
import tregression.separatesnapshots.diff.FilePairWithDiff;
import tregression.views.CorrectTraceView.CompareFileName;

public class ConcurrentCorrectTraceView extends ConcurrentTregressionTraceView {
	public static final String ID = "tregression.evalView.concurrentCorrectTraceView";
	
	public ConcurrentCorrectTraceView() {}
	
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
					
					ConcurrentBuggyTraceView buggyTraceView = TregressionViews.getConcBuggyTraceView();
					ClassLocation correspondingLocation = diffMatcher.findCorrespondingLocation(node.getBreakPoint(), true);
					TraceNode otherControlDom = new RootCauseFinder().findControlMendingNodeOnOtherTrace(node, pairList, 
							buggyTraceView.getTrace(), true, correspondingLocation, diffMatcher);
					
					if (otherControlDom != null) {
						buggyTraceView.otherViewsBehavior(otherControlDom);
						buggyTraceView.jumpToNode(buggyTraceView.getTrace(), otherControlDom.getOrder(), refreshProgramState);
					}
					
				}
				
			}
			
			public String getText() {
				return "control mend";
			}
		};
		
		return action;
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
		String fixPath = breakPoint.getFullJavaFilePath();
		String buggyPath = "null";

		FilePairWithDiff fileDiff = getDiffMatcher().findDiffByTargetFile(breakPoint);
		if (getDiffMatcher() == null || fileDiff == null) {
			String fixBase = diffMatcher.getFixPath();
			String content = fixPath.substring(fixBase.length(), fixPath.length());
			buggyPath = diffMatcher.getBuggyPath() + content;	
			
			if(!new File(buggyPath).exists()){
				buggyPath = fixPath;
			}
			
		} else {
			buggyPath = fileDiff.getSourceFile();
		}
		
		CompareFileName cfn = new CompareFileName(buggyPath, fixPath);
		return cfn;
	}

	@Override
	protected void markJavaEditor(TraceNode node) {
		BreakPoint breakPoint = node.getBreakPoint();
		
		CompareFileName cfn = generateCompareFile(breakPoint, getDiffMatcher());

		CompareTextEditorInput input = new CompareTextEditorInput(node, this.pairList, 
				cfn.buggyFileName, cfn.fixFileName, getDiffMatcher());

		openInCompare(input, node);

	}

	@Override
	public void otherViewsBehavior(TraceNode correctNode) {
		if (this.refreshProgramState) {
			ConcurrentStepPropertyView stepPropertyView = TregressionViews.getConcStepPropertyView();
			TraceNodePair pair = pairList.findByAfterNode(correctNode);
			correctNode.toString();
			TraceNode buggyNode = null;
			if(pair != null){
				buggyNode = pair.getBeforeNode();
				if (buggyNode != null) {
					ConcurrentBuggyTraceView buggyTraceView = TregressionViews.getConcBuggyTraceView();
					buggyTraceView.jumpToNode(buggyNode.getTrace(), buggyNode.getOrder(), false);
				}
			}
			
			stepPropertyView.refreshConc(correctNode, buggyNode, diffMatcher, pairList);
		}

		markJavaEditor(correctNode);
	}

	public PairList getPairList() {
		return pairList;
	}


	public DiffMatcher getDiffMatcher() {
		return diffMatcher;
	}
}
