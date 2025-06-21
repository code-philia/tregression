package tregression.aliastracking;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class HeapPtrAssign {
    private final int traceId;
    private final HeapObject heapObject;
}
