package tregression.incontextlearning;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.swt.widgets.Display;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import microbat.codeanalysis.runtime.InstrumentationExecutor;
import microbat.codeanalysis.runtime.StepLimitException;
import microbat.compatibilitylayer.CompatibilityLayer;
import microbat.instrumentation.CommonParams;
import microbat.instrumentation.output.RunningInfo;
import microbat.model.trace.Trace;
import microbat.util.StringFormatUtils;
import sav.strategies.dto.AppJavaClassPath;
import tregression.empiricalstudy.TestCase;
import tregression.empiricalstudy.config.Defects4jProjectConfig;
import tregression.empiricalstudy.config.ProjectConfig;
import tregression.model.PairList;
import tregression.separatesnapshots.AppClassPathInitializer;
import tregression.separatesnapshots.DiffMatcher;
import tregression.views.BuggyTraceView;
import tregression.views.TregressionViews;

public class GPTInContextLearning {
    public static Logger log = LoggerFactory.getLogger(GPTInContextLearning.class);

    public static final String IN_CONTEXT_LEARNING_FOLDER = "in-context-learning";
    public static final String SRC_FOLDER = "src";
    public static final String BIN_FOLDER = "bin";
    public static final String TRACE_FOLDER = "trace";
    public static final String MAIN_JAVA_NAME = "SampleTest.java";
    public static final String TRACE_FILE_NAME = "trace";

    private static AtomicInteger counter = new AtomicInteger(0);

    public CompatibilityLayer compatibilityLayer = CompatibilityLayer.getDefaultCompatibilityLayer();

    public String currentWorkingDirName;
    public String srcDirName;
    public String binDirName;
    public String srcFileName;
    public String traceDirName;
    public File currentWorkingDir;
    public File srcDir;
    public File binDir;
    public File srcFile;
    public File traceDir;

    public TestCase testCase;
    public ProjectConfig d4jConfig;
    public AppJavaClassPath appClassPath;

    private static int getNextCounter() {
        return counter.getAndIncrement();
    }

    private static String getNewWorkingDirName() {
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss");
        String formatted = now.format(formatter);
        return formatted + "-" + getNextCounter();
    }

    public GPTInContextLearning() {
        String separator = File.separator;

        currentWorkingDirName = compatibilityLayer.getWorkingSpacePath()
                + separator + IN_CONTEXT_LEARNING_FOLDER
                + separator + getNewWorkingDirName();
        currentWorkingDir = new File(currentWorkingDirName);

        srcDirName = currentWorkingDir
                + separator + SRC_FOLDER;
        srcDir = new File(srcDirName);
        if (!srcDir.exists()) {
            srcDir.mkdirs();
        }

        binDirName = currentWorkingDir
                + separator + BIN_FOLDER;
        binDir = new File(binDirName);
        if (!binDir.exists()) {
            binDir.mkdirs();
        }

        traceDirName = currentWorkingDir
                + separator + TRACE_FOLDER;
        traceDir = new File(traceDirName);
        if (!traceDir.exists()) {
            traceDir.mkdirs();
        }

        srcFileName = srcDirName + separator + MAIN_JAVA_NAME;
        srcFile = new File(srcFileName);

        log.info("Java home: {}", compatibilityLayer.getTargetJavaHome());
        log.info("working path: {}", currentWorkingDirName);
        log.info("src path: {}", srcDirName);
        log.info("bin path: {}", binDirName);
    }

    public void run() {
        try {
            runInner();
        } catch (Exception e) {
            log.error("Failed to run gpt.", e);
        }
    }

    public void runInner() {
        writeSrcFile();
        initializeAppClassPath();
        compileFile();
        runTarget();
    }

    public void writeSrcFile() {
        try (FileWriter writer = new FileWriter(srcFile)) {
            writer.write(getTestSampleSource(1));
        } catch (IOException e) {
            String msg = "Failed to write source file: " + srcFileName;
            log.error(msg, e);
            throw new RuntimeException(msg, e);
        }
    }

    public void compileFile() {
        String javaHome = compatibilityLayer.getTargetJavaHome();
        String javac = javaHome + File.separator + "bin" + File.separator + "javac";

        ArrayList<String> command = new ArrayList<>();
        command.add(javac);
        String classpaths = String.join(File.pathSeparator, appClassPath.getClasspaths());
        if (!classpaths.isEmpty()) {
            command.add("-cp");
            command.add(classpaths);
        }
        command.add("-d");
        command.add(binDirName);
        command.add(srcFileName);
        runCommand(command);
    }

    public void initializeAppClassPath() {
        testCase = new TestCase("SampleTest", "test");
        d4jConfig = Defects4jProjectConfig.getConfig(
                compatibilityLayer.getProjectName(),
                compatibilityLayer.getBugId());

        appClassPath = AppClassPathInitializer.initialize(binDirName, testCase, d4jConfig);

        appClassPath.setWorkingDirectory(binDirName);

        List<String> classPaths = new ArrayList<>();
        classPaths.add(binDirName);
        appClassPath.addClasspaths(classPaths);

        appClassPath.setSourceCodePath(srcDirName);
        appClassPath.setSourceCodePath(srcDirName);
    }

    public void runTarget() {
        List<String> includeLibs = new ArrayList<>();
        List<String> excludeLibs = new ArrayList<>();
        includeLibs.add("*");

        InstrumentationExecutor executor = new InstrumentationExecutor(
                appClassPath,
                traceDirName,
                TRACE_FILE_NAME,
                includeLibs,
                excludeLibs);
        executor.getAgentRunner().addAgentParam(CommonParams.OPT_FORCE_EXIT_WITHOUT_WAIT_OTHER_THREADS, "true");
        executor.getAgentRunner().addAgentParam(CommonParams.OPT_MANUALLY_TEST_RUNNING_CLASS, "SampleTest");
        RunningInfo results = null;
        try {
            results = executor.run();
        } catch (StepLimitException e) {
            log.error("Step limit exceeded", e);
            throw new RuntimeException("Step limit exceeded", e);
        }

        updateTrace(results.getMainTrace());
    }

    public void updateTrace(Trace trace) {
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

    public void runCommand(List<String> cmdline) {
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
                log.error("Command line failed: {}. STDOUT: {}, STDERR: {}",
                        cmdline, readStdout.getOutput(), readStderr.getOutput());
                throw new RuntimeException("Command line failed: " + cmdline);
            }
        } catch (IOException | InterruptedException e) {
            String msg = "Failed to compile source file: " + srcFileName;
            log.error(msg, e);
            throw new RuntimeException(msg, e);
        } finally {
            executor.shutdown();
        }
    }

    public static String getTestSampleSource(int idx) {
        try (
                InputStream is = GPTInContextLearning.class.getClassLoader()
                        .getResourceAsStream("run_sample/sample" + idx + "/SampleTest.java")) {
            ReadFromStream readFromStream = new ReadFromStream(is);
            readFromStream.call();
            return readFromStream.getOutput();
        } catch (Exception e) {
            log.error("Failed to read test sample source: {}", idx, e);
            throw new RuntimeException("Failed to read test sample source", e);
        }
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
            return StringFormatUtils.decodeWithIgnore(bytes);
        }
    }
}
