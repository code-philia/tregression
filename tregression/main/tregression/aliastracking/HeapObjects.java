package tregression.aliastracking;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import microbat.model.trace.TraceNode;
import microbat.model.value.ArrayValue;
import microbat.model.value.ReferenceValue;
import microbat.model.value.VarValue;

@Slf4j
@Getter
public class HeapObjects {
    private HashMap<String, HeapPtr> variablePtrs;
    private HashMap<String, HeapObject> heapIdMapping;
    private HeapObject nullObject;

    public HeapObjects() {
        variablePtrs = new HashMap<>();
        heapIdMapping = new HashMap<>();

        nullObject = HeapObject.createNullObject();
    }

    private void printVarInfo(VarValue var, String prefix) {
        String res = String.format(
                "%s: %s. ID: %s. Alias: %s. (value_type: %s, var_type: %s) (Type: %s)",
                prefix, var.getVarName(),
                var.getVarID(),
                var.getAliasVarID(),
                var.getClass().getSimpleName(),
                var.getVariable().getClass().getSimpleName(),
                var.getVariable().getType());
        System.out.println(res);
        for (VarValue child : var.getChildren()) {
            String prefix_child = "  " + prefix;
            printVarInfo(child, prefix_child);
        }
    }

    public void processTrace(List<TraceNode> traceNodes) {
        for (TraceNode node : traceNodes) {
            System.out.println("Trace: " + node + ".");
            Collection<VarValue> readVariables = node.getReadVariables();
            Collection<VarValue> writtenVariables = node.getWrittenVariables();
            for (VarValue var : readVariables) {
                printVarInfo(var, "  Read");
                recordVariable(var.getVarID(), var, node.getOrder(), false);
            }
            for (VarValue var : writtenVariables) {
                printVarInfo(var, "  Written");
                recordVariable(var.getVarID(), var, node.getOrder(), true);
            }
        }
    }

    public HeapPtr recordVariable(String variableName, VarValue value, int stepId, boolean isWritten) {
        if (!variablePtrs.containsKey(variableName)) {
            createVarPtr(variableName, value, stepId);
        }
        return variablePtrs.get(variableName);
    }

    public HeapPtr getVariablePtr(String variableName) {
        return variablePtrs.get(variableName);
    }

    private void createVarPtr(String variableName, VarValue value, int stepId) {
        String heapId = value.getAliasVarID();

        if (value instanceof ReferenceValue) {

        } else if (value instanceof ArrayValue) {

        } else {
            log.warn("Variable {} is not a ReferenceValue, cannot create HeapPtr",
                    variableName);
        }

        // if (!(value instanceof ReferenceValue)) {
        // }
        // ReferenceValue refValue = (ReferenceValue) value;
        // String heapId = refValue.getAliasVarID();

    }
}
