package tregression.separatesnapshots;

import java.io.File;
import java.io.IOException;
import java.util.List;

import microbat.codeanalysis.runtime.InstrumentationExecutor;
import microbat.codeanalysis.runtime.PreCheckInformation;
import microbat.instrumentation.output.RunningInfo;
import microbat.instrumentation.output.StorableReader;
import microbat.instrumentation.precheck.PrecheckInfo;
import microbat.instrumentation.utils.MicrobatUtils;
import microbat.preference.MicrobatPreference;
import microbat.util.MicroBatUtil;
import microbat.util.Settings;
import sav.strategies.dto.AppJavaClassPath;
import tregression.empiricalstudy.TestCase;
import tregression.empiricalstudy.TrialGenerator0;
import tregression.empiricalstudy.config.ProjectConfig;

/**
 * Clas
 * @author Gabau
 *
 */
public class BuggyRnRTraceCollector extends BuggyTraceCollector {
	
	private int firstIter = -1;
	private int secondIter = -1;

	public BuggyRnRTraceCollector(int limit) {
		super(limit);
	}

	@Override
	protected RunningResult generateResult(String workingDir, TestCase tc, ProjectConfig config,
			boolean isRunInTestCaseMode, boolean allowMultiThread, List<String> includeLibs, List<String> excludeLibs) {
		
		InstrumentationExecutor executor = generateExecutor(workingDir, tc, config, isRunInTestCaseMode, includeLibs, excludeLibs);
		executor.setIsForceJunit3Or4(true);
		try {
			File precheckInfoFile = File.createTempFile("precheck", ".txt");
			File recording = File.createTempFile("var", ".txt");
			File concDumpFile = File.createTempFile("recording", ".txt");
			File outputFile = File.createTempFile("output", ".txt");
			String concDumpFileString = concDumpFile.getAbsolutePath();
			executor.runPrecheck(precheckInfoFile.getAbsolutePath(), Settings.stepLimit);
			PreCheckInformation precheckInfo = executor.getPrecheckInfo();

			if(precheckInfo.isOverLong()) {
				System.out.println("The trace is over long!");
				RunningResult rs = new RunningResult();
				rs.setFailureType(TrialGenerator0.OVER_LONG);
				return rs;
			}
			
			executor.runSharedVariable(recording.getPath(), Settings.stepLimit);
			for (int i = 0; i < limit; ++i) {
				executor.runRecordConc(recording.getPath(), concDumpFile.getAbsolutePath(), Settings.stepLimit);
				try {
					StorableReader reader = new StorableReader(concDumpFile);
					reader.read();
					String programMsgString = reader.getProgramMsg();
					// when it is a fail
					if (!MicroBatUtil.checkTestResult(programMsgString)) {
						firstIter = i + 1;
						break;
					}	
				} catch (Exception exception) {
					// handle recording exceptions
					System.out.println("Exception occured during record");
					throw new RuntimeException(exception);
				}
			}
			RunningInfo resultInfo = null;
			for (int i = 0; i < limit; ++i) {
				resultInfo = executor.runReplayTracer(concDumpFileString, outputFile.getAbsolutePath(), Settings.stepLimit);
				if (!MicroBatUtil.checkTestResult(resultInfo.getProgramMsg())) {
					secondIter = i + 1;
					break;
				}
			}
			if (secondIter == -1) {
				System.out.println("Failed to find failing test case");
				RunningResult rsResult = new RunningResult();
				rsResult.setFailureType(TrialGenerator0.UNDETERMINISTIC);;
				return rsResult;
			}
			
			if (resultInfo.getMainTrace() == null) {
				System.out.println("Missing main trace");
				RunningResult rsResult = new RunningResult();
				rsResult.setFailureType(TrialGenerator0.INSUFFICIENT_TRACE);
				return rsResult;
				
			}
			
//			if(!precheckInfo.getOverLongMethods().isEmpty()) {
//				String method = precheckInfo.getOverLongMethods().get(0);
//				System.out.println("Method " + method + " is over long after instrumentation!");
//				RunningResult rs = new RunningResult();
//				rs.setFailureType(TrialGenerator0.OVER_LONG_INSTRUMENTATION_METHOD);
//				return rs;
//			}
			
			updateTraceInfo(resultInfo);
			
			RunningResult result = new RunningResult(resultInfo.getMainTrace(), 
					null, null, precheckInfo, executor.getAppPath());
			result.setRunningInfo(resultInfo);
			result.setRunningTrace(resultInfo.getMainTrace());
			return result;
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}
	

	
	
}

