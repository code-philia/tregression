package traceagent.handler;

import java.util.Comparator;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;

import microbat.model.trace.Trace;
import microbat.model.trace.TraceNode;
import tregression.empiricalstudy.RootCauseFinder;
import tregression.empiricalstudy.RootCauseNode;
import tregression.views.BuggyTraceView;
import tregression.views.ConcurrentBuggyTraceView;
import tregression.views.ConcurrentCorrectTraceView;
import tregression.views.CorrectTraceView;
import tregression.views.TregressionViews;

public class PlayRegressionLocalizationHandlerConc extends AbstractHandler {

	public static RootCauseFinder finder = null;
	
	ConcurrentBuggyTraceView buggyView;
	ConcurrentCorrectTraceView correctView;
	
	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {

		Job job = new Job("Recovering Regression ...") {
			@Override
			protected IStatus run(IProgressMonitor monitor) {
				try {
					Display.getDefault().asyncExec(new Runnable() {
					    @Override
					    public void run() {
							buggyView = TregressionViews.getConcBuggyTraceView();
							correctView = TregressionViews.getConcCorrectTraceView();		
					    }
					});
					
					Thread.sleep(2000);
					
					finder.getRegressionNodeList().sort(new Comparator<TraceNode>() {
						@Override
						public int compare(TraceNode o1, TraceNode o2) {
							return o2.getOrder() - o1.getOrder();
						}
					});;
					
					for(final TraceNode node: finder.getRegressionNodeList()) {
						Display.getDefault().asyncExec(new Runnable() {
						    @Override
						    public void run() {
								Trace buggyTrace = node.getTrace();
//								Trace correctTrace = correctView.getTrace();
								buggyView.jumpToNode(buggyTrace, node.getOrder(), true); 
								
								try {
									Thread.sleep(2000);
								} catch (InterruptedException e) {
									e.printStackTrace();
								}
						    }
						});
						
						boolean stop = false;
						for(RootCauseNode rNode: finder.getRealRootCaseList()) {
							if(rNode.getRoot().equals(node)) {
								stop = true;
								break;
							}
						}
						
						if(stop)break;
						
					}
					
				} catch (Exception e) {
					e.printStackTrace();
				}

				return Status.OK_STATUS;
			}
		};
		job.schedule();

		return null;
	}

}
