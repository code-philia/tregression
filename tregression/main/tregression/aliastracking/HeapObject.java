package tregression.aliastracking;

import java.util.HashMap;
import java.util.Map;

import lombok.Getter;

@Getter
public class HeapObject {
    private String heapId;
    private Map<String, HeapObjectField> fields;

    private HeapObject() {
        heapId = "null";
        fields = new HashMap<>();
    }

    public static HeapObject createNullObject() {
        HeapObject nullObject = new HeapObject();
        nullObject.heapId = "null";
        return nullObject;
    }

    public boolean isNull() {
        return heapId.equals("null");
    }
}
