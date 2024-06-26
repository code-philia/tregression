package tregression.empiricalstudy.config;

public class ConfigFactory {
	public static ProjectConfig createConfig(String projectName, String regressionID, String buggyPath, String fixPath) {
		if(isDefects4JProject(projectName)) {
			ProjectConfig config = Defects4jProjectConfig.getConfig(projectName, regressionID);
			return config;
		}
		else{
			boolean isBuggyMavenProject = MavenProjectConfig.check(buggyPath);
			boolean isFixMavenProject = MavenProjectConfig.check(fixPath);
			if(isBuggyMavenProject && isFixMavenProject) {
				return MavenProjectConfig.getConfig(projectName, regressionID);
			}
			
		}
		
		return null;
	}

	private static boolean isDefects4JProject(String projectName) {
		return projectName.equals("Chart") ||
				projectName.equals("Closure") ||
				projectName.equals("Lang") ||
				projectName.equals("Math") ||
				projectName.equals("Mockito") ||
				projectName.equals("Time");
	}
}
