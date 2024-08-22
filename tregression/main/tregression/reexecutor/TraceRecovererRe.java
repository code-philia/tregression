package tregression.reexecutor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import microbat.model.trace.Trace;
import microbat.model.trace.TraceNode;
import microbat.model.value.ArrayValue;
import microbat.model.value.PrimitiveValue;
import microbat.model.value.ReferenceValue;
import microbat.model.value.StringValue;
import microbat.model.value.VarValue;
import microbat.model.variable.FieldVar;
import microbat.model.variable.Variable;
import microbat.tracerecov.TraceRecovUtils;

public class TraceRecovererRe {

	private Trace currentTrace;
	private ReExecutionSimulator executionSimulator;
	
	public TraceRecovererRe(Trace t) {
		currentTrace = t;
		executionSimulator = new ReExecutionSimulator();
	}
	
	public TraceNode findDataDependency(TraceNode checkingNode, VarValue readVar) {
		TraceNode dataDominator = findProducer(readVar, checkingNode);

		/*
		 * Collect ground truth of alias inference & definition inference
		 */
		VarValue rootVar = readVar;
		while (!checkingNode.getReadVariables().contains(rootVar)) {
			rootVar = rootVar.getParents().get(0);
		}

		if (TraceRecovUtils.shouldBeChecked(rootVar.getType())) {
			recoverDataDependency(checkingNode, readVar, rootVar);

			checkingNode.addRecoveredDataDependency(readVar);
			rootVar.setRecoveryPerformed(true);

			dataDominator = findProducer(readVar, checkingNode);
		}

		return dataDominator;
	}
	
	public void recoverDataDependency(TraceNode currentStep, VarValue targetVar, VarValue rootVar) {

		Trace trace = currentStep.getTrace();
		List<VarValue> criticalVariables = createQueue(targetVar, rootVar);
		Set<String> variablesToCheck = getVariablesToCheck(criticalVariables);
		List<Integer> relevantSteps = new ArrayList<>();

		// determine scope of searching
		TraceNode scopeStart = determineScopeOfSearching(rootVar, trace, currentStep);
		if (scopeStart == null)
			return;
		int start = scopeStart.getOrder() + 1;
		int end = currentStep.getOrder() - 1;

		// alias and definition inferencing
		inferAliasRelationsRe(trace, start, end, rootVar, criticalVariables, variablesToCheck, relevantSteps);
		
		relevantSteps.clear();
		for(int i = start;i<=end;i++) {
			relevantSteps.add(i);
		}
		
		inferDefinition(trace, rootVar, targetVar, criticalVariables, relevantSteps);
	}
	
	/**
	 * the first element is rootVar, and the last one is the direct parent of the
	 * targetVar.
	 */
	private List<VarValue> createQueue(VarValue targetVar, VarValue rootVar) {
		List<VarValue> list = new ArrayList<VarValue>();

		VarValue temp = targetVar;
		list.add(temp);

		// TODO consider more complicated graph scenarios
		while (temp != rootVar) {
			temp = temp.getParents().get(0);
			if (!list.contains(temp)) {
				list.add(temp);
			}
		}

		List<VarValue> list0 = new ArrayList<VarValue>();
		// modified by hongshu
		// Add target var to list: the address of target var should also be inferred
		for (int i = list.size() - 1; i >= 0; i--) {
			list0.add(list.get(i));
		}

		return list0;
	}
	
	/**
	 * add all the existing alias IDs
	 */
	private Set<String> getVariablesToCheck(List<VarValue> criticalVariables) {
		Set<String> variablesToCheck = new HashSet<>();

		for (VarValue criticalVar : criticalVariables) {
			String aliasID = criticalVar.getAliasVarID();
			if (isValidAliasID(aliasID)) {
				variablesToCheck.add(aliasID);
			}
		}

		return variablesToCheck;
	}
	
	private boolean isValidAliasID(String aliasID) {
		return aliasID != null && !aliasID.equals("0") && !aliasID.equals("");
	}
	
