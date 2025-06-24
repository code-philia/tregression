package tregression.views;

import java.io.File;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.List;
import java.io.FileWriter;
import java.io.IOException;
import org.eclipse.jface.viewers.Viewer;
import microbat.model.BreakPointValue;
import microbat.model.value.ReferenceValue;
import microbat.model.value.VarValue;
import microbat.model.value.VirtualValue;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.viewers.ILabelProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;

import microbat.model.BreakPoint;
import microbat.model.ClassLocation;
import microbat.model.trace.Trace;
import microbat.model.trace.TraceNode;
import tregression.editors.CompareEditor;
import tregression.editors.CompareTextEditorInput;
import tregression.empiricalstudy.RootCauseFinder;
import tregression.model.PairList;
import tregression.model.TraceNodePair;
import tregression.separatesnapshots.DiffMatcher;
import tregression.separatesnapshots.diff.FilePairWithDiff;

public class BuggyTraceView extends TregressionTraceView {
	
	public static final String ID = "tregression.evalView.buggyTraceView";

	public BuggyTraceView() {
	}
	
	@Override
	protected Action createControlMendingAction() {
		Action action = new Action() {
			public void run() {
				if (listViewer.getSelection().isEmpty()) {
					return;
				}

				if (listViewer.getSelection() instanceof IStructuredSelection) {
					IStructuredSelection selection = (IStructuredSelection) listViewer.getSelection();
					TraceNode node = (TraceNode) selection.getFirstElement();
					
					CorrectTraceView correctTraceView = TregressionViews.getCorrectTraceView();
					ClassLocation correspondingLocation = diffMatcher.findCorrespondingLocation(node.getBreakPoint(), false);
					TraceNode otherControlDom = new RootCauseFinder().findControlMendingNodeOnOtherTrace(node, pairList, 
							correctTraceView.getTrace(), false, correspondingLocation, diffMatcher);
					
					if (otherControlDom != null) {
						correctTraceView.otherViewsBehavior(otherControlDom);
						correctTraceView.jumpToNode(correctTraceView.getTrace(), otherControlDom.getOrder(), refreshProgramState);
					}
					
				}
				
			}
			
			public String getText() {
				return "control mend";
			}
		};
		
		return action;
	}
	
	private void openInCompare(CompareTextEditorInput input, TraceNode node) {
		IWorkbench wb = PlatformUI.getWorkbench();
		IWorkbenchWindow win = wb.getActiveWorkbenchWindow();
		IWorkbenchPage workBenchPage = win.getActivePage();

		IEditorPart editPart = workBenchPage.findEditor(input);
		if(editPart != null){
			workBenchPage.activate(editPart);
			CompareEditor editor = (CompareEditor)editPart;
			editor.highLight(node);
		}
		else{
			try {
				workBenchPage.openEditor(input, CompareEditor.ID);
			} catch (PartInitException e) {
				e.printStackTrace();
			}
		}
		
	}

	class CompareFileName {
		String buggyFileName;
		String fixFileName;

		public CompareFileName(String buggyFileName, String fixFileName) {
			super();
			this.buggyFileName = buggyFileName;
			this.fixFileName = fixFileName;
		}

	}

	private CompareFileName generateCompareFile(BreakPoint breakPoint, DiffMatcher matcher) {
		
		String fixPath = "null";
		String buggyPath = breakPoint.getFullJavaFilePath();
		
		FilePairWithDiff fileDiff = diffMatcher.findDiffBySourceFile(breakPoint);
		if (getDiffMatcher() == null || fileDiff == null) {
			String bugBase = diffMatcher.getBuggyPath();
			String content = buggyPath.substring(bugBase.length(), buggyPath.length());
			fixPath = diffMatcher.getFixPath() + content;				
			if(!new File(fixPath).exists()){
				fixPath = buggyPath;
			}
		} else {
			fixPath = fileDiff.getTargetFile();
		}
		
		CompareFileName cfn = new CompareFileName(buggyPath, fixPath);
		return cfn;
	}

	@Override
	protected void markJavaEditor(TraceNode node) {
		BreakPoint breakPoint = node.getBreakPoint();
		
		CompareFileName cfn = generateCompareFile(breakPoint, diffMatcher);

		// System.out.println("buggyFileName: " + cfn.buggyFileName);
	    // System.out.println("fixFileName: " + cfn.fixFileName);
		CompareTextEditorInput input = new CompareTextEditorInput(node, this.pairList, 
				cfn.buggyFileName, cfn.fixFileName, diffMatcher);

		openInCompare(input, node);

	}
	
