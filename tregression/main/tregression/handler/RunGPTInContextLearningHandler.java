package tregression.handler;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;

import tregression.incontextlearning.GPTInContextLearning;

public class RunGPTInContextLearningHandler extends AbstractHandler {
    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {
        Job job = new Job("RunGPTInContextLearning") {
            @Override
            protected IStatus run(IProgressMonitor monitor) {
                new GPTInContextLearning().run();
                return Status.OK_STATUS;
            }
        };
        job.schedule();
        return null;
    }
}
