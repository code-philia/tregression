package tregression.aliastracking;

import lombok.Getter;

@Getter
public class HeapObjectField {
    private final HeapObject heapObject;
    private final String fieldName;
    private final HeapPtr ptr;

    public HeapObjectField(HeapObject obj, String fieldName) {
        this.heapObject = obj;
        this.fieldName = fieldName;
        this.ptr = HeapPtr.newEmpty();
    }
}
