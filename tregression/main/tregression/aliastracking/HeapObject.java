package tregression.aliastracking;

import java.util.List;
import java.util.Map;

import lombok.Getter;

@Getter
public class HeapObject {
    private String heapId;
    private String className;
    private boolean isArray;
    private int arrayLength;

    private int mergedStep;
    private HeapObject mergedTo;

    private List<HeapObjectField> fields;
    private Map<String, HeapObjectField> fieldMap;
}
