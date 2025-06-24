package tregression.handler;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.PartInitException;
import org.eclipse.swt.widgets.Display;

import tregression.views.DecisionView;

public class PilotTerminateHandler extends AbstractHandler {

    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {
        // 终止 Python 进程
        PilotStepHandler.terminatePythonProcess();

        // 在 UI 线程中清空 DecisionView
        Display.getDefault().asyncExec(() -> {
            IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
            if (window != null) {
                IWorkbenchPage page = window.getActivePage();
                try {
                    DecisionView view = (DecisionView) page.showView(DecisionView.ID);
                    view.terminate();
                    System.out.println("[Eclipse] 已清空 DecisionView");
                } catch (PartInitException e) {
                    e.printStackTrace();
                }
            }
        });

        return null;
    }
}
