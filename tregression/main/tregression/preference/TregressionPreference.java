package tregression.preference;

import org.eclipse.core.runtime.preferences.ConfigurationScope;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.jface.preference.PreferencePage;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;

import microbat.Activator;

public class TregressionPreference extends PreferencePage implements IWorkbenchPreferencePage {

	private Text projectPathText;
	private Text projectNameText;
	private Text bugIDText;
	private Text testCaseText;
	private Text defects4jFileText;

	/* Simulator Setting */
//	protected Text inputFolderText;
//	protected Text outputFolderText;
//	protected Text mistakeProbabilityText;
//	protected Combo simulateMethodTypeCombo;
//	protected AutoSimulationMethod autoSimulationMethod = AutoSimulationMethod.DEBUG_PILOT;

	/* Recov-Slicing RQ4 Setting */
	protected Text baseFolderText;
	protected Text resultText;
	protected Text timeLimitText;

	private String defaultProjectPath;
	private String defaultProjectName;
	private String defaultBugID;
	private String defaultTestCase;
	private String defaultDefects4jFile;

	protected String defaultInputFolder;
	protected String defaultOutputPath;
	protected double defaultMistakeProbability;
	protected int defaultTimeLimit;

	protected String defaultBaseFolderPath;
	protected String defaultResultPath;

	public static final String REPO_PATH = "project_path";
	public static final String PROJECT_NAME = "project_name";
	public static final String BUG_ID = "bug_id";
	public static final String TEST_CASE = "test_case";
	public static final String DEFECTS4J_FILE = "defects4j_file";
	public static final String AUTO_FEEDBACK_METHOD = "autoFeedbackMethod";

	public static final String INPUT_FOLDER_KEY = "input_folder_key";
	public static final String OUTPUT_PATH_KEY = "output_path_key";
	public static final String MISTAKE_PROBABILITY_KEY = "mistake_probability_key";
	public static final String AUTO_SIMULATION_METHOD_KEY = "auto_simulation_method_key";
	public static final String TIME_LIMIT_KEY = "time_limit_key";

	public static final String BASE_FOLDER_KEY = "base_folder_key";
	public static final String RESULT_PATH_KEY = "result_path_key";

	public TregressionPreference() {
	}

	public TregressionPreference(String title) {
		super(title);
	}

	public TregressionPreference(String title, ImageDescriptor image) {
		super(title, image);
	}

	@Override
	public void init(IWorkbench workbench) {
		this.defaultProjectPath = Activator.getDefault().getPreferenceStore().getString(REPO_PATH);
		this.defaultProjectName = Activator.getDefault().getPreferenceStore().getString(PROJECT_NAME);
		this.defaultBugID = Activator.getDefault().getPreferenceStore().getString(BUG_ID);
		this.defaultTestCase = Activator.getDefault().getPreferenceStore().getString(TEST_CASE);
		this.defaultDefects4jFile = Activator.getDefault().getPreferenceStore().getString(DEFECTS4J_FILE);

		this.defaultInputFolder = Activator.getDefault().getPreferenceStore().getString(INPUT_FOLDER_KEY);
		this.defaultOutputPath = Activator.getDefault().getPreferenceStore().getString(OUTPUT_PATH_KEY);

		this.defaultBaseFolderPath = Activator.getDefault().getPreferenceStore().getString(BASE_FOLDER_KEY);
		this.defaultResultPath = Activator.getDefault().getPreferenceStore().getString(RESULT_PATH_KEY);

		String timeLimitStr = Activator.getDefault().getPreferenceStore().getString(TIME_LIMIT_KEY);
		if (timeLimitStr.contains(".")) {
			timeLimitStr = timeLimitStr.split("\\.")[0];
		}
		this.defaultTimeLimit = timeLimitStr == null || timeLimitStr.isEmpty() ? 60 : Integer.valueOf(timeLimitStr);
	}

