package tregression.reexecutor;

import java.util.List;

import microbat.Activator;
import microbat.codeanalysis.runtime.Condition;
import microbat.model.trace.Trace;
import microbat.model.trace.TraceNode;
import microbat.model.value.VarValue;
import microbat.preference.RecovSlicingPreference;
import microbat.tracerecov.executionsimulator.ExecutionSimulationFileLogger;
import tregression.empiricalstudy.EmpiricalTrial;
import tregression.empiricalstudy.TraceGenerator;
import tregression.empiricalstudy.TrialGenerator0;
import tregression.empiricalstudy.config.ConfigFactory;
import tregression.empiricalstudy.config.ProjectConfig;
import tregression.handler.PathConfiguration;
import tregression.preference.TregressionPreference;

public class ConditionalExecutor {

	private Condition condition;
	TrialGenerator0 generator0 = new TrialGenerator0();
	TraceGenerator traceGenerator = new TraceGenerator();

	public ConditionalExecutor() {
		
	}
	
	public ConditionalExecutor(Condition condition) {
		this.condition = condition;
	}

	public void expandVariable(VarValue obj, TraceNode currentNode) {
		String projectPath = Activator.getDefault().getPreferenceStore().getString(TregressionPreference.PROJECT_NAME);
		String bugID = Activator.getDefault().getPreferenceStore().getString(TregressionPreference.BUG_ID);

		String buggyPath = PathConfiguration.getBuggyPath(projectPath, bugID);
		String fixPath = PathConfiguration.getCorrectPath(projectPath, bugID);

		String projectName = Activator.getDefault().getPreferenceStore().getString(TregressionPreference.PROJECT_NAME);
		String id = Activator.getDefault().getPreferenceStore().getString(TregressionPreference.BUG_ID);

		String testcase = Activator.getDefault().getPreferenceStore().getString(TregressionPreference.TEST_CASE);

		System.out.println("Re-excution on the " + id + "th bug of " + projectName + " project.");

		String isMutatedBugString = Activator.getDefault().getPreferenceStore()
				.getString(RecovSlicingPreference.USE_MUTATION_CONFIG);
		boolean isMutatedBug = isMutatedBugString != null && isMutatedBugString.equals("true");
		ProjectConfig config = ConfigFactory.createConfig(projectName, id, buggyPath, fixPath, isMutatedBug);

		config.condition = condition;

		/* re-execute */
		List<EmpiricalTrial> trials = generator0.generateTrials(buggyPath, fixPath, false, false, false, 3, true, true,
				config, testcase);
		EmpiricalTrial trial = trials.get(0); // assume one trial is generated.

		Trace buggyTrace = trial.getBuggyTrace();
//		Trace fixedTrace = trial.getFixedTrace();

		/* write results to file */
		ExecutionSimulationFileLogger gtLogger = new ExecutionSimulationFileLogger();
		gtLogger.collectGT(condition, buggyTrace);
//		gtLogger.collectGT(condition, fixedTrace);
	}
	
	public String expandVariable(boolean isOnBuggy) {
		String projectPath = Activator.getDefault().getPreferenceStore().getString(TregressionPreference.PROJECT_NAME);
		String bugID = Activator.getDefault().getPreferenceStore().getString(TregressionPreference.BUG_ID);

		String buggyPath = PathConfiguration.getBuggyPath(projectPath, bugID);
		String fixPath = PathConfiguration.getCorrectPath(projectPath, bugID);

		String projectName = Activator.getDefault().getPreferenceStore().getString(TregressionPreference.PROJECT_NAME);
		String id = Activator.getDefault().getPreferenceStore().getString(TregressionPreference.BUG_ID);

		String testcase = Activator.getDefault().getPreferenceStore().getString(TregressionPreference.TEST_CASE);

		System.out.println("Re-execution on buggy trace? "+isOnBuggy);

		String isMutatedBugString = Activator.getDefault().getPreferenceStore()
				.getString(RecovSlicingPreference.USE_MUTATION_CONFIG);
		boolean isMutatedBug = isMutatedBugString != null && isMutatedBugString.equals("true");
		ProjectConfig config = ConfigFactory.createConfig(projectName, id, buggyPath, fixPath, isMutatedBug);
		config.condition = condition;
		
		/* re-execute */
		Trace trace = traceGenerator.generateTrace(buggyPath, fixPath, config, testcase, isOnBuggy);
		if(trace == null) {
			System.out.println("__ERROR__ Re-execution failed!");
			return null;
		}
		
		/* write results to file */
		ExecutionSimulationFileLogger gtLogger = new ExecutionSimulationFileLogger();
		String groundTruthStr = gtLogger.collectGT(condition, trace);
		
		return groundTruthStr;
	}
}
