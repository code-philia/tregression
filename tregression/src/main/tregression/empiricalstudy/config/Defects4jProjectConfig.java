package tregression.empiricalstudy.config;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.ui.actions.RemoveFromClasspathAction;

public class Defects4jProjectConfig extends ProjectConfig{
	
	private static final String TARGET_DIR = "target";
	private static final String DEFAULT_JAVA_DIR = "src:java";
	private static final String MAIN_JAVA_DIR = "src:main:java";
	private static final String DEFAULT_MAVEN_TEST = "src:test";
	
	private Defects4jProjectConfig(String srcTestFolder, String srcSourceFolder, String bytecodeTestFolder,
			String bytecodeSourceFolder, String buildFolder, String projectName, String bugID) {
		super(srcTestFolder, srcSourceFolder, bytecodeTestFolder, bytecodeSourceFolder, buildFolder, projectName, bugID);
	}
	
	public String rootPath = ""+File.separator+"home"+File.separator+"linyun"+File.separator+"doc"+File.separator+"git_space"+File.separator+"defects4j"+File.separator+"framework"+File.separator+"bin"+File.separator+"defects4j";

	public static ProjectConfig getConfig(String projectName, String regressionID) {
		int bugID = Integer.valueOf(regressionID);
		ProjectConfig config = null;
		if(projectName.equals("Chart")) {
			config = new Defects4jProjectConfig("tests", "source", "build-tests", "build", "build", projectName, regressionID);
		}
		else if (projectName.equals("Closure")) {
			config = new Defects4jProjectConfig("test", "src", "build"+File.separator+"test", "build"+File.separator+"classes", "build", projectName, regressionID);
		}
		else if (projectName.equals("Lang")) {
			if(bugID<21){
				config = new Defects4jProjectConfig("src"+File.separator+"test"+File.separator+"java", "src"+File.separator+"main"+File.separator+"java", "target"+File.separator+"tests", "target"+File.separator+"classes", "target", projectName, regressionID);
			}
			else if(bugID<42){
				config = new Defects4jProjectConfig("src"+File.separator+"test"+File.separator+"java", "src"+File.separator+"main"+File.separator+"java", "target"+File.separator+"test-classes", "target"+File.separator+"classes", "target", projectName, regressionID);				
			}
			else{
				config = new Defects4jProjectConfig("src"+File.separator+"test", "src"+File.separator+"java", "target"+File.separator+"tests", "target"+File.separator+"classes", "target", projectName, regressionID);
			}
			
			if(bugID>=36 && bugID<=41){
				config.srcSourceFolder = "src"+File.separator+"java";
				config.srcTestFolder = "src"+File.separator+"test";
			}
		}
		else if (projectName.equals("Math")) {
			if(bugID<85){
				config = new Defects4jProjectConfig("src"+File.separator+"test"+File.separator+"java", "src"+File.separator+"main"+File.separator+"java", "target"+File.separator+"test-classes", "target"+File.separator+"classes", "target", projectName, regressionID);	
			}
			else{
				config = new Defects4jProjectConfig("src"+File.separator+"test", "src"+File.separator+"java", "target"+File.separator+"test-classes", "target"+File.separator+"classes", "target", projectName, regressionID);
			}
		}
		else if (projectName.equals("Mockito")) {
			if(bugID<12 || bugID==20 || bugID==21 || bugID==18 || bugID==19){
				config = new Defects4jProjectConfig("test", "src", "build"+File.separator+"classes"+File.separator+"test", "build"+File.separator+"classes"+File.separator+"main", "build", projectName, regressionID);				
			}
			else{
				config = new Defects4jProjectConfig("test", "src", "target"+File.separator+"test-classes", "target"+File.separator+"classes", "target", projectName, regressionID);
			}
			
			List<String> addSrcList = new ArrayList<>();
			addSrcList.add("mockmaker" + File.separator + "bytebuddy"+ File.separator + "main" + File.separator + "java");
			addSrcList.add("mockmaker" + File.separator + "bytebuddy"+ File.separator + "test" + File.separator + "java");
			addSrcList.add("mockmaker" + File.separator + "cglib"+ File.separator + "main" + File.separator + "java");
			addSrcList.add("mockmaker" + File.separator + "cglib"+ File.separator + "test" + File.separator + "java");
			config.additionalSourceFolder = addSrcList;
		}
		else if (projectName.equals("Time")) {
			if(bugID<12){
				config = new Defects4jProjectConfig("src"+File.separator+"test"+File.separator+"java", "src"+File.separator+"main"+File.separator+"java", "target"+File.separator+"test-classes", "target"+File.separator+"classes", "target", projectName, regressionID);				
			}
			else{
				config = new Defects4jProjectConfig("src"+File.separator+"test"+File.separator+"java", "src"+File.separator+"main"+File.separator+"java", "build"+File.separator+"tests", "build"+File.separator+"classes", "build", projectName, regressionID);
			}
		} else if (projectName.equals("Codec")) {
			config = new MavenProjectConfig("src"+File.separator+"test", "src"+File.separator+"java", "target"+File.separator+"tests", "target"+File.separator+"classes", "target", projectName, regressionID);
		} else if (projectName.equals("Collections")) {
			config = generateMaventProjectConfig("src:test", "src:main:java", "target", "tests", projectName, regressionID);
		} else if (projectName.equals("Compress")) {
			config = generateMaventProjectConfig(
					DEFAULT_MAVEN_TEST, MAIN_JAVA_DIR, TARGET_DIR, "test-classes", 
					projectName, regressionID);
		} else if (projectName.equals("Csv")) {
			config = generateMaventProjectConfig(
					DEFAULT_MAVEN_TEST, MAIN_JAVA_DIR, TARGET_DIR, "test-classes", 
					projectName, regressionID);
		}
		
		if (projectName.equals("simple_defects")) {
			config = generateMaventProjectConfig(DEFAULT_MAVEN_TEST, MAIN_JAVA_DIR, TARGET_DIR, "test-classes", projectName, regressionID);
		}
		
		return config;
	}
	
	private static String fromColonSeparatorString(String path) {
		String[] dirs = path.split(":");
		return String.join(File.separator, dirs);
	}
	
	private static MavenProjectConfig generateMaventProjectConfig(String testDir, String javaDir, String buildOutput, String testClasses, String projName, String projId) {
		String testSrcDirString = fromColonSeparatorString(testDir);
		String javaDirString = fromColonSeparatorString(javaDir);
		return new MavenProjectConfig(testSrcDirString, 
				javaDirString, 
				buildOutput +File.separator + testClasses, 
				buildOutput + File.separator + "classes", 
				buildOutput, projName, projId);
	}
	
}
