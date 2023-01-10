package tregression.handler;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;

import debuginfo.DebugInfo;
import debuginfo.NodeFeedbackPair;
import microbat.model.trace.Trace;
import microbat.model.trace.TraceNode;
import microbat.model.value.VarValue;
import microbat.probability.SPP.ActionPath;
import microbat.probability.SPP.StepwisePropagator;
import microbat.recommendation.UserFeedback;
import microbat.util.JavaUtil;
import microbat.util.TraceUtil;
import tregression.views.BuggyTraceView;
import tregression.views.CorrectTraceView;

public class StepwisePropagationHandler extends AbstractHandler {

	private BuggyTraceView buggyView;
	private CorrectTraceView correctView;
	
//	private TraceNode currentNode = null;
	
	private Stack<NodeFeedbackPair> records = new Stack<>();
	
	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		JavaUtil.sourceFile2CUMap.clear();
		Job job = new Job("Testing Tregression") {

			@Override
			protected IStatus run(IProgressMonitor monitor) {
				
				setup();
				
				System.out.println();
				System.out.println("---------------------------------------------");
				System.out.println("\t Stepwise Probability Propagation");
				System.out.println();
				
				// Check is the trace ready
				if (buggyView.getTrace() == null) {
					System.out.println("Please setup the trace before propagation");
					return Status.OK_STATUS;
				}
				
				// Check is the IO ready
				if (!isIOReady()) {
					System.out.println("Please provide the inputs and the outputs");
					return Status.OK_STATUS;
				}
				
				// Obtain the inputs and outputs from users
				List<VarValue> inputs = DebugInfo.getInputs();
				List<VarValue> outputs = DebugInfo.getOutputs();
				
				final TraceNode startingNode = getStartingNode(buggyView.getTrace(), outputs.get(0));
				TraceNode currentNode = startingNode;
				
				// Set up the propagator that perform propagation
				StepwisePropagator propagator = new StepwisePropagator(buggyView.getTrace(), inputs, outputs);
				propagator.init();
				propagator.computeComputationalCost();
				
				int feedbackCounts = 0;
				
				while(!DebugInfo.isRootCauseFound() && !DebugInfo.isStop()) {
					System.out.println("---------------------------------- " + feedbackCounts + " iteration");
					System.out.println("Propagation Start");
					
					// Start propagation
					propagator.forwardPropagation();
					propagator.backwardPropagate();
					propagator.combineProbability();
					
					System.out.println("Propagation End");
					
//					UserFeedback feedback = propagator.giveFeedback(startingNode);
					
					jumpToNode(startingNode);
					
//					System.out.println();
//					System.out.println("Prediction for node: " + startingNode.getOrder());
//					System.out.println(feedback);
//			
					// Get the predicted root cause
					TraceNode rootCause = propagator.proposeRootCause();
					System.out.println("Proposed Root Cause: " + rootCause.getOrder());

					ActionPath path = propagator.findPathway(startingNode, rootCause);
					
					System.out.println();
					System.out.println("Debug: Suggested Pathway");
					for (NodeFeedbackPair section : path) {
						System.out.println("Debug: " + section);
					}
					System.out.println();
					
					List<NodeFeedbackPair> responses = new ArrayList<>();
					for (NodeFeedbackPair pair : path) {
						
						final TraceNode node = pair.getNode();
						if (node.getOrder() > currentNode.getOrder()) {
							continue;
						}
						
						System.out.println("Predicted feedback: ");
						System.out.println(pair);
						
						jumpToNode(currentNode);
						
						// Obtain feedback from user
						System.out.println("Please give a feedback");
						DebugInfo.waitForFeedbackOrRootCauseOrStop();
						
						NodeFeedbackPair userPair = DebugInfo.getNodeFeedbackPair();
						System.out.println("User Feedback: ");
						System.out.println(userPair);
						
						UserFeedback userFeedback = userPair.getFeedback();
						UserFeedback predictedFeedback = pair.getFeedback();
						
						responses.add(userPair);
						if (userFeedback.week_equals(predictedFeedback)) {
							currentNode = TraceUtil.findNextNode(currentNode, userFeedback, buggyView.getTrace());
						} else {
							propagator.responseToFeedbacks(responses);
							currentNode = TraceUtil.findNextNode(currentNode, userFeedback, buggyView.getTrace());
							break;
						}

//						if (userFeedback.week_equals(predictedFeedback)) {
//							responses.add(userPair);
//							currentNode = findNextNode(currentNode, userFeedback);
//						} else if (userFeedback.getFeedbackType().equals(UserFeedback.CORRECT)){
//							System.out.println("Wrong feedback predicted. Recalculate the process...");
//							currentNode = startingNode;
//							propagator.responseToFeedback(userPair);
//							break;
//						} else {
//							System.out.println("Wrong feedback predicted. Recalculate the process...");
//							boolean isExternalFeedback = true;
//							for (NodeFeedbackPair pair_ : path) {
//								if (pair_.getNode().equals(node)) {
//									isExternalFeedback = false;
//									break;
//								}
//							}
//							
//							if (isExternalFeedback) {
//								currentNode = startingNode;
//								propagator.responseToFeedback(pair);
//							} else {
//								currentNode = findNextNode(currentNode, userFeedback);
//								responses.add(userPair);
//								propagator.responseToFeedbacks(responses);
//							}
//							break;
//						}
					}
				}
				return Status.OK_STATUS;
			}
			
		};
		job.schedule();
		return null;
	}
	
//	private TraceNode findNextNode(final TraceNode node, final UserFeedback feedback) {
//		TraceNode nextNode = null;
//		if (feedback.getFeedbackType() == UserFeedback.WRONG_PATH) {
//			nextNode = node.getControlDominator();
//		} else if (feedback.getFeedbackType() == UserFeedback.WRONG_VARIABLE_VALUE) {
//			VarValue wrongVar = feedback.getOption().getReadVar();
//			nextNode = buggyView.getTrace().findDataDependency(node, wrongVar);
//		}
//		return nextNode;
//	}
	
	private void setup() {
		Display.getDefault().syncExec(new Runnable() {
			@Override
			public void run() {
				try {
					buggyView = (BuggyTraceView)PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage().showView(BuggyTraceView.ID);
					correctView = (CorrectTraceView)PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage().showView(CorrectTraceView.ID);
				} catch (PartInitException e) {
					buggyView = null;
					correctView = null;
					System.out.println("Fail to get the view");
				}
			}
		});
	}
	
	private boolean isIOReady() {
		return !DebugInfo.getInputs().isEmpty() && !DebugInfo.getOutputs().isEmpty();
	}
	
	private void jumpToNode(final TraceNode targetNode) {
		Display.getDefault().asyncExec(new Runnable() {
		    @Override
		    public void run() {
				Trace buggyTrace = buggyView.getTrace();
				buggyView.jumpToNode(buggyTrace, targetNode.getOrder(), true);
		    }
		});
	}

	private void printReport(final int noOfFeedbacks) {
		System.out.println("---------------------------------");
		System.out.println("Number of feedbacks: " + noOfFeedbacks);
		System.out.println("---------------------------------");
	}
	
	private TraceNode getStartingNode(final Trace trace, final VarValue output) {
		for (int order = trace.size(); order>=0; order--) {
			TraceNode node = trace.getTraceNode(order);
			final String varID = output.getVarID();
			if (node.isReadVariablesContains(varID)) {
				return node;
			} else if (node.isWrittenVariablesContains(varID)) {
				return node;
			}
		}
		return null;
	}
}
