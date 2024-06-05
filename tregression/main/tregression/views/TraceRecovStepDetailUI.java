package tregression.views;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.MouseListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.Shell;
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

					List<VariableSkeleton> variableSkeletons = new ArrayList<>();

					VarValue readVar = (VarValue) obj;
					/*
					 * Expand the selected variable.
					 */
					VariableSkeleton parentSkeleton = VarSkeletonBuilder.getVariableStructure(readVar.getType());
					variableSkeletons.add(parentSkeleton);

					// assume var layer == 1, then only elementArray will be recorded in ArrayList
					if (!readVar.getChildren().isEmpty()) {
						VarValue child = readVar.getChildren().get(0);
						String childType = child.getType();
						if (childType.contains("[]")) {
							childType = childType.substring(0, childType.length() - 2); // remove [] at the end
						}
						VariableSkeleton childSkeleton = VarSkeletonBuilder.getVariableStructure(childType);
						variableSkeletons.add(childSkeleton);
					}

					try {
						ExecutionSimulator executionSimulator = new ExecutionSimulator();
						executionSimulator.expandVariable(readVar, variableSkeletons, currentNode);
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
								Display.getDefault().asyncExec(new Runnable() {
									@Override
									public void run() {
										Shell shell = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell();
										if (!shell.isDisposed()) {
											// find parent node
											VarValue rootVar = readVar;
											while (!currentNode.getReadVariables().contains(rootVar)) {
												rootVar = rootVar.getParents().get(0); // TODO: multiple parents?
											}

											new TraceRecoverer().recoverDataDependency(trace, currentNode, readVar,
													rootVar);
											TraceNode targetNode = trace.findDataDependency(currentNode, readVar);

											if (targetNode != null) {
												traceView.recordVisitedNode(currentNode);
												jumpToNode(trace, targetNode);
											}
										}
									}
								});

//								Job job = new Job("Recovering Data Dependencies") {
//									@Override
//									protected IStatus run(IProgressMonitor monitor) {
//										// find parent node
//										VarValue rootVar = readVar;
//										while (!currentNode.getReadVariables().contains(rootVar)) {
//											rootVar = rootVar.getParents().get(0); // TODO: multiple parents?
//										}
//
//										new TraceRecoverer().recoverDataDependency(trace, currentNode, readVar,
//												rootVar);
//										TraceNode targetNode = trace.findDataDependency(currentNode, readVar);
//
//										if (targetNode != null) {
//											traceView.recordVisitedNode(currentNode);
//											jumpToNode(trace, targetNode);
//										}
//
//										return Status.OK_STATUS;
//									}
//								};
//								job.schedule(); // Start the job
							} else {
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
