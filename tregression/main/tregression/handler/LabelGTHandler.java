package tregression.handler;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.swt.widgets.Display;
import org.json.JSONArray;

import microbat.Activator;
import microbat.codeanalysis.runtime.InstrumentationExecutor;
import microbat.codeanalysis.runtime.StepLimitException;
import microbat.instrumentation.output.RunningInfo;
import microbat.model.trace.Trace;
import microbat.model.trace.TraceNode;
import microbat.model.value.VarValue;
import microbat.preference.MicrobatPreference;
import microbat.util.IResourceUtils;
import microbat.util.JavaUtil;
import microbat.util.MicroBatUtil;
import sav.strategies.dto.AppJavaClassPath;
import tregression.empiricalstudy.TestCase;
import tregression.empiricalstudy.config.Defects4jProjectConfig;
import tregression.empiricalstudy.config.ProjectConfig;
import tregression.model.PairList;
import tregression.preference.TregressionPreference;
import tregression.separatesnapshots.AppClassPathInitializer;
import tregression.separatesnapshots.DiffMatcher;
import tregression.views.BuggyTraceView;
import tregression.views.TregressionViews;

public class LabelGTHandler extends AbstractHandler {

	public static final String SRC_FOLDER = "src";
	public static final String BIN_FOLDER = "bin";
	public static final String TRACE_FOLDER = "trace";
	public static final String TRACE_FILE_NAME = "trace";
	public static final String METHOD_NAME = "testMainLogic";
	public static final String COMPILE_ERRORS = "tc_with_compilation_error.txt";
	public static final String RUNTIME_ERRORS = "tc_with_runtime_error.txt";
	public static final String MISMATCHES = "mismatched_ids.json";
	public static final String SLICING_CRITERIA = "slicing_criteria.txt";
	public static final String RESULTS_FOLDER = "ground_truth";
	public static final String JAVA_HOME = Activator.getDefault().getPreferenceStore()
			.getString(MicrobatPreference.JAVA7HOME_PATH);
	public static final String INSTRUMENTATION_JAR_PATH = IResourceUtils.getResourceAbsolutePath(Activator.PLUGIN_ID,
			"lib") + File.separator + "instrumentator.jar";

	public String sliceDatasetPath = "D:\\recov-slicing-benchmark\\dataset-for-recov\\llm-slicer";
	public String isJunitStr = "false";

	private String srcDirName;
	private String binDirName;
	private String traceDirName;
	private TestCase testCase;
	private ProjectConfig d4jConfig;
	private AppJavaClassPath appClassPath;

