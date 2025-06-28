package tregression.aliastracking;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

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

import com.google.gson.Gson;

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
import microbat.tracerecov.TraceRecoverer.AliasInferencer;
import tregression.aliastracking.ast.Expr;
import tregression.aliastracking.ast.HeapAddr;
import tregression.aliastracking.ast.HeapAddrHeapId;
import tregression.aliastracking.ast.HeapAddrPtrValue;
import tregression.aliastracking.ast.Ptr;
import tregression.aliastracking.ast.PtrField;
import tregression.aliastracking.ast.PtrFieldNotResolved;
import tregression.aliastracking.ast.PtrVar;
import tregression.aliastracking.ast.PtrVarNotResolved;

@Slf4j
@Getter
public class HeapObjects implements AliasInferencer {
    private HashMap<String, HeapPtr> variablePtrs;
    private HashMap<String, HeapObject> heapIdMapping;
    private HeapObject nullObject;

    private String requestUrl;
    private Gson gson;

    public HeapObjects(String requestUrl) {
        variablePtrs = new HashMap<>();
        heapIdMapping = new HashMap<>();

        nullObject = HeapObject.createNullObject();

        this.requestUrl = requestUrl;
        this.gson = new Gson();
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    private static class MethodRequest {
        private String className;
        private String methodName;
        private String methodSign;
        private String thisName;
        private List<String> args;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    private static class MethodResponse {
        private List<Expr> result;
        private String error;
    }

    private List<Expr> requestMethod(String className, String methodName, String methodSign, List<String> args,
            String thisName) {

        MethodRequest request = new MethodRequest(className, methodName, methodSign, thisName, args);
        String requestBody = gson.toJson(request);
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(requestUrl).openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            conn.getOutputStream().write(requestBody.getBytes("UTF-8"));
            conn.connect();
            if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
                throw new IOException("Failed to connect to the server: " + requestUrl + ". Response code: "
                        + conn.getResponseCode());
            }
            StringBuilder response = new StringBuilder();
            try (var reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(conn.getInputStream(), "UTF-8"))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
            }
            MethodResponse responseObj = Expr.getGson().fromJson(response.toString(), MethodResponse.class);
            if (responseObj.getError() != null) {
                throw new RuntimeException("Error from server: " + responseObj.getError());
            }
            log.info("Request: {} {} {} {} {}. Response: {}",
                    className, methodName, methodSign, thisName, args, responseObj.getResult());
            return responseObj.getResult();
        } catch (Exception e) {
            throw new RuntimeException("Failed to connect to the server: " + requestUrl, e);
        }
    }

    private void printVarInfo(VarValue var, String prefix) {
        // String res = String.format(
        // "%s: %s. ID: %s. Alias: %s.",
        // prefix, var.getVarName(),
        // var.getVarID(),
        // var.getAliasVarID(),
        // var.getClass().getSimpleName(),
        // var.getVariable().getClass().getSimpleName(),
        // var.getVariable().getType());
        // System.out.println(res);
        // for (VarValue child : var.getChildren()) {
        // String prefix_child = " " + prefix;
        // printVarInfo(child, prefix_child);
        // }
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

    public List<InvokingInfo> parseInvoking(String code, List<String> signatures) {
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

            return matchedInvokingInfos;
        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
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
                    List<InvokingInfo> infos = parseInvoking(sourceCode, unrecorded);
                    if (infos != null) {
                        for (InvokingInfo info : infos) {
                            try {
                                List<Expr> exprs = requestMethod(info.getClassName(), info.getMethodName(),
                                        info.getSignature(),
                                        info.getArguments(), info.getObject());

                                for (Expr expr : exprs) {
                                    try {
                                        addAssignmentWithUnresolved(expr, node.getOrder(), node);
                                    } catch (FailedToResolveException e) {
                                    }
                                }
                            } catch (Exception e) {
                                log.error("Failed to process invoking info: " + info, e);
                            }
                        }
                    }
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
        System.out.println(" Assign: " + expr);
        HeapPtr ptr = findPtr(expr.getLeft(), stepId);
        HeapObject value = findHeapObject(expr.getRight(), stepId);
        ptr.addAssignment(stepId, value);
    }

    public void addAssignmentWithUnresolved(Expr expr, int stepId, TraceNode node)
            throws FailedToResolveException {
        Ptr left = findPtrWithUnresolved(expr.getLeft(), stepId, node);
        HeapAddr right = findHeapObjectWithUnresolved(expr.getRight(), stepId, node);
        Expr resolvedExpr = new Expr(left, right);
        addAssignment(resolvedExpr, stepId);
    }

    public Ptr findPtrWithUnresolved(Ptr ptr, int stepId, TraceNode node)
            throws FailedToResolveException {
        try {
            return findPtrOnTrace(ptr, stepId, node);
        } catch (FailedToResolveException e) {
        }

        if (ptr instanceof PtrField) {
            PtrField ptrField = (PtrField) ptr;
            HeapAddr resolved = findHeapObjectOnTrace(ptrField.getMemAddr(), stepId, node);
            return new PtrField(resolved, ptrField.getFieldId());
        } else if (ptr instanceof PtrFieldNotResolved) {
            PtrFieldNotResolved ptrFieldNotResolved = (PtrFieldNotResolved) ptr;
            HeapAddr resolved = findHeapObjectWithUnresolved(ptrFieldNotResolved.getMemAddr(), stepId, node);
            VarValue resolvedExpr = findPtrVarOnTrace(ptrFieldNotResolved.getMemAddr(), stepId, node);
            String value = resolvedExpr.getStringValue();
            String fieldName = "[" + value + "]";
            return new PtrField(resolved, fieldName);
        } else if (ptr instanceof PtrVar) {
            return ptr;
        } else if (ptr instanceof PtrVarNotResolved) {
            return findPtrOnTrace(ptr, stepId, node);
        }

        throw new FailedToResolveException("Failed to resolve Ptr: " + ptr);
    }

    public VarValue findPtrVarOnTrace(HeapAddr addr, int stepId, TraceNode node) throws FailedToResolveException {
        if (addr instanceof HeapAddrHeapId) {
            throw new FailedToResolveException("HeapAddrHeapId cannot be resolved on trace: " + addr);
        } else if (addr instanceof HeapAddrPtrValue) {
            return findPtrVarOnTrace(((HeapAddrPtrValue) addr).getPtr(), stepId, node);
        } else {
            throw new IllegalStateException("Unsupported HeapAddr type: " + addr.getClass().getName());
        }
    }

    public VarValue findPtrVarOnTrace(Ptr ptr, int stepId, TraceNode node) throws FailedToResolveException {
        if (ptr instanceof PtrField) {
            PtrField ptrField = (PtrField) ptr;
            VarValue resolved = findPtrVarOnTrace(ptrField.getMemAddr(), stepId, node);
            VarValue result = null;
            for (VarValue child : resolved.getChildren()) {
                if (child.getVarName().equals(ptrField.getFieldId())) {
                    result = child;
                    break;
                }
            }
            if (result != null) {
                return result;
            }
            throw new FailedToResolveException("Field not found in resolved variable: " + ptrField);
        } else if (ptr instanceof PtrFieldNotResolved) {
            throw new FailedToResolveException("PtrFieldNotResolved cannot be resolved on trace: " + ptr);
        } else if (ptr instanceof PtrVar) {
            throw new FailedToResolveException("PtrVar cannot be resolved on trace: " + ptr);
        } else if (ptr instanceof PtrVarNotResolved) {
            PtrVarNotResolved ptrVarNotResolved = (PtrVarNotResolved) ptr;
            String varName = ptrVarNotResolved.getVarName();
            VarValue matched = null;
            for (VarValue var : node.getReadVariables()) {
                if (var.getVarName().equals(varName)) {
                    matched = var;
                    break;
                }
            }
            if (matched != null) {
                return matched;
            }
            throw new FailedToResolveException("Variable not found in read variables: " + varName);
        } else {
            throw new IllegalStateException("Unsupported Ptr type: " + ptr.getClass().getName());
        }
    }

    public Ptr findPtrOnTrace(Ptr ptr, int stepId, TraceNode node)
            throws FailedToResolveException {
        if (ptr instanceof PtrVar) {
            return ptr;
        } else if (ptr instanceof PtrFieldNotResolved) {
            throw new FailedToResolveException(
                    "PtrFieldNotResolved cannot be resolved on trace: " + ptr);
        } else if (ptr instanceof PtrVarNotResolved || ptr instanceof PtrField) {
            VarValue resolved = findPtrVarOnTrace(ptr, stepId, node);
            return new PtrVar(resolved.getVarID());
        } else {
            throw new IllegalStateException("Unsupported Ptr type: " + ptr.getClass().getName());
        }
    }

    public HeapAddr findHeapObjectWithUnresolved(HeapAddr addr, int stepId, TraceNode node)
            throws FailedToResolveException {
        if (addr instanceof HeapAddrHeapId) {
            return addr;
        } else if (addr instanceof HeapAddrPtrValue) {
            HeapAddrPtrValue heapAddrPtrValue = (HeapAddrPtrValue) addr;
            VarValue resolvedVar = findPtrVarOnTrace(heapAddrPtrValue.getPtr(), stepId, node);
            return new HeapAddrHeapId(resolvedVar.getAliasVarID());
        } else {
            throw new IllegalStateException("Unsupported HeapAddr type: " + addr.getClass().getName());
        }
    }

    public HeapAddr findHeapObjectOnTrace(HeapAddr addr, int stepId, TraceNode node) throws FailedToResolveException {
        if (addr instanceof HeapAddrHeapId) {
            return addr;
        } else if (addr instanceof HeapAddrPtrValue) {
            HeapAddrPtrValue heapAddrPtrValue = (HeapAddrPtrValue) addr;
            VarValue resolvedVar = findPtrVarOnTrace(heapAddrPtrValue.getPtr(), stepId, node);
            return new HeapAddrHeapId(resolvedVar.getAliasVarID());
        } else {
            throw new IllegalStateException("Unsupported HeapAddr type: " + addr.getClass().getName());
        }
    }

    public boolean isAlias(Ptr left, Ptr right, int stepId) {
        HeapObject leftObject = findHeapObject(new HeapAddrPtrValue(left), stepId);
        HeapObject rightObject = findHeapObject(new HeapAddrPtrValue(right), stepId);
        return leftObject == rightObject;
    }

    public HeapObject createAnonymousObject() {
        return HeapObject.createAnonymousObject();
    }

    @Override
    public Map<VarValue, VarValue> inferAliasBetween(TraceNode slicingCreteria, TraceNode targetLine) {
        List<VarValuePtrPair> slicingPairs = getVarValuePtrPairs(slicingCreteria, true);
        List<VarValuePtrPair> targetPairs = getVarValuePtrPairs(targetLine, false);

        Map<VarValue, VarValue> aliasMap = new HashMap<>();
        for (VarValuePtrPair slicingPair : slicingPairs) {
            for (VarValuePtrPair targetPair : targetPairs) {
                if (isAlias(slicingPair.ptr, targetPair.ptr, targetLine.getOrder())) {
                    aliasMap.put(slicingPair.varValue, targetPair.varValue);
                }
            }
        }
        return aliasMap;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    private static class VarValuePtrPair {
        private VarValue varValue;
        private Ptr ptr;
    }

    private List<VarValuePtrPair> getVarValuePtrPairs(TraceNode node, boolean shouldHaveAlias) {
        List<VarValuePtrPair> result = new ArrayList<>();
        for (VarValue var : node.getReadVariables()) {
            getVarPtrPairsRoot(var, result, shouldHaveAlias);
        }
        for (VarValue var : node.getWrittenVariables()) {
            getVarPtrPairsRoot(var, result, shouldHaveAlias);
        }
        return result;
    }

    private void getVarPtrPairsRoot(VarValue var, List<VarValuePtrPair> result, boolean shouldHaveAlias) {
        if (!(var instanceof ReferenceValue || var instanceof ArrayValue)) {
            return;
        }
        String heapId = var.getAliasVarID();
        boolean hasAlias = heapId != null && !heapId.isEmpty() && !heapId.equals("-1");
        Ptr ptr = new PtrVar(var.getVarID());

        if (hasAlias == shouldHaveAlias) {
            VarValuePtrPair pair = new VarValuePtrPair(var, ptr);
            result.add(pair);
        }

        for (VarValue child : var.getChildren()) {
            getVarPtrPairsRecur(child, ptr, result, shouldHaveAlias);
        }
    }

    private void getVarPtrPairsRecur(VarValue var, Ptr currentPtr, List<VarValuePtrPair> result,
            boolean shouldHaveAlias) {
        if (!(var instanceof ReferenceValue || var instanceof ArrayValue)) {
            return;
        }
        String heapId = var.getAliasVarID();
        boolean hasAlias = heapId != null && !heapId.isEmpty() && !heapId.equals("-1");
        Ptr ptr = new PtrField(new HeapAddrPtrValue(currentPtr), var.getVarName());

        if (hasAlias == shouldHaveAlias) {
            VarValuePtrPair pair = new VarValuePtrPair(var, ptr);
            result.add(pair);
        }

        for (VarValue child : var.getChildren()) {
            getVarPtrPairsRecur(child, ptr, result, shouldHaveAlias);
        }
    }
}
