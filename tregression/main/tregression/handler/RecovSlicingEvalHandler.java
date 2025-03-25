package tregression.handler;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
import microbat.instrumentation.CommonParams;
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
	public static final String LIB_FOLDER = "lib";
	public static final String TRACE_FOLDER = "trace";
	public static final String TRACE_FILE_NAME = "trace";
	public static final String METHOD_NAME = "testMainLogic";
	public static final String COMPILE_ERRORS = "tc_with_compilation_error.txt";
	public static final String RUNTIME_ERRORS = "tc_with_runtime_error.txt";
	public static final String SKIP = "skip.txt";
	public static final String MISMATCHES = "mismatched_ids.json";
	public static final String SLICING_CRITERIA_INFO = "info.txt";
	public static final String CRITICAL_VAR = "real-var.txt";
	public static final String RESULTS_FOLDER = "slicing_results";
	public static final String B1_FOLDER = "RQ3_baseline1";
	public static final String B2_FOLDER = "RQ3_baseline2";
	public static final String B3_FOLDER = "RQ3_baseline3";
	public static final String RE_EXECUTION_FOLDER = "RQ1_re_execution";
	public static final String JAVA_HOME = Activator.getDefault().getPreferenceStore()
			.getString(MicrobatPreference.JAVA7HOME_PATH);
	public static final String INSTRUMENTATION_JAR_PATH = IResourceUtils.getResourceAbsolutePath(Activator.PLUGIN_ID,
			"lib") + File.separator + "instrumentator.jar";
	public static final String DEPENDENCY_FILE = "dependencies.txt";

	public String sliceDatasetPath = Activator.getDefault().getPreferenceStore()
			.getString(RecovSlicingPreference.SLICE_DATASET_PATH);
	public String bugsToRun = Activator.getDefault().getPreferenceStore()
			.getString(RecovSlicingPreference.SLICE_BUGS_TO_RUN);
	public String isJunitStr = Activator.getDefault().getPreferenceStore().getString(RecovSlicingPreference.IS_JUNIT);
	public String enableIncontextLearningStr = Activator.getDefault().getPreferenceStore()
			.getString(RecovSlicingPreference.ENABLE_IN_CONTEXT_LEARNING);
	public String enableAliasInferStr = Activator.getDefault().getPreferenceStore()
			.getString(RecovSlicingPreference.ENABLE_ALIAS_INFERENCE);
	public String javac = JAVA_HOME + File.separator + BIN_FOLDER + File.separator + "javac";
	public boolean isGuava = sliceDatasetPath.contains("guava");
	public boolean isGeneratedDataset = sliceDatasetPath.contains("generated") || isGuava
			|| sliceDatasetPath.contains("benchmark-");
	public boolean isMultiFile = sliceDatasetPath.contains("multi_files");
	public boolean isReexecution = Activator.getDefault().getPreferenceStore()
			.getString(RecovSlicingPreference.ENABLE_RE_EXECUTION).equals("true");

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
				Set<String> runtimeErrors = readContent(sliceDatasetPath, RUNTIME_ERRORS);
				Set<String> processedFiles = getProcessedFiles(sliceDatasetPath);
				Set<String> classesToSkip = readContent(sliceDatasetPath, SKIP);
				Map<String, Integer> singleFileCriteria = readSingleFileSlicingCriteria(sliceDatasetPath,
						SLICING_CRITERIA_INFO);
				Map<String, Map<String, Integer>> multiFileCriteria = readMultiFileSlicingCriteria(sliceDatasetPath,
						SLICING_CRITERIA_INFO);

				// set up trace recoverer
				if (!isReexecution) {
					executionSimulator = ExecutionSimulatorFactory.getExecutionSimulator();
					traceRecoverer = new TraceRecoverer();
				}

				File folder = new File(srcDirName);
				if (folder.exists() && folder.isDirectory()) {
					File[] files = folder.listFiles();
					if (files != null) {
						for (File file : files) {
							String className = isGeneratedDataset ? file.getName()
									: file.getName().substring(0, file.getName().lastIndexOf('.'));

							// read critical variable
							String criticalVar = "";
							if (isGeneratedDataset) {
								try {
									criticalVar = getCriticalVar(srcDirName + File.separator + className, CRITICAL_VAR);
								} catch (IOException e) {
									// skip file for now
									try {
										File resultFile = new File(
												sliceDatasetPath + File.separator + "without_critical_var.txt");
										FileWriter resultWriter = new FileWriter(resultFile, true);
										resultWriter.append(className);
										resultWriter.close();
									} catch (IOException e1) {
										e1.printStackTrace();
										continue;
									}
								}
							}

							int singleCriteria = (isGeneratedDataset && !isMultiFile)
									? (singleFileCriteria.containsKey(className) ? singleFileCriteria.get(className)
											: -1)
									: -1;
							Map<String, Integer> MultiCriteria = (isGeneratedDataset && isMultiFile)
									? multiFileCriteria.get(className)
									: null;

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
									|| runtimeErrors.contains(className) || processedFiles.contains(className)
									|| (!classesToRun.isEmpty() && !classesToRun.contains(className))
									|| classesToSkip.contains(className)) {
								continue;
							}

							try {
								System.out.println("compiling " + file.getName() + " ...");
								if (isGeneratedDataset) {
									compileFolder(file, binDirName, file.getName());
								} else {
									compileFile(file, binDirName);
								}

								List<String> dependencies = null;
								if (isReexecution) {
									dependencies = readDependencies(file.getPath());
								}

								System.out.println("collecting trace...");
								if (isJunitStr != null && isJunitStr.equals("true")) {
									initializeAppClassPathJunitTest(className, METHOD_NAME);
								} else if (isGeneratedDataset) {
									initializeAppClassPathGeneratedDataset(className);
								} else {
									initializeAppClassPath(className);
								}
								Trace trace = runTarget(dependencies);
								visualizeTrace(trace);

								System.out.println("dynamic slicing...");
								List<TraceNode> steps = trace.getExecutionList();
								Set<Integer> visitedLines = new HashSet<>();

//								if (processedFiles.contains(className)) {
//									visitedLines = readVisitedLines(sliceDatasetPath, className);
//								}

								int criterionCounter = 1;

								while (criterionCounter < steps.size()) {
									TraceNode slicingCriterion = steps.get(criterionCounter);
									int lineNo = slicingCriterion.getLineNumber();
									String fileContainingCriterion = slicingCriterion.getClassCanonicalName();
									if (isMultiFile) {
										if (!(MultiCriteria.containsKey(fileContainingCriterion)
												&& MultiCriteria.get(fileContainingCriterion) == lineNo)) {
											criterionCounter++;
											continue;
										}
									} else {
										if (singleCriteria != -1 && singleCriteria != lineNo) {
											criterionCounter++;
											continue;
										}
									}

									List<VarValue> readVars = slicingCriterion.getReadVariables();
									if (visitedLines.contains(lineNo)) {
										criterionCounter++;
										continue;
									}
									visitedLines.add(slicingCriterion.getLineNumber());

									/*
									 * 1. Identify Critical Variable
									 */
									String criticalRootVarName = "";
									if (!isReexecution) {
										criticalRootVarName = executionSimulator.getCriticalVar(slicingCriterion,
												criticalVar);
									}

									for (VarValue v : readVars) {
										if (!isReexecution) {
											if (!v.getVarName().equals(criticalRootVarName)) {
												continue;
											}
										}
										System.out.println("slicing criterion:");
										System.out.println(slicingCriterion.getOrder());
										System.out.println(v.getVarName());

										/*
										 * 2. Variable Expansion
										 */
										try {
											if (!isReexecution) {
												executionSimulator.expandVariable(v, slicingCriterion, null, null);
											}
										} catch (IOException e) {
											e.printStackTrace();
										}

										/*
										 * 3. Identify Critical Field
										 */
										String criticalFieldName = "";
										if (!isReexecution) {
											criticalFieldName = executionSimulator.getCriticalField(v, slicingCriterion,
													criticalVar);
										}

										/*
										 * 4. Recover Dependency
										 */
										System.out.println("slicing destination after recovery:");
										Set<TraceNode> dataDominatorsAfterRecovery = new HashSet<>();
										VarValue criticalVarValue = null;
										for (VarValue targetVar : v.getAllDescedentChildren()) {
											if (!isReexecution) {
												if (!targetVar.getVarName().equals(criticalFieldName)) {
													continue;
												}
												criticalVarValue = targetVar;
												traceRecoverer.recoverDataDependency(slicingCriterion, targetVar, v);
											}
											TraceNode dataDominator = trace.findProducer(targetVar, slicingCriterion);
											if (dataDominator != null) {
												dataDominatorsAfterRecovery.add(dataDominator);
												System.out.println(dataDominator.getOrder());
											}
										}

										// original data dominator
										TraceNode dataDom = trace.findDataDependency(slicingCriterion, v);
										System.out.println("slicing destination without recovery:");
										if (dataDom == null) {
											System.out.println("none");
										} else {
											System.out.println(dataDom.getOrder());
										}
										if (dataDominatorsAfterRecovery.isEmpty() && dataDom != null) {
											dataDominatorsAfterRecovery.add(dataDom);
										}

										// write result
										System.out.println("writing results...");
										StringBuilder result = new StringBuilder();
										if (isMultiFile) {
											result.append(fileContainingCriterion + ",");
										}
										result.append(lineNo + ",");
										if (criticalVarValue == null) {
											result.append(v.getVarName() + ",");
										} else {
											String name = getCascadingName(criticalVarValue, v);
											result.append(name + ",");
										}
										StringBuilder slicingDestinations = new StringBuilder("[");
										for (TraceNode i : dataDominatorsAfterRecovery) {
											if (isMultiFile) {
												slicingDestinations.append(i.getClassCanonicalName());
												slicingDestinations.append(" ");
												slicingDestinations.append(i.getLineNumber());
											} else {
												slicingDestinations.append(i.getLineNumber());
											}
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
									criterionCounter++;
								}
							} catch (Exception e) {
								System.out.println("An error occurred: " + e.getMessage());
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

	private String getCascadingName(VarValue targetVar, VarValue rootVar) {
		String name = targetVar.getVarName();

		VarValue temp = targetVar;

		while (temp != rootVar) {
			temp = temp.getParents().get(0);
			name = temp.getVarName() + "." + name;
		}

		return name;
	}

	private List<String> readDependencies(String folder) {
		List<String> dependencies = new ArrayList<>();
		String filePath = folder + File.separator + DEPENDENCY_FILE;
		if (!new File(filePath).exists()) {
			System.err.println("Dependency file not found: " + filePath);
			return dependencies;
		}
		try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
			String line = br.readLine();
			while (line != null) {
				String linestrip = line.strip();
				if (!linestrip.isEmpty()) {
					dependencies.add(line);
				}
				line = br.readLine();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return dependencies;
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
		if (files == null || files.length == 0) {
			return output;
		}
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
		ArrayList<String> command = new ArrayList<>();
		command.add(javac);
		command.add("-g");

		List<String> jars = MicroBatUtil.getJunitJars();
		if (isGuava) {
			jars.add(
					sliceDatasetPath + File.pathSeparator + LIB_FOLDER + File.separator + "guava-libs-package-all.jar");
		}
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

	private void compileFolder(File folder, String buildPath, String projectName) throws CompilationFailureException {
		ArrayList<String> command = new ArrayList<>();
		command.add(javac);
		command.add("-g");

		List<String> jars = MicroBatUtil.getJunitJars();
		if (isGuava) {
			jars.add(sliceDatasetPath + File.separator + LIB_FOLDER + File.separator + "guava-libs-package-all.jar");
		}
		String classpaths = String.join(File.pathSeparator, jars);
		if (!classpaths.isEmpty()) {
			command.add("-cp");
			command.add(classpaths);
		}

		command.add("-d");
		String binPath = buildPath + File.separator + projectName;
		File buildFolder = new File(binPath);
		if (!buildFolder.exists()) {
			buildFolder.mkdir();
		}
		command.add(binPath);
		command.add(folder.getPath() + File.separator + "*.java");
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

	public void initializeAppClassPathGeneratedDataset(String projectName) {
		appClassPath = new AppJavaClassPath();

		appClassPath.setJavaHome(JAVA_HOME);
		appClassPath.setAgentLib(INSTRUMENTATION_JAR_PATH);
		MicroBatUtil.setSystemJars(appClassPath);
		appClassPath.setLaunchClass("Main");

		List<String> classPaths = MicroBatUtil.getJunitJars();
		if (isGuava) {
			classPaths.add(
					sliceDatasetPath + File.separator + LIB_FOLDER + File.separator + "guava-libs-package-all.jar");
		}
		String binPath = binDirName + File.separator + projectName;
		String srcPath = srcDirName + File.separator + projectName;
		classPaths.add(binPath);
		appClassPath.setClasspaths(classPaths);

		appClassPath.setWorkingDirectory(binPath);
		appClassPath.setSourceCodePath(srcPath);
		appClassPath.setTestCodePath(srcPath);
	}

	private Trace runTarget(List<String> dependencies) {
		List<String> includeLibs = new ArrayList<>();
		List<String> excludeLibs = new ArrayList<>();
		if (isReexecution) {
			includeLibs.add("^^^");
			includeLibs.add("Main*");
		} else {
			includeLibs.add("*");
		}
		if (dependencies != null) {
			includeLibs.addAll(dependencies);
		}
		for (String lib : includeLibs) {
			System.out.println("include: " + lib);
		}

		InstrumentationExecutor executor = new InstrumentationExecutor(appClassPath, traceDirName, TRACE_FILE_NAME,
				includeLibs, excludeLibs);
		if (isReexecution) {
			executor.getAgentRunner().setToTenSecondsTimeout = true;
			executor.getAgentRunner().addAgentParam("no_exclude_all_java", "true");
		}
		executor.getAgentRunner().addAgentParam(CommonParams.OPT_FORCE_EXIT_WITHOUT_WAIT_OTHER_THREADS, "true");
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
		if (isReexecution) {
			return RE_EXECUTION_FOLDER;
		}
		if (enableIncontextLearning) {
//			if (enableAliasInfer) {
//				return RESULTS_FOLDER; // all features enabled, use default result folder "slicing_results"
//			} else {
//				return B2_FOLDER; // Baseline 2: enable in-context learning only
//			}
			return RESULTS_FOLDER;
		} else {
//			if (enableAliasInfer) {
//				return B3_FOLDER; // Baseline 3: enable alias inference only
//			} else {
//				return B1_FOLDER; // Baseline 1: disable in-context learning and alias inference
//			}
			return B1_FOLDER;
		}
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

	private Map<String, Integer> readSingleFileSlicingCriteria(String basePath, String fileName) {
		String filePath = basePath + File.separator + fileName;
		Map<String, Integer> criteria = new HashMap<>();

		boolean isMultiFile = sliceDatasetPath.contains("multi_files");
		if (isMultiFile) {
			return criteria;
		}

		try {
			File targetResultFile = new File(filePath);
			String content = new String(Files.readAllBytes(targetResultFile.toPath()), StandardCharsets.UTF_8);
			String[] lines = content.split(System.lineSeparator());
			boolean isHeader = true;
			for (String line : lines) {
				if (isHeader) {
					isHeader = false;
					continue;
				}
				if (line == null || line.equals("")) {
					break;
				}
				String projectName = line.split(",")[0];
				Integer lineNumber = Integer.valueOf(line.split(",")[1]);
				criteria.put(projectName, lineNumber);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}

		return criteria;
	}

	private Map<String, Map<String, Integer>> readMultiFileSlicingCriteria(String basePath, String fileName) {
		String filePath = basePath + File.separator + fileName;
		Map<String, Map<String, Integer>> criteria = new HashMap<>();

		boolean isMultiFile = sliceDatasetPath.contains("multi_files");
		if (!isMultiFile) {
			return criteria;
		}

		try {
			File targetResultFile = new File(filePath);
			String content = new String(Files.readAllBytes(targetResultFile.toPath()), StandardCharsets.UTF_8);
			String[] lines = content.split(System.lineSeparator());
			boolean isHeader = true;
			for (String line : lines) {
				if (isHeader) {
					isHeader = false;
					continue;
				}
				if (line == null || line.equals("")) {
					break;
				}
				String projectName = line.split(",")[0];
				String fName = line.split(",")[1];
				fName = fName.substring(0, fName.lastIndexOf("."));
				Integer lineNumber = Integer.valueOf(line.split(",")[2]);
				Map<String, Integer> pair = new HashMap<>();
				pair.put(fName, lineNumber);
				criteria.put(projectName, pair);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}

		return criteria;
	}

	private String getCriticalVar(String basePath, String fileName) throws IOException {
		String filePath = basePath + File.separator + fileName;
		String criticalVar = "";

		try {
			File criticalVarFile = new File(filePath);
			String content = new String(Files.readAllBytes(criticalVarFile.toPath()), StandardCharsets.UTF_8);
			String[] lines = content.split(System.lineSeparator());
			for (String line : lines) {
				if (line == null || line.equals("")) {
					break;
				}
				criticalVar = line.strip();
			}
		} catch (IOException e) {
			throw e;
		}

		return criticalVar;
	}
}
