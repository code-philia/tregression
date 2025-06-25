package tregression.util;

import java.io.File;
import java.net.URL;
import java.security.CodeSource;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class JarVersionReader {
    @Getter
    private static String VERSION;
    @Getter
    private static String PARENT_COMMIT;
    @Getter
    private static String MICROBAT_COMMIT;
    @Getter
    private static String TREGRESSION_COMMIT;
    @Getter
    private static String PARENT_DIFF;
    @Getter
    private static String MICROBAT_DIFF;
    @Getter
    private static String TREGRESSION_DIFF;

    private static void loadVersion() {
        try {
            CodeSource codeSource = JarVersionReader.class.getProtectionDomain().getCodeSource();
            if (codeSource == null) {
                log.error("Cannot determine version: CodeSource is null");
            } else {
                URL jarUrl = codeSource.getLocation();
                File jarFile = new File(jarUrl.toURI());
                try (JarFile jar = new JarFile(jarFile)) {
                    Manifest manifest = jar.getManifest();
                    Attributes mainAttrs = manifest.getMainAttributes();
                    VERSION = mainAttrs.getValue("Git-Metadata");
                }

                String[] splits = VERSION.split("__VERSION_SPLIT");
                if (splits.length != 6) {
                    log.error("Invalid version format: {}", VERSION);
                    log.error("Splits length: {}", splits.length);
                    VERSION = "unknown";
                } else {
                    PARENT_COMMIT = splits[0];
                    MICROBAT_COMMIT = splits[1];
                    TREGRESSION_COMMIT = splits[2];
                    PARENT_DIFF = splits[3];
                    MICROBAT_DIFF = splits[4];
                    TREGRESSION_DIFF = splits[5];
                }
            }
        } catch (Exception e) {
            VERSION = "unknown";
        }
    }

    public static String buildVersionString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Parent Commit: ").append(PARENT_COMMIT).append("\n")
                .append("MicroBat Commit: ").append(MICROBAT_COMMIT).append("\n")
                .append("TRegression Commit: ").append(TREGRESSION_COMMIT).append("\n")
                .append("Parent Diff: ").append(PARENT_DIFF).append("\n")
                .append("MicroBat Diff: ").append(MICROBAT_DIFF).append("\n")
                .append("Tregression Diff: ").append(TREGRESSION_DIFF).append("\n")
                .append("Version: ").append(VERSION);
        return sb.toString();
    }

    static {
        loadVersion();
    }
}
