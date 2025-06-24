package tregression.views;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.*;

import java.util.List;

public class CodeBlockDialog extends Dialog {

    private final List<String> codeLines;      // 每行代码
    private final List<int[]> blockRanges;     // 每个block的起止行号（从1开始）
    private final int baseLineNumber;          // method 在原文件中的起始行号

    /**
     * 支持自定义起始行号的构造函数
     */
    public CodeBlockDialog(Shell parentShell, List<String> codeLines, List<int[]> blockRanges, int baseLineNumber) {
        super(parentShell);
        this.codeLines = codeLines;
        this.blockRanges = blockRanges;
        this.baseLineNumber = baseLineNumber;
        setShellStyle(SWT.RESIZE | SWT.MAX | SWT.APPLICATION_MODAL | getShellStyle());
    }

    /**
     * 默认起始行为 1 的构造函数（保持兼容）
     */
    public CodeBlockDialog(Shell parentShell, List<String> codeLines, List<int[]> blockRanges) {
        this(parentShell, codeLines, blockRanges, 1);
    }

    @Override
    protected Control createDialogArea(Composite parent) {
        Composite container = (Composite) super.createDialogArea(parent);
        container.setLayout(new GridLayout(1, false));

        ScrolledComposite scroll = new ScrolledComposite(container, SWT.V_SCROLL | SWT.H_SCROLL | SWT.BORDER);
        scroll.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        scroll.setExpandHorizontal(true);
        scroll.setExpandVertical(true);

        Composite codeComposite = new Composite(scroll, SWT.NONE);
        codeComposite.setLayout(new GridLayout(1, false));
        scroll.setContent(codeComposite);

        // 为每个 block 添加一组 label（交替背景色）
        for (int i = 0; i < blockRanges.size(); i++) {
            int[] block = blockRanges.get(i);
            int start = block[0];
            int end = block[1];

            Composite blockGroup = new Composite(codeComposite, SWT.NONE);
            blockGroup.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
            blockGroup.setLayout(new GridLayout(1, false));

            Color bg = (i % 2 == 0)
                ? Display.getCurrent().getSystemColor(SWT.COLOR_WIDGET_LIGHT_SHADOW)
                : Display.getCurrent().getSystemColor(SWT.COLOR_WHITE);
            blockGroup.setBackground(bg);

            for (int lineNum = start; lineNum <= end && lineNum <= codeLines.size(); lineNum++) {
                Label codeLabel = new Label(blockGroup, SWT.NONE);
                int actualLineNumber = baseLineNumber + lineNum - 1;
                codeLabel.setText(actualLineNumber + ": " + codeLines.get(lineNum - 1));  // 显示实际行号
                codeLabel.setBackground(bg);
                codeLabel.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
            }
        }

        scroll.setMinSize(codeComposite.computeSize(SWT.DEFAULT, SWT.DEFAULT));
        return container;
    }

    @Override
    protected Point getInitialSize() {
        return new Point(1200, 1000);
    }

    @Override
    protected void configureShell(Shell shell) {
        super.configureShell(shell);
        shell.setText("Method Block View");
    }

    @Override
    protected void createButtonsForButtonBar(Composite parent) {
        createButton(parent, IDialogConstants.OK_ID, "OK", true);
    }
}
