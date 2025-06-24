package tregression.views;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.events.ControlAdapter;
import org.eclipse.swt.events.ControlEvent;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Iterator;

public class ChatDialog extends Dialog {

    private ScrolledComposite scrollComposite;
    private Composite messageArea;
    private Text userInput;
    private int round = 0;
    private final int MAX_ROUNDS = 20;

    private static final String MESSAGE_FILE = "E:\\workplace\\demoPilot\\Pilot\\task\\messages.txt";
    private static final String RESPONSE_FILE = "E:\\workplace\\demoPilot\\Pilot\\task\\response.json";
    private static final String PYTHON_SCRIPT = "E:\\workplace\\demoPilot\\Pilot\\task\\query.py";

    public ChatDialog(Shell parentShell) {
        super(parentShell);
        setShellStyle(SWT.RESIZE | SWT.MAX | SWT.APPLICATION_MODAL | getShellStyle());
    }

    @Override
    protected Control createDialogArea(Composite parent) {
        Composite container = (Composite) super.createDialogArea(parent);
        container.setLayout(new GridLayout(1, false));

        // 滚动区域
        scrollComposite = new ScrolledComposite(container, SWT.V_SCROLL | SWT.BORDER);
        scrollComposite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        scrollComposite.setExpandHorizontal(true);
        scrollComposite.setExpandVertical(true);

        messageArea = new Composite(scrollComposite, SWT.NONE);
        messageArea.setLayout(new GridLayout(1, false));
        scrollComposite.setContent(messageArea);
        scrollComposite.setMinSize(messageArea.computeSize(SWT.DEFAULT, SWT.DEFAULT));

        scrollComposite.addControlListener(new ControlAdapter() {
            @Override
            public void controlResized(ControlEvent e) {
                int width = scrollComposite.getClientArea().width;
                for (Control child : messageArea.getChildren()) {
                    if (child instanceof Label) {
                        GridData gd = (GridData) child.getLayoutData();
                        gd.widthHint = width - scrollBarCompensation();
                    }
                }
                Point size = messageArea.computeSize(width, SWT.DEFAULT);
                messageArea.setSize(size);
                scrollComposite.setMinSize(size);
            }
        });

        // 加载历史消息
        loadPreviousMessages();

        // 强制刷新滚动条（在 UI 初始化完成后执行）
        parent.getDisplay().asyncExec(() -> {
            int width = scrollComposite.getClientArea().width;
            Point size = messageArea.computeSize(width, SWT.DEFAULT);
            messageArea.setSize(size);
            scrollComposite.setMinSize(size);
            scrollComposite.layout(true, true);
        });

        // 输入区域
        Composite inputArea = new Composite(container, SWT.NONE);
        inputArea.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        inputArea.setLayout(new GridLayout(2, false));

        userInput = new Text(inputArea, SWT.BORDER | SWT.MULTI | SWT.WRAP | SWT.V_SCROLL);
        GridData inputData = new GridData(SWT.FILL, SWT.CENTER, true, false);
        inputData.heightHint = 60;
        userInput.setLayoutData(inputData);

        Button sendButton = new Button(inputArea, SWT.PUSH);
        sendButton.setText("Send");
        sendButton.setLayoutData(new GridData(SWT.RIGHT, SWT.BOTTOM, false, false));
        sendButton.addListener(SWT.Selection, e -> handleSend());

        return container;
    }

    private void handleSend() {
        if (round >= MAX_ROUNDS) {
            userInput.setEnabled(false);
            return;
        }

        String userMsg = userInput.getText().trim();
        if (!userMsg.isEmpty()) {
            addMessage("[User]: " + userMsg);
            userInput.setText("");
            round++;

            try (Writer writer = new OutputStreamWriter(new FileOutputStream(MESSAGE_FILE), StandardCharsets.UTF_8)) {
                writer.write(userMsg);
            } catch (IOException e) {
                addMessage("[System]: Failed to write user message.");
                return;
            }

            try {
                ProcessBuilder pb = new ProcessBuilder("python", PYTHON_SCRIPT);
                pb.redirectErrorStream(true);
                Process process = pb.start();

                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println("[Python]: " + line);
                }
                int exitCode = process.waitFor();
                if (exitCode != 0) {
                    addMessage("[System]: GPT interaction failed. Exit code " + exitCode);
                    return;
                }
            } catch (Exception e) {
                addMessage("[System]: Error during Python execution: " + e.getMessage());
                return;
            }

            try {
                File file = new File(RESPONSE_FILE);
                if (file.exists()) {
                    ObjectMapper mapper = new ObjectMapper();
                    JsonNode messages = mapper.readTree(file);
                    JsonNode lastMessage = messages.get(messages.size() - 1);
                    if (lastMessage.has("role") && "assistant".equals(lastMessage.get("role").asText())) {
                        String content = lastMessage.get("content").asText();
                        addMessage("[Agent]: " + content);
                    } else {
                        addMessage("[System]: No valid GPT response found.");
                    }
                } else {
                    addMessage("[System]: No GPT response found.");
                }
            } catch (IOException e) {
                addMessage("[System]: Failed to read response.");
            }
        }
    }

    private void loadPreviousMessages() {
        File file = new File(RESPONSE_FILE);
        if (!file.exists()) {
            addMessage("[Agent]: Hello, how can I help you?");
            return;
        }

        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode messages = mapper.readTree(file);
            Iterator<JsonNode> iter = messages.iterator();
            while (iter.hasNext()) {
                JsonNode msg = iter.next();
                if (msg.has("role") && msg.has("content")) {
                    String role = msg.get("role").asText();
                    String content = msg.get("content").asText();
                    String prefix = switch (role) {
                        case "user" -> "[User]: ";
                        case "assistant" -> "[Agent]: ";
                        case "system" -> "[System]: ";
                        default -> "[???]: ";
                    };
                    addMessage(prefix + content);
                }
            }
        } catch (IOException e) {
            addMessage("[System]: Failed to load chat history.");
        }
    }

    private void addMessage(String text) {
        Label messageLabel = new Label(messageArea, SWT.WRAP);
        messageLabel.setText(text);

        int width = scrollComposite.getClientArea().width;
        GridData data = new GridData(SWT.FILL, SWT.TOP, true, false);
        data.widthHint = width - scrollBarCompensation();
        messageLabel.setLayoutData(data);

        messageArea.layout(true, true);

        Point size = messageArea.computeSize(width, SWT.DEFAULT);
        messageArea.setSize(size);
        scrollComposite.setMinSize(size);
        scrollComposite.layout(true, true);
        scrollComposite.setOrigin(0, size.y); // 滚动到底部
    }

    private int scrollBarCompensation() {
        ScrollBar verticalBar = scrollComposite.getVerticalBar();
        return verticalBar != null && verticalBar.isVisible() ? verticalBar.getSize().x : 0;
    }

    @Override
    protected Point getInitialSize() {
        return new Point(1200, 1000);
    }

    @Override
    protected void configureShell(Shell newShell) {
        super.configureShell(newShell);
        newShell.setText("Chat with Agent");
    }

    @Override
    protected void createButtonsForButtonBar(Composite parent) {
        // 不创建默认按钮，防止 Enter 键关闭对话框
    }
}

