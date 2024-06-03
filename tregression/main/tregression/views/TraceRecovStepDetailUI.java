package tregression.views;

import java.io.IOException;
import java.util.Arrays;

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

import microbat.model.trace.Trace;
import microbat.model.trace.TraceNode;
import microbat.model.value.VarValue;
import microbat.tracerecov.TraceRecoverer;
import microbat.tracerecov.executionsimulator.ExecutionSimulator;
import microbat.tracerecov.varexpansion.VarSkeletonBuilder;
import microbat.tracerecov.varexpansion.VariableSkeleton;

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

		/* Added by hongshuwang */
		Button contextAnalysisButton = new Button(slicingGroup, SWT.NONE);
		contextAnalysisButton.setText("Analyse Context");
		contextAnalysisButton.setLayoutData(new GridData(SWT.RIGHT, SWT.TOP, true, false));
		ContextAnalysisListener cListener = new ContextAnalysisListener();
		contextAnalysisButton.addMouseListener(cListener);
	}

	class ContextAnalysisListener implements MouseListener {

		public void mouseUp(MouseEvent e) {
		}

		public void mouseDoubleClick(MouseEvent e) {
		}

		public void mouseDown(MouseEvent e) {
			Object[] objList = readVariableTreeViewer.getCheckedElements();
			if (objList.length != 0) {
				Object obj = objList[0];
				if (obj instanceof VarValue) {
					VarValue readVar = (VarValue) obj;

					/*
					 * Expand the selected variable and replace the original variable with the
					 * expanded variable.
					 */
					VariableSkeleton variable = VarSkeletonBuilder.getVariableStructure(readVar.getType());
					try {
						System.out.println("***Variable Expansion***");
						System.out.println();
						ExecutionSimulator executionSimulator = new ExecutionSimulator();
						executionSimulator.expandVariable(readVar, Arrays.asList(variable), currentNode);
					} catch (IOException ioException) {
						ioException.printStackTrace();
					}

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

		public void mouseDown(MouseEvent e) {
			if (feedback == null) {
				openChooseFeedbackDialog();
			} else {
				Trace trace = traceView.getTrace();

				TraceNode suspiciousNode = null;
				if (dataButton.getSelection()) {
					Object[] objList = readVariableTreeViewer.getCheckedElements();
					if (objList.length != 0) {
						Object obj = objList[0];
						if (obj instanceof VarValue) {
							VarValue readVar = (VarValue) obj;
							suspiciousNode = trace.findDataDependency(currentNode, readVar);
							if (suspiciousNode == null) {
								// find parent node
								VarValue rootVar = readVar;
								while (!currentNode.getReadVariables().contains(rootVar)) {
									rootVar = rootVar.getParents().get(0); // TODO: multiple parents?
								}

								new TraceRecoverer().recoverDataDependency(trace, currentNode, readVar, rootVar);
								suspiciousNode = trace.findDataDependency(currentNode, readVar);
							}
						}
					}
				} else if (controlButton.getSelection()) {
					suspiciousNode = currentNode.getInvocationMethodOrDominator();
				}

				if (suspiciousNode != null) {
					traceView.recordVisitedNode(currentNode);
					jumpToNode(trace, suspiciousNode);
				}
			}
		}

		private void jumpToNode(Trace trace, TraceNode suspiciousNode) {
			traceView.jumpToNode(trace, suspiciousNode.getOrder(), true);
		}
	}
}
