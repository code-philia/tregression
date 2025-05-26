package tregression.handler;

import java.io.FileReader;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.ui.IStartup;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import microbat.MicrobatConsole;
import microbat.runconfigs.ExecutionInfo;
import microbat.runconfigs.TraceRecovRunConfig;

@Slf4j
public class AutoRecovSlicingHandler implements IStartup {
    public static final int WAITING_BEFORE_AUTO_RECOV_SLICING = 5000;

    @Getter
    @Setter
    private static class AutoRecovSlicingInfo {
        private String taskName;
        private String descriptionFilePath;
        private String configFilePath;
        private String workingFolder;
        private String configFileName;
    }

    private void runTask() throws Exception {
        MicrobatConsole.showConsole();
        log.info("AutoRecovSlicingHandler started");

        Gson gson = new GsonBuilder().setPrettyPrinting().serializeNulls().create();
        String env = System.getenv("AUTO_RECOV_SLICING_INFO");
        AutoRecovSlicingInfo info = gson.fromJson(env, AutoRecovSlicingInfo.class);
        ExecutionInfo<TraceRecovRunConfig> executionInfo = new ExecutionInfo<>();
        executionInfo.setTaskName(info.getTaskName());
        executionInfo.setDescriptionFilePath(info.getDescriptionFilePath());
        executionInfo.setConfigFilePath(info.getConfigFilePath());
        executionInfo.setWorkingFolder(info.getWorkingFolder());

        TraceRecovRunConfig config = null;
        log.info("Loading config from file: {}", info.getConfigFilePath());
        try (FileReader fis = new FileReader(info.getConfigFilePath())) {
            config = gson.fromJson(fis, TraceRecovRunConfig.class);
        }
        executionInfo.setConfig(config);

        log.info("Starting auto recovery slicing task: {}", gson.toJson(config));
        new RecovSlicingEvalRunner().execute(executionInfo);
    }

    public void execute() {
        log.info("AutoRecovSlicingHandler executed");
        Job job = new Job("Auto Recovery Slicing") {
            @Override
            protected IStatus run(IProgressMonitor monitor) {
                try {
                    runTask();
                    return Status.OK_STATUS;
                } catch (Exception e) {
                    log.error("Error during auto recovery slicing", e);
                    return new Status(IStatus.ERROR, "tregression", "Error during auto recovery slicing", e);
                }
            }
        };
        job.schedule(WAITING_BEFORE_AUTO_RECOV_SLICING);
    }

    @Override
    public void earlyStartup() {
        String env = System.getenv("AUTO_RECOV_SLICING_INFO");
        if (env == null || env.isEmpty()) {
            log.info("AUTO_RECOV_SLICING_INFO not set, skipping auto recovery slicing");
            return;
        }
        execute();
    }
}
