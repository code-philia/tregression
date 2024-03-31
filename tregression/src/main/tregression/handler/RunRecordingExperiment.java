package tregression.handler;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Scanner;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;

import microbat.codeanalysis.runtime.InstrumentationExecutor;
import microbat.evaluation.junit.TestCaseAnalyzer;
import microbat.handler.CancelThread;
import microbat.instrumentation.instr.aggreplay.shared.ParseData;
import microbat.instrumentation.instr.aggreplay.shared.RecordingOutput;
import microbat.instrumentation.instr.aggreplay.shared.SharedDataParser;
import microbat.preference.AnalysisScopePreference;
import microbat.util.MicroBatUtil;
import microbat.util.Settings;
import sav.common.core.utils.FileUtils;
import sav.common.core.utils.SingleTimer;
import sav.strategies.dto.AppJavaClassPath;
import traceagent.report.excel.AbstractExcelWriter;
import traceagent.report.excel.ExcelHeader;

/**
 * Used to run benchmark for data on record log size + success rate
 * at replaying bugs
 */
public class RunRecordingExperiment extends AbstractHandler {
	
	private CancelThread cancelThread;
	IProgressMonitor monitor;
	private String testCaseLoc = "C:\\Users\\Gabriel\\Desktop\\jacon_testcases.txt";
	RecordingReport report;
	enum RecordHeaders implements ExcelHeader {
		TEST_CASE,
		EXECUTION_TIME,
		ORIGINAL_MEMORY_SIZE,
		MEMEORY_SIZE,
		LOG_SIZE;
		public String getTitle() {
			return name();
		}

		public int getCellIdx() {
			return ordinal();
		}
	}
	enum ErrorHeaders implements ExcelHeader {

		TEST_CASE,
		EXCEPTION;
		@Override
		public String getTitle() {
			return name();
		}

		@Override
		public int getCellIdx() {
			return ordinal();
		}
	}
	
	class RecordingReport extends AbstractExcelWriter {
		public RecordingReport(File file) throws Exception {
			super(file);
		}
		
		public void writeRow(String testCaseName, long executionTime, long originalMem, long memorySize, long logSize) {
			Sheet sheet = getSheet("data", RecordHeaders.values(), 0);
			int rowNum = sheet.getLastRowNum() + 1;
			Row row = sheet.createRow(rowNum);
			addCell(row, RecordHeaders.TEST_CASE, testCaseName);
			addCell(row, RecordHeaders.EXECUTION_TIME, executionTime);
			addCell(row, RecordHeaders.ORIGINAL_MEMORY_SIZE, originalMem);
			addCell(row, RecordHeaders.MEMEORY_SIZE, memorySize);
			addCell(row, RecordHeaders.LOG_SIZE, logSize);
		}
		
