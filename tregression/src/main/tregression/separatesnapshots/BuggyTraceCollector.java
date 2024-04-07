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
		return super.run(workingDir, tc, config, isRunInTestCaseMode, allowMultiThread, includeLibs, excludeLibs);
	}

	@Override
	public RunningResult run(String workingDir, TestCase tc, ProjectConfig config, boolean isRunInTestCaseMode,
			boolean allowMultiThread, List<String> includeLibs, List<String> excludeLibs) {
		RunningResult tmpResult = null;
		for (int i = 0; i < limit; ++i) {
			tmpResult = generateResult(workingDir, tc, config, isRunInTestCaseMode, allowMultiThread, includeLibs, excludeLibs);
			if (!tmpResult.hasPassedTest()) {
				numOfIter = i + 1;
				return tmpResult;
			}
		}
		return tmpResult;
	}
	
	
}
