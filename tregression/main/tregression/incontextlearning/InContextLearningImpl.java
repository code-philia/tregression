package tregression.incontextlearning;

import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import microbat.incontextlearning.InContextLearning;
import microbat.model.trace.Trace;
import microbat.model.trace.TraceNode;
import microbat.model.value.VarValue;
import microbat.model.value.VirtualValue;
import microbat.tracerecov.executionsimulator.ExecutionSimulator;
import microbat.tracerecov.executionsimulator.LLMResponseType;
import microbat.util.StringFormatUtils;
import tregression.incontextlearning.InContextExecutor.SourceCodeWritter;

@Slf4j
public class InContextLearningImpl implements InContextLearning {
    @Getter
    @Setter
    private ExecutionSimulator executionSimulator;

    @Override
    public String executeInContextLearning(
            String imports,
            String targetMethod,
            int targetLineNumber,
            InContextLearningType type) {
        if (executionSimulator == null) {
            throw new IllegalStateException("Execution simulator is not set");
        }

        String input = processInputString(imports, targetMethod, targetLineNumber);
        String gptSystem = getBackgroundContent();
        String gptUser = getQuestionContent(input);

        String response = null;
        try {
            response = executionSimulator.sendRequest(gptSystem, gptUser, LLMResponseType.TEXT);
        } catch (Exception e) {
            log.error("Failed to execute in context learning", e);
            return "";
        }

        InContextLearningCode generatedCode = processCodeGeneratedByLLM(response);
        InContextExecutor executor = new InContextExecutor(generatedCode);

        Trace trace = null;
        try {
            trace = executor.run();
        } catch (Exception e) {
            log.error("Failed to execute generated code", e);
            return "";
        }

        InContextLearningVariables variables = postProcessTrace(trace, type, generatedCode);
        String explanation = variablesToExplainString(variables);

        return explanation;
    }

    public static final String TARGET_METHOD_SIGN = "SampleTest#test()";

    private static class TraceRange {
        int start;
        int end;
    }

    private int getLastDescendant(TraceNode node) {
        if (node.getInvocationChildren().size() != 0) {
            return getLastDescendant(node.getInvocationChildren().get(node.getInvocationChildren().size() - 1));
        } else if (node.getLoopChildren().size() != 0) {
            return getLastDescendant(node.getLoopChildren().get(node.getLoopChildren().size() - 1));
        } else if (node.getAbstractChildren().size() != 0) {
            return getLastDescendant(node.getAbstractChildren().get(node.getAbstractChildren().size() - 1));
        } else {
            return node.getOrder();
        }
    }

    @AllArgsConstructor
    @Getter
    @Setter
    public static class InContextLearningVariables {
        private List<VarValue> allWrittenVariables;
        private List<VarValue> allReadVariables;
        private List<VarValue> outerWrittenVariables;
        private List<VarValue> outerReadVariables;
        private InContextLearningCode code;

        public InContextLearningVariables() {
            allWrittenVariables = new ArrayList<>();
            allReadVariables = new ArrayList<>();
            outerWrittenVariables = new ArrayList<>();
            outerReadVariables = new ArrayList<>();
        }
    }

    public InContextLearningVariables postProcessTrace(
            Trace trace,
            InContextLearningType type,
            InContextLearningCode code) {
        int firstLoc = Integer.MAX_VALUE;
        int lastLoc = Integer.MIN_VALUE;

        List<Integer> targetLineTraces = new ArrayList<>();

        for (int i = 0; i < trace.size(); i++) {
            TraceNode node = trace.getTraceNode(i);
            if (node.getMethodSign().equals(TARGET_METHOD_SIGN)) {
                firstLoc = Math.min(firstLoc, i);
                lastLoc = Math.max(lastLoc, i);

                if (node.getLineNumber() == code.getMarkerLine()) {
                    targetLineTraces.add(i);
                }
            }
        }

        if (lastLoc < 0) {
            log.error("Trace of the target method was not recorded");
            throw new IllegalStateException("Trace of the target method was not recorded");
        }

        if (targetLineTraces.size() == 0) {
            log.error("No trace node found for the target line");
            throw new IllegalStateException("No trace node found for the target line");
        }

        List<TraceRange> ranges = new ArrayList<>();
        int lastIdx = -1;
        int neighborIdx = -1;

        for (int i : targetLineTraces) {
            TraceNode node = trace.getTraceNode(i);
            if (neighborIdx == -1) {
                lastIdx = i;
                neighborIdx = getLastDescendant(node);
            } else {
                if (i == neighborIdx + 1) {
                    neighborIdx = getLastDescendant(node);
                } else {
                    TraceRange range = new TraceRange();
                    range.start = lastIdx;
                    range.end = neighborIdx;
                    ranges.add(range);

                    lastIdx = i;
                    neighborIdx = getLastDescendant(node);
                }
            }
        }

        if (lastIdx != -1) {
            TraceRange range = new TraceRange();
            range.start = lastIdx;
            range.end = neighborIdx;
            ranges.add(range);
        }

        if (ranges.size() == 0) {
            log.error("INTERNAL ERROR: No trace range found");
            throw new IllegalStateException("INTERNAL ERROR: No trace range found");
        }

        // FIXME: only take the first trace range
        TraceRange focusRange = ranges.get(0);

        InContextLearningVariables variables = new InContextLearningVariables();
        variables.setCode(code);

        for (int i = focusRange.start; i <= focusRange.end; i++) {
            TraceNode node = trace.getTraceNode(i);
            boolean isOuter = node.getMethodSign().equals(TARGET_METHOD_SIGN);

            for (VarValue var : node.getWrittenVariables()) {
                if (var instanceof VirtualValue) {
                    continue;
                }
                variables.getAllWrittenVariables().add(var);
                if (isOuter) {
                    variables.getOuterWrittenVariables().add(var);
                }
            }

            for (VarValue var : node.getReadVariables()) {
                if (var instanceof VirtualValue) {
                    continue;
                }
                variables.getAllReadVariables().add(var);
                if (isOuter) {
                    variables.getOuterReadVariables().add(var);
                }
            }
        }

        return variables;
    }

