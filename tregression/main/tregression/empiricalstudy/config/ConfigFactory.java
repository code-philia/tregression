package tregression.empiricalstudy.config;

public class ConfigFactory {
	public static ProjectConfig createConfig(String projectName, String regressionID, String buggyPath,
			String fixPath) {
		return createConfig(projectName, regressionID, buggyPath, fixPath, false);
	}
	
	public static ProjectConfig createConfig(String projectName, String regressionID, String buggyPath,
			String fixPath, boolean isMutatedBug) {
		if (isMutatedBug) {
			ProjectConfig config = TraceRecovMutationConfig.getConfig(projectName, regressionID);
			return config;
		} else if (isDefects4JProject(projectName)) {
			ProjectConfig config = Defects4jProjectConfig.getConfig(projectName, regressionID);
			return config;
		} else if (isMutationDatasetProject(projectName)) {
			return MutationDatasetProjectConfig.getConfig(projectName, regressionID);
		} else {
			boolean isBuggyMavenProject = MavenProjectConfig.check(buggyPath);
			boolean isFixMavenProject = MavenProjectConfig.check(fixPath);
			if (isBuggyMavenProject && isFixMavenProject) {
				return MavenProjectConfig.getConfig(projectName, regressionID);
			}
		}
		return null;
	}

	private static boolean isDefects4JProject(String projectName) {
		return projectName.equals("Chart") || projectName.equals("Cli") || projectName.equals("Closure") 
				|| projectName.equals("Codec") || projectName.equals("Collections") || projectName.equals("Compress")
				|| projectName.equals("Csv") || projectName.equals("Gson") || projectName.equals("JacksonCore") 
				|| projectName.equals("JacksonDatabind") || projectName.equals("JacksonXml") || projectName.equals("Jsoup")
				|| projectName.equals("JxPath") || projectName.equals("Lang") || projectName.equals("Math") 
				|| projectName.equals("Mockito") || projectName.equals("Time");
	}

	private static boolean isMutationDatasetProject(String projectName) {
		return "math_70".equals(projectName) || "secor".equals(projectName) || "commons-pool".equals(projectName);
	}
}
