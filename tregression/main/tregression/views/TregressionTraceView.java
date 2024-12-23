package tregression.views;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IMenuListener;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.IActionBars;

import microbat.Activator;
import microbat.model.trace.TraceNode;
import microbat.ondemandtrace.views.ExpandTraceOptions;
import microbat.views.ImageUI;
import microbat.views.TraceView;
import tregression.empiricalstudy.TestCase;
import tregression.handler.PathConfiguration;
import tregression.model.PairList;
import tregression.preference.TregressionPreference;
import tregression.separatesnapshots.DiffMatcher;

public abstract class TregressionTraceView extends TraceView {
	protected PairList pairList;
	protected DiffMatcher diffMatcher;

	@Override
	protected MenuManager createExpandTraceMenu(MenuManager parentMenuMgr) {
		String projectPath = Activator.getDefault().getPreferenceStore().getString(TregressionPreference.PROJECT_NAME);
		String bugID = Activator.getDefault().getPreferenceStore().getString(TregressionPreference.BUG_ID);

		String buggyPath = PathConfiguration.getBuggyPath(projectPath, bugID);

		try {
			List<TestCase> testCases = retrieveFailingTestCase(buggyPath);
			TestCase defaultTC = testCases.get(0); // TODO: other test cases? consider other naming convension
			String className = defaultTC.testClass;
			String testCase = defaultTC.testMethod;
			ExpandTraceOptions options = new ExpandTraceOptions(listViewer.getSelection(), className, testCase);
			return options.getOptions();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}

	private List<TestCase> retrieveFailingTestCase(String buggyVersionPath) throws IOException {
		String failingFile = buggyVersionPath + File.separator + "failing_tests";
		File file = new File(failingFile);

		BufferedReader reader = new BufferedReader(new FileReader(file));

		List<TestCase> list = new ArrayList<>();
		String line = null;
		while ((line = reader.readLine()) != null) {
			if (line.startsWith("---")) {
				String testClass = line.substring(line.indexOf(" ") + 1, line.indexOf("::"));
				String testMethod = line.substring(line.indexOf("::") + 2, line.length());
				System.currentTimeMillis();
				TestCase tc = new TestCase(testClass, testMethod);
				list.add(tc);
			}
		}
		reader.close();

		return list;
	}

	@Override
	protected void appendMenuForTraceStep() {
		menuMgr.setRemoveAllWhenShown(true);
		menuMgr.addMenuListener(new IMenuListener() {
			@Override
			public void menuAboutToShow(IMenuManager manager) {
				Action forSearchAction = createForSearchAction();
				Action controlMendingAction = createControlMendingAction();
				MenuManager expandTraceOptions = createExpandTraceMenu(menuMgr);
				menuMgr.add(forSearchAction);
				menuMgr.add(controlMendingAction);
				menuMgr.add(expandTraceOptions);
			}
		});

		listViewer.getTree().setMenu(menuMgr.createContextMenu(listViewer.getTree()));
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
