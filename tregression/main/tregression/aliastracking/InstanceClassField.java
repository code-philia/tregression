package tregression.aliastracking;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class InstanceClassField {
    private final String className;
    private final String fieldName;
    private final String fieldType;
}
