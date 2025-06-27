package tregression.aliastracking.ast;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PtrVarNotResolved extends Ptr {
    private String varName;

    @Override
    public String toString() {
        return "VAR_NOT_RESOLVED{" + varName + "}";
    }
}
