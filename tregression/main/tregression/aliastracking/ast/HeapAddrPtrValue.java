package tregression.aliastracking.ast;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class HeapAddrPtrValue extends HeapAddr {
    private Ptr ptr;

    @Override
    public String toString() {
        return ptr.toString();
    }
}