	/**
	 * search for data dominator of parentVar (skip return steps TODO: test more
	 * scenarios)
	 */
	private TraceNode determineScopeOfSearching(VarValue parentVar, Trace trace, TraceNode currentStep) {
		VarValue lastWrittenVariable = null;
		TraceNode scopeStart = currentStep;
		while (lastWrittenVariable == null) {

			scopeStart = trace.findProducer(parentVar, scopeStart);
			if (scopeStart == null) {
				break;
			}

			lastWrittenVariable = scopeStart.getWrittenVariables().stream()
					.filter(v -> v.getVarName() != null && !v.getVarName().contains("#")).findFirst().orElse(null);
		}
		return scopeStart;
	}
	
	/**
	 * only check steps containing variablesToCheck AND are calling API
	 */
	private boolean isRelevantStep(TraceNode step, Set<String> variablesToCheck) {
		Set<VarValue> variablesInStep = step.getAllVariables();
		Set<String> aliasIDsInStep = new HashSet<>(variablesInStep.stream().map(v -> v.getAliasVarID()).toList());

		return aliasIDsInStep.stream().anyMatch(id -> variablesToCheck.contains(id));
	}
	
	/**
	 * only infer address when there are new variables at the current step.
	 * 
	 * TODO: consider edge cases
	 */
	private boolean isRequiringAliasInference(TraceNode step, Set<String> variablesToCheck) {
		Set<VarValue> variablesInStep = step.getAllVariables();
		Set<String> aliasIDsInStep = new HashSet<>(variablesInStep.stream().map(v -> v.getAliasVarID()).toList());

		int numOfNewVars = 0;
		for (String id : aliasIDsInStep) {
			if (!variablesToCheck.contains(id)) {
				numOfNewVars++;
			}
		}
		return numOfNewVars > 0;
	}
	
	private boolean isCriticalVariable(List<VarValue> criticalVariables, VarValue variable) {
		String varID = variable.getVarID();
		return criticalVariables.stream().anyMatch(v -> v.getVarID().equals(varID));
	}
	
	private void updateAliasIDOfField(VarValue writtenField, VarValue variableOnTrace,
			List<VarValue> criticalVariables) {
		VarValue targetField = null;
		VarValue targetVarOnTrace = variableOnTrace;
		for (VarValue var : criticalVariables) {
			if (targetField == null) {
				if (var.equals(writtenField)) {
					targetField = var;
					if (targetVarOnTrace.getAliasVarID().equals(targetField.getAliasVarID())) {
						break;
					}
					targetField.setAliasVarID(targetVarOnTrace.getAliasVarID());
				}
			} else {
				targetField = var;
				String currentTargetName = targetField.getVarName();

				if (targetVarOnTrace != null) {
					targetVarOnTrace = targetVarOnTrace.getChildren().stream()
							.filter(v -> v.getVarName().equals(currentTargetName)).findFirst().orElse(null);
				}

				if (targetVarOnTrace != null) {
					targetField.setAliasVarID(targetVarOnTrace.getAliasVarID());
				} else {
					// reset children field IDs
					targetField.setAliasVarID("");
				}

			}
		}
	}
	
	private void addVarSkeletonToVariablesOnTrace(TraceNode step, List<VarValue> criticalVariables) {
		for (VarValue readVar : step.getReadVariables()) {
			String aliasID = readVar.getAliasVarID();
			VarValue criticalVar = null;
			for (VarValue var : criticalVariables) {
				if (criticalVar == null) {
					if (aliasID.equals(var.getAliasVarID())) {
						criticalVar = var;
					}
				} else {
					VarValue varValueCopy = null;
					Variable variableCopy = new FieldVar(var.isStatic(), var.getVarName(), var.getType(),
							var.getType());
					if (var instanceof ArrayValue) {
						varValueCopy = new ArrayValue(false, var.isRoot(), variableCopy);
					} else if (var instanceof ReferenceValue) {
						varValueCopy = new ReferenceValue(false, var.isRoot(), variableCopy);
					} else if (var instanceof StringValue) {
						varValueCopy = new StringValue(VarValue.VALUE_TBD, var.isRoot(), variableCopy);
					} else if (var instanceof PrimitiveValue) {
						varValueCopy = new PrimitiveValue(VarValue.VALUE_TBD, var.isRoot(), variableCopy);
					}

					if (varValueCopy != null) {
						varValueCopy.setStringValue(VarValue.VALUE_TBD);
						varValueCopy.setVarID(readVar.getVarID() + "." + varValueCopy.getVarName());
						varValueCopy.setAliasVarID(var.getAliasVarID());

						readVar.updateChild(varValueCopy);
						readVar = varValueCopy;
					}
				}
			}
		}
	}
	
