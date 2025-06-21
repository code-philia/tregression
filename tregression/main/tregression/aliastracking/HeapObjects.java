package tregression.aliastracking;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import microbat.model.trace.TraceNode;
import microbat.model.value.ReferenceValue;
import microbat.model.value.VarValue;

@Slf4j
@Getter
public class HeapObjects {
    private HashMap<String, HeapPtr> variablePtrs;
    private HashMap<String, HeapObject> heapIdMapping;
    private InstanceClassInfoGetter infoGetter;

    public HeapObjects(InstanceClassInfoGetter classInfoGetter) {
        variablePtrs = new HashMap<>();
        heapIdMapping = new HashMap<>();
        infoGetter = classInfoGetter;
    }

    private void printVarInfo(VarValue var, String prefix) {
        String res = String.format(
                "%s: %s. ID: %s. Alias: %s. (value_type: %s, var_type: %s)",
                prefix, var.getVarName(),
                var.getVarID(),
                var.getAliasVarID(),
                var.getClass().getSimpleName(),
                var.getVariable().getClass().getSimpleName());
        System.out.println(res);
        for (VarValue child : var.getChildren()) {
            String prefix_child = "  " + prefix;
            printVarInfo(child, prefix_child);
        }
    }

    public void processTrace(List<TraceNode> traceNodes) {
        for (TraceNode node : traceNodes) {
            int order = node.getOrder();
            if (order >= 10) {
                break;
            }
            // log.info("Trace: {}.", order);
            System.out.println("Trace: " + order + ".");
            Collection<VarValue> readVariables = node.getReadVariables();
            Collection<VarValue> writtenVariables = node.getWrittenVariables();
            for (VarValue var : readVariables) {
                printVarInfo(var, "  Read");
            }
            for (VarValue var : writtenVariables) {
                printVarInfo(var, "  Written");
            }
        }
    }

    public HeapPtr recordVariable(String variableName, VarValue value, int stepId) {
        if (!variablePtrs.containsKey(variableName)) {
            createVarPtr(variableName, value, stepId);
        }
        return variablePtrs.get(variableName);
    }

    public HeapPtr getVariablePtr(String variableName) {
        return variablePtrs.get(variableName);
    }

    private void createVarPtr(String variableName, VarValue value, int stepId) {
        if (!(value instanceof ReferenceValue)) {
            log.warn("Variable {} is not a ReferenceValue, cannot create HeapPtr", variableName);
            return;
        }
        ReferenceValue refValue = (ReferenceValue) value;
        long heapId = refValue.getUniqueID();
        String heapIdStr = String.valueOf(heapId);

    }
}
