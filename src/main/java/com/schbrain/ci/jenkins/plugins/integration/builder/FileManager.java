package com.schbrain.ci.jenkins.plugins.integration.builder;

import hudson.model.AbstractBuild;

import java.io.File;
import java.io.IOException;

/**
 * @author liaozan
 * @since 2022/2/8
 */
@SuppressWarnings("ResultOfMethodCallIgnored")
public class FileManager {

    public static File getCacheDir(AbstractBuild<?, ?> build) {
        return build.getRootDir();
    }

    public static File getEnvVarsFile(AbstractBuild<?, ?> build) throws IOException {
        File cacheDir = getCacheDir(build);
        File envVarsFile = new File(cacheDir, "envVars");
        if (!envVarsFile.exists()) {
            envVarsFile.createNewFile();
        }
        return envVarsFile;
    }

    public static File getBuildScriptDir(AbstractBuild<?, ?> build) {
        File cacheDir = getCacheDir(build);
        File buildScriptDir = new File(cacheDir, "build-script");
        if (!buildScriptDir.exists()) {
            buildScriptDir.mkdirs();
        }
        return buildScriptDir;
    }

}
