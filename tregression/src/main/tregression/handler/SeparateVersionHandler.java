package tregression.handler;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;

import microbat.Activator;
import microbat.model.trace.Trace;
import microbat.util.Settings;
import tregression.EmpiricalTrial;
import tregression.SimulationFailException;
import tregression.SimulatorWithCompilcatedModification;
import tregression.model.PairList;
import tregression.preference.TregressionPreference;
import tregression.separatesnapshots.DiffMatcher;
import tregression.separatesnapshots.PathConfiguration;
import tregression.separatesnapshots.RunningResult;
import tregression.separatesnapshots.TraceCollector;
import tregression.tracematch.ControlPathBasedTraceMatcher;
import tregression.views.Visualizer;

public class SeparateVersionHandler extends AbstractHandler{

	private RunningResult cachedBuggyRS;
	private RunningResult cachedCorrectRS;
	
	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		Job job = new Job("Do evaluation") {
			@Override
			protected IStatus run(IProgressMonitor monitor) {
				TraceCollector collector = new TraceCollector();
				boolean isReuse = true;
				
				PathConfiguration.buggyPath = Activator.getDefault().getPreferenceStore().getString(TregressionPreference.BUGGY_PATH);
				PathConfiguration.fixPath = Activator.getDefault().getPreferenceStore().getString(TregressionPreference.CORRECT_PATH);
				
				try {
					TestCase tc = retrieveD4jFailingTestCase(PathConfiguration.buggyPath);
					
					RunningResult buggyRS;
					RunningResult correctRs;
					if(cachedBuggyRS!=null && cachedCorrectRS!=null && isReuse){
						buggyRS = cachedBuggyRS;
						correctRs = cachedCorrectRS;
					}
					else{
						Settings.compilationUnitMap.clear();
						buggyRS = collector.run(PathConfiguration.buggyPath, tc.testClass, tc.testMethod);
						
						Settings.compilationUnitMap.clear();
						correctRs = collector.run(PathConfiguration.fixPath, tc.testClass, tc.testMethod);
						
						cachedBuggyRS = buggyRS;
						cachedCorrectRS = correctRs;
					}
					
					DiffMatcher diffMatcher = new DiffMatcher("source", "tests",
							PathConfiguration.buggyPath, PathConfiguration.fixPath);
					diffMatcher.matchCode();
					
//					LCSBasedTraceMatcher traceMatcher = new LCSBasedTraceMatcher();
					ControlPathBasedTraceMatcher traceMatcher = new ControlPathBasedTraceMatcher();
					PairList pairList = traceMatcher.matchTraceNodePair(buggyRS.getRunningTrace(), 
							correctRs.getRunningTrace(), diffMatcher); 
					
					Visualizer visualizer = new Visualizer();
					
					Trace buggyTrace = buggyRS.getRunningTrace();
					Trace correctTrace = correctRs.getRunningTrace();
					visualizer.visualize(buggyTrace, correctTrace, pairList, diffMatcher);
					
					SimulatorWithCompilcatedModification simulator = new SimulatorWithCompilcatedModification();
					simulator.prepare(buggyTrace, correctTrace, pairList, diffMatcher);
					
					List<EmpiricalTrial> trials = simulator.detectMutatedBug(buggyTrace, correctTrace, diffMatcher, 0);
					System.out.println("all the trials");
					for(int i=0; i<trials.size(); i++) {
						System.out.println("Trial " + (i+1));
						System.out.println(trials.get(i));
					}
					
				} catch (IOException | SimulationFailException e) {
					e.printStackTrace();
				}
				
				
				return Status.OK_STATUS;
			}
		};
		
		job.schedule();
		
		return null;
	}
	
	class TestCase{
		public String testClass;
		public String testMethod;
		public TestCase(String testClass, String testMethod) {
			super();
			this.testClass = testClass;
			this.testMethod = testMethod;
		}
	}
	
	public TestCase retrieveD4jFailingTestCase(String buggyVersionPath) throws IOException{
		String failingFile = buggyVersionPath + File.separator + "failing_tests";
		File file = new File(failingFile);
		
		BufferedReader reader = new BufferedReader(new FileReader(file));
		String line = reader.readLine();
		reader.close();
		
		String testClass = line.substring(line.indexOf(" ")+1, line.indexOf("::"));
		String testMethod = line.substring(line.indexOf("::")+2, line.length());
		
		TestCase tc = new TestCase(testClass, testMethod);
		return tc;
	}

}