	/**
	 * Alias Inference: FORWARD ITERATION
	 * 
	 * iterate through steps in scope, infer address, add relevant variables to the
	 * set and the corresponding steps
	 */
	private void inferAliasRelationsRe(Trace trace, int scopeStart, int scopeEnd, VarValue rootVar,
			List<VarValue> criticalVariables, Set<String> variablesToCheck, List<Integer> relevantSteps) {

		for (int i = scopeStart; i <= scopeEnd; i++) {
			TraceNode step = trace.getTraceNode(i);
			if (isRelevantStep(step, variablesToCheck)) {
				relevantSteps.add(i);

				if (isRequiringAliasInference(step, variablesToCheck)) {
					Map<VarValue, VarValue> fieldToVarOnTraceMap = this.executionSimulator.inferAliasRelations(step,rootVar, criticalVariables);

					for (VarValue writtenField : fieldToVarOnTraceMap.keySet()) {
						if (isCriticalVariable(criticalVariables, writtenField)) {
							VarValue variableOnTrace = fieldToVarOnTraceMap.get(writtenField);
							String aliasIdOfCriticalVar = variableOnTrace.getAliasVarID();

							if (isValidAliasID(aliasIdOfCriticalVar)) {
								updateAliasIDOfField(writtenField, variableOnTrace, criticalVariables);
								variablesToCheck.add(aliasIdOfCriticalVar);
							}
						} else {
							/*
							 * key and value: variable on trace. Field in variable is not recorded.
							 */
							if (isValidAliasID(writtenField.getAliasVarID())) {
								variablesToCheck.add(writtenField.getAliasVarID());
							}
						}
					}
				}

				addVarSkeletonToVariablesOnTrace(step, criticalVariables);
			}
		}
	}

	public TraceNode findProducer(VarValue varValue, TraceNode startNode) {
		
		if (varValue == null || startNode == null) {
			return null;
		}

		String varID = Variable.truncateSimpleID(varValue.getVarID());
		String headID = Variable.truncateSimpleID(varValue.getAliasVarID());
		
		for(int i=startNode.getOrder()-1; i>=1; i--) {
			TraceNode node = currentTrace.getTraceNode(i);
			for(VarValue writtenValue: node.getWrittenVariables()) {
				
				String wVarID = Variable.truncateSimpleID(writtenValue.getVarID());
				String wHeadID = Variable.truncateSimpleID(writtenValue.getAliasVarID());
				
				if(wVarID != null && wVarID.equals(varID)) {
					return node;						
				}
				
				if(wHeadID != null && wHeadID.equals(headID)) {
					return node;
				}
				
//				VarValue childValue = writtenValue.findVarValue(varID, headID);
//				if(childValue != null) {
//					return node;
//				}
				
			}
		}
		
		return null;
	}
	
	/**
	 * Definition Inference: BACKWARD ITERATION
	 * 
	 * iterate through steps in scope, infer definition
	 */
	private void inferDefinition(Trace trace, VarValue rootVar, VarValue targetVar, List<VarValue> criticalVariables, List<Integer> relevantSteps) {

		int startIndex = relevantSteps.size() - 1;
		for (int i = startIndex; i >= 0; i--) {
			int stepOrder = relevantSteps.get(i);
			TraceNode step = trace.getTraceNode(stepOrder);
			if (step.isCallingAPI()) {
				// INFER DEFINITION STEP
				boolean def = this.executionSimulator.inferDefinition(step, rootVar, targetVar, criticalVariables);

				if (def && !step.getWrittenVariables().contains(targetVar)) {
					step.getWrittenVariables().add(targetVar);
					break;
				}
			}
		}
	}
}
