package tregression.views;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;

import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.viewers.*;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.part.ViewPart;

import microbat.Activator;
import tregression.preference.TregressionPreference;
import tregression.editors.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.List;

public class DecisionView extends ViewPart {
    public static final String ID = "tregression.evalView.decisionView";

    private TreeViewer viewer;
    private final String basePath = "E:\\workplace\\demoPilot\\Pilot\\debugging";
    private final String fileName = "data_demo.json";

    private String projectName;
    private String bugId;

    static class PlanEntry {
        String focus;
        String displayFocus;  // 显示用（带序号）
        String phase;
        JsonNode options;
        List<PlanEntry> children = new ArrayList<>();
        String path;

        PlanEntry(String focus, String phase, JsonNode options) {
            this(focus, phase, options, "");
        }

        PlanEntry(String focus, String phase, JsonNode options, String path) {
            this.focus = focus;
            this.displayFocus = focus;
            this.phase = phase;
            this.options = options;
            this.path = path;
        }

        void addChild(PlanEntry child) {
            children.add(child);
        }
    }

    @Override
    public void createPartControl(Composite parent) {
        viewer = new TreeViewer(parent, SWT.BORDER | SWT.FULL_SELECTION | SWT.H_SCROLL | SWT.V_SCROLL);
        Tree tree = viewer.getTree();
        tree.setHeaderVisible(true);
        tree.setLinesVisible(true);

        createColumns();

        viewer.setContentProvider(new ITreeContentProvider() {
            public Object[] getElements(Object inputElement) {
                return ((List<?>) inputElement).toArray();
            }

            public Object[] getChildren(Object parentElement) {
                return ((PlanEntry) parentElement).children.toArray();
            }

            public Object getParent(Object element) {
                return null;
            }

            public boolean hasChildren(Object element) {
                return !((PlanEntry) element).children.isEmpty();
            }
        });

        viewer.addSelectionChangedListener(event -> {
            IStructuredSelection selection = (IStructuredSelection) viewer.getSelection();
            Object selected = selection.getFirstElement();
            if (selected instanceof PlanEntry entry && entry.options != null) {
                DecisionListView.refresh(entry.options, entry.path);
            }
        });

        // 初始化后自动加载数据
        // refresh();
    }

    private void createColumns() {
        TreeViewerColumn focusCol = new TreeViewerColumn(viewer, SWT.LEFT);
        focusCol.getColumn().setText("Focus");
        focusCol.getColumn().setWidth(450);
        focusCol.setLabelProvider(new ColumnLabelProvider() {
            @Override
            public String getText(Object element) {
                return ((PlanEntry) element).displayFocus;
            }
        });

        TreeViewerColumn phaseCol = new TreeViewerColumn(viewer, SWT.LEFT);
        phaseCol.getColumn().setText("Phase");
        phaseCol.getColumn().setWidth(150);
        phaseCol.setLabelProvider(new ColumnLabelProvider() {
            @Override
            public String getText(Object element) {
                return ((PlanEntry) element).phase;
            }
        });
    }

    public void refresh() {
        IPreferenceStore store = Activator.getDefault().getPreferenceStore();
        this.projectName = store.getString(TregressionPreference.PROJECT_NAME);
        this.bugId = store.getString(TregressionPreference.BUG_ID);

        List<PlanEntry> data = loadJsonData();

        // 添加序号标记
        int methodIndex = 1;
        for (PlanEntry methodEntry : data) {
            methodEntry.displayFocus = methodIndex + ". " + methodEntry.focus;
            int childIndex = 1;
            for (PlanEntry child : methodEntry.children) {
                child.displayFocus = methodIndex + "-" + childIndex + ". " + child.focus;
                childIndex++;
            }
            methodIndex++;
        }

        viewer.setInput(data);
        viewer.refresh();
        viewer.expandAll();
    }

    private List<PlanEntry> loadJsonData() {
        List<PlanEntry> rootEntries = new ArrayList<>();
        try {
            Path path = Paths.get(basePath, projectName + "_" + bugId, fileName);
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(new File(path.toString()));

            for (JsonNode methodNode : root) {
                String method = methodNode.get("method").asText();
                String filePath = methodNode.has("path") ? methodNode.get("path").asText() : "";
                PlanEntry methodEntry = new PlanEntry(method, "", null, filePath);

                JsonNode planArray = methodNode.get("plan");
                if (planArray != null && planArray.isArray()) {
                    for (JsonNode plan : planArray) {
                        String focus = plan.get("focus").asText("");
                        String phase = plan.get("phase").asText("");
                        JsonNode options = plan.get("options");
                        PlanEntry planEntry = new PlanEntry(focus, phase, options, filePath);
                        methodEntry.addChild(planEntry);
                    }
                }

                rootEntries.add(methodEntry);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return rootEntries;
    }

    @Override
    public void setFocus() {
        if (viewer != null && !viewer.getTree().isDisposed()) {
            viewer.getControl().setFocus();
        }
    }
}


