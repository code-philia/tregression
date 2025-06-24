package tregression.aliastracking;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.ChildListPropertyDescriptor;
import org.eclipse.jdt.core.dom.ChildPropertyDescriptor;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.objectweb.asm.Type;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import microbat.model.trace.TraceNode;
import microbat.model.value.ArrayValue;
import microbat.model.value.ReferenceValue;
import microbat.model.value.VarValue;
import microbat.tracerecov.TraceRecovUtils;
import tregression.aliastracking.ast.Expr;
import tregression.aliastracking.ast.HeapAddr;
import tregression.aliastracking.ast.HeapAddrHeapId;
import tregression.aliastracking.ast.HeapAddrPtrValue;
import tregression.aliastracking.ast.Ptr;
import tregression.aliastracking.ast.PtrField;
import tregression.aliastracking.ast.PtrVar;

@Slf4j
@Getter
public class HeapObjects {
    private HashMap<String, HeapPtr> variablePtrs;
    private HashMap<String, HeapObject> heapIdMapping;
    private HeapObject nullObject;

    public HeapObjects() {
        variablePtrs = new HashMap<>();
        heapIdMapping = new HashMap<>();

        nullObject = HeapObject.createNullObject();
    }

    private void printVarInfo(VarValue var, String prefix) {
        String res = String.format(
                "%s: %s. ID: %s. Alias: %s.",
                prefix, var.getVarName(),
                var.getVarID(),
                var.getAliasVarID(),
                var.getClass().getSimpleName(),
                var.getVariable().getClass().getSimpleName(),
                var.getVariable().getType());
        // System.out.println(res);
        for (VarValue child : var.getChildren()) {
            String prefix_child = "  " + prefix;
            printVarInfo(child, prefix_child);
        }
    }

    public List<Expression> findInvokingChildren(ASTNode node) {
        ArrayDeque<ASTNode> queue = new ArrayDeque<>();
        queue.add(node);

        List<Expression> results = new ArrayList<>();

        while (!queue.isEmpty()) {
            ASTNode current = queue.poll();
            if (current instanceof MethodInvocation) {
                results.add((MethodInvocation) current);
            }
            if (current instanceof ClassInstanceCreation) {
                results.add((ClassInstanceCreation) current);
            }

            for (Object child : current.structuralPropertiesForType()) {
                if (child instanceof ChildListPropertyDescriptor) {
                    Object value = current.getStructuralProperty((ChildListPropertyDescriptor) child);
                    child = value;
                } else if (child instanceof ChildPropertyDescriptor) {
                    Object value = current.getStructuralProperty((ChildPropertyDescriptor) child);
                    child = value;
                }
                if (child instanceof ASTNode) {
                    queue.add((ASTNode) child);
                }
                if (child instanceof List) {
                    for (Object item : (List<?>) child) {
                        if (item instanceof ASTNode) {
                            queue.add((ASTNode) item);
                        }
                    }
                }
            }
        }

        List<Expression> reversedResults = new ArrayList<>();
        for (int i = results.size() - 1; i >= 0; i--) {
            reversedResults.add(results.get(i));
        }

        return reversedResults;
    }

    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    public static class InvokingInfo {
        private String methodName;
        private List<String> arguments;
        private String object;

        private String className;
        private String signature;

        @Override
        public String toString() {
            StringBuffer sb = new StringBuffer();
            sb.append(className);
            sb.append("#");
            sb.append(methodName);
            sb.append(signature);
            sb.append(" ");
            sb.append(object != null ? object : "null");
            sb.append(".");
            sb.append(methodName);
            sb.append("(");
            for (int i = 0; i < arguments.size(); i++) {
                sb.append(arguments.get(i));
                if (i < arguments.size() - 1) {
                    sb.append(", ");
                }
            }
            sb.append(")");
            return sb.toString();
        }
    }

