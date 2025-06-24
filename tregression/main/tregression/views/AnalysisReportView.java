package tregression.views;

import com.fasterxml.jackson.databind.JsonNode;

import org.eclipse.jface.viewers.*;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.*;
import org.eclipse.ui.part.ViewPart;

import java.util.ArrayList;
import java.util.List;

import tregression.views.ChatDialog;

public class AnalysisReportView extends ViewPart {
    public static final String ID = "tregression.evalView.analysisReportView";

    private Composite root;
    private Text summaryText;
    private CheckboxTableViewer tableViewer;
    private Label statusLabel;

    static class Detail {
        String name;
        String oracle;
        String actual;
        boolean accept;

        Detail(String name, String oracle, String actual, boolean accept) {
            this.name = name;
            this.oracle = oracle;
            this.actual = actual;
            this.accept = accept;
        }
    }

    @Override
    public void createPartControl(Composite parent) {
        this.root = parent;
        parent.setLayout(new GridLayout(1, false));
    }

    public void refresh(JsonNode optionNode) {
        for (Control c : root.getChildren()) {
            c.dispose();  // 清除所有旧组件（包括 label 和 text）
        }

        // 1. Decision Summary Label
        Label summaryLabel = new Label(root, SWT.NONE);
        summaryLabel.setText("Decision Summary");

        // 2. summaryText 显示 option 内容
        summaryText = new Text(root, SWT.BORDER | SWT.READ_ONLY | SWT.WRAP | SWT.MULTI);
        summaryText.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        String summary = optionNode.has("decision summary") ? optionNode.get("decision summary").asText() : "(No summary)";
        summaryText.setText(summary);

        // 3. details list
        List<Detail> details = parseDetails(optionNode);

        if (details.isEmpty()) {
            Label noDetails = new Label(root, SWT.NONE);
            noDetails.setText("");//noDetails.setText("(No detailed inconsistency information available.)");
        } else {
            Label detailsLabel = new Label(root, SWT.NONE);
            detailsLabel.setText("Details");

            tableViewer = CheckboxTableViewer.newCheckList(root, SWT.BORDER | SWT.FULL_SELECTION | SWT.V_SCROLL);
            Table table = tableViewer.getTable();
            table.setHeaderVisible(true);
            table.setLinesVisible(true);
            GridData tableData = new GridData(SWT.FILL, SWT.TOP, true, false);
            tableData.heightHint = Math.min(details.size(), 6) * table.getItemHeight() + 30;
            table.setLayoutData(tableData);

            createColumns();

            tableViewer.setContentProvider(ArrayContentProvider.getInstance());
            tableViewer.setInput(details);
            for (Detail d : details) {
                tableViewer.setChecked(d, !d.accept);
            }

            // Footer
            int numerator = (int) details.stream().filter(d -> !d.accept).count();
            int denominator = details.size();
            int rank = optionNode.has("rank") ? optionNode.get("rank").asInt() : -1;

            String ratioStr = (denominator == 0) ? "0 / 0 = NaN"
                    : (numerator + " / " + denominator + " = " + String.format("%.2f", (double) numerator / denominator));

            statusLabel = new Label(root, SWT.NONE);
            statusLabel.setText("Inconsistency = " + ratioStr + ". Rank = " + rank);
        }

        // 4. Chat Agent 按钮
        Button chatButton = new Button(root, SWT.PUSH);
        chatButton.setText("Ask the Agent");
        chatButton.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false));
        chatButton.addListener(SWT.Selection, e -> {
            Shell shell = root.getShell();
            ChatDialog dialog = new ChatDialog(shell);
            dialog.open();
        });
        
        root.layout(true);  // 强制刷新布局
    }

    private List<Detail> parseDetails(JsonNode optionNode) {
        List<Detail> list = new ArrayList<>();
        JsonNode detailsNode = optionNode.get("details");
        if (detailsNode != null && detailsNode.isArray()) {
            for (JsonNode item : detailsNode) {
                String name = item.has("name") ? item.get("name").asText() : "(Unnamed)";
                String oracle = item.has("oracle") ? item.get("oracle").asText() : "(?)";
                String actual = item.has("actual") ? item.get("actual").asText() : "(?)";
                boolean accept = item.has("accept") && item.get("accept").asBoolean();
                list.add(new Detail(name, oracle, actual, accept));
            }
        }
        return list;
    }

    private void createColumns() {
        String[] titles = {"Name", "Oracle", "Actual"};
        int[] bounds = {300, 400, 400};

        TableViewerColumn colName = new TableViewerColumn(tableViewer, SWT.LEFT);
        colName.getColumn().setText(titles[0]);
        colName.getColumn().setWidth(bounds[0]);
        colName.setLabelProvider(new ColumnLabelProvider() {
            @Override
            public String getText(Object element) {
                return ((Detail) element).name;
            }
        });

        TableViewerColumn colOracle = new TableViewerColumn(tableViewer, SWT.LEFT);
        colOracle.getColumn().setText(titles[1]);
        colOracle.getColumn().setWidth(bounds[1]);
        colOracle.setLabelProvider(new ColumnLabelProvider() {
            @Override
            public String getText(Object element) {
                return ((Detail) element).oracle;
            }
        });

        TableViewerColumn colActual = new TableViewerColumn(tableViewer, SWT.LEFT);
        colActual.getColumn().setText(titles[2]);
        colActual.getColumn().setWidth(bounds[2]);
        colActual.setLabelProvider(new ColumnLabelProvider() {
            @Override
            public String getText(Object element) {
                return ((Detail) element).actual;
            }
        });
    }

    @Override
    public void setFocus() {
        if (summaryText != null && !summaryText.isDisposed()) {
            summaryText.setFocus();
        }
    }

}