	String[] dataStructures = new String[] { "List", "Map", "Set", "StringBuffer", "StringWriter", "PrintWriter",
			"StringBuilder", "Iterator", "Queue", "Stack" };

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {

		JavaUtil.sourceFile2CUMap.clear();

		Job job = new Job("slicing evaluation") {
			@Override
			protected IStatus run(IProgressMonitor monitor) {
				// load dataset
				srcDirName = sliceDatasetPath + File.separator + SRC_FOLDER;
				binDirName = sliceDatasetPath + File.separator + BIN_FOLDER;
				traceDirName = sliceDatasetPath + File.separator + TRACE_FOLDER;

				Set<String> idsToSkip = getMismatchFiles(sliceDatasetPath);
				Set<String> compilationErrors = readContent(sliceDatasetPath, COMPILE_ERRORS);
				Set<String> runtimeErrors = readContent(sliceDatasetPath, RUNTIME_ERRORS);
				Set<String> processedFiles = getProcessedFiles(sliceDatasetPath);
				Map<String, Integer> slicingCriteria = getSlicingCriteria(sliceDatasetPath, SLICING_CRITERIA);

				File folder = new File(srcDirName);
				if (folder.exists() && folder.isDirectory()) {
					File[] files = folder.listFiles();
					if (files != null) {
						for (File file : files) {
							String className = file.getName().substring(0, file.getName().lastIndexOf('.'));

							File doubleCheckFile = new File(sliceDatasetPath + File.separator + "double_check_gt.txt");

							// files to double check
							try {
								String content = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
								for (String dataStructure : dataStructures) {
									if (content.contains(dataStructure)) {
										FileWriter resultWriter;
										try {
											resultWriter = new FileWriter(doubleCheckFile, true);
											resultWriter.append(file.getName() + System.lineSeparator());
											resultWriter.close();
										} catch (IOException e) {
											e.printStackTrace();
										}
										break;
									}
								}
							} catch (IOException e) {
								e.printStackTrace();
							}

							/* nd-dataset settings */
							String id = "";
							boolean idStarted = false;
							for (int i = 1; i < className.length(); i++) {
								char c = className.charAt(i);
								if (c == '0' && !idStarted) {
									continue;
								} else if (c == 'T') {
									break;
								} else if (c != '0') {
									idStarted = true;
									id += c;
								}
							}

							if (idsToSkip.contains(id) || compilationErrors.contains(className)
									|| runtimeErrors.contains(className)) {
								continue;
							}

							try {
								System.out.println("compiling " + file.getName() + " ...");
								compileFile(file, binDirName);

								System.out.println("collecting trace...");
								if (isJunitStr != null && isJunitStr.equals("true")) {
									initializeAppClassPathJunitTest(className, METHOD_NAME);
								} else {
									initializeAppClassPath(className);
								}
								Trace trace = runTarget();
								visualizeTrace(trace);

								System.out.println("dynamic slicing...");
								List<TraceNode> steps = trace.getExecutionList();
								Set<Integer> visitedLines = new HashSet<>();

								if (processedFiles.contains(className)) {
									visitedLines = readVisitedLines(sliceDatasetPath, className);
								}

								int criterionCounter = 1;

								while (criterionCounter < steps.size()) {
									TraceNode slicingCriterion = steps.get(criterionCounter);
									List<VarValue> readVars = slicingCriterion.getReadVariables();
									int lineNo = slicingCriterion.getLineNumber();
									if (visitedLines.contains(lineNo)
											|| !slicingCriteria.get(className).equals(lineNo)) {
										criterionCounter++;
										continue;
									}
									visitedLines.add(slicingCriterion.getLineNumber());
									for (VarValue v : readVars) {
										System.out.println("slicing criterion:");
										System.out.println(slicingCriterion.getOrder());
										System.out.println(v.getVarName());

										// original data dominator
										TraceNode dataDom = trace.findDataDependency(slicingCriterion, v);
										System.out.println("slicing destination from root:");
										if (dataDom == null) {
											System.out.println("none");
										} else {
											System.out.println(dataDom.getOrder());
										}

										System.out.println("slicing destination:");
										Set<Integer> dataDominatorsAfterRecovery = new HashSet<>();
										for (VarValue targetVar : v.getAllDescedentChildren()) {
											TraceNode dataDominator = trace.findProducer(targetVar, slicingCriterion);
											if (dataDominator != null) {
												dataDominatorsAfterRecovery.add(dataDominator.getLineNumber());
												System.out.println(dataDominator.getOrder());
											}
										}
										if (dataDom != null) {
											dataDominatorsAfterRecovery.add(dataDom.getLineNumber());
										}

										// write result
										if (!dataDominatorsAfterRecovery.isEmpty()) {
											System.out.println("writing results...");
											StringBuilder result = new StringBuilder();
											result.append(slicingCriterion.getLineNumber() + ",");
											result.append(v.getVarName() + ",");
											StringBuilder slicingDestinations = new StringBuilder("[");
											for (Integer i : dataDominatorsAfterRecovery) {
												slicingDestinations.append(i);
												slicingDestinations.append(";");
											}
											slicingDestinations.append("]");
											result.append(slicingDestinations);
											result.append(System.lineSeparator());

											try {
												File resultFile = new File(sliceDatasetPath + File.separator
														+ getResultFolderName() + File.separator + className + ".txt");
												FileWriter resultWriter = new FileWriter(resultFile, true);
												resultWriter.append(result.toString());
												resultWriter.close();
											} catch (IOException e) {
												e.printStackTrace();
												return null;
											}
										}
									}
									criterionCounter++;
								}
							} catch (Exception e) {
								System.out.println(e);
							}
						}
					}
				} else {
					System.out.println("Dataset is not found at: " + sliceDatasetPath);
				}


				return null;
			}
		};

		job.schedule();

		return null;
	}

