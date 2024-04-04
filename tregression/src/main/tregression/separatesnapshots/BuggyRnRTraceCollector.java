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
		
		try {
			File precheckInfoFile = File.createTempFile("precheck", ".txt");
			File recording = File.createTempFile("var", ".txt");
			File concDumpFile = File.createTempFile("recording", ".txt");
			String concDumpFileString = concDumpFile.getAbsolutePath();
			executor.runPrecheck(precheckInfoFile.getAbsolutePath(), Settings.stepLimit);
			PreCheckInformation precheckInfo = executor.getPrecheckInfo();
			
			executor.runSharedVariable(recording.getPath(), Settings.stepLimit);
			for (int i = 0; i < limit; ++i) {
				executor.runRecordConc(recording.getPath(), concDumpFile.getAbsolutePath(), Settings.stepLimit);
				StorableReader reader = new StorableReader(concDumpFile);
				reader.read();
				String programMsgString = reader.getProgramMsg();
				// when it is a fail
				if (!MicrobatUtils.checkTestResult(programMsgString)) {
					firstIter = i + 1;
					break;
				}
			}
			RunningInfo resultInfo = null;
			for (int i = 0; i < limit; ++i) {
				resultInfo = executor.runReplayTracer(workingDir, concDumpFileString, Settings.stepLimit);
				if (!MicrobatUtils.checkTestResult(resultInfo.getProgramMsg())) {
					secondIter = i + 1;
					break;
				}
			}
			updateTraceInfo(resultInfo);
			
			RunningResult result = new RunningResult(resultInfo.getMainTrace(), 
					null, null, precheckInfo, executor.getAppPath());
			return result;
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}
	

	
	
}

