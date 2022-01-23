package com.schbrain.ci.jenkins.plugins.integration.builder.env;

import com.schbrain.ci.jenkins.plugins.integration.builder.util.FileUtils;
import hudson.EnvVars;
import hudson.Extension;
import hudson.model.AbstractBuild;
import hudson.model.BuildVariableContributor;
import hudson.model.Environment;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.Map;

/**
 * @author liaozan
 * @since 2022/1/17
 */
@Extension
@SuppressWarnings("unused")
public class BuildEnvContributor extends BuildVariableContributor {

    private static final String DELIMITER = "=";

    public static void saveEnvVarsToDisk(EnvVars envVars, String baseName) {
        FileUtils.writeUtf8Map(envVars, getEnvVarsFile(baseName), DELIMITER);
    }

    public static void clearEnvVarsFromDisk(String baseName) {
        FileUtils.writeUtf8String("", getEnvVarsFile(baseName));
    }

    private static File getEnvVarsFile(String baseName) {
        File directory = new File(System.getProperty("java.io.tmpdir"), baseName);
        if (!directory.exists()) {
            // noinspection ResultOfMethodCallIgnored
            directory.mkdirs();
        }
        File envVarsFile = new File(directory, "envVars");
        if (!envVarsFile.exists()) {
            try {
                // noinspection ResultOfMethodCallIgnored
                envVarsFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return envVarsFile;
    }

    @Override
    public void buildVariablesFor(AbstractBuild build, Map<String, String> variables) {
        // noinspection ConstantConditions
        File envFilePath = getEnvVarsFile(build.getWorkspace().getBaseName());
        EnvVars envVars = new EnvVars();
        for (String line : FileUtils.readUtf8Lines(envFilePath)) {
            String[] variablePair = line.split(DELIMITER);
            // variables may not be split by =
            if (variablePair.length != 1) {
                envVars.putIfNotNull(variablePair[0], variablePair[1]);
            }
        }
        variables.putAll(envVars);
    }

    public static class CustomEnvironment extends Environment {

        private final Map<String, String> envVars = new HashMap<>();

        private final PrintStream logger;

        public CustomEnvironment(PrintStream logger) {
            this.logger = logger;
        }

        @Override
        public void buildEnvVars(Map<String, String> env) {
            env.putAll(envVars);
            logger.println(env.get("JAVA_HOME"));
        }

        public void addEnvVars(Map<String, String> envVars) {
            this.envVars.putAll(envVars);
        }

        public void addEnvVars(String envKey, String value) {
            envVars.put(envKey, value);
        }

    }

}
