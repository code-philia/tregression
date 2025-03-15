package tregression.reexecutor;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.json.JSONObject;

import microbat.Activator;
import microbat.codeanalysis.runtime.Condition;
import microbat.model.trace.Trace;
import microbat.model.trace.TraceNode;
import microbat.model.value.VarValue;
import microbat.preference.RecovSlicingPreference;
import microbat.tracerecov.TraceRecovUtils;
import microbat.tracerecov.varskeleton.VarSkeletonBuilder;
import microbat.tracerecov.varskeleton.VariableSkeleton;

public class ReExecutionSimulatorFileLogger {
	
	private String aliasFilePath;
	
	public ReExecutionSimulatorFileLogger() {
        String aliasFileName = "aliases.txt";
        this.aliasFilePath = Activator.getDefault().getPreferenceStore().getString(RecovSlicingPreference.INCONTEXT_FILE_PATH)
                + File.separator + aliasFileName;
	}
	
    private PrintWriter createWriter(String path) throws IOException {
        FileWriter fileWriter = new FileWriter(path, true);
        PrintWriter writer = new PrintWriter(fileWriter);
        return writer;
    }

    public void collectAliasGT(TraceNode step, VarValue rootVar, List<VarValue> criticalVariables, Map<VarValue, VarValue> map) {
        // write to results file
        PrintWriter writer;
        try {
            writer = createWriter(this.aliasFilePath);
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
        writer.println(getAliasCsvContent(step, rootVar, criticalVariables, map));
    }

    private String getAliasCsvContent(TraceNode step, VarValue rootVar, List<VarValue> criticalVariables, Map<VarValue, VarValue> map) {
    	StringBuilder groundTruth = new StringBuilder();
    	String delimiter = "###";
    	
		/* source code */
		int lineNo = step.getLineNumber();
		String location = step.getBreakPoint().getFullJavaFilePath();
		String sourceCode = TraceRecovUtils.processInputStringForLLM(TraceRecovUtils.getSourceCodeOfALine(location, lineNo).trim());

		/* all variables */
		Set<VarValue> variablesInStep = step.getAllVariables();

		/* variable properties */
		String rootVarName = rootVar.getVarName();

		/* type structure */
		String jsonString = TraceRecovUtils.processInputStringForLLM(rootVar.toJSON().toString());

		/* invoked methods to be checked */
		Set<String> invokedMethods = TraceRecovUtils.getInvokedMethodsToBeChecked(step.getInvokingMethod());
    	
		// source code
		groundTruth.append(sourceCode);
		groundTruth.append(delimiter);

		// variables information (name, type, value)
		for (VarValue var : variablesInStep) {
			groundTruth.append("`");
			groundTruth.append(var.getVarName());
			groundTruth.append("` is of type `");
			groundTruth.append(var.getType());
			groundTruth.append("`, of runtime value \"");
			groundTruth.append(var.getStringValue());
			groundTruth.append("\",");
		}
		groundTruth.append(delimiter);

		// target variable structure
		groundTruth.append(rootVarName);
		groundTruth.append(":");
		groundTruth.append(jsonString);
		groundTruth.append(delimiter);

		// existing alias relations
		for (VarValue var : variablesInStep) {
			VarValue criticalVariable = null;
			if (var.getAliasVarID() != null) {
				criticalVariable = criticalVariables.stream().filter(v -> var.getAliasVarID().equals(v.getAliasVarID()))
						.findFirst().orElse(null);
			}
			if (criticalVariable == null) {
				continue;
			}

			String cascadeFieldName = "";
			int splitIndex = criticalVariable.getVarID().indexOf(".");
			if (splitIndex >= 0) {
				cascadeFieldName = rootVar.getVarName() + criticalVariable.getVarID().substring(splitIndex);
			} else {
				cascadeFieldName = rootVar.getVarName();
			}

			if (!cascadeFieldName.equals(var.getVarName())) {
				groundTruth.append("`");
				groundTruth.append(var.getVarName());
				groundTruth.append("` has the same memory address as `");
				groundTruth.append(cascadeFieldName);
				groundTruth.append("`,");
			}
		}
		groundTruth.append(delimiter);
		
		// fields in other variables
		for (VarValue var : variablesInStep) {
			if (var.equals(rootVar)) {
				continue;
			}
			VariableSkeleton varSkeleton = VarSkeletonBuilder.getVariableStructure(var.getType(), null);
			if (varSkeleton == null) {
				continue;
			}
			groundTruth.append(var.getVarName());
			groundTruth.append("` has the following fields:\n");
			groundTruth.append(varSkeleton.fieldsToString());
			groundTruth.append(",");
		}
		
		groundTruth.append(delimiter);

		// invoked methods
		if (!invokedMethods.isEmpty()) {
			for (String methodSig : invokedMethods) {
				groundTruth.append(methodSig);
				groundTruth.append(";");
			}
		}
		else {
			groundTruth.append(" ");
		}
		groundTruth.append(delimiter);
		
		// keys (critical variables)
		List<String> criticalVarName = new ArrayList<String>();
		String cascadeName = "";
		for (VarValue criticalVar : criticalVariables) {
			groundTruth.append("`" + cascadeName + criticalVar.getVarName() + "`,");
			criticalVarName.add(cascadeName + criticalVar.getVarName());
			cascadeName += criticalVar.getVarName() + ".";
		}
		groundTruth.append(delimiter);
		
		// ground truth
		Map<String,String> gt = new HashMap<String,String>();
		for (Map.Entry<VarValue, VarValue> entry : map.entrySet()) {
		    VarValue key_var = entry.getKey();
		    VarValue value_var = entry.getValue();
		    for(String name:criticalVarName) {
		    	if(name.endsWith(key_var.getVarName())) {
		    		gt.put(name, getCascadeName(value_var));
		    		break;
		    	}
		    }
		}
		
        JSONObject jsonObject = new JSONObject(gt);
        String json = jsonObject.toString();
        
        groundTruth.append(json);
		
    	return groundTruth.toString();
    }
    
    public String getCascadeName(VarValue v) {
    	String cascadeName = v.getVarName();
    	while(!v.getParents().isEmpty()) {
    		v = v.getParents().get(0);
    		cascadeName = v.getVarName()+"."+cascadeName;
    	}
    	return cascadeName;
    }
    
}
