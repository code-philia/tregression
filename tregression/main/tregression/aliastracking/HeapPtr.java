package tregression.aliastracking;

import java.util.ArrayList;

import lombok.Getter;

@Getter
public class HeapPtr {
    private ArrayList<HeapPtrAssign> assignments;

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
            assignments.add(0, lastAssign);
        }
        return lastAssign.getHeapObject();
    }

    public void addAssignment(int traceId, HeapObject heapObject) {
        HeapPtrAssign assign = new HeapPtrAssign(traceId, heapObject);
        assignments.add(assign);
    }
}
