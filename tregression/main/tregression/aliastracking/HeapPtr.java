package tregression.aliastracking;

import java.util.List;

import lombok.Getter;

@Getter
public class HeapPtr {
    private List<HeapPtrAssign> assignments;
    private String type;
}