	private Set<String> readContent(String basePath, String fileName) {
		Set<String> output = new HashSet<>();
		if (isJunitStr != null && isJunitStr.equals("true")) {
			output.add(fileName);
			output.add(BIN_FOLDER);
		}

		String filePath = basePath + File.separator + fileName;
		File problematicClasses = new File(filePath);

		try {
			String content = new String(Files.readAllBytes(problematicClasses.toPath()), StandardCharsets.UTF_8);
			String[] files = content.split(System.lineSeparator());
			for (String f : files) {
				int index = f.lastIndexOf('.');
				if (index >= 0) {
					output.add(f.substring(0, f.lastIndexOf('.')));
				} else {
					output.add(f);
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
			return new HashSet<>();
		}

		return output;
	}

	private Set<String> getMismatchFiles(String basePath) {
		Set<String> output = new HashSet<>();
		String fileName = MISMATCHES;

		String filePath = basePath + File.separator + fileName;
		File mismatchFiles = new File(filePath);

		try {
			String content = new String(Files.readAllBytes(mismatchFiles.toPath()), StandardCharsets.UTF_8);

			JSONArray jsonArray = new JSONArray(content);
			for (Object object : jsonArray.toList()) {
				String id = String.valueOf((Integer) object);
				output.add(id);
			}
		} catch (IOException e) {
			e.printStackTrace();
			return new HashSet<>();
		}

		return output;
	}

	private Set<String> getProcessedFiles(String basePath) {
		Set<String> output = new HashSet<>();
		String resultsPath = basePath + File.separator + getResultFolderName();
		File processedClasses = new File(resultsPath);

		File[] files = processedClasses.listFiles();
		for (File f : files) {
			int index = f.getName().lastIndexOf('.');
			if (index >= 0) {
				output.add(f.getName().substring(0, f.getName().lastIndexOf('.')));
			} else {
				output.add(f.getName());
			}
		}
		return output;
	}

	public List<String> getJunitJars() {
		List<String> jars = new ArrayList<>();

		String dropinsDir = IResourceUtils.getDropinsDir();
		String junitDir = dropinsDir + File.separator + "junit_lib";

		String junitPath = junitDir + File.separator + "junit.jar";
		String hamcrestCorePath = junitDir + File.separator + "org.hamcrest.core.jar";
		jars.add(junitPath);
		jars.add(hamcrestCorePath);

		String testRunnerDir = junitDir + File.separator + "testrunner.jar";
		jars.add(testRunnerDir);

		// JUnit5
		String junit5Path = junitDir + File.separator + "junit-platform-console-standalone-1.0.0.jar";
		jars.add(junit5Path);
		String junit5RunnerPath = junitDir + File.separator + "junit-platform-runner-1.0.0.jar";
		jars.add(junit5RunnerPath);

		// TestNG
		String testNG = junitDir + File.separator + "testng-6.0.jar";
		jars.add(testNG);

		return jars;
	}

	private void compileFile(File file, String buildPath) throws Exception {
		String javac = JAVA_HOME + File.separator + BIN_FOLDER + File.separator + "javac";

		ArrayList<String> command = new ArrayList<>();
		command.add(javac);
		command.add("-g");

		List<String> jars = getJunitJars();
		String classpaths = String.join(File.pathSeparator, jars);
		if (!classpaths.isEmpty()) {
			command.add("-cp");
			command.add(classpaths);
		}

		command.add("-d");
		command.add(buildPath);
		command.add(file.getPath());
		runCommand(command);
	}

	private void runCommand(List<String> cmdline) throws Exception {
		ProcessBuilder pb = new ProcessBuilder(cmdline);
		ExecutorService executor = Executors.newFixedThreadPool(2);
		int exitCode = -1;
		try {
			ReadFromStream readStdout = null, readStderr = null;
			Process p = pb.start();
			try {
				readStdout = new ReadFromStream(p.getInputStream());
				readStderr = new ReadFromStream(p.getErrorStream());
				executor.submit(readStdout);
				executor.submit(readStderr);
				boolean ret = p.waitFor(10, TimeUnit.SECONDS);
				if (!ret) {
					throw new RuntimeException("Timeout while waiting for process to finish");
				}
				exitCode = p.exitValue();
			} finally {
				p.destroyForcibly();
			}

			executor.shutdown();
			if (!executor.awaitTermination(1, TimeUnit.SECONDS)) {
				throw new RuntimeException("Timeout while waiting for process to finish");
			}

			if (exitCode != 0) {
				System.out.println(readStderr.getOutput());
				throw new Exception("Command line failed: " + cmdline);
			}
		} catch (IOException | InterruptedException e) {
			String msg = "Failed to compile source file: " + cmdline.get(cmdline.size() - 1);
			throw new RuntimeException(msg, e);
		} finally {
			executor.shutdown();
		}
	}

	public void initializeAppClassPathJunitTest(String className, String methodName) {
		testCase = new TestCase(className, methodName);
		String projectName = Activator.getDefault().getPreferenceStore().getString(TregressionPreference.PROJECT_NAME);
		String bugID = Activator.getDefault().getPreferenceStore().getString(TregressionPreference.BUG_ID);
		d4jConfig = Defects4jProjectConfig.getConfig(projectName, bugID);

		appClassPath = AppClassPathInitializer.initialize(binDirName, testCase, d4jConfig);

		List<String> classPaths = getJunitJars();
		classPaths.add(binDirName);
		appClassPath.setClasspaths(classPaths);

		appClassPath.setWorkingDirectory(binDirName);
		appClassPath.setSourceCodePath(srcDirName);
		appClassPath.setTestCodePath(srcDirName);
	}

	public void initializeAppClassPath(String className) {
		appClassPath = new AppJavaClassPath();

		appClassPath.setJavaHome(JAVA_HOME);
		appClassPath.setAgentLib(INSTRUMENTATION_JAR_PATH);
		MicroBatUtil.setSystemJars(appClassPath);
		appClassPath.setLaunchClass(className);

		List<String> classPaths = getJunitJars();
		classPaths.add(binDirName);
		appClassPath.setClasspaths(classPaths);

		appClassPath.setWorkingDirectory(binDirName);
		appClassPath.setSourceCodePath(srcDirName);
		appClassPath.setTestCodePath(srcDirName);
	}

	private Trace runTarget() {
		List<String> includeLibs = new ArrayList<>();
		List<String> excludeLibs = new ArrayList<>();
		includeLibs.add("*");

		InstrumentationExecutor executor = new InstrumentationExecutor(appClassPath, traceDirName, TRACE_FILE_NAME,
				includeLibs, excludeLibs);
		RunningInfo results = null;
		try {
			results = executor.run();
		} catch (StepLimitException e) {
			throw new RuntimeException("Step limit exceeded", e);
		}

		return results.getMainTrace();
	}

	private void visualizeTrace(Trace trace) {
		Display.getDefault().asyncExec(new Runnable() {
			@Override
			public void run() {
				BuggyTraceView buggyTraceView = TregressionViews.getBuggyTraceView();
				buggyTraceView.setMainTrace(trace);
				buggyTraceView.updateData();
				buggyTraceView.setPairList(new PairList(new ArrayList<>()));
				DiffMatcher diffMatcher = new DiffMatcher(srcDirName, srcDirName, srcDirName, srcDirName);
				diffMatcher.matchCode();
				buggyTraceView.setDiffMatcher(diffMatcher);
			}
		});
	}

	private String getResultFolderName() {
		return RESULTS_FOLDER;
	}

	private Set<Integer> readVisitedLines(String basePath, String className) {
		String filePath = basePath + File.separator + getResultFolderName() + File.separator + className + ".txt";
		Set<Integer> visitedLines = new HashSet<>();

		try {
			File targetResultFile = new File(filePath);
			String content = new String(Files.readAllBytes(targetResultFile.toPath()), StandardCharsets.UTF_8);
			String[] lines = content.split(System.lineSeparator());
			for (String line : lines) {
				if (line == null || line.equals("")) {
					break;
				}
				String lineNo = line.split(",")[0];
				Integer lineNumber = Integer.valueOf(lineNo);
				visitedLines.add(lineNumber);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}

		return visitedLines;
	}

	public static class ReadFromStream implements Callable<Void> {
		public ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
		public InputStream inputStream;

		public ReadFromStream(InputStream inputStream) {
			this.inputStream = inputStream;
		}

		@Override
		public Void call() throws Exception {
			byte[] buffer = new byte[1024];
			int bytesRead;

			while ((bytesRead = inputStream.read(buffer)) != -1) {
				byteArrayOutputStream.write(buffer, 0, bytesRead);
			}

			return null;
		}

		public String getOutput() {
			byte[] bytes = byteArrayOutputStream.toByteArray();
			return decodeWithIgnore(bytes);
		}

		public String decodeWithIgnore(byte[] bytes) {
			CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder();

			decoder.onMalformedInput(CodingErrorAction.REPLACE);
			decoder.onUnmappableCharacter(CodingErrorAction.REPLACE);

			StringBuilder sb = new StringBuilder();
			ByteBuffer byteBuffer = ByteBuffer.wrap(bytes);
			CharBuffer charBuffer = CharBuffer.allocate(bytes.length);

			decoder.decode(byteBuffer, charBuffer, true);
			charBuffer.flip();
			sb.append(charBuffer);

			decoder.flush(charBuffer);
			charBuffer.flip();
			sb.append(charBuffer);

			return sb.toString();
		}
	}

	public Map<String, Integer> getSlicingCriteria(String basePath, String fileName) {
		String filePath = basePath + File.separator + fileName;
		Map<String, Integer> criteria = new HashMap<>();

		try {
			File contentFile = new File(filePath);
			String content = new String(Files.readAllBytes(contentFile.toPath()), StandardCharsets.UTF_8);
			String[] lines = content.split("\n");
			for (String line : lines) {
				if (line == null || line.equals("")) {
					break;
				}
				String bugName = line.split(":")[0];
				Integer lineNumber = Integer.valueOf(line.split(":")[1].trim());
				criteria.put(bugName, lineNumber);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}

		return criteria;
	}
}