	public void jumpToNode2(Trace trace, int index, boolean refresh, TraceNode node, String path, int startLine, int endLine, double inc) {
	    // ======== 原有行为：调用默认的 Trace 跳转与多视图同步逻辑 ========
	    jumpToNode(trace, index, refresh);

	    BreakPoint breakPoint = node.getBreakPoint();
		
		CompareFileName cfn = generateCompareFile(breakPoint, diffMatcher);
		CompareTextEditorInput input = new CompareTextEditorInput(node, this.pairList, 
				cfn.buggyFileName, cfn.fixFileName, diffMatcher);

		IWorkbench wb = PlatformUI.getWorkbench();
		IWorkbenchWindow win = wb.getActiveWorkbenchWindow();
		IWorkbenchPage workBenchPage = win.getActivePage();

		IEditorPart editPart = workBenchPage.findEditor(input);
		if(editPart != null){
			workBenchPage.activate(editPart);
			CompareEditor editor = (CompareEditor)editPart;
			editor.highLight(node);
			editor.highlightBuggyBlock(node, path, startLine, endLine, inc);
		}
		else{
			try {
				workBenchPage.openEditor(input, CompareEditor.ID);
			} catch (PartInitException e) {
				e.printStackTrace();
			}
		}
	}


	
    class tempContentProvider implements ITreeContentProvider {
        boolean rw;

        public tempContentProvider(boolean rw) {
            this.rw = rw;
        }

        @Override
        public void dispose() {

        }

        @Override
        public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {

        }

        @Override
        public Object[] getElements(Object inputElement) {
            if (inputElement instanceof ArrayList) {
                ArrayList<VarValue> elements = (ArrayList<VarValue>) inputElement;
                return elements.toArray(new VarValue[0]);
            }

            return null;
        }

        @Override
        public Object[] getChildren(Object parentElement) {
            if (parentElement instanceof ReferenceValue) {
                ReferenceValue refValue = (ReferenceValue)parentElement;
                
                if(refValue.getChildren()==null){
                    return null;
                }
                
                return refValue.getChildren().toArray(new VarValue[0]);
            }

            return null;
        }

        @Override
        public Object getParent(Object element) {
            return null;
        }

        @Override
        public boolean hasChildren(Object element) {
            Object[] children = getChildren(element);
            if (children == null || children.length == 0) {
                return false;
            } else {
                return true;
            }
        }

    }

    private void sortVars(List<VarValue> vars){
        List<VarValue> readVars = vars;
        Collections.sort(readVars, new Comparator<VarValue>() {
            @Override
            public int compare(VarValue o1, VarValue o2) {
                return o1.getVarName().compareTo(o2.getVarName());
            }
        });
    }

    public String getVariablesText(Object element, int Depth) {
        if (element instanceof VarValue) {
            VarValue varValue = (VarValue) element;

            String type = varValue.getType();
            if (type.contains(".")) {
                type = type.substring(type.lastIndexOf(".") + 1, type.length());
            }

            String name = varValue.getVarName();
            if (varValue instanceof VirtualValue) {
                    String methodName = name.substring(name.indexOf(":") + 1);
                    name = "return from " + methodName + "()";
            }

            String value = varValue.getStringValue();

            String id = varValue.getVarID();
            String aliasVarID = varValue.getAliasVarID();

            if(aliasVarID == null){
                aliasVarID = "null";
            }

            String message = "{type: " + type +
                    "},{name: " + name +
                    "},{value: " + value +
                    "},{depth: " + Depth +
                    "},{id: " + id +
                    "},{alias: " + aliasVarID + "}";

            return message;
        }
        return null;
    }

