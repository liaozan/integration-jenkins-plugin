package com.schbrain.ci.jenkins.plugins.integration.builder;

import hudson.model.AbstractBuild;

import java.io.File;
import java.io.IOException;

/**
 * @author liaozan
 * @since 2022/2/8
 */
public class FileManager {

    public static File getEnvVarsFile(AbstractBuild<?, ?> build) throws IOException {
        File rootDir = build.getRootDir();
        File envVarsFile = new File(rootDir, "envVars");
        if (!envVarsFile.exists()) {
            // noinspection ResultOfMethodCallIgnored
            envVarsFile.createNewFile();
        }
        return envVarsFile;
    }

    public static File getBuildScriptDir(AbstractBuild<?, ?> build) {
        File rootDir = build.getRootDir();
        File buildScriptDir = new File(rootDir, "build-script");
        if (!buildScriptDir.exists()) {
            // noinspection ResultOfMethodCallIgnored
            buildScriptDir.mkdirs();
        }
        return buildScriptDir;
    }

}
