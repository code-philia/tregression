package tregression.views;

import java.util.Stack;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IMenuListener;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.IActionBars;

import microbat.Activator;
import microbat.model.trace.TraceNode;
import microbat.views.ConcurrentTraceView;
import microbat.views.ImageUI;
import tregression.model.PairList;
import tregression.separatesnapshots.DiffMatcher;

public abstract class ConcurrentTregressionTraceView extends ConcurrentTraceView {
	protected PairList pairList;
	protected DiffMatcher diffMatcher;
	
	public void setPairList(final PairList pairList) {
		this.pairList = pairList;
	}
	
	public void setDiffMatcher(final DiffMatcher diffMatcher) {
		this.diffMatcher = diffMatcher;
	}
	
	@Override
	protected void appendMenuForTraceStep() {
		menuMgr.setRemoveAllWhenShown(true);
		menuMgr.addMenuListener(new IMenuListener() {
			@Override
			public void menuAboutToShow(IMenuManager manager) {
				Action forSearchAction = createForSearchAction();
				Action controlMendingAction = createControlMendingAction();
				menuMgr.add(forSearchAction);
				menuMgr.add(controlMendingAction);
			}
		});
	
		curTreeViewer.getTree().setMenu(menuMgr.createContextMenu(listViewer.getTree()));
	}

	protected abstract Action createControlMendingAction();
	
	@Override
	public void createPartControl(Composite parent) {
		super.createPartControl(parent);
		hookActionsOnToolBar();
	}
	
	private Stack<TraceNode> visitedNodeStack = new Stack<>();

	private void hookActionsOnToolBar() {
		IActionBars actionBars = getViewSite().getActionBars();
		IToolBarManager toolBar = actionBars.getToolBarManager();
		
		Action undoAction = new Action("Undo"){
			public void run(){
				if(!visitedNodeStack.isEmpty()) {
					TraceNode node = visitedNodeStack.pop();
					jumpToNode(trace, node.getOrder(), true);
				}
			}
		};
		undoAction.setImageDescriptor(Activator.getDefault().getImageRegistry().getDescriptor(ImageUI.UNDO_MARK));
		
		
		toolBar.add(undoAction);
		
	}

	public Stack<TraceNode> getVisitedNodeStack() {
		return visitedNodeStack;
	}
	
	public void recordVisitedNode(TraceNode node) {
		this.visitedNodeStack.push(node);
	}
}
