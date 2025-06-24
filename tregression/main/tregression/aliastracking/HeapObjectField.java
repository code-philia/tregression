package tregression.aliastracking;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class HeapObjectField {
    private final HeapObject heapObject;
    private final String fieldName;
    private final HeapPtr ptr;
}
