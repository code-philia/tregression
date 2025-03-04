package tregression.handler;

import java.io.InputStream;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.slf4j.Logger;

import microbat.incontextlearning.InContextLearning.InContextLearningType;
import microbat.tracerecov.executionsimulator.ExecutionSimulatorFactory;
import tregression.incontextlearning.InContextLearningImpl;

public class RunGPTInContextLearningHandler extends AbstractHandler {
    private static final Logger log = org.slf4j.LoggerFactory.getLogger(RunGPTInContextLearningHandler.class);

    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {
        Job job = new Job("RunGPTInContextLearning") {
            @Override
            protected IStatus run(IProgressMonitor monitor) {
                try {
                    String sampleCode = readResourceString("/run_sample/sample_code.java");
                    String sampleImport = readResourceString("/run_sample/sample_import.java");
                    int sampleLineIdx = Integer.parseInt(readResourceString("/run_sample/sample_idx.txt").trim());
                    InContextLearningImpl learning = new InContextLearningImpl(true);
                    learning.setExecutionSimulator(ExecutionSimulatorFactory.getExecutionSimulator());
                    learning.executeInContextLearning(
                            sampleImport,
                            sampleCode,
                            sampleLineIdx,
                            InContextLearningType.ALIAS_INFERENCE,
                            InContextLearningImpl.defaultToString());
                } catch (Exception e) {
                    log.error("Failed to run GPT in context learning", e);
                    return Status.CANCEL_STATUS;
                }
                return Status.OK_STATUS;
            }
        };
        job.schedule();
        return null;
    }

    public static String readResourceString(String path) {
        StringBuilder sb = new StringBuilder();
        try (InputStream is = RunGPTInContextLearningHandler.class.getResourceAsStream(path)) {
            byte[] buffer = new byte[1024];
            int len;
            while ((len = is.read(buffer)) != -1) {
                sb.append(new String(buffer, 0, len));
            }
            return sb.toString();
        } catch (Exception e) {
            log.error("Failed to read resource: {}", path, e);
            throw new RuntimeException(e);
        }
    }
}
