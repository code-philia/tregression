package tregression.views;

import java.io.IOException;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.MouseListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.ui.PlatformUI;

import microbat.codeanalysis.runtime.Condition;
import microbat.model.trace.Trace;
import microbat.model.trace.TraceNode;
import microbat.model.value.VarValue;
import microbat.tracerecov.executionsimulator.ExecutionSimulator;
import microbat.tracerecov.varskeleton.VarSkeletonBuilder;
import microbat.tracerecov.varskeleton.VariableSkeleton;
import microbat.util.Settings;
import tregression.reexecutor.ConditionalExecutor;

/**
 * A subclass of StepDetailUI with context scope analysis.
 * 
 * @author hongshuwang
 */
public class TraceRecovStepDetailUI extends StepDetailUI {

	public TraceRecovStepDetailUI(TregressionTraceView view, TraceNode node, boolean isOnBefore) {
		super(view, node, isOnBefore);
	}

	@Override
	protected void createSlicingGroup(Composite panel) {
		Group slicingGroup = new Group(panel, SWT.NONE);
		GridData data = new GridData(SWT.FILL, SWT.TOP, true, false);
		data.minimumHeight = 35;
		slicingGroup.setLayoutData(data);

		GridLayout gl = new GridLayout(3, true);
		slicingGroup.setLayout(gl);

		dataButton = new Button(slicingGroup, SWT.RADIO);
		dataButton.setLayoutData(new GridData(SWT.LEFT, SWT.TOP, true, false));
		dataButton.setText("data ");

		controlButton = new Button(slicingGroup, SWT.RADIO);
		controlButton.setLayoutData(new GridData(SWT.LEFT, SWT.TOP, true, false));
		controlButton.setText("control ");

		Button submitButton = new Button(slicingGroup, SWT.NONE);
		submitButton.setText("Go");
		submitButton.setLayoutData(new GridData(SWT.RIGHT, SWT.TOP, true, false));
		DependencyRecoveryBasedFeedbackSubmitListener fListener = new DependencyRecoveryBasedFeedbackSubmitListener();
		submitButton.addMouseListener(fListener);

		/**
		 * A button for expanding variable values
		 * 
		 * @author Hongshu
		 */
		Button variableExpansionByLLMButton = new Button(slicingGroup, SWT.NONE);
		variableExpansionByLLMButton.setText("Expand Variable By LLM");
		variableExpansionByLLMButton.setLayoutData(new GridData(SWT.RIGHT, SWT.TOP, true, false));
		ContextAnalysisListener cListener = new ContextAnalysisListener();
		variableExpansionByLLMButton.addMouseListener(cListener);

		/**
		 * A button for expanding varaible values by reexecution
		 */
		Button variableExpansionByExecButton = new Button(slicingGroup, SWT.NONE);
		variableExpansionByExecButton.setText("Expand Variable By Execution");
		variableExpansionByExecButton.setLayoutData(new GridData(SWT.RIGHT, SWT.TOP, true, false));
		VariableExpansionByExecListener vListener = new VariableExpansionByExecListener();
		variableExpansionByExecButton.addMouseListener(vListener);
	}

	class ContextAnalysisListener implements MouseListener {

		public void mouseUp(MouseEvent e) {
		}

		public void mouseDoubleClick(MouseEvent e) {
		}

		/**
		 * Variable Expansion
		 */
		public void mouseDown(MouseEvent e) {

			Settings.isEnableGPTInference = true;

			Object[] objList = readVariableTreeViewer.getCheckedElements();
			if (objList.length != 0) {
				Object obj = objList[0];
				if (obj instanceof VarValue) {

					try {
						ExecutionSimulator executionSimulator = new ExecutionSimulator();
						executionSimulator.expandVariable((VarValue) obj, currentNode, null);
					} catch (IOException ioException) {
						ioException.printStackTrace();
					}

					Settings.isEnableGPTInference = false;

					readVariableTreeViewer.refresh();
				}
			}
		}
	}

	class VariableExpansionByExecListener implements MouseListener {

		public void mouseUp(MouseEvent e) {
		}

		public void mouseDoubleClick(MouseEvent e) {
		}

		/**
		 * Variable Expansion
		 */
		public void mouseDown(MouseEvent e) {

			Object[] objList = readVariableTreeViewer.getCheckedElements();
			if (objList.length != 0) {
				Object obj = objList[0];
				if (obj instanceof VarValue) {

					VarValue selectedVar = (VarValue) obj;
					String variableName = selectedVar.getVarName();
					String variableType = selectedVar.getType();
					String variableValue = selectedVar.getStringValue();

					/*
					 * Expand the selected variable.
					 */
					VariableSkeleton variableSkeleton = VarSkeletonBuilder.getVariableStructure(variableType,
							currentNode.getTrace().getAppJavaClassPath());
					String classStructure = variableSkeleton.toString();

					Condition condition = new Condition(variableName, variableType, variableValue, classStructure);

					ConditionalExecutor executor = new ConditionalExecutor(condition);
					executor.expandVariable((VarValue) obj, currentNode);

					readVariableTreeViewer.refresh();
				}
			}
		}
	}

	class DependencyRecoveryBasedFeedbackSubmitListener implements MouseListener {
		public void mouseUp(MouseEvent e) {
		}

		public void mouseDoubleClick(MouseEvent e) {
		}

		private void openChooseFeedbackDialog() {
			MessageBox box = new MessageBox(PlatformUI.getWorkbench().getDisplay().getActiveShell());
			box.setMessage("Please tell me whether this step is correct or not!");
			box.open();
		}

		/**
		 * Find data dominator with TraceRecov
		 */
		public void mouseDown(MouseEvent e) {

			Settings.isEnableGPTInference = true;

			if (feedback == null) {
				openChooseFeedbackDialog();
			} else {
				Trace trace = traceView.getTrace();

				final TraceNode suspiciousNode;
				if (dataButton.getSelection()) {
					Object[] objList = readVariableTreeViewer.getCheckedElements();
					if (objList.length != 0) {
						Object obj = objList[0];
						if (obj instanceof VarValue) {
							VarValue readVar = (VarValue) obj;
							suspiciousNode = trace.findDataDependency(currentNode, readVar);
//							Display.getDefault().asyncExec(new Runnable() {
//								@Override
//								public void run() {
//									Shell shell = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell();
//									if (!shell.isDisposed()) {
//										if (suspiciousNode != null) {
//											traceView.recordVisitedNode(currentNode);
//											jumpToNode(trace, suspiciousNode);
//										}
//									}
//								}
//							});

							Settings.isEnableGPTInference = false;

							if (suspiciousNode != null) {
								traceView.recordVisitedNode(currentNode);
								jumpToNode(trace, suspiciousNode);
								readVariableTreeViewer.refresh();
							}

						}
					}
				} else if (controlButton.getSelection()) {
					suspiciousNode = currentNode.getInvocationMethodOrDominator();

					if (suspiciousNode != null) {
						traceView.recordVisitedNode(currentNode);
						jumpToNode(trace, suspiciousNode);
					}
				}

			}

		}

		private void jumpToNode(Trace trace, TraceNode suspiciousNode) {
			traceView.jumpToNode(trace, suspiciousNode.getOrder(), true);
		}
	}
}
