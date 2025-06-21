package tregression.aliastracking;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class HeapObjectField {
    private final HeapObject heapObject;
    private final boolean isArrayElement;
    private final int arrayIndex;
    private final String fieldName;
    private final String fieldType;
    private final HeapPtr ptr;
}
