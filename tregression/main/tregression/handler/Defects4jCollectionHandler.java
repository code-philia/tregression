package tregression.handler;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;

import microbat.Activator;
import microbat.util.JavaUtil;
import tregression.auto.Defects4jRunner;
import tregression.auto.ProjectsRunner;
import tregression.preference.TregressionPreference;

public class Defects4jCollectionHandler extends AbstractHandler {

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		JavaUtil.sourceFile2CUMap.clear();
		Job job = new Job("Testing Tregression") {

			@Override
			protected IStatus run(IProgressMonitor monitor) {
				execute();
				return Status.OK_STATUS;
			}
			
		};
		job.schedule();
		return null;
	}
	
	private void execute() {
		final String basePath = Activator.getDefault().getPreferenceStore().getString(TregressionPreference.REPO_PATH);
		final String resultPath = Activator.getDefault().getPreferenceStore().getString(TregressionPreference.RESULT_PATH_KEY);
		final ProjectsRunner runner = new Defects4jRunner(basePath, resultPath);
		runner.run();
	}
}
