package tregression.aliastracking.ast;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PtrField extends Ptr {
    private HeapAddr memAddr;
    private String fieldId;

    @Override
    public String toString() {
        return memAddr.toString() + "." + fieldId;
    }
}
