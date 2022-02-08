package com.schbrain.ci.jenkins.plugins.integration.builder.env;

import com.schbrain.ci.jenkins.plugins.integration.builder.BuilderContext;
import com.schbrain.ci.jenkins.plugins.integration.builder.FileManager;
import com.schbrain.ci.jenkins.plugins.integration.builder.util.FileUtils;
import hudson.EnvVars;
import hudson.Extension;
import hudson.model.AbstractBuild;
import hudson.model.BuildVariableContributor;

import java.io.File;
import java.io.IOException;
import java.util.Map;

/**
 * @author liaozan
 * @since 2022/1/17
 */
@Extension
@SuppressWarnings("unused")
public class BuildEnvContributor extends BuildVariableContributor {

    private static final String DELIMITER = "=";

    public static void saveEnvVarsToDisk(BuilderContext context) throws IOException {
        File envVarsFile = FileManager.getEnvVarsFile(context.getBuild());
        FileUtils.writeUtf8String("", envVarsFile);
        FileUtils.writeUtf8Map(context.getEnvVars(), envVarsFile, DELIMITER);
    }

    @Override
    public void buildVariablesFor(AbstractBuild build, Map<String, String> variables) {
        File envFilePath;
        try {
            envFilePath = FileManager.getEnvVarsFile(build);
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
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

}
