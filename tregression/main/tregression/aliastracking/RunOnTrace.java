package tregression.aliastracking;

import java.util.List;

import lombok.extern.slf4j.Slf4j;
import microbat.instrumentation.output.RunningInfo;
import microbat.model.trace.Trace;
import microbat.model.trace.TraceNode;

@Slf4j
public class RunOnTrace {
    public static void main(String[] args) {
        if (args.length < 1) {
            log.error("Please provide the path to the trace file as an argument.");
            return;
        }

        String traceFilePath = args[0];
        RunningInfo info = RunningInfo.readFromFile(traceFilePath);
        Trace trace = info.getMainTrace();
        List<TraceNode> steps = trace.getExecutionList();
        HeapObjects heapObjects = new HeapObjects();
        heapObjects.processTrace(steps);
    }
}
