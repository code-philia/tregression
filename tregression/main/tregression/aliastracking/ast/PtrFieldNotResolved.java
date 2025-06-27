package tregression.aliastracking.ast;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PtrFieldNotResolved {
    private HeapAddr memAddr;
    private Ptr fieldToResolve;

    @Override
    public String toString() {
        return "FIELD_NOT_RESOLVED{" + memAddr + "[" + fieldToResolve + "]}";
    }
}
