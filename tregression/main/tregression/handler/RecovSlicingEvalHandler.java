package tregression.handler;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
import microbat.preference.RecovSlicingPreference;
import microbat.tracerecov.TraceRecoverer;
import microbat.tracerecov.autoprompt.incontextlearning.CompilationFailureException;
import microbat.tracerecov.autoprompt.incontextlearning.InContextExecutor.ReadFromStream;
import microbat.tracerecov.executionsimulator.ExecutionSimulator;
import microbat.tracerecov.executionsimulator.ExecutionSimulatorFactory;
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

/**
 * This handler is responsible for running recov slicing on the given dataset.
 * 
 * @author HongshuW
 */
public class RecovSlicingEvalHandler extends AbstractHandler {

	public static final String SRC_FOLDER = "src";
	public static final String BIN_FOLDER = "bin";
	public static final String TRACE_FOLDER = "trace";
	public static final String TRACE_FILE_NAME = "trace";
	public static final String METHOD_NAME = "testMainLogic";
	public static final String COMPILE_ERRORS = "tc_with_compilation_error.txt";
	public static final String MISMATCHES = "mismatched_ids.json";
	public static final String RESULTS_FOLDER = "slicing_results";
	public static final String B1_FOLDER = "RQ3_baseline1";
	public static final String B2_FOLDER = "RQ3_baseline2";
	public static final String B3_FOLDER = "RQ3_baseline3";
	public static final String JAVA_HOME = Activator.getDefault().getPreferenceStore()
			.getString(MicrobatPreference.JAVA7HOME_PATH);
	public static final String INSTRUMENTATION_JAR_PATH = IResourceUtils.getResourceAbsolutePath(Activator.PLUGIN_ID,
			"lib") + File.separator + "instrumentator.jar";

	public String sliceDatasetPath = Activator.getDefault().getPreferenceStore()
			.getString(RecovSlicingPreference.SLICE_DATASET_PATH);
	public String bugsToRun = Activator.getDefault().getPreferenceStore()
			.getString(RecovSlicingPreference.SLICE_BUGS_TO_RUN);
	public String isJunitStr = Activator.getDefault().getPreferenceStore().getString(RecovSlicingPreference.IS_JUNIT);
	public String enableIncontextLearningStr = Activator.getDefault().getPreferenceStore()
			.getString(RecovSlicingPreference.ENABLE_IN_CONTEXT_LEARNING);
	public String enableAliasInferStr = Activator.getDefault().getPreferenceStore()
			.getString(RecovSlicingPreference.ENABLE_ALIAS_INFERENCE);

	private String srcDirName;
	private String binDirName;
	private String traceDirName;
	private TestCase testCase;
	private ProjectConfig d4jConfig;
	private AppJavaClassPath appClassPath;
	private ExecutionSimulator executionSimulator;
	private TraceRecoverer traceRecoverer;

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

				Set<String> classesToRun = readContent(sliceDatasetPath, bugsToRun);
				Set<String> idsToSkip = getMismatchFiles(sliceDatasetPath);
				Set<String> compilationErrors = readContent(sliceDatasetPath, COMPILE_ERRORS);
				Set<String> processedFiles = getProcessedFiles(sliceDatasetPath);

				// set up trace recoverer
				executionSimulator = ExecutionSimulatorFactory.getExecutionSimulator();
				traceRecoverer = new TraceRecoverer();

				File folder = new File(srcDirName);
				if (folder.exists() && folder.isDirectory()) {
					File[] files = folder.listFiles();
					if (files != null) {
						for (File file : files) {
							String className = file.getName().substring(0, file.getName().lastIndexOf('.'));

							/* nd-dataset settings */
							String id = "";
							boolean idStarted = false;
							for (int i = 0; i < className.length(); i++) {
								char c = className.charAt(i);
								if (c == '0' && !idStarted) {
									continue;
								} else if (c != '0') {
									idStarted = true;
									id += c;
								} else if (c == 'T') {
									break;
								}
							}

							if (idsToSkip.contains(id) || processedFiles.contains(className)
									|| compilationErrors.contains(className) || !classesToRun.contains(className)) {
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

								int criterionCounter = 1;

								while (criterionCounter < steps.size()) {
									TraceNode slicingCriterion = steps.get(criterionCounter);
									List<VarValue> readVars = slicingCriterion.getReadVariables();
									if (visitedLines.contains(slicingCriterion.getLineNumber())) {
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
										System.out.println("slicing destination without recovery:");
										if (dataDom == null) {
											System.out.println("none");
										} else {
											System.out.println(dataDom.getOrder());
										}

										/*
										 * 1. Variable Expansion
										 */
										try {
											executionSimulator.expandVariable(v, slicingCriterion, null);
										} catch (IOException e) {
											e.printStackTrace();
										}

										/*
										 * 2. Recover Dependency
										 */
										System.out.println("slicing destination after recovery:");
										Set<Integer> dataDominatorsAfterRecovery = new HashSet<>();
										for (VarValue targetVar : v.getAllDescedentChildren()) {
											traceRecoverer.recoverDataDependency(slicingCriterion, targetVar, v);
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
							} catch (CompilationFailureException e) {
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

	private void compileFile(File file, String buildPath) throws CompilationFailureException {
		String javac = JAVA_HOME + File.separator + BIN_FOLDER + File.separator + "javac";

		ArrayList<String> command = new ArrayList<>();
		command.add(javac);
		command.add("-g");

		List<String> jars = MicroBatUtil.getJunitJars();
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

	private void runCommand(List<String> cmdline) throws CompilationFailureException {
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
				throw new CompilationFailureException("Command line failed: " + cmdline);
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

		List<String> classPaths = MicroBatUtil.getJunitJars();
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

		List<String> classPaths = MicroBatUtil.getJunitJars();
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
		boolean enableIncontextLearning = enableIncontextLearningStr != null
				&& enableIncontextLearningStr.equals("true");
		boolean enableAliasInfer = enableAliasInferStr != null && enableAliasInferStr.equals("true");
		if (enableIncontextLearning) {
			if (enableAliasInfer) {
				return RESULTS_FOLDER; // all features enabled, use default result folder "slicing_results"
			} else {
				return B2_FOLDER; // Baseline 2: enable in-context learning only
			}
		} else {
			if (enableAliasInfer) {
				return B3_FOLDER; // Baseline 3: enable alias inference only
			} else {
				return B1_FOLDER; // Baseline 1: disable in-context learning and alias inference
			}
		}
	}
}
