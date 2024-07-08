package tregression.reexecutor;

import java.util.List;

import microbat.Activator;
import microbat.model.trace.TraceNode;
import microbat.model.value.VarValue;
import microbat.preference.TraceRecovPreference;
import tregression.empiricalstudy.EmpiricalTrial;
import tregression.empiricalstudy.TrialGenerator0;
import tregression.empiricalstudy.config.ConfigFactory;
import tregression.empiricalstudy.config.ProjectConfig;
import tregression.handler.PathConfiguration;
import tregression.preference.TregressionPreference;

public class ConditionalExecutor {
	
	TrialGenerator0 generator0 = new TrialGenerator0();

	public void expandVariable(VarValue obj, TraceNode currentNode) {
		
		String projectPath = Activator.getDefault().getPreferenceStore().getString(TregressionPreference.PROJECT_NAME);
		String bugID = Activator.getDefault().getPreferenceStore().getString(TregressionPreference.BUG_ID);
		
		String buggyPath = PathConfiguration.getBuggyPath(projectPath, bugID);
		String fixPath = PathConfiguration.getCorrectPath(projectPath, bugID);
		
		String projectName = Activator.getDefault().getPreferenceStore().getString(TregressionPreference.PROJECT_NAME);
		String id = Activator.getDefault().getPreferenceStore().getString(TregressionPreference.BUG_ID);
		
		String testcase = Activator.getDefault().getPreferenceStore().getString(TregressionPreference.TEST_CASE);
		
		System.out.println("working on the " + id + "th bug of " + projectName + " project.");
		
		String isMutatedBugString = Activator.getDefault().getPreferenceStore().getString(TraceRecovPreference.USE_MUTATION_CONFIG);
		boolean isMutatedBug = isMutatedBugString != null && isMutatedBugString.equals("true");
		ProjectConfig config = ConfigFactory.createConfig(projectName, id, buggyPath, fixPath, isMutatedBug);
		
		if(config == null) {
			try {
				throw new Exception("cannot parse the configuration of the project " + projectName + " with id " + id);						
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		
		List<EmpiricalTrial> trials = generator0.generateTrials(buggyPath, fixPath, 
				false, false, false, 3, true, true, config, testcase);
		
		
		
	}
}
