package tregression.handler;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;

public class PromptCollectionHandler extends AbstractHandler {
	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {

		Job job = new Job("Prompt Collection & Labelling") {
			@Override
			protected IStatus run(IProgressMonitor monitor) {
				System.out.println("Hello World");
				return Status.OK_STATUS;
			}
		};

		job.schedule();

		return null;
	}
}