    public void exportVariablesToFile(List<VarValue> Variables, boolean rw, FileWriter writer) {
        ITreeContentProvider contentProvider = new tempContentProvider(rw);
        // try (FileWriter writer = new FileWriter(filePath)) {
        try {
            for (VarValue variable : Variables) {
                writeElement(variable, contentProvider, writer, "");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void writeElement(Object element, ITreeContentProvider contentProvider, FileWriter writer, String indent) throws IOException {
        String label = getVariablesText(element, indent.length() / 2);
        writer.write(indent + label + System.lineSeparator());
        if (contentProvider.hasChildren(element)) {
            Object[] children = contentProvider.getChildren(element);
            for (Object child : children) {
                writeElement(child, contentProvider, writer, indent + "- ");
            }
        }
    }

	public void getVariables(Object element, FileWriter writer) {
		if (element instanceof TraceNode) {
				TraceNode node = (TraceNode) element;

				sortVars(node.getWrittenVariables());
				sortVars(node.getReadVariables());
				
				try {
					writer.write("--- Written ---\n" + System.lineSeparator());
					exportVariablesToFile(node.getWrittenVariables(), false, writer);
					writer.write("---- Read -----\n" + System.lineSeparator());
					exportVariablesToFile(node.getReadVariables(), true, writer);
				} catch (IOException e) {
		            e.printStackTrace();
				}
				//String message = message_R + message_W;
				return;
		}
		return;
	}
	
    public String getTraceText(Object element, int Depth) {
        if (element instanceof TraceNode) {
            TraceNode node = (TraceNode) element;
            
            BreakPoint breakPoint = node.getBreakPoint();
            // BreakPointValue programState = node.getProgramState();

            String className = breakPoint.getClassCanonicalName();
            //if (className.contains(".")) {
            //    className = className.substring(className.lastIndexOf(".") + 1, className.length());
            //}

            String methodName = breakPoint.getMethodName();
            
            int lineNumber = breakPoint.getLineNumber();
            int order = node.getOrder();
                
            int predOrder = -1;
            TraceNode controlDominator = node.getControlDominator();
            if (controlDominator != null) {
                predOrder = controlDominator.getOrder();
            }

            int paOrder = -1;
            TraceNode invocationParent = node.getInvocationParent();
            if (invocationParent != null) {
                paOrder = invocationParent.getOrder();
            }

            String message = "{order: " + order +
                    "},{className: " + className + 
                    "},{methodName: " + methodName +
                    "},{line: " + lineNumber +
                    "},{depth: " + Depth +
                    "},{dom: " + predOrder +
                    "},{parent: " + paOrder + "}";
            
            
            if (node.getStepInNext() != null) {
            	message = message + ",{sin: " + node.getStepInNext().getOrder() + "}" ;
            } else {
            	message = message + ",{sin: -1}";
            }
            if (node.getStepInPrevious() != null) {
            	message = message + ",{sip: " + node.getStepInPrevious().getOrder() + "}" ;
            } else {
            	message = message + ",{sip: -1}";
            }
            if (node.getStepOverNext() != null) {
            	message = message + ",{son: " + node.getStepOverNext().getOrder() + "}" ;
            } else {
            	message = message + ",{son: -1}";
            }
            if (node.getStepOverPrevious() != null) {
            	message = message + ",{sop: " + node.getStepOverPrevious().getOrder() + "}" ;
            } else {
            	message = message + ",{sop: -1}";
            }
            
            
            return message;

        }

        return null;
    }

    public void exportTreeToFile(Object inputElement, ITreeContentProvider contentProvider, String filePath) {
        try (FileWriter writer = new FileWriter(filePath, true)) {
            writer.write("Start Export......\n");
            Object[] elements = contentProvider.getElements(inputElement);
            if (elements == null) {
                return;
            }
            for (Object element : elements) {
                writeTraceElement(element, contentProvider, writer, "");
            }
            writer.write("\n\n\n");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void writeTraceElement(Object element, ITreeContentProvider contentProvider, FileWriter writer, String indent) throws IOException {
        String label = getTraceText(element, indent.length() / 2);
        writer.write(indent + label + System.lineSeparator());

        writer.write("====== Details ======" + System.lineSeparator());
        getVariables(element, writer);
        writer.write("=== Details Ends ====" + System.lineSeparator());
        
        if (contentProvider.hasChildren(element)) {
            Object[] children = contentProvider.getChildren(element);
            for (Object child : children) {
                writeTraceElement(child, contentProvider, writer, indent + "  ");
            }
        }
    }

	@Override
	public void updateData() {
		listViewer.setInput(trace);
		
		// Add: trace export
		Object inputElement = listViewer.getInput();
		ITreeContentProvider contentProvider = (ITreeContentProvider) listViewer.getContentProvider();
		String filePath = "E:/workplace/details/trace.txt";
		exportTreeToFile(inputElement, contentProvider, filePath);
		
		listViewer.refresh();
	}

	@Override
	protected void otherViewsBehavior(TraceNode buggyNode) {
		if (this.refreshProgramState) {
			
			StepPropertyView stepPropertyView = null;
			try {
				stepPropertyView = (StepPropertyView)PlatformUI.getWorkbench().
						getActiveWorkbenchWindow().getActivePage().showView(StepPropertyView.ID);
			} catch (PartInitException e) {
				e.printStackTrace();
			}
			
			TraceNodePair pair = pairList.findByBeforeNode(buggyNode);
			TraceNode correctNode = null;
			if(pair != null){
				correctNode = pair.getAfterNode();
				if (correctNode != null) {
					CorrectTraceView correctTraceView = TregressionViews.getCorrectTraceView();
					correctTraceView.jumpToNode(correctTraceView.getTrace(), correctNode.getOrder(), false);
				}
			}
			
			stepPropertyView.refresh(correctNode, buggyNode, diffMatcher, pairList);
		}

		markJavaEditor(buggyNode);
	}

	public PairList getPairList() {
		return pairList;
	}

	public void setPairList(PairList pairList) {
		this.pairList = pairList;
	}

	public DiffMatcher getDiffMatcher() {
		return diffMatcher;
	}

	public void setDiffMatcher(DiffMatcher diffMatcher) {
		this.diffMatcher = diffMatcher;
	}
}
