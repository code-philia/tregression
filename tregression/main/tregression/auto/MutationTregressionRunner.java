package tregression.auto;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;

import microbat.model.trace.Trace;
import tregression.auto.result.RunResult;
import tregression.empiricalstudy.EmpiricalTrial;
import tregression.empiricalstudy.config.MutationConfig;
import tregression.empiricalstudy.config.ProjectConfig;
import tregression.empiricalstudy.config.TraceRecovMutationConfig;

/*
 * See Defects4jRunner
 */
public class MutationTregressionRunner extends ProjectsRunner {
	private String projectsBasePath;
	private String mutationFileBasePath;
	private String workingBasePath;

	public MutationTregressionRunner(String projectsBasePath, String mutationFileBasePath, String workingBasePath,
			String resultPath) {
		super(mutationFileBasePath, resultPath, 5);
		this.projectsBasePath = projectsBasePath;
		this.mutationFileBasePath = mutationFileBasePath;
		this.workingBasePath = workingBasePath;
	}

	@Override
	public RunResult runProject(String projectName, String bugID_str) {
		RunResult result = new RunResult();
		try {
			Integer.valueOf(bugID_str);
		} catch (NumberFormatException e) {
			return null;
		}

		result.projectName = projectName;
		result.bugID = Integer.valueOf(bugID_str);

		final ProjectConfig config = MutationConfig.getConfig(projectName, bugID_str);
		if (config == null) {
			result.errorMessage = ProjectsRunner.genMsg("Cannot generate project config");
			return result;
		}

		// 1. create bug-fix folder
		try {
			createBugFixFolder(projectName, bugID_str, config);
		} catch (IOException e) {
			e.printStackTrace();
		}

		// 2. run tregression
		Path fixFolderPath = Paths.get(this.workingBasePath, projectName, bugID_str, "fix");
		Path bugFolderPath = Paths.get(this.workingBasePath, projectName, bugID_str, "bug");
		String fixFolder = fixFolderPath.toString();
		String bugFolder = bugFolderPath.toString();

		List<EmpiricalTrial> trials = this.generateTrials(bugFolder, fixFolder, config);
		if (trials == null || trials.isEmpty()) {
			result.errorMessage = ProjectsRunner.genMsg("No trials generated");
			File dir = new File(Paths.get(this.workingBasePath,projectName).toString());
			deleteDirectory(dir);
			return result;
		}

		for (int i = 0; i < trials.size(); i++) {
			EmpiricalTrial trial = trials.get(i);
			System.out.println(trial);
			Trace trace = trial.getBuggyTrace();
			if (trace == null) {
				result.errorMessage = "[Trials Generation]: " + trial.getExceptionExplanation();
				File dir = new File(Paths.get(this.workingBasePath,projectName).toString());
				deleteDirectory(dir);
				return result;
			}
			result.traceLen = Long.valueOf(trace.size());
			result.isOmissionBug = trial.getBugType() == EmpiricalTrial.OVER_SKIP;
			result.rootCauseOrder = trial.getRootcauseNode() == null ? -1 : trial.getRootcauseNode().getOrder();
			result.traceCollectionTime = trial.getTraceCollectionTime();
			result.traceMatchingTime = trial.getTraceMatchTime();
			result.simulationTime = trial.getSimulationTime();
		}

		// 3. delete the previous bug-fix folder
		File dir = new File(Paths.get(this.workingBasePath,projectName).toString());
		deleteDirectory(dir);
		
		return result;
	}

	public void createBugFixFolder(String projectName, String bugID_str, ProjectConfig config) throws IOException {
		// 1. create fix and bug folder
		// create workingBasePath/Chart/1/fix and workingBasePath/Chart/1/bug
		Path fixFolder = Paths.get(this.workingBasePath, projectName, bugID_str, "fix");
		Path bugFolder = Paths.get(this.workingBasePath, projectName, bugID_str, "bug");
		Files.createDirectories(fixFolder);
		Files.createDirectories(bugFolder);

		// 2. copy project into fix and bug folder
		Path projectFolder = Paths.get(projectsBasePath, projectName);
		Files.walk(projectFolder).forEach(source -> {
			try {
				Path target1 = fixFolder.resolve(projectFolder.relativize(source));
				Files.copy(source, target1, StandardCopyOption.REPLACE_EXISTING);

				Path target2 = bugFolder.resolve(projectFolder.relativize(source));
				Files.copy(source, target2, StandardCopyOption.REPLACE_EXISTING);

			} catch (Exception e) {
				e.printStackTrace();
			}
		});

		// 3. substitute files with mutated ones
		Path mutationFileFolderPath = Paths.get(this.mutationFileBasePath, projectName, bugID_str);

		File mutatedJavaFile = getSingleJavaFile(mutationFileFolderPath.toString()); // MuClass.class

		String simpleClassName = mutatedJavaFile.getName(); // MuClass.java
		simpleClassName = simpleClassName.substring(0, simpleClassName.indexOf(".")); // MuClass
		String fullClassName = getClassFullName(mutatedJavaFile.getAbsolutePath(), simpleClassName);// org.jfree.chart.text.MuClass
		if(fullClassName == null) {
			System.out.println("__ERROR__ get full class name failed!");
		}
		
		String tmp = fullClassName.replace(".", File.separator); // org\jfree\chart\text\MuClass
		Path srcFilePathInProject = Paths.get(bugFolder.toString(), config.srcSourceFolder, tmp + ".java"); // D:\MutationWorkSpace\Chart\2\bug\source\org\jfree\chart\text\MuClass.java
		Path classFilePathInProject = Paths.get(bugFolder.toString(), config.bytecodeSourceFolder, tmp + ".class"); // D:\MutationWorkSpace\Chart\2\bug\build\org\jfree\chart\text\TextLine.class

		Files.copy(Paths.get(mutationFileFolderPath.toString(), simpleClassName + ".java"), srcFilePathInProject,
				StandardCopyOption.REPLACE_EXISTING);
		Files.copy(Paths.get(mutationFileFolderPath.toString(), simpleClassName + ".class"), classFilePathInProject,
				StandardCopyOption.REPLACE_EXISTING);
		Files.copy(Paths.get(mutationFileFolderPath.toString(), "failing_tests"),
				Paths.get(bugFolder.toString(), "failing_tests"), StandardCopyOption.REPLACE_EXISTING);

		System.out.println("finish creating bug-fix folder");
	}

	public static File getSingleJavaFile(String directoryPath) {
		File directory = new File(directoryPath);
		File[] allFiles = directory.listFiles();
		for (File file : allFiles) {
			if (file.getName().endsWith(".java")) {
				return file;
			}
		}
		return null;
	}

	public static String getClassFullName(String javaFilePath, String simpleClassName) {
		try (BufferedReader reader = new BufferedReader(new FileReader(javaFilePath))) {
			String line;
			while ((line = reader.readLine()) != null) {
				if (line.startsWith("package ")) {
					String packageName = line.substring(8, line.indexOf(';')).trim();
					return packageName + "." + simpleClassName;
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}

	
    public static void deleteDirectory(File directory) {
        if (directory.isDirectory()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    deleteDirectory(file);
                }
            }
        }
        directory.delete();
    }
}
