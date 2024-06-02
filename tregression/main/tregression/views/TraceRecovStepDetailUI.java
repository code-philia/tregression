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
import microbat.model.trace.Trace;
import microbat.model.trace.TraceNode;
import microbat.model.value.VarValue;
import microbat.tracerecov.VariableGraph;
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
		FeedbackSubmitListener fListener = new FeedbackSubmitListener();
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
			Trace trace = traceView.getTrace();

			Object[] objList = readVariableTreeViewer.getCheckedElements();
			if (objList.length != 0) {
				Object obj = objList[0];
				if (obj instanceof VarValue) {
					VarValue readVar = (VarValue) obj;
					
					ExecutionSimulator executionSimulator = new ExecutionSimulator();

					/* 1. Variable Expansion */
					/*
					 * Expand the selected variable and replace the original variable with the
					 * expanded variable.
					 */
					VariableSkeleton variable = VarSkeletonBuilder.getVariableStructure(readVar.getType());
					try {
						executionSimulator.expandVariable(readVar, Arrays.asList(variable), currentNode);
					} catch (IOException e1) {
						e1.printStackTrace();
					}
					
					if (variable != null) {
						currentNode.getReadVariables().remove(readVar);
						readVar = variable.toVarValue(readVar);
						currentNode.addReadVariable(readVar);
					}

					/* 2. Context Scope Analysis */
					/*
					 * Build a variable graph.
					 */
					VariableGraph.reset();
					trace.recoverDataDependency(currentNode, readVar);

					/* 3. Execution Simulation */
					/*
					 * Identify additional linking steps and simulate execution by calling LLM
					 * model.
					 */
					try {
						
						executionSimulator.recoverLinkageSteps();
						executionSimulator.sendRequests();
					} catch (IOException ioException) {
						ioException.printStackTrace();
					}

					readVariableTreeViewer.refresh();
				}
			}
		}
	}
}
