package tregression.views;

import java.util.List;
import java.util.Map;

import org.eclipse.swt.widgets.Display;

import microbat.model.trace.ConcurrentTrace;
import microbat.model.trace.Trace;
import tregression.model.PairList;
import tregression.separatesnapshots.DiffMatcher;

/**
 * Class representing the visualiser for concurrent programs
 */
public class ConcurrentVisualiser implements Runnable {

	private final List<Trace> correctTraces;
	private final List<Trace> bugTraces;
	private PairList pairList;
	private DiffMatcher diffMatcher;
	private ConcurrentTrace buggyTrace;
	private ConcurrentTrace correctTrace;
	/**
	 * Concurrent visualiser for showing concurrent programs in diff
	 * @param correctTraces
	 * @param bugTraces
	 * @param pairList
	 * @param diffMatcher
	 */
	public ConcurrentVisualiser(List<Trace> correctTraces, List<Trace> bugTraces,
			ConcurrentTrace buggyTrace, ConcurrentTrace correctTrace,
			PairList pairList, DiffMatcher diffMatcher) {
		this.pairList = pairList;
		this.diffMatcher = diffMatcher;
		this.correctTraces = correctTraces;
		this.bugTraces = bugTraces;
		this.buggyTrace = buggyTrace;
		this.correctTrace = correctTrace;
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
		view.setDiffMatcher(diffMatcher);
		view.setPairList(pairList);
		view.updateData();
		view.setMainTrace(buggyTrace);
		
		
		ConcurrentCorrectTraceView correctView = TregressionViews.getConcCorrectTraceView();
		correctView.setTraceList(correctTraces);
		correctView.setPairList(this.pairList);
		correctView.setDiffMatcher(diffMatcher);
		correctView.updateData();
		correctView.setMainTrace(correctTrace);
		
	}

}
