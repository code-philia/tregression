package tregression.aliastracking;

import java.util.List;

import lombok.extern.slf4j.Slf4j;
import microbat.instrumentation.output.RunningInfo;
import microbat.model.trace.Trace;
import microbat.model.trace.TraceNode;

@Slf4j
public class RunOnTrace {
    public static void main(String[] args) {
        if (args.length < 2) {
            log.error("Please provide the path to the trace file as an argument.");
            return;
        }

        String traceFilePath = args[0];
        String sourceFileName = args[1];

        RunningInfo info = RunningInfo.readFromFile(traceFilePath);
        Trace trace = info.getMainTrace();
        List<TraceNode> steps = trace.getExecutionList();
        for (TraceNode step : steps) {
            step.getBreakPoint().setFullJavaFilePath(sourceFileName);
        }

        HeapObjects heapObjects = new HeapObjects("http://127.0.0.1:3322/process");
        heapObjects.processTrace(steps);

        // StringBuilder sb = new StringBuilder();
        // try (BufferedReader reader = new BufferedReader(new FileReader(args[0]))) {
        // String line;
        // while ((line = reader.readLine()) != null) {
        // sb.append(line);
        // }
        // } catch (Exception e) {
        // log.error("Error reading file", e);
        // }

        // log.info("Result: {}", Expr.readFromJson(sb.toString()));
    }
}
