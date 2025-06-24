package tregression.aliastracking.ast;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PtrVar extends Ptr {
    private String varId;

    @Override
    public String toString() {
        return "VAR{" + varId + "}";
    }
}
