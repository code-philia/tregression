package tregression.rpc;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import org.scalasbt.ipcsocket.UnixDomainServerSocket;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import microbat.compatibilitylayer.CompatibilityLayer;
import microbat.runconfigs.ExecutionInfo;
import microbat.runconfigs.TraceRecovRunConfig;
import tregression.handler.RecovSlicingEvalRunner;
import tregression.handler.AutoRecovSlicingHandler.AutoRecovSlicingInfo;
import tregression.handler.RecovSlicingEvalRunner.FinishedList;

@Slf4j
public class RPC {
    private static final String ENV_RPC_LOC = "RECOV_SLICING_RPC_LOC";

    private Gson gson;
    private UnixDomainServerSocket socket;
    private RecovSlicingEvalRunner runner;

    public RPC() {
        this.gson = new GsonBuilder().serializeNulls().create();
    }

    public void init() {
        String rpcLoc = System.getenv(ENV_RPC_LOC);
        if (rpcLoc == null || rpcLoc.isEmpty()) {
            log.info("Environment variable {} is not set. RPC will not be initialized.", ENV_RPC_LOC);
            return;
        }

        try {
            Path rpcPath = Path.of(rpcLoc);
            Path rpcFolder = rpcPath.getParent();
            if (!Files.exists(rpcFolder)) {
                Files.createDirectories(rpcFolder);
            }

            if (Files.exists(rpcPath)) {
                log.info("Removing existing RPC socket at {}", rpcLoc);
                Files.delete(rpcPath);
            }

            socket = new UnixDomainServerSocket(rpcLoc);
            log.info("RPC server initialized at {}", rpcLoc);
            Thread t = new Thread(this::run);
            t.setDaemon(true);
            t.start();
        } catch (Exception e) {
            log.error("Failed to initialize RPC server at {}.", rpcLoc, e);
            return;
        }
    }

    private void run() {
        try {
            runInner();
        } catch (Exception e) {
            log.error("RPC crashed.", e);
        } finally {
            if (socket != null) {
                try {
                    socket.close();
                } catch (Exception e) {
                    log.error("Failed to close RPC socket.", e);
                }
            }
        }
    }

    private void runInner() throws Exception {
        log.info("RPC server is running and waiting for connections...");
        long index = 0;
        while (true) {
            Socket client = socket.accept();
            index += 1;
            String threadName = "RPC-Thread-" + index;
            Thread t = new Thread(() -> {
                this.handleRequest(client);
            }, threadName);
            t.setDaemon(true);
            t.start();
        }
    }

    private void handleRequest(Socket client) {
        try {
            handleRequestInner(client);
        } catch (Exception e) {
            log.error("Error handling request from {}.", client.getRemoteSocketAddress(), e);
        } finally {
            try {
                client.close();
            } catch (Exception e) {
                log.error("Failed to close client socket.", e);
            }
        }
    }

    private void handleRequestInner(Socket client) throws Exception {
        log.info("Handling request from {}", client.getRemoteSocketAddress());
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8));
                BufferedWriter writer = new BufferedWriter(
                        new OutputStreamWriter(client.getOutputStream(), StandardCharsets.UTF_8))) {
            String line = reader.readLine();
            RPCRequest request = gson.fromJson(line, RPCRequest.class);
            log.info("Received request: {} from {}.", request, client.getRemoteSocketAddress());
            processRequest(request);
            RPCResponse response = processRequest(request);
            String responseJson = gson.toJson(response);
            log.info("Sending response: {} to {}.", response, client.getRemoteSocketAddress());
            writer.write(responseJson + "\n");
        }
    }

    private RPCResponse processRequest(RPCRequest request) {
        String task = request.getTask();
        if (task == null) {
            task = "";
        }

        try {
            if (task.equals("hello")) {
                return processRequestHello(request);
            }

            if (task.equals("exit")) {
                return processRequestExit(request);
            }
            if (task.equals("runRecovSlicing")) {
                return processRequestRunRecovSlicing(request);
            }
            if (task.equals("isFinished")) {
                return processRequestIsFinished(request);
            }
            if (task.equals("getFinishList")) {
                return processGetFinishList(request);
            }
            if (task.equals("waitForFinish")) {
                return processWaitForFinish(request);
            }

            log.warn("Unknown task: {}. Returning error response.", request.task);
            return new RPCResponse("error", "Unknown task: " + request.task);

        } catch (Exception e) {
            log.error("Error processing request: {}", request, e);
            return new RPCResponse("error", "Error processing request: " + e.getMessage());
        }
    }

    private RPCResponse processRequestHello(RPCRequest request) {
        return new RPCResponse("success", "Hello, world!");
    }

    private RPCResponse processRequestExit(RPCRequest request) {
        new Thread(() -> {
            try {
                Thread.sleep(1000); // Give some time for the response to be sent
            } catch (InterruptedException e) {
            }
            log.info("Exiting RPC server as requested.");
            CompatibilityLayer.getDefaultCompatibilityLayer().exit();
        }).start();
        return new RPCResponse("success", "");
    }

    private RPCResponse processRequestRunRecovSlicing(RPCRequest request) throws Exception {
        if (runner != null && runner.getFinished().getCount() != 0) {
            return new RPCResponse("error", "A recovery slicing task is already running.");
        }
        runner = new RecovSlicingEvalRunner();

        AutoRecovSlicingInfo info = gson.fromJson(request.getConfig(), AutoRecovSlicingInfo.class);
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

        return new RPCResponse("success", "");
    }

    private RPCResponse processRequestIsFinished(RPCRequest request) {
        if (runner == null) {
            return new RPCResponse("error", "No recovery slicing task is running.");
        }
        boolean finished = runner.getFinished().getCount() == 0;
        if (finished) {
            return new RPCResponse("success", "finished");
        } else {
            return new RPCResponse("success", "running");
        }
    }

    private RPCResponse processGetFinishList(RPCRequest request) {
        if (runner == null) {
            return new RPCResponse("error", "No recovery slicing task is running.");
        }
        List<String> finishedFiles = new ArrayList<>();
        FinishedList finishedList = runner.getFinishedList();
        while (finishedList != null) {
            if (finishedList.getName() != null) {
                finishedFiles.add(finishedList.getName());
            }
            finishedList = finishedList.getNext();
        }
        String finishedListJson = gson.toJson(finishedFiles);
        return new RPCResponse("success", finishedListJson);
    }

    private RPCResponse processWaitForFinish(RPCRequest request) {
        if (runner == null) {
            return new RPCResponse("error", "No recovery slicing task is running.");
        }
        CountDownLatch finished = runner.getFinished();
        while (true) {
            try {
                finished.await();
                break;
            } catch (InterruptedException e) {
                log.warn("Interrupted while waiting for recovery slicing task to finish.", e);
            }
        }
        return new RPCResponse("success", "");
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RPCRequest {
        String task;
        String config;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RPCResponse {
        private String status;
        private String result;
    }

}
