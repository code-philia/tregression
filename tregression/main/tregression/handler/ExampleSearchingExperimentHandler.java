package tregression.handler;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;

import microbat.Activator;
import microbat.preference.TraceRecovPreference;
import microbat.tracerecov.autoprompt.AliasInferenceExampleSearcher;
import microbat.tracerecov.autoprompt.DefinitionInferenceExampleSearcher;
import microbat.tracerecov.autoprompt.ExampleSearcher;
import microbat.tracerecov.autoprompt.PromptType;
import microbat.tracerecov.autoprompt.VarExpansionExampleSearcher;

public class ExampleSearchingExperimentHandler extends AbstractHandler {
	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		Job job = new Job("Search Example Experiment") {
			@Override
			protected IStatus run(IProgressMonitor monitor) {
				PromptType promptType = PromptType.valueOf(
						Activator.getDefault().getPreferenceStore().getString(TraceRecovPreference.PROMPT_TYPE));
				ExampleSearcher exampleSearcher = null;

				switch (promptType) {
				case VAR_EXPANSION:
					exampleSearcher = new VarExpansionExampleSearcher();
					break;
				case ALIAS_INFERENCE:
					exampleSearcher = new AliasInferenceExampleSearcher();
					break;
				case DEF_INFERENCE:
					exampleSearcher = new DefinitionInferenceExampleSearcher();
					break;
				default:
					exampleSearcher = new VarExpansionExampleSearcher();
				}

				exampleSearcher.recordLoss();
				return Status.OK_STATUS;
			}
		};

		job.schedule();

		return null;
	}
}