    public String formatVarValue(VarValue var) {
        Map<String, String> vars = Map.of("var_name", var.getVarName(), "var_value", var.getStringValue());
        final String format = "Variable: ${var_name} with value: ${var_value}";
        return StringFormatUtils.formatString(format, vars);
    }

    public String formatVarValueList(List<VarValue> vars) {
        StringBuilder sb = new StringBuilder();
        for (VarValue var : vars) {
            sb.append("  ");
            sb.append(formatVarValue(var));
            sb.append("\n");
        }

        return sb.toString();
    }

    public String variablesToExplainString(InContextLearningVariables variables) {
        Map<String, String> varMap = new HashMap<>();
        varMap.put("code", variables.getCode().getCodeToView());
        varMap.put("variables_read", formatVarValueList(variables.getAllReadVariables()));
        varMap.put("variables_written", formatVarValueList(variables.getAllWrittenVariables()));
        varMap.put("variables_read_outer", formatVarValueList(variables.getOuterReadVariables()));
        varMap.put("variables_written_outer", formatVarValueList(variables.getOuterWrittenVariables()));
        return StringFormatUtils.formatString(StringFormatUtils.getPromptInContextLearningExplain(), varMap);
    }

    public String insertLineMarker(String line) {
        int i = 0;
        while (i < line.length() && (line.charAt(i) == ' ' || line.charAt(i) == '\t')) {
            i++;
        }

        if (i == line.length()) {
            throw new IllegalArgumentException("Empty line");
        }

        return line.substring(0, i) + ">>> " + line.substring(i);
    }

    public String processInputString(String imports, String targetMethod, int targetLineNumber) {
        StringBuilder sb = new StringBuilder();
        sb.append(imports);
        sb.append("\n");
        String[] lines = targetMethod.split("\n");
        for (int i = 0; i < lines.length; i++) {
            if (i == targetLineNumber - 1) {
                sb.append(insertLineMarker(lines[i]));
            } else {
                sb.append(lines[i]);
            }
        }

        return sb.toString();
    }

    public String getBackgroundContent() {
        return StringFormatUtils.getPromptInContextLearningSystem();
    }

    public String getQuestionContent(String code) {
        String format = StringFormatUtils.getPromptInContextLearningUser();
        return StringFormatUtils.formatString(format, Map.of("original_code", code));
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InContextLearningCode implements SourceCodeWritter {
        private String code;
        private String codeToView;
        private int markerLine;

        @Override
        public void writeSourceCode(FileWriter writer) throws IOException {
            writer.write(code);
        }
    }

    public InContextLearningCode processCodeGeneratedByLLM(String response) {
        int beginMark = response.indexOf("```java");
        if (beginMark == -1) {
            throw new IllegalArgumentException("No code block \"```java\" found in the response");
        }
        int endMark = response.indexOf("```", beginMark + 1);
        if (endMark == -1) {
            throw new IllegalArgumentException("No end mark \"```\" found in the response");
        }

        String code = response.substring(beginMark, endMark);
        int lastBracket = code.lastIndexOf("}");
        if (lastBracket == -1) {
            throw new IllegalArgumentException("No closing bracket found in the code block");
        }

        String codeBefore = code.substring(0, lastBracket);
        String codeAfter = code.substring(lastBracket);

        String ressembleCode = codeBefore + StringFormatUtils.getPromptInContextLearningInsertEnd() + codeAfter;

        String[] lines = ressembleCode.split("\n");

        StringBuilder sb = new StringBuilder();
        int markerLine = -1;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            int markLoc = line.indexOf(">>>");
            if (markLoc != -1) {
                if (markerLine != -1) {
                    throw new IllegalArgumentException("Multiple marker lines found in the code block \">>>\"");
                } else {
                    markerLine = i;
                }

                sb.append(line.substring(0, markLoc));
                sb.append(" ");
                sb.append(line.substring(markLoc + 3));
                sb.append("\n");
            } else {
                sb.append(line);
                sb.append("\n");
            }
        }
        if (markerLine == -1) {
            throw new IllegalArgumentException("No marker line found in the code block \">>>\"");
        }

        String outputCode = sb.toString();

        return new InContextLearningCode(outputCode, code, markerLine);
    }
}
