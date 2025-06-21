package tregression.aliastracking;

import java.util.ArrayList;
import java.util.List;

import lombok.Getter;

@Getter
public class InstanceClassInfo {
    private final String className;
    private final String superClassName;
    private final List<String> interfaces;
    private final boolean isAbstract;
    private final boolean isInterface;

    private final List<InstanceClassField> fields;

    public InstanceClassInfo(String className, String superClassName, List<String> interfaces, boolean isAbstract,
            boolean isInterface) {
        this.fields = new ArrayList<>();
        this.className = className;
        this.superClassName = superClassName;
        this.interfaces = interfaces != null ? new ArrayList<>(interfaces) : new ArrayList<>();
        this.isAbstract = isAbstract;
        this.isInterface = isInterface;
    }

    public void addField(String className, String fieldName, String fieldType) {
        fields.add(new InstanceClassField(className, fieldName, fieldType));
    }
}
