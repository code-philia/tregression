package tregression.handler;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.eclipse.swt.widgets.Display;
import org.json.JSONArray;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import microbat.Activator;
import microbat.codeanalysis.runtime.InstrumentationExecutor;
import microbat.codeanalysis.runtime.StepLimitException;
import microbat.instrumentation.CommonParams;
import microbat.instrumentation.output.RunningInfo;
import microbat.model.trace.Trace;
import microbat.model.trace.TraceNode;
import microbat.model.value.VarValue;
import microbat.runconfigs.ExecutionInfo;
import microbat.runconfigs.TraceRecovRunConfig;
import microbat.tracerecov.TraceRecoverer;
import microbat.tracerecov.autoprompt.incontextlearning.CompilationFailureException;
import microbat.tracerecov.autoprompt.incontextlearning.InContextExecutor.ReadFromStream;
import microbat.tracerecov.executionsimulator.ExecutionSimulator;
import microbat.tracerecov.executionsimulator.ExecutionSimulatorFactory;
import microbat.util.MicroBatUtil;
import sav.strategies.dto.AppJavaClassPath;
import tregression.aliastracking.HeapObjects;
import tregression.empiricalstudy.TestCase;
import tregression.empiricalstudy.config.Defects4jProjectConfig;
import tregression.empiricalstudy.config.ProjectConfig;
import tregression.model.PairList;
import tregression.preference.TregressionPreference;
import tregression.separatesnapshots.AppClassPathInitializer;
import tregression.separatesnapshots.DiffMatcher;
import tregression.util.JarVersionReader;
import tregression.views.BuggyTraceView;
import tregression.views.TregressionViews;

@Slf4j
public class RecovSlicingEvalRunner {
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
    public static final String DEPENDENCY_FILE = "dependencies.txt";
    public static final String INFO_JSON_NAME = "info.json";
    public static final String OUTPUT_ERROR_FILE = "output_error.md";

    @Getter
    private CountDownLatch finished;

    @Getter
    private volatile FinishedList finishedList;

    @Getter
    @AllArgsConstructor
    public static class FinishedList {
        private final String name;
        private final FinishedList next;
    }

    private String srcDirName;
    private String binDirName;
    private String traceDirName;
    private TestCase testCase;
    private ProjectConfig d4jConfig;
    private AppJavaClassPath appClassPath;
    private ExecutionSimulator executionSimulator;
    private TraceRecoverer traceRecoverer;
    private String outputFolderOverride;

    private String javaHome;
    private String javac;
    private boolean isJunit;
    private boolean isReexecution;
    private String sliceDatasetPath;
    private String instrumentatorJarPath;
    private boolean isGuava;
    private boolean enableIncontextLearning;
    private boolean enableAliasInfer;
    private Set<String> onlyRun;
    private Set<String> idsToSkip;
    private boolean isGeneratedDataset;
    private boolean isMultiFile;

    private String processMethodUrl;

    private FileWriter errorWriter;

    private Gson gson = new GsonBuilder().serializeNulls().setPrettyPrinting().create();

    public RecovSlicingEvalRunner() {
        this.finished = new CountDownLatch(1);
        this.finishedList = new FinishedList(null, null);
    }

    public void execute(ExecutionInfo<TraceRecovRunConfig> exeinfo) {
        try {
            executeInner(exeinfo);
        } finally {
            finished.countDown();
            try {
                if(ExecutionSimulator.defInfWriter != null) {
                    ExecutionSimulator.defInfWriter.close();
                    ExecutionSimulator.defInfWriter = null;
                }
            } catch (IOException e) {
                log.error("Failed to close defInfWriter", e);
            }
        }
    }

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss-SSS")
            .withZone(ZoneId.systemDefault());

