package tregression.handler;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;

import microbat.debugpilot.DebugPilotInfo;

public class StopDebugPilotTHandler extends AbstractHandler {

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		Job job = new Job("DebugPilot") {
			@Override
			protected IStatus run(IProgressMonitor monitor) {
//				DebugPilotInfo.getInstance().setStop(true);
				return Status.OK_STATUS;
			}
		};
		job.schedule();
		return null;
	}

}
