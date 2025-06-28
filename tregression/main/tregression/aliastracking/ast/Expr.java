package tregression.aliastracking.ast;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.typeadapters.RuntimeTypeAdapterFactory;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Expr {
    private Ptr left;
    private HeapAddr right;

    @Getter
    private static final Gson gson;
    static {
        RuntimeTypeAdapterFactory<Ptr> ptrAdapter = RuntimeTypeAdapterFactory.of(Ptr.class, "type")
                .registerSubtype(PtrField.class, "field")
                .registerSubtype(PtrVar.class, "var")
                .registerSubtype(PtrFieldNotResolved.class, "fieldnotresolved")
                .registerSubtype(PtrVarNotResolved.class, "varnotresolved");
        RuntimeTypeAdapterFactory<HeapAddr> heapAddrAdapter = RuntimeTypeAdapterFactory.of(HeapAddr.class, "type")
                .registerSubtype(HeapAddrHeapId.class, "heapid")
                .registerSubtype(HeapAddrPtrValue.class, "ptrvalue");
        gson = new GsonBuilder()
                .setPrettyPrinting()
                .registerTypeAdapterFactory(ptrAdapter)
                .registerTypeAdapterFactory(heapAddrAdapter)
                .create();
    }

    public static Expr readFromJson(String json) {
        return gson.fromJson(json, Expr.class);
    }

    @Override
    public String toString() {
        return left.toString() + " = " + right.toString();
    }
}
