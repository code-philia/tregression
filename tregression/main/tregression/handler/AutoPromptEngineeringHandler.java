package tregression.handler;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;

import microbat.tracerecov.autoprompt.AutoPromptEngineer;

public class AutoPromptEngineeringHandler extends AbstractHandler {

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {

		Job job = new Job("Search Example Experiment") {
			@Override
			protected IStatus run(IProgressMonitor monitor) {
				AutoPromptEngineer autoPromptEngineer = new AutoPromptEngineer();
				double originalAvgLoss = autoPromptEngineer.getAverageLoss();
				String newExample = autoPromptEngineer.adjustVariableExpansionPromptExample();
				double updatedAvgLoss = autoPromptEngineer.getAverageLoss(newExample);

				System.out.println("Original Average Loss: " + originalAvgLoss);
				System.out.println("Updated Average Loss: " + updatedAvgLoss);

				return Status.OK_STATUS;
			}
		};

		return null;
	}

}
