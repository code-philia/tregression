package tregression.aliastracking;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import microbat.model.trace.TraceNode;
import microbat.model.value.ArrayValue;
import microbat.model.value.ReferenceValue;
import microbat.model.value.VarValue;
import tregression.aliastracking.ast.Expr;
import tregression.aliastracking.ast.HeapAddr;
import tregression.aliastracking.ast.HeapAddrHeapId;
import tregression.aliastracking.ast.HeapAddrPtrValue;
import tregression.aliastracking.ast.Ptr;
import tregression.aliastracking.ast.PtrField;
import tregression.aliastracking.ast.PtrVar;

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
                "%s: %s. ID: %s. Alias: %s.",
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
                recordVariable(new PtrVar(var.getVarID()), var, node.getOrder(), false);
            }
            for (VarValue var : writtenVariables) {
                printVarInfo(var, "  Written");
                recordVariable(new PtrVar(var.getVarID()), var, node.getOrder(), true);
            }

            if (node.isCallingAPI()) {
                String invokingMethod = node.getInvokingMethod();
                String[] invokedMethods = invokingMethod.split("%");
                List<String> unrecorded = new ArrayList<>();

                List<TraceNode> children = node.getInvocationChildren();
                String expandedMethod = null;
                if (children != null && !children.isEmpty()) {
                    expandedMethod = children.get(0).getMethodSign();
                }
                for (String m : invokedMethods) {
                    if (m == null || m.isEmpty()) {
                        continue;
                    }
                    if (!m.equals(traceNodes)) {
                        unrecorded.add(m);
                    }
                }

                for (String m : unrecorded) {
                    System.out.println("  Invoking: " + m);
                }

            }
        }
    }

    public void recordVariable(Ptr ptr, VarValue value, int stepId, boolean isWritten) {
        if (value instanceof ReferenceValue) {
            ReferenceValue refValue = (ReferenceValue) value;
            String heapId = refValue.getAliasVarID();
            HeapAddr addr = new HeapAddrHeapId(heapId);
            Expr expr = new Expr(ptr, addr);
            addAssignment(expr, stepId);

            for (VarValue child : refValue.getChildren()) {
                recordVariable(new PtrField(addr, child.getVarName()), child, stepId, isWritten);
            }
        } else if (value instanceof ArrayValue) {
            ArrayValue arrValue = (ArrayValue) value;
            String heapId = arrValue.getAliasVarID();
            HeapAddr addr = new HeapAddrHeapId(heapId);
            Expr expr = new Expr(ptr, addr);
            addAssignment(expr, stepId);

            for (VarValue child : arrValue.getChildren()) {
                recordVariable(new PtrField(addr, child.getVarName()), child, stepId, isWritten);
            }
        }
    }

    public HeapPtr getVariablePtr(String variableName) {
        return variablePtrs.get(variableName);
    }

    public HeapPtr findPtrByVarId(String varId) {
        if (!variablePtrs.containsKey(varId)) {
            variablePtrs.put(varId, HeapPtr.newEmpty());
        }
        return variablePtrs.get(varId);
    }

    public HeapObject findHeapObjectById(String heapId) {
        if (heapId == null || heapId.isEmpty() || heapId.equals("-1")) {
            return nullObject;
        }
        if (!heapIdMapping.containsKey(heapId)) {
            HeapObject newObject = HeapObject.createWithHeapId(heapId);
            heapIdMapping.put(heapId, newObject);
        }
        return heapIdMapping.get(heapId);
    }

    public HeapPtr findPtr(Ptr ptr, int stepId) {
        if (ptr instanceof PtrVar) {
            PtrVar ptrVar = (PtrVar) ptr;
            return findPtrByVarId(ptrVar.getVarId());
        } else if (ptr instanceof PtrField) {
            PtrField ptrField = (PtrField) ptr;
            HeapObject obj = findHeapObject(ptrField.getMemAddr(), stepId);
            return obj.resolveField(ptrField.getFieldId());
        } else {
            throw new IllegalArgumentException("Unsupported Ptr type: " + ptr.getClass().getName());
        }
    }

    public HeapObject findHeapObject(HeapAddr addr, int stepId) {
        if (addr instanceof HeapAddrHeapId) {
            HeapAddrHeapId heapAddrHeapId = (HeapAddrHeapId) addr;
            return findHeapObjectById(heapAddrHeapId.getHeapId());
        } else if (addr instanceof HeapAddrPtrValue) {
            HeapAddrPtrValue heapAddrPtrValue = (HeapAddrPtrValue) addr;
            HeapPtr ptr = findPtr(heapAddrPtrValue.getPtr(), stepId);
            return ptr.resolveValue(this, stepId);
        } else {
            throw new IllegalArgumentException("Unsupported HeapAddr type: " + addr.getClass().getName());
        }
    }

    public void addAssignment(Expr expr, int stepId) {
        System.out.println("  Assign: " + expr);
        HeapPtr ptr = findPtr(expr.getLeft(), stepId);
        HeapObject value = findHeapObject(expr.getRight(), stepId);
        ptr.addAssignment(stepId, value);
    }

    public boolean isAlias(Ptr left, Ptr right, int stepId) {
        HeapObject leftObject = findHeapObject(new HeapAddrPtrValue(left), stepId);
        HeapObject rightObject = findHeapObject(new HeapAddrPtrValue(right), stepId);
        return leftObject == rightObject;
    }

    public HeapObject createAnonymousObject() {
        return HeapObject.createAnonymousObject();
    }
}