		private void writeError(String testCaseName, Exception exception) {
			Sheet sheet = getSheet("error", ErrorHeaders.values(), 0);
			int rowNum = sheet.getLastRowNum() + 1;
			Row row = sheet.createRow(rowNum);
			addCell(row, ErrorHeaders.TEST_CASE, testCaseName);
			StringWriter sw = new StringWriter();
			PrintWriter pWriter = new PrintWriter(sw);
			exception.printStackTrace(pWriter);
			addCell(row, ErrorHeaders.EXCEPTION, sw.toString());
		}
	}
	
	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		File testCasesFile = new File(testCaseLoc);
		Scanner sc = null;
		try {
			report = new RecordingReport(new File("C:\\Users\\Gabriel\\Desktop\\defects4j_out\\output.xlsx"));
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		try {
			sc = new Scanner(testCasesFile);
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return null;
		}
		List<String> testCases = new LinkedList<>();
		while (sc.hasNext()) {
			testCases.add(sc.nextLine());
		}
		sc.close();
		Job runningJob = new Job("Recording experiment") {

			@Override
			protected IStatus run(IProgressMonitor m) {
				monitor = m;
				for (String testCase : testCases) {
					if (m.isCanceled()) break;
					try {
						runSingleTestCase(testCase);
					} catch (Exception e) {
						report.writeError(testCase, e);
					}
				}
				try {
					report.writeWorkbook();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				return Status.OK_STATUS;
			}
			
		};
		runningJob.schedule();
		return null;
	}
	
	private void runSingleTestCase(String testCase) {
		String temp = Settings.launchClass;
		Settings.launchClass = testCase;
		final AppJavaClassPath appClassPath = MicroBatUtil.constructClassPaths();
		List<String> srcFolders = MicroBatUtil.getSourceFolders(Settings.projectName);
		appClassPath.setSourceCodePath(appClassPath.getTestCodePath());
		for (String srcFolder : srcFolders) {
			if (!srcFolder.equals(appClassPath.getTestCodePath())) {
				appClassPath.getAdditionalSourceFolders().add(srcFolder);
			}
		}
		executeAggrRecord(null, appClassPath);
		
		Settings.launchClass = temp;
	}
	
	protected String generateTraceDir(AppJavaClassPath appPath) {
		String traceFolder;
		if (appPath.getOptionalTestClass() != null) {
			traceFolder = FileUtils.getFilePath(MicroBatUtil.getTraceFolder(), 
					Settings.projectName,
					appPath.getOptionalTestClass(), 
					appPath.getOptionalTestMethod());
		} else {
			traceFolder = FileUtils.getFilePath(MicroBatUtil.getTraceFolder(), 
					Settings.projectName, 
					appPath.getLaunchClass()); 
		}
		FileUtils.createFolder(traceFolder);
		return traceFolder;
	}
	
	
	private Object executeAggrRecord(ExecutionEvent event, final AppJavaClassPath appJavaClassPath) {
		long origMemSize = -1;
		List<String> includedClassNames = AnalysisScopePreference.getIncludedLibList();
		List<String> excludedClassNames = AnalysisScopePreference.getExcludedLibList();
		InstrumentationExecutor executor = new InstrumentationExecutor(appJavaClassPath,
		generateTraceDir(appJavaClassPath), "trace", includedClassNames, excludedClassNames);
		cancelThread = new CancelThread(monitor, executor);
		cancelThread.start();
		String fileName = null;
		File dumpFile = null;
		File concDumpFile = null;
		// the absolute path to the dump file.
		String concFileNameString = null;
		if (Settings.concurrentDumpFile.isPresent()) {
			concFileNameString = Settings.concurrentDumpFile.get();
			concDumpFile = new File(concFileNameString);
		}
		try {
			dumpFile = File.createTempFile("temp", ".txt");
			if (concDumpFile == null) {
				concDumpFile = File.createTempFile("concTemp", ".txt");
				Settings.concurrentDumpFile = Optional.of(concDumpFile.getPath());
			}
			fileName = dumpFile.getPath();
			concFileNameString = concDumpFile.getPath();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		executor.runMemoryMeasureMent(fileName);
		Scanner dumpFileScanner;
		try {
			dumpFileScanner = new Scanner(dumpFile);
			if (dumpFileScanner.hasNext()) origMemSize = dumpFileScanner.nextLong();
			dumpFileScanner.close();
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		CancelThread ctThread = new CancelThread(monitor, executor);
		ctThread.start();
		executor.runSharedVariable(fileName, Settings.stepLimit);
		SingleTimer timer = SingleTimer.start("execute record");
		executor.runRecordConc(fileName, concFileNameString, Settings.stepLimit);
		ctThread.stopMonitoring();
		long logSize = concDumpFile.length();
		long timeTaken = timer.getExecutionTime();
		List<ParseData> data;
		long memoryUsed = -1;
		try {
			data = new SharedDataParser().parse(new FileReader(concDumpFile));
			RecordingOutput output = new RecordingOutput().parse(data.get(0));
			
			memoryUsed = output.memoryUsed;
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		report.writeRow(Settings.launchClass, timeTaken, origMemSize, memoryUsed, logSize);
		cancelThread.stopMonitoring();
		return null;
	}
	
	
	
}
