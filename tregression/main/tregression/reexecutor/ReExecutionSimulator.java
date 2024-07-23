package tregression.reexecutor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import microbat.codeanalysis.runtime.Condition;
import microbat.model.trace.TraceNode;
import microbat.model.value.VarValue;
import microbat.tracerecov.TraceRecovUtils;
import microbat.tracerecov.executionsimulator.VariableExpansionUtils;
import microbat.tracerecov.varskeleton.VarSkeletonBuilder;
import microbat.tracerecov.varskeleton.VariableSkeleton;

public class ReExecutionSimulator {
	public ReExecutionSimulator() {
		
	}
	
	/*
	 * Expand the selected variable by re-excution
	 */
	public void expandVariable(VarValue selectedVar, TraceNode step, boolean isOnBuggy) {
		if(selectedVar.isExpanded()) {
			return;
		}
		String variableName = selectedVar.getVarName();
		String variableType = selectedVar.getType();
		String variableValue = selectedVar.getStringValue();
		VariableSkeleton variableSkeleton = VarSkeletonBuilder.getVariableStructure(selectedVar.getType(),
				step.getTrace().getAppJavaClassPath());
		String classStructure = variableSkeleton.toString();;
		Condition condition = new Condition(variableName,variableType,variableValue,classStructure);
		
		ConditionalExecutor executor = new ConditionalExecutor(condition);
		String groundTruthStr = executor.expandVariable(isOnBuggy);
		
		if(groundTruthStr == null) {
			System.out.println("__ERROR__ Expand variable failed!");
		}
		
		VariableExpansionUtils.processResponse(selectedVar, groundTruthStr);
	}

	/*
	 * Alias inference through re-excution
	 */
	public Map<VarValue, VarValue> inferAliasRelations(TraceNode step, VarValue rootVar,List<VarValue> criticalVariables) {
		List<VarValue> variablesInStep = step.getReadVariables(); // TODO: Written or Read or All variables ?
		
		// Expand variable at current step
		for(VarValue var:variablesInStep) {
			if(!var.isExpanded() && TraceRecovUtils.shouldBeChecked(var.getType())) {
				System.out.println("Expand variable "+var.getVarName()+" at step: "+step.getOrder());
				expandVariable(var,step,true);
			}
		}
		
		// Check if have same alias id
		Map<VarValue, VarValue> result = new HashMap<VarValue, VarValue>(); // key: criticalVariables,  value: fields of variables in step
		for(VarValue targetVar : criticalVariables) {
			IsSameAlias(targetVar,variablesInStep,result);
		}
		
		// Record ground truth to file
		if(result.isEmpty()) {
			return result;
		}
		ReExecutionSimulatorFileLogger fileLogger = new ReExecutionSimulatorFileLogger();
		fileLogger.collectAliasGT(step, rootVar, criticalVariables,result);
		
		return result;
	}
	
	public void IsSameAlias(VarValue targetVar, List<VarValue> variablesInStep, Map<VarValue, VarValue> result) {
		for(VarValue varInStep : variablesInStep) {
			IsSameAliasRecur(targetVar, varInStep,result);
		}
	}
	
	public void IsSameAliasRecur(VarValue targetVar, VarValue varInStep, Map<VarValue, VarValue> result) {
		if(targetVar.equals(varInStep) || varInStep.getAliasVarID() == null) {
			return;
		}
		if(varInStep.getAliasVarID().equals(targetVar.getAliasVarID())) {
			result.put(targetVar, varInStep);
		}
		else {
			for(VarValue child: varInStep.getChildren()) {
				IsSameAliasRecur(targetVar,child, result);
			}
		}
	}
}
