package tregression.handler;

import java.nio.file.Paths;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;

import microbat.util.JavaUtil;
import tregression.auto.Defects4jRunner;
import tregression.auto.MutationTregressionRunner;
import tregression.auto.ProjectsRunner;

/*
 * See Defects4jCollectionHandler
 */
public class AllMutationHandler extends AbstractHandler{
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
		final String projectsBasePath = "D:\\MutationDataset\\OriginalProjects";
		final String mutationFileBasePath = "D:\\MutationDataset\\MutationFiles";
		final String workingBasePath = "D:\\MutationWorkSpace";
		final String resultPath = Paths.get("C:\\Users\\Kwy\\Desktop\\result\\mutation_1_baseline.txt").toString();
		
		final ProjectsRunner runner = new MutationTregressionRunner(projectsBasePath,mutationFileBasePath,workingBasePath,resultPath);
		runner.run();
	}
}
