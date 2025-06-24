package tregression.handler;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PlatformUI;

import java.io.*;
import java.nio.file.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import microbat.Activator;
import tregression.preference.TregressionPreference;
import tregression.views.DecisionView;

public class PilotStepHandler extends AbstractHandler {

    private static Process pythonProcess = null;
    private static BufferedWriter processWriter = null;
    private static final AtomicBoolean isWaitingForStep = new AtomicBoolean(false);

    private static final String PYTHON_SCRIPT = "pilot.py";
    private static final String PYTHON_DIR = "E:\\workplace\\demoPilot\\Pilot";

    private File STATUS_FILE_PATH;
    private File COMMAND_FILE_PATH;

    private static final int POLL_INTERVAL_MS = 500;
    private ScheduledExecutorService pollExecutor = null;

    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {
        if (pythonProcess == null || !pythonProcess.isAlive()) {
            startPythonProcess();
        } else if (isWaitingForStep.get()) {
            sendStepCommand();
        }
        return null;
    }

    private void startPythonProcess() {
        try {
            String bugId = getBugId();
            String project = getProjectName();
            String bugFolder = project + "_" + bugId;

            File workingDir = new File(PYTHON_DIR);
            ProcessBuilder builder = new ProcessBuilder(
                "python", PYTHON_SCRIPT, "-t", project, "-i", bugId, "--debug", "1"
            );
            builder.directory(workingDir);
            builder.redirectErrorStream(true);
            pythonProcess = builder.start();

            processWriter = new BufferedWriter(new OutputStreamWriter(pythonProcess.getOutputStream()));

            STATUS_FILE_PATH = Paths.get(PYTHON_DIR, "debugging", bugFolder, "pilot_status.txt").toFile();
            COMMAND_FILE_PATH = Paths.get(PYTHON_DIR, "debugging", bugFolder, "pilot_command.txt").toFile();

            startStatusPolling();

            new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(pythonProcess.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        System.out.println("[Python] " + line);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }).start();

            new Thread(() -> {
                try {
                    pythonProcess.waitFor();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } finally {
                    pythonProcess = null;
                    isWaitingForStep.set(false);
                    stopStatusPolling();
                }
            }).start();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void sendStepCommand() {
        try (FileWriter writer = new FileWriter(COMMAND_FILE_PATH)) {
            writer.write("step\n");
            writer.flush();
            isWaitingForStep.set(false);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void sendRetryCommand() {
        try {
            String bugId = Activator.getDefault().getPreferenceStore().getString(TregressionPreference.BUG_ID);
            String project = Activator.getDefault().getPreferenceStore().getString(TregressionPreference.PROJECT_NAME);
            String bugFolder = project + "_" + bugId;
            File commandFile = Paths.get(PYTHON_DIR, "debugging", bugFolder, "pilot_command.txt").toFile();

            try (FileWriter writer = new FileWriter(commandFile)) {
                writer.write("retry\n");
                writer.flush();
            }

            isWaitingForStep.set(false);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void startStatusPolling() {
        if (pollExecutor != null && !pollExecutor.isShutdown()) {
            pollExecutor.shutdownNow();
        }
        pollExecutor = Executors.newSingleThreadScheduledExecutor();

        pollExecutor.scheduleAtFixedRate(() -> {
            if (!STATUS_FILE_PATH.exists()) return;

            try {
                List<String> lines = Files.readAllLines(STATUS_FILE_PATH.toPath());
                if (!lines.isEmpty() && lines.get(0).trim().equalsIgnoreCase("waiting")) {
                    if (!isWaitingForStep.get()) {
                        isWaitingForStep.set(true);
                        Files.deleteIfExists(STATUS_FILE_PATH.toPath());

                        Display.getDefault().asyncExec(() -> {
                            try {
                                IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
                                if (window != null) {
                                    IWorkbenchPage page = window.getActivePage();
                                    DecisionView view = (DecisionView) page.showView(DecisionView.ID);
                                    view.refresh();
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        });
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }, 0, POLL_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    private void stopStatusPolling() {
        if (pollExecutor != null) {
            pollExecutor.shutdownNow();
            pollExecutor = null;
        }
    }

    public static void terminatePythonProcess() {
        if (pythonProcess != null && pythonProcess.isAlive()) {
            pythonProcess.destroy();
            pythonProcess = null;
            isWaitingForStep.set(false);
        }
    }

    private String getBugId() {
        return Activator.getDefault().getPreferenceStore().getString(TregressionPreference.BUG_ID);
    }

    private String getProjectName() {
        return Activator.getDefault().getPreferenceStore().getString(TregressionPreference.PROJECT_NAME);
    }
}
