package tregression.views;

import java.util.List;

import org.eclipse.swt.widgets.Display;

import microbat.model.trace.Trace;
import tregression.model.PairList;
import tregression.separatesnapshots.DiffMatcher;

/**
 * Class representing the visualiser for concurrent programs
 */
public class ConcurrentVisualiser implements Runnable {

	private final List<Trace> correctTraces;
	private final List<Trace> bugTraces;
	private List<PairList> pairLists;
	private DiffMatcher diffMatcher;
	
	public ConcurrentVisualiser(List<Trace> correctTraces, List<Trace> bugTraces,
			List<PairList> pairList, DiffMatcher diffMatcher) {
		this.pairLists = pairList;
		this.diffMatcher = diffMatcher;
		this.correctTraces = correctTraces;
		this.bugTraces = bugTraces;
	}
	
	public void visualise() {
		Display.getDefault().asyncExec(this);
	}
	
	/**
	 * Should only be called in the display call
	 */
	@Override
	public void run() {
		ConcurrentTregressionTraceView view = TregressionViews.getConcBuggyTraceView();
		view.setTraceList(bugTraces);
		view.updateData();
		
		ConcurrentTregressionTraceView correctView = TregressionViews.getConcCorrectTraceView();
		correctView.setTraceList(correctTraces);
		correctView.updateData();
		
	}

}