	@Override
	protected Control createContents(Composite parent) {
		Composite compo = new Composite(parent, SWT.NONE);
		compo.setLayout(new GridLayout(2, false));

		Label projectPathLabel = new Label(compo, SWT.NONE);
		projectPathLabel.setText("Repository Path: ");
		projectPathText = new Text(compo, SWT.NONE);
		projectPathText.setLayoutData(new GridData(SWT.FILL, SWT.LEFT, true, false));
		projectPathText.setText(this.defaultProjectPath);

		Label projectNameLabel = new Label(compo, SWT.NONE);
		projectNameLabel.setText("Project Name: ");
		projectNameText = new Text(compo, SWT.NONE);
		projectNameText.setLayoutData(new GridData(SWT.FILL, SWT.LEFT, true, false));
		projectNameText.setText(this.defaultProjectName);

		Label bugIDLabel = new Label(compo, SWT.NONE);
		bugIDLabel.setText("Bug ID: ");
		bugIDText = new Text(compo, SWT.NONE);
		bugIDText.setLayoutData(new GridData(SWT.FILL, SWT.LEFT, true, false));
		bugIDText.setText(this.defaultBugID);

		Label testcaseLabel = new Label(compo, SWT.NONE);
		testcaseLabel.setText("Test Case: ");
		testCaseText = new Text(compo, SWT.NONE);
		testCaseText.setLayoutData(new GridData(SWT.FILL, SWT.LEFT, true, false));
		testCaseText.setText(this.defaultTestCase);

		Label defects4jFileLabel = new Label(compo, SWT.NONE);
		defects4jFileLabel.setText("Defects4j benchmark: ");
		defects4jFileText = new Text(compo, SWT.NONE);
		defects4jFileText.setLayoutData(new GridData(SWT.FILL, SWT.LEFT, true, false));
		defects4jFileText.setText(this.defaultDefects4jFile);

		this.createRQ4SettingGroup(compo);

		return compo;
	}

	@Override
	public boolean performOk() {
		IEclipsePreferences preferences = ConfigurationScope.INSTANCE.getNode("tregression.preference");
		preferences.put(REPO_PATH, this.projectPathText.getText());
		preferences.put(PROJECT_NAME, this.projectNameText.getText());
		preferences.put(BUG_ID, this.bugIDText.getText());
		preferences.put(TEST_CASE, this.testCaseText.getText());
		preferences.put(DEFECTS4J_FILE, this.defects4jFileText.getText());
		preferences.put(TIME_LIMIT_KEY, this.timeLimitText.getText());
		preferences.put(BASE_FOLDER_KEY, this.baseFolderText.getText());
		preferences.put(RESULT_PATH_KEY, this.resultText.getText());

		Activator.getDefault().getPreferenceStore().putValue(REPO_PATH, this.projectPathText.getText());
		Activator.getDefault().getPreferenceStore().putValue(PROJECT_NAME, this.projectNameText.getText());
		Activator.getDefault().getPreferenceStore().putValue(BUG_ID, this.bugIDText.getText());
		Activator.getDefault().getPreferenceStore().putValue(TEST_CASE, this.testCaseText.getText());
		Activator.getDefault().getPreferenceStore().putValue(DEFECTS4J_FILE, this.defects4jFileText.getText());
		Activator.getDefault().getPreferenceStore().putValue(TIME_LIMIT_KEY, this.timeLimitText.getText());
		Activator.getDefault().getPreferenceStore().putValue(BASE_FOLDER_KEY, this.baseFolderText.getText());
		Activator.getDefault().getPreferenceStore().putValue(RESULT_PATH_KEY, this.resultText.getText());

		return true;
	}

	protected void createRQ4SettingGroup(Composite parent) {
		Group rq4Group = new Group(parent, SWT.NONE);
		rq4Group.setText("Recov-Slicing RQ4 Setting");

		GridData rq4Data = new GridData(SWT.FILL, SWT.FILL, true, true);
		rq4Data.horizontalSpan = 2;
		rq4Group.setLayoutData(rq4Data);

		GridLayout layout = new GridLayout();
		layout.numColumns = 2;
		rq4Group.setLayout(layout);

		Label baseFolderPath = new Label(rq4Group, SWT.NONE);
		baseFolderPath.setText("Path of folder containing projects: ");
		this.baseFolderText = new Text(rq4Group, SWT.NONE);
		this.baseFolderText.setLayoutData(new GridData(SWT.FILL, SWT.LEFT, true, false));
		this.baseFolderText.setText(this.defaultBaseFolderPath);

		Label resultPath = new Label(rq4Group, SWT.NONE);
		resultPath.setText("Result Folder: ");
		this.resultText = new Text(rq4Group, SWT.NONE);
		this.resultText.setLayoutData(new GridData(SWT.FILL, SWT.LEFT, true, false));
		this.resultText.setText(this.defaultResultPath);

		Label timeLimitLabel = new Label(rq4Group, SWT.NONE);
		timeLimitLabel.setText("Time Limit (Mins): ");
		this.timeLimitText = new Text(rq4Group, SWT.NONE);
		this.timeLimitText.setLayoutData(new GridData(SWT.FILL, SWT.LEFT, true, false));
		this.timeLimitText.setText(String.valueOf(this.defaultTimeLimit));
	}

}
