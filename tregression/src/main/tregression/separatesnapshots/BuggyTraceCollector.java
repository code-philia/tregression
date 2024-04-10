package tregression.separatesnapshots;

import java.util.List;

import tregression.empiricalstudy.TestCase;
import tregression.empiricalstudy.config.ProjectConfig;

/**
 * Class used to run non determistic traces by running them multiple times
 * @author Gabau
 *
 */
public class BuggyTraceCollector extends TraceCollector0 {

	/**
	 * The limit on the number of runs to get the bug.
	 */
	protected final int limit;
	protected int numOfIter = 0;
	
	public BuggyTraceCollector(int limit) {
		super(true);
		isForceJunit3Or4 = true;
		this.limit = limit;
		// TODO Auto-generated constructor stub
	}
	
	
	protected RunningResult generateResult(String workingDir, TestCase tc, ProjectConfig config, boolean isRunInTestCaseMode,
			boolean allowMultiThread, List<String> includeLibs, List<String> excludeLibs) {
		RunningResult tmpResult = super.run(workingDir, tc, config, isRunInTestCaseMode, allowMultiThread, includeLibs, excludeLibs);
		for (int i = 0; i < limit; ++i) {
			if (!tmpResult.hasPassedTest()) {
				break;
			}
			numOfIter = i + 2;
			tmpResult = super.run(workingDir, tc, config, isRunInTestCaseMode, allowMultiThread, includeLibs, excludeLibs);
		}
		return tmpResult;
	}

	@Override
	public RunningResult run(String workingDir, TestCase tc, ProjectConfig config, boolean isRunInTestCaseMode,
			boolean allowMultiThread, List<String> includeLibs, List<String> excludeLibs) {
		return this.generateResult(workingDir, tc, config, isRunInTestCaseMode, allowMultiThread, includeLibs, excludeLibs);
	}
	
	
}
