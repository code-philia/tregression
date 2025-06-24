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

    public static HeapObject createAnonymousObject() {
        HeapObject result = new HeapObject();
        return result;
    }

    public static HeapObject createWithHeapId(String heapId) {
        HeapObject result = new HeapObject();
        result.heapId = heapId;
        return result;
    }

    public boolean isNull() {
        return heapId.equals("null");
    }

    public boolean isAnonymous() {
        return heapId == null;
    }

    public HeapPtr resolveField(String fieldName) {
        if (!fields.containsKey(fieldName)) {
            fields.put(fieldName, new HeapObjectField(this, fieldName));
        }
        return fields.get(fieldName).getPtr();
    }
}
