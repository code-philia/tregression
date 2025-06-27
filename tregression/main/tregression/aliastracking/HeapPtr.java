package tregression.aliastracking;

import java.util.ArrayList;
import java.util.List;

import lombok.Getter;

@Getter
public class HeapPtr {
    private List<HeapPtrAssign> assignments;

    public HeapPtr() {
        assignments = new ArrayList<>();
    }

    public static HeapPtr newEmpty() {
        return new HeapPtr();
    }

    public HeapObject resolveValue(HeapObjects heap, int stepId) {
        HeapPtrAssign lastAssign = null;
        for (HeapPtrAssign assign : assignments) {
            if (assign.getTraceId() <= stepId) {
                lastAssign = assign;
            } else {
                break;
            }
        }
        if (lastAssign == null) {
            HeapObject obj = heap.createAnonymousObject();
            lastAssign = new HeapPtrAssign(-1, obj);
            // assignments.addFirst(lastAssign);
        }
        return lastAssign.getHeapObject();
    }

    public void addAssignment(int traceId, HeapObject heapObject) {
        HeapPtrAssign assign = new HeapPtrAssign(traceId, heapObject);
        assignments.add(assign);
    }
}