    public void exportingTregressionVersionInfo() {
        String timestamp = FORMATTER.format(Instant.now());
        String outputFile = sliceDatasetPath + File.separator
                + getResultFolderName() + File.separator + timestamp + "-version";
        String outputFolder = sliceDatasetPath + File.separator
                + getResultFolderName();
        File folder = new File(outputFolder);
        folder.mkdirs();
        try (FileWriter writer = new FileWriter(outputFile)) {
            writer.write(JarVersionReader.buildVersionString());
        } catch (IOException e) {
            log.error("Failed to write version info", e);
            throw new RuntimeException("Failed to write version info", e);
        }
    }

    public void executeInner(ExecutionInfo<TraceRecovRunConfig> exeinfo) {
        MicroBatUtil.initJarFiles();

        TraceRecovRunConfig config = exeinfo.getConfig();

        if (config.getDumpDefInf() != null) {
            try {
                ExecutionSimulator.defInfWriter = new FileWriter(config.getDumpDefInf(), true);
                ExecutionSimulator.defInfWriter.write("{\"___new___\": \"\"}\n");
                ExecutionSimulator.defInfWriter.flush();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } else {
            ExecutionSimulator.defInfWriter = null;
        }

        config.getJdkConfig().setJavaHome(exeinfo.resolvePath(config.getJdkConfig().getJavaHome()));
        config.setDatasetFolder(exeinfo.resolvePath(config.getDatasetFolder()));
        config.setDumpGptPath(exeinfo.resolvePath(config.getDumpGptPath()));
        config.setInContextLearningPath(exeinfo.resolvePath(config.getInContextLearningPath()));

        config.setToGlobal();

        outputFolderOverride = config.getResultFolderName();
        javaHome = config.getJdkConfig().getJavaHome();
        javac = javaHome + File.separator + "bin" + File.separator + "javac";
        sliceDatasetPath = config.getDatasetFolder();
        isJunit = config.isUtilizeJunitInsteadOfMain();
        isGuava = config.isGuava();
        enableIncontextLearning = config.isEnableInContextLearning();
        enableAliasInfer = config.isEnableAliasInference();
        isGeneratedDataset = config.isGeneratedDataset();
        isMultiFile = config.isMultiFiles();
        isReexecution = config.isEnableReExecution();

        instrumentatorJarPath = MicroBatUtil.getAgentLib();

        srcDirName = sliceDatasetPath + File.separator + SRC_FOLDER;
        binDirName = sliceDatasetPath + File.separator + BIN_FOLDER;
        traceDirName = sliceDatasetPath + File.separator + TRACE_FOLDER;

        processMethodUrl = config.getMethodProcessUrl();

        exportingTregressionVersionInfo();

        try {
            File errorFile = new File(sliceDatasetPath + File.separator
                    + OUTPUT_ERROR_FILE);
            errorWriter = new FileWriter(errorFile, true);
        } catch (IOException e) {
            log.error("Failed to create error file: {}", e.getMessage());
            throw new RuntimeException("Failed to create error file", e);
        }

        if (config.getOnlyRun() == null) {
            onlyRun = null;
        } else {
            onlyRun = new HashSet<>();
            for (String s : config.getOnlyRun()) {
                onlyRun.add(s);
            }
        }
        idsToSkip = new HashSet<>();
        if (config.getSkip() != null) {
            for (String s : config.getSkip()) {
                idsToSkip.add(s);
            }
        }

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
                    String fileName = file.getName();

                    if (onlyRun != null && !onlyRun.contains(fileName)) {
                        continue;
                    }
                    if (idsToSkip.contains(fileName)) {
                        continue;
                    }

                    try {
                        String className = isGeneratedDataset ? file.getName()
                                : file.getName().substring(0, file.getName().lastIndexOf('.'));
                        File resultFile = new File(sliceDatasetPath + File.separator
                                + getResultFolderName() + File.separator + className + ".txt");
                        if (resultFile.exists()) {
                            log.info("Already processed file: {}", fileName);
                            continue;
                        }

                        executeFile(file);
                    } finally {
                        this.finishedList = new FinishedList(fileName, this.finishedList);
                    }

                }
            }
        } else {
            log.error("Source folder does not exist: {}", srcDirName);
        }

        try {
            errorWriter.close();
        } catch (IOException e) {
            log.error("Failed to close error writer: {}", e.getMessage());
        }
    }

    private synchronized void writeErrorFile(File processedFile, Exception e) {
        log.error("Error processing file: {}", processedFile.getName(), e);
        try {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            pw.format("---------------------------------------------\n");
            pw.format("# Error\n");
            pw.format("File: %s\n", processedFile.getName());
            pw.format("Path: %s\n", processedFile.getAbsolutePath());
            pw.format("Error: %s\n", e.getMessage());
            e.printStackTrace(pw);
            pw.format("\n\n");
            errorWriter.append(sw.toString());
            errorWriter.flush();
        } catch (Exception ex) {
            log.error("Failed to write error file: {}", ex.getMessage());
        }
    }

    private void executeFile(File file) {
        try {
            ExecutionSimulator.dumpTaskName(file.getAbsolutePath());

            String className = isGeneratedDataset ? file.getName()
                    : file.getName().substring(0, file.getName().lastIndexOf('.'));

            GeneratedDataInfo info = null;

            // read critical variable
            int singleCriteria = -1;
            Map<String, Integer> MultiCriteria = null;
            String criticalVar = "";
            if (isGeneratedDataset) {
                try (Reader r = new FileReader(
                        file.toPath().resolve(INFO_JSON_NAME).toFile())) {
                    info = gson.fromJson(r, GeneratedDataInfo.class);
                    ExecutionSimulator.targetFileName = info.getWrite_file();
                    ExecutionSimulator.targetLineNumber = info.getWrite_idx();
                    criticalVar = info.getName();
                }

                MultiCriteria = new HashMap<>();
                String readFile = info.getRead_file();
                readFile = readFile.substring(0, readFile.lastIndexOf('.'));
                MultiCriteria.put(readFile, info.getRead_idx());
            }

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
            if (isJunit) {
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

            HeapObjects objects = new HeapObjects(processMethodUrl);
            // objects.processTrace(steps);
            // traceRecoverer.setAliasInferencer(objects);

            int criterionCounter = 1;

            List<ResultItem> resultItems = new ArrayList<>();
            List<String> criticleVarPredictions = new ArrayList<>();

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
                    if (!isReexecution) {
                        executionSimulator.expandVariable(v, slicingCriterion, null, null, criticalVar);
                    }

                    /*
                     * 3. Identify Critical Field
                     */
                    String criticalFieldName = "";
                    if (!isReexecution) {
                        criticalFieldName = executionSimulator.getCriticalField(v, slicingCriterion,
                                criticalVar);
                        criticleVarPredictions.add(criticalRootVarName + "##" + criticalFieldName);
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
                            traceRecoverer.recoverDataDependency(slicingCriterion, targetVar, v, criticalVar);
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

                    ResultItem resultItem = new ResultItem();
                    resultItem.setSrcFile(fileContainingCriterion);
                    resultItem.setSrcLine(String.valueOf(lineNo));
                    if (criticalVarValue == null) {
                        resultItem.setSlicingVar(v.getVarName());
                    } else {
                        String name = getCascadingName(criticalVarValue, v);
                        resultItem.setSlicingVar(name);
                    }
                    resultItem.setTgts(new ArrayList<>());
                    for (TraceNode i : dataDominatorsAfterRecovery) {
                        ResultItemTgt tgt = new ResultItemTgt();
                        tgt.setTgtFile(i.getClassCanonicalName());
                        tgt.setTgtLine(String.valueOf(i.getLineNumber()));
                        resultItem.getTgts().add(tgt);
                    }
                    resultItems.add(resultItem);

                    // write result
                    // System.out.println("writing results...");
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
                        File slicePathFolder = new File(sliceDatasetPath + File.separator
                                + getResultFolderName());
                        slicePathFolder.mkdirs();

                        File resultFile = new File(sliceDatasetPath + File.separator
                                + getResultFolderName() + File.separator + className + ".txt");
                        log.info("Writing result to: {}", resultFile.getAbsolutePath());
                        FileWriter resultWriter = new FileWriter(resultFile, true);
                        resultWriter.append(result.toString());
                        resultWriter.close();
                    } catch (IOException e) {
                        log.error("Failed to write result file: {}", e.getMessage());
                    }
                }
                criterionCounter++;
            }

            File slicePathFolder = new File(sliceDatasetPath + File.separator
                    + getResultFolderName());
            slicePathFolder.mkdirs();
            File resultFile = new File(sliceDatasetPath + File.separator
                    + getResultFolderName() + File.separator + className + ".json");
            log.info("Writing result to: {}", resultFile.getAbsolutePath());
            FileWriter resultWriter = new FileWriter(resultFile);

            AllResults allResults = new AllResults();
            allResults.setPredictedCriticalVar(criticleVarPredictions);
            allResults.setResults(resultItems);

            resultWriter.append(gson.toJson(allResults));
            resultWriter.close();
        } catch (Exception e) {
            writeErrorFile(file, e);
        }
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    private static class AllResults {
        private List<String> predictedCriticalVar;
        private List<ResultItem> results;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResultItem {
        private String srcFile;
        private String srcLine;
        private String slicingVar;
        private List<ResultItemTgt> tgts;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResultItemTgt {
        private String tgtFile;
        private String tgtLine;
    }

    @Getter
    @Setter
    public static class GeneratedDataInfo {
        private String name;
        private String read_file;
        private int read_idx;
        private String write_file;
        private int write_idx;
        private String loc;
        private List<String> file_names;
        private String category;
        private String original_name;
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
        if (isJunit) {
            output.add(fileName);
            output.add(BIN_FOLDER);
        }

        String filePath = basePath + File.separator + fileName;
        File problematicClasses = new File(filePath);

        try {
            String content = new String(Files.readAllBytes(problematicClasses.toPath()),
                    StandardCharsets.UTF_8);
            String[] files = content.split("\n");
            for (String f : files) {
                f = f.strip();
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
            String content = new String(Files.readAllBytes(mismatchFiles.toPath()),
                    StandardCharsets.UTF_8);

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
                    sliceDatasetPath + File.pathSeparator + LIB_FOLDER + File.separator +
                            "guava-libs-package-all.jar");
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

    private void compileFolder(File folder, String buildPath, String projectName)
            throws CompilationFailureException {
        ArrayList<String> command = new ArrayList<>();
        command.add(javac);
        command.add("-g");

        List<String> jars = MicroBatUtil.getJunitJars();
        if (isGuava) {
            jars.add(sliceDatasetPath + File.separator + LIB_FOLDER + File.separator +
                    "guava-libs-package-all.jar");
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
            buildFolder.mkdirs();
        }
        command.add(binPath);

        List<String> sourceFiles = new ArrayList<>();
        for (File f : folder.listFiles()) {
            if (f.isFile() && f.getName().endsWith(".java")) {
                sourceFiles.add(f.getPath());
            }
        }

        for (String sourceFile : sourceFiles) {
            command.add(sourceFile);
        }

        // command.add(folder.getPath() + File.separator + "*.java");
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
                // System.out.println(readStderr.getOutput());
                // throw new CompilationFailureException("Command line failed: " + cmdline);
                String stdout = readStdout.getOutput();
                String stderr = readStderr.getOutput();
                log.error("Command line failed: {}. STDOUT: {}. STDERR: {}", cmdline, stdout, stderr);
            }
        } catch (IOException | InterruptedException e) {
            String msg = "Failed to compile source file: " + cmdline.get(cmdline.size() -
                    1);
            log.error(msg, e);
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

        appClassPath = AppClassPathInitializer.initialize(binDirName, testCase,
                d4jConfig);

        List<String> classPaths = MicroBatUtil.getJunitJars();
        classPaths.add(binDirName);
        appClassPath.setClasspaths(classPaths);

        appClassPath.setWorkingDirectory(binDirName);
        appClassPath.setSourceCodePath(srcDirName);
        appClassPath.setTestCodePath(srcDirName);
    }

    public void initializeAppClassPath(String className) {
        appClassPath = new AppJavaClassPath();

        appClassPath.setJavaHome(javaHome);
        appClassPath.setAgentLib(instrumentatorJarPath);
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

        appClassPath.setJavaHome(javaHome);
        appClassPath.setAgentLib(instrumentatorJarPath);
        MicroBatUtil.setSystemJars(appClassPath);
        appClassPath.setLaunchClass("Main");

        List<String> classPaths = MicroBatUtil.getJunitJars();
        if (isGuava) {
            classPaths.add(
                    sliceDatasetPath + File.separator + LIB_FOLDER + File.separator +
                            "guava-libs-package-all.jar");
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

        InstrumentationExecutor executor = new InstrumentationExecutor(appClassPath,
                traceDirName, TRACE_FILE_NAME,
                includeLibs, excludeLibs);
        executor.getAgentRunner().setToTenSecondsTimeout = true;
        if (isReexecution) {
            executor.getAgentRunner().addAgentParam("no_exclude_all_java", "true");
        }
        executor.getAgentRunner().addAgentParam(CommonParams.OPT_FORCE_EXIT_WITHOUT_WAIT_OTHER_THREADS,
                "true");
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
                DiffMatcher diffMatcher = new DiffMatcher(srcDirName, srcDirName, srcDirName,
                        srcDirName);
                diffMatcher.matchCode();
                buggyTraceView.setDiffMatcher(diffMatcher);
            }
        });
    }

    private String getResultFolderName() {
        if (outputFolderOverride != null) {
            return outputFolderOverride;
        }

        if (isReexecution) {
            return RE_EXECUTION_FOLDER;
        }
        if (enableIncontextLearning) {
            // if (enableAliasInfer) {
            // return RESULTS_FOLDER; // all features enabled, use default result folder
            // "slicing_results"
            // } else {
            // return B2_FOLDER; // Baseline 2: enable in-context learning only
            // }
            return RESULTS_FOLDER;
        } else {
            // if (enableAliasInfer) {
            // return B3_FOLDER; // Baseline 3: enable alias inference only
            // } else {
            // return B1_FOLDER; // Baseline 1: disable in-context learning and alias
            // inference
            // }
            return B1_FOLDER;
        }
    }

    private Set<Integer> readVisitedLines(String basePath, String className) {
        String filePath = basePath + File.separator + getResultFolderName() +
                File.separator + className + ".txt";
        Set<Integer> visitedLines = new HashSet<>();

        try {
            File targetResultFile = new File(filePath);
            String content = new String(Files.readAllBytes(targetResultFile.toPath()),
                    StandardCharsets.UTF_8);
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

    private Map<String, Integer> readSingleFileSlicingCriteria(String basePath,
            String fileName) {
        String filePath = basePath + File.separator + fileName;
        Map<String, Integer> criteria = new HashMap<>();

        boolean isMultiFile = sliceDatasetPath.contains("multi_files");
        if (isMultiFile) {
            return criteria;
        }

        try {
            File targetResultFile = new File(filePath);
            String content = new String(Files.readAllBytes(targetResultFile.toPath()),
                    StandardCharsets.UTF_8);
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
            String content = new String(Files.readAllBytes(targetResultFile.toPath()),
                    StandardCharsets.UTF_8);
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
            String content = new String(Files.readAllBytes(criticalVarFile.toPath()),
                    StandardCharsets.UTF_8);
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
