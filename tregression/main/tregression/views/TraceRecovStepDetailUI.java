package tregression.views;

import java.io.IOException;
import java.util.List;
import java.util.Map;

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
import microbat.tracerecov.candidatevar.CandidateVarRetriever;
import microbat.tracerecov.executionsimulator.ExecutionSimulator;

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

					/* Candidate Variables Identification */
					/* Identify candidate variables through Java bytecode analysis (static) */
					List<String> candidateVariables = CandidateVarRetriever
							.getCandidateVariables(currentNode.getInvokingMethod());
					readVar.setCandidateVariables(candidateVariables);

					/* Context Scope Analysis */
					/* 1. Relevant Step Identification: Identify relevant steps by variable ID
					 * 2. Variable Mapping: Link candidate variables to variables on trace
					 * 3. Repeat step (1) and (2) to find the relevant steps for each candidate variable */
					Map<String, List<TraceNode>> relevantSteps = trace.findRecoveredDataDependency(currentNode,
							readVar);

					/* Execution Simulation */
					/* Simulate execution by calling LLM model */
					try {
						ExecutionSimulator executionSimulator = new ExecutionSimulator(relevantSteps, currentNode, readVar);
						List<String> response = executionSimulator.sendRequests();
						System.out.println();
					} catch (IOException ioException) {
						ioException.printStackTrace();
					}

					/* Variable Mapping */
					/* Add critical variables identified by the LLM to the trace */

					readVariableTreeViewer.refresh();
				}
			}
		}
	}
}
