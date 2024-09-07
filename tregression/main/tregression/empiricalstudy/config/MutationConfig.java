package tregression.empiricalstudy.config;


import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class MutationConfig extends ProjectConfig {

	private MutationConfig(String srcTestFolder, String srcSourceFolder, String bytecodeTestFolder,
			String bytecodeSourceFolder, String buildFolder, String projectName, String regressionID) {
		super(srcTestFolder, srcSourceFolder, bytecodeTestFolder, bytecodeSourceFolder, buildFolder, projectName, regressionID);
	}

	public String rootPath = ""+File.separator+"home"+File.separator+"linyun"+File.separator+"doc"+File.separator+"git_space"+File.separator+"defects4j"+File.separator+"framework"+File.separator+"bin"+File.separator+"defects4j";

	public static ProjectConfig getConfig(String projectName, String regressionID) {
		int bugID = Integer.valueOf(regressionID);
		ProjectConfig config = null;
		if(projectName.equals("Chart")) {
			config = new MutationConfig("tests", "source", "build-tests", "build", "build", projectName, regressionID);
		}
		else if (projectName.equals("closure-compiler")) {
			config = new MutationConfig("test", "src", "build"+File.separator+"test", "build"+File.separator+"classes", "build", projectName, regressionID);
		}
		else if (projectName.equals("commons-compress") || projectName.equals("Csv") || projectName.equals("commons-exec")
				|| projectName.equals("commons-validator") || projectName.equals("Jsoup")) {
			config = new MavenProjectConfig("src"+File.separator+"test"+File.separator+"java", "src"+File.separator+"main"+File.separator+"java", "target"+File.separator+"test-classes", "target"+File.separator+"classes", "target", projectName, regressionID);
		}
		else if (projectName.equals("Gson")) {
			config = new MavenProjectConfig("gson"+File.separator+"src"+File.separator+"test"+File.separator+"java", "gson"+File.separator+"src"+File.separator+"main"+File.separator+"java", "target"+File.separator+"test-classes", "target"+File.separator+"classes", "target", projectName, regressionID);
		}
		else if (projectName.equals("JxPath")) {
			config = new MavenProjectConfig("src"+File.separator+"test", "src"+File.separator+"java", "target"+File.separator+"test-classes", "target"+File.separator+"classes", "target", projectName, regressionID);
		}
		else if (projectName.equals("Lang")) {
			config = new MavenProjectConfig("src"+File.separator+"test", "src"+File.separator+"java", "target"+File.separator+"tests", "target"+File.separator+"classes", "target", projectName, regressionID);
		}
		else if (projectName.equals("Time")) {
			config = new MutationConfig("src"+File.separator+"test"+File.separator+"java", "src"+File.separator+"main"+File.separator+"java", "target"+File.separator+"test-classes", "target"+File.separator+"classes", "target", projectName, regressionID);				
		}
		else if(projectName.equals("LeetBugs")) {
			config = new MutationConfig("src"+File.separator+"test"+File.separator+"java", "src"+File.separator+"main"+File.separator+"java", "target"+File.separator+"test-classes", "target"+File.separator+"classes", "target", projectName, regressionID);	
		}
		else if (projectName.equals("Collections")) {
			config = new MavenProjectConfig("src"+File.separator+"test"+File.separator+"java", "src"+File.separator+"main"+File.separator+"java", "target"+File.separator+"test-classes", "target"+File.separator+"classes", "target", projectName, regressionID);
		}
		else if (projectName.equals("Math")) {
			config = new MutationConfig("src"+File.separator+"test"+File.separator+"java", "src"+File.separator+"main"+File.separator+"java", "target"+File.separator+"test-classes", "target"+File.separator+"classes", "target", projectName, regressionID);
		}
		else if(projectName.equals("fastjson")) {
			config = new MutationConfig("src"+File.separator+"test"+File.separator+"java", "src"+File.separator+"main"+File.separator+"java", "target"+File.separator+"test-classes", "target"+File.separator+"classes", "target", projectName, regressionID);
		}
		
		return config;
	}
	

}
