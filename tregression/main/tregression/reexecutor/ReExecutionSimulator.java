package tregression.reexecutor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import microbat.codeanalysis.bytecode.CFG;
import microbat.codeanalysis.runtime.Condition;
import microbat.model.trace.TraceNode;
import microbat.model.value.VarValue;
import microbat.model.variable.Variable;
import microbat.tracerecov.CannotBuildCFGException;
import microbat.tracerecov.TraceRecovUtils;
import microbat.tracerecov.candidatevar.CandidateVarVerifier;
import microbat.tracerecov.candidatevar.CandidateVarVerifier.WriteStatus;
import microbat.tracerecov.executionsimulator.ReExecutionSimulatorFileLogger;
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
		executor.expandVariable(selectedVar,isOnBuggy);
		
		System.out.println("After expanded:");
		printVar(selectedVar,0);
	}

	/*
	 * Expand the current step/method by re-excution
	 */
	public void expandStep() {
		
	}
	
	/*
	 * Alias inference through re-excution
	 */
	public Map<VarValue, VarValue> inferAliasRelations(TraceNode step, VarValue rootVar,List<VarValue> criticalVariables) {
		Set<VarValue> variablesInStep = step.getAllVariables();
		
		// Expand variable at current step
		for(VarValue var:variablesInStep) {
			if(!var.isExpanded() && TraceRecovUtils.shouldBeChecked(var.getType())) {
				expandVariable(var,step,true);
				var.setExpanded(true);
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
	
	/*
	 * Definition inference through re-excution
	 */
	public boolean inferDefinition(TraceNode step, VarValue rootVar, VarValue targetVar, List<VarValue> criticalVariables) {

		WriteStatus complication = WriteStatus.NO_GUARANTEE;
		VarValue ancestorVarOnTrace = null;
		for (VarValue readVarInStep : step.getReadVariables()) {
			String aliasID = readVarInStep.getAliasVarID();
			VarValue criticalAncestor = criticalVariables.stream()
					.filter(criticalVar -> aliasID.equals(criticalVar.getAliasVarID())).findFirst().orElse(null);

			if (criticalAncestor != null) {
				ancestorVarOnTrace = readVarInStep;
				break;
			}
		}
		
		ReExecutionSimulatorFileLogger fileLogger = new ReExecutionSimulatorFileLogger();
		
		// static analyze
		if (ancestorVarOnTrace == null) {
			complication = WriteStatus.GUARANTEE_NO_WRITE;
		} else if (TraceRecovUtils.shouldBeChecked(ancestorVarOnTrace.getType())) {
			complication = estimateComplication(step, ancestorVarOnTrace, targetVar);
		}
		if (complication == WriteStatus.GUARANTEE_WRITE) {
			fileLogger.collectDefinitionGT(step, rootVar, targetVar, criticalVariables, true);
			return true;
		} else if (complication == WriteStatus.GUARANTEE_NO_WRITE) {
			fileLogger.collectDefinitionGT(step, rootVar, targetVar, criticalVariables, false);
			return false;
		}
		
		// re-excution
		
		
		fileLogger.collectDefinitionGT(step, rootVar, targetVar, criticalVariables, false);
		return false;
	}
	
	
	public void IsSameAlias(VarValue targetVar, Set<VarValue> variablesInStep, Map<VarValue, VarValue> result) {
		for(VarValue varInStep : variablesInStep) {
			IsSameAliasRecur(targetVar, varInStep,result, true);
		}
	}
	
	public void IsSameAliasRecur(VarValue targetVar, VarValue varInStep, Map<VarValue, VarValue> result, boolean isOnStep) {
		if(targetVar.equals(varInStep) || varInStep.getAliasVarID() == null || targetVar == varInStep) {
			return;
		}
		if(Variable.truncateSimpleID(varInStep.getAliasVarID()).equals(Variable.truncateSimpleID(targetVar.getAliasVarID())) && !isOnStep) {
			result.put(targetVar, varInStep);
		}
		else {
			for(VarValue child: varInStep.getChildren()) {
				IsSameAliasRecur(targetVar,child, result,false);
			}
		}
	}
	
	public void printVar(VarValue v,int depth) {
		System.out.println(String.valueOf(" ").repeat(depth*4)+v.getVarName()+": "+v.getVarID()+"  "+v.getAliasVarID());
		for(VarValue child:v.getChildren()) {
			printVar(child,depth+1);
		}
	}
	
	/**
	 * deterministic flow: guarantee_write, guarantee_no_write
	 * must-analysis by LLM: no_guarantee
	 */
	private WriteStatus estimateComplication(TraceNode step, VarValue parentVar, VarValue targetVar) {

		String[] invokingMethods = step.getInvokingMethod().split("%");

		for (String invokedMethod : invokingMethods) {

			try {
				CFG cfg = TraceRecovUtils.getCFGFromMethodSignature(invokedMethod);
				CandidateVarVerifier candidateVarVerifier = new CandidateVarVerifier(cfg);
				return candidateVarVerifier.getVarWriteStatus(targetVar.getVarName());
			} catch (CannotBuildCFGException e) {
				e.printStackTrace();
	 			}
	 		}
		return WriteStatus.NO_GUARANTEE;
	 }

}
