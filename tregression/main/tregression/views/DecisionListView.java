package tregression.views;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.viewers.*;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.*;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.part.ViewPart;

import microbat.Activator;
import microbat.model.trace.TraceNode;
import tregression.preference.TregressionPreference;

import java.io.File;
import java.io.IOException;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;

public class DecisionListView extends ViewPart {
    public static final String ID = "tregression.evalView.decisionListView";

    private CheckboxTableViewer viewer;
    private Image redIcon;
    private Image yellowIcon;
    private Image greenIcon;

    private final List<OptionEntry> currentOptions = new ArrayList<>();
    private String currentPath;  // 保存来自 DecisionView 的 path

    static class OptionEntry {
        String option;
        String decision;
        int status;
        JsonNode rawNode;

        OptionEntry(String option, String decision, int status, JsonNode rawNode) {
            this.option = option;
            this.decision = decision;
            this.status = status;
            this.rawNode = rawNode;
        }
    }

    @Override
    public void createPartControl(Composite parent) {
        Display display = parent.getDisplay();
        try {
            redIcon = new Image(display, new FileInputStream("C:\\Users\\user\\git\\tregression\\tregression\\icons\\red.png"));
            yellowIcon = new Image(display, new FileInputStream("C:\\Users\\user\\git\\tregression\\tregression\\icons\\yellow.png"));
            greenIcon = new Image(display, new FileInputStream("C:\\Users\\user\\git\\tregression\\tregression\\icons\\green.png"));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        parent.setLayout(new FillLayout());
        viewer = CheckboxTableViewer.newCheckList(parent, SWT.BORDER | SWT.FULL_SELECTION);
        Table table = viewer.getTable();
        table.setHeaderVisible(true);
        table.setLinesVisible(true);

        createColumns();
        viewer.setContentProvider(ArrayContentProvider.getInstance());

        // 点击条目时处理跳转逻辑
        viewer.addSelectionChangedListener(event -> {
            IStructuredSelection selection = (IStructuredSelection) event.getSelection();
            Object selected = selection.getFirstElement();
            if (selected instanceof OptionEntry entry && entry.rawNode != null) {
                try {
                    JsonNode item = entry.rawNode;
                    int trace = item.get("trace").asInt();
                    if (trace > 0) {
                        BuggyTraceView buggyTraceView = TregressionViews.getBuggyTraceView();
                        TraceNode node = buggyTraceView.getTrace().getExecutionList().get(trace - 1);

                        if (item.has("startline")) {
                            int startline = item.get("startline").asInt();
                            int endline = item.get("endline").asInt();
                            double inc = -1;
                            if (item.has("inconsistency") && item.get("inconsistency").isArray()) {
                                JsonNode incArr = item.get("inconsistency");
                                double numerator = incArr.get(0).asDouble();
                                double denominator = incArr.get(1).asDouble();
                                if (denominator > 0) {
                                    inc = numerator / denominator;
                                }
                            }
                            buggyTraceView.jumpToNode2(
                                buggyTraceView.getTrace(),
                                trace,
                                false,
                                node,
                                currentPath,
                                startline,
                                endline,
                                inc
                            );
                        } else {
                            buggyTraceView.otherViewsBehavior(node);
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }

                try {
                    AnalysisReportView view = (AnalysisReportView) PlatformUI.getWorkbench()
                            .getActiveWorkbenchWindow().getActivePage()
                            .showView(AnalysisReportView.ID);
                    view.refresh(entry.rawNode);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

    private void createColumns() {
        TableViewerColumn col1 = new TableViewerColumn(viewer, SWT.LEFT);
        col1.getColumn().setText("Fault Option");
        col1.getColumn().setWidth(450);
        col1.setLabelProvider(new ColumnLabelProvider() {
            @Override
            public String getText(Object element) {
                return ((OptionEntry) element).option;
            }
        });

        TableViewerColumn col2 = new TableViewerColumn(viewer, SWT.LEFT);
        col2.getColumn().setText("Decision");
        col2.getColumn().setWidth(150);
        col2.setLabelProvider(new CellLabelProvider() {
            @Override
            public void update(ViewerCell cell) {
                OptionEntry opt = (OptionEntry) cell.getElement();
                cell.setText(opt.decision);
                Image icon = switch (opt.status) {
                    case 1 -> redIcon;
                    case 0 -> yellowIcon;
                    case -1 -> greenIcon;
                    default -> null;
                };
                cell.setImage(icon);
            }
        });
    }

    // 修改后的 refresh 方法，包含 path 参数
    public static void refresh(JsonNode options, String path) {
        Display.getDefault().asyncExec(() -> {
            try {
                DecisionListView view = (DecisionListView) PlatformUI.getWorkbench()
                        .getActiveWorkbenchWindow().getActivePage()
                        .showView(DecisionListView.ID);
                view.loadFromOptions(options, path);
            } catch (PartInitException e) {
                e.printStackTrace();
            }
        });
    }

    private void loadFromOptions(JsonNode options, String path) {
        currentPath = path; // 记录 path
        currentOptions.clear();

        if (options != null && options.isArray()) {
            for (JsonNode opt : options) {
                String option = opt.has("option") ? opt.get("option").asText() : "(unnamed)";
                String decision = opt.has("decision") ? opt.get("decision").asText() : "(undecided)";
                int status = opt.has("status") ? opt.get("status").asInt() : 0;
                currentOptions.add(new OptionEntry(option, decision, status, opt));
            }
        }

        viewer.setInput(currentOptions);
        viewer.setCheckedElements(currentOptions.stream()
                .filter(e -> e.status == 1).toArray());
    }

    @Override
    public void setFocus() {
        viewer.getControl().setFocus();
    }

    @Override
    public void dispose() {
        if (redIcon != null) redIcon.dispose();
        if (yellowIcon != null) redIcon.dispose();
        if (greenIcon != null) greenIcon.dispose();
        super.dispose();
    }
}





