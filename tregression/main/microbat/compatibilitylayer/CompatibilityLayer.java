package microbat.compatibilitylayer;

public interface CompatibilityLayer {
    public String getWorkingSpacePath();

    public String getTargetJavaHome();

    public String getProjectName();

    public String getBugId();

    public boolean isEnabledInContextLearning();

    public static CompatibilityLayer getDefaultCompatibilityLayer() {
        return CompatibilityLayerEclipse.INSTANCE;
    }
}