    public InvokingInfo parsingInvokingInfo(Expression expression) {
        if (expression instanceof MethodInvocation) {
            MethodInvocation methodInvocation = (MethodInvocation) expression;
            String methodName = methodInvocation.getName().toString();
            Expression expr = methodInvocation.getExpression();
            String object = null;
            if (expr != null) {
                object = expr.toString();
            }
            List<String> arguments = new ArrayList<>();
            for (Object arg : methodInvocation.arguments()) {
                arguments.add(arg.toString());
            }
            return new InvokingInfo(methodName, arguments, object, null, null);
        } else if (expression instanceof ClassInstanceCreation) {
            ClassInstanceCreation classInstanceCreation = (ClassInstanceCreation) expression;
            String methodName = "<init>";
            String object = null;
            List<String> arguments = new ArrayList<>();
            for (Object arg : classInstanceCreation.arguments()) {
                arguments.add(arg.toString());
            }
            return new InvokingInfo(methodName, arguments, object, null, null);
        } else {
            throw new IllegalArgumentException("Unsupported expression type: " + expression.getClass().getName());
        }
    }

    public void parseInvoking(String code, List<String> signatures) {
        try {
            @SuppressWarnings("deprecation")
            ASTParser parser = ASTParser.newParser(AST.JLS8);
            parser.setSource(code.toCharArray());
            parser.setKind(ASTParser.K_STATEMENTS);
            Block node = (Block) parser.createAST(null);
            List<Expression> invokingNodes = findInvokingChildren(node);
            List<InvokingInfo> invokingInfos = new ArrayList<>();
            for (Expression expr : invokingNodes) {
                InvokingInfo info = parsingInvokingInfo(expr);
                invokingInfos.add(info);
            }
            List<InvokingInfo> matchedInvokingInfos = new ArrayList<>();
            IdentityHashMap<InvokingInfo, Void> added = new IdentityHashMap<>();

            for (String sign : signatures) {
                String[] sign_parts = sign.split("#");
                String className = sign_parts[0];
                String methodNameSignature = sign_parts[1];
                int index_left = methodNameSignature.indexOf('(');
                String methodName = methodNameSignature.substring(0, index_left);
                String signature = methodNameSignature.substring(index_left, methodNameSignature.length());

                Type type = Type.getMethodType(signature);
                // System.out.println("Signature: " + signature);
                // System.out.println("Signature: " + type.toString());
                int argCount = type.getArgumentTypes().length;

                for (InvokingInfo info : invokingInfos) {
                    if (added.containsKey(info)) {
                        continue;
                    }
                    if (info.getMethodName().equals(methodName) && info.getArguments().size() == argCount) {
                        info.setClassName(className);
                        info.setSignature(signature);
                        added.put(info, null);
                        matchedInvokingInfos.add(info);
                        break;
                    }
                }
            }

            // for (InvokingInfo info : matchedInvokingInfos) {
            // System.out.println(" Matched Invoking: " + info);
            // }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void processTrace(List<TraceNode> traceNodes) {
        for (TraceNode node : traceNodes) {
            int idx = node.getOrder();
            if (idx > 10) {
                continue;
            }

            // System.out.println("Trace: " + node + ".");

            int lineNo = node.getLineNumber();
            String location = node.getBreakPoint().getFullJavaFilePath();
            String sourceCode = TraceRecovUtils.getSourceCodeOfALine(location, lineNo).trim();
            // System.out.println(" CODE: " + sourceCode);

            Collection<VarValue> readVariables = node.getReadVariables();
            Collection<VarValue> writtenVariables = node.getWrittenVariables();
            for (VarValue var : readVariables) {
                printVarInfo(var, "  Read");
                recordVariable(new PtrVar(var.getVarID()), var, node.getOrder(), false);
            }
            for (VarValue var : writtenVariables) {
                printVarInfo(var, "  Written");
                recordVariable(new PtrVar(var.getVarID()), var, node.getOrder(), true);
            }

            if (node.isCallingAPI()) {
                String invokingMethod = node.getInvokingMethod();
                String[] invokedMethods = invokingMethod.split("%");
                List<String> unrecorded = new ArrayList<>();

                List<TraceNode> children = node.getInvocationChildren();
                String expandedMethod = null;
                if (children != null && !children.isEmpty()) {
                    expandedMethod = children.get(0).getMethodSign();
                }
                for (String m : invokedMethods) {
                    if (m == null || m.isEmpty()) {
                        continue;
                    }
                    if (!m.equals(traceNodes)) {
                        unrecorded.add(m);
                    }
                }
                if (!unrecorded.isEmpty()) {
                    parseInvoking(sourceCode, unrecorded);
                }
            }

        }
    }

    public void recordVariable(Ptr ptr, VarValue value, int stepId, boolean isWritten) {
        if (value instanceof ReferenceValue) {
            ReferenceValue refValue = (ReferenceValue) value;
            String heapId = refValue.getAliasVarID();
            HeapAddr addr = new HeapAddrHeapId(heapId);
            Expr expr = new Expr(ptr, addr);
            addAssignment(expr, stepId);

            for (VarValue child : refValue.getChildren()) {
                recordVariable(new PtrField(addr, child.getVarName()), child, stepId, isWritten);
            }
        } else if (value instanceof ArrayValue) {
            ArrayValue arrValue = (ArrayValue) value;
            String heapId = arrValue.getAliasVarID();
            HeapAddr addr = new HeapAddrHeapId(heapId);
            Expr expr = new Expr(ptr, addr);
            addAssignment(expr, stepId);

            for (VarValue child : arrValue.getChildren()) {
                recordVariable(new PtrField(addr, child.getVarName()), child, stepId, isWritten);
            }
        }
    }

    public HeapPtr getVariablePtr(String variableName) {
        return variablePtrs.get(variableName);
    }

    public HeapPtr findPtrByVarId(String varId) {
        if (!variablePtrs.containsKey(varId)) {
            variablePtrs.put(varId, HeapPtr.newEmpty());
        }
        return variablePtrs.get(varId);
    }

    public HeapObject findHeapObjectById(String heapId) {
        if (heapId == null || heapId.isEmpty() || heapId.equals("-1")) {
            return nullObject;
        }
        if (!heapIdMapping.containsKey(heapId)) {
            HeapObject newObject = HeapObject.createWithHeapId(heapId);
            heapIdMapping.put(heapId, newObject);
        }
        return heapIdMapping.get(heapId);
    }

    public HeapPtr findPtr(Ptr ptr, int stepId) {
        if (ptr instanceof PtrVar) {
            PtrVar ptrVar = (PtrVar) ptr;
            return findPtrByVarId(ptrVar.getVarId());
        } else if (ptr instanceof PtrField) {
            PtrField ptrField = (PtrField) ptr;
            HeapObject obj = findHeapObject(ptrField.getMemAddr(), stepId);
            return obj.resolveField(ptrField.getFieldId());
        } else {
            throw new IllegalArgumentException("Unsupported Ptr type: " + ptr.getClass().getName());
        }
    }

    public HeapObject findHeapObject(HeapAddr addr, int stepId) {
        if (addr instanceof HeapAddrHeapId) {
            HeapAddrHeapId heapAddrHeapId = (HeapAddrHeapId) addr;
            return findHeapObjectById(heapAddrHeapId.getHeapId());
        } else if (addr instanceof HeapAddrPtrValue) {
            HeapAddrPtrValue heapAddrPtrValue = (HeapAddrPtrValue) addr;
            HeapPtr ptr = findPtr(heapAddrPtrValue.getPtr(), stepId);
            return ptr.resolveValue(this, stepId);
        } else {
            throw new IllegalArgumentException("Unsupported HeapAddr type: " + addr.getClass().getName());
        }
    }

    public void addAssignment(Expr expr, int stepId) {
        // System.out.println(" Assign: " + expr);
        HeapPtr ptr = findPtr(expr.getLeft(), stepId);
        HeapObject value = findHeapObject(expr.getRight(), stepId);
        ptr.addAssignment(stepId, value);
    }

    public boolean isAlias(Ptr left, Ptr right, int stepId) {
        HeapObject leftObject = findHeapObject(new HeapAddrPtrValue(left), stepId);
        HeapObject rightObject = findHeapObject(new HeapAddrPtrValue(right), stepId);
        return leftObject == rightObject;
    }

    public HeapObject createAnonymousObject() {
        return HeapObject.createAnonymousObject();
    }
}
