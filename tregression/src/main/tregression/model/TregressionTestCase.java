package tregression.model;

import tregression.empiricalstudy.config.ProjectConfig;

public class TregressionTestCase {
	private final ProjectConfig projectConfig;
	private final String testClassName;
	private final String testMethodName;
	
	public TregressionTestCase(final ProjectConfig pc, 
			final String testClassName, 
			final String testMethod) { 
		this.projectConfig = pc;
		this.testClassName = testClassName;
		this.testMethodName = testMethod;
	}

	public ProjectConfig getProjectConfig() {
		return projectConfig;
	}

	public String getTestClassName() {
		return testClassName;
	}

	public String getTestMethodName() {
		return testMethodName;
	}
	

}
