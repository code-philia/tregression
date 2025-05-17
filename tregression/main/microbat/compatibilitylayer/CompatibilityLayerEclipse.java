package microbat.compatibilitylayer;

import org.eclipse.core.resources.ResourcesPlugin;

import microbat.Activator;
import microbat.preference.MicrobatPreference;
import microbat.preference.RecovSlicingPreference;
import sav.common.core.utils.StringUtils;
import tregression.preference.TregressionPreference;

public enum CompatibilityLayerEclipse implements CompatibilityLayer {
    INSTANCE;

    @Override
    public String getWorkingSpacePath() {
        return ResourcesPlugin.getWorkspace().getRoot().getLocation().toString();
    }

    @Override
    public String getTargetJavaHome() {
        String javaHome = Activator.getDefault().getPreferenceStore().getString(MicrobatPreference.JAVA7HOME_PATH);
        if (StringUtils.isEmpty(javaHome)) {
            throw new RuntimeException("Java home is not set in the preference page.");
        }
        return javaHome;
    }

    @Override
    public String getProjectName() {

        String projectName = Activator.getDefault().getPreferenceStore()
                .getString(TregressionPreference.PROJECT_NAME);
        return projectName;
    }

    @Override
    public String getBugId() {
        String id = Activator.getDefault().getPreferenceStore().getString(TregressionPreference.BUG_ID);
        return id;
    }

    @Override
    public boolean isEnabledInContextLearning() {
        String isEnableIncontextLearningStr = Activator.getDefault().getPreferenceStore()
                .getString(RecovSlicingPreference.ENABLE_IN_CONTEXT_LEARNING);
        return isEnableIncontextLearningStr != null && isEnableIncontextLearningStr.equals("true");
    }
}
