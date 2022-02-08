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

}
