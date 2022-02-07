package com.schbrain.ci.jenkins.plugins.integration.builder.config;

import com.schbrain.ci.jenkins.plugins.integration.builder.constants.Constants.DockerConstants;
import com.schbrain.ci.jenkins.plugins.integration.builder.util.FileUtils;
import hudson.*;
import hudson.model.Descriptor;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;

import static com.schbrain.ci.jenkins.plugins.integration.builder.util.FileUtils.lookupFile;

/**
 * @author liaozan
 * @since 2022/1/16
 */
@SuppressWarnings("unused")
public class MavenConfig extends BuildConfig<MavenConfig> {

    private final String mvnCommand;

    private final String javaHome;

    @DataBoundConstructor
    public MavenConfig(String mvnCommand, String javaHome) {
        this.mvnCommand = Util.fixNull(mvnCommand);
        this.javaHome = Util.fixNull(javaHome);
    }

    public String getMvnCommand() {
        return mvnCommand;
    }

    public String getJavaHome() {
        return javaHome;
    }

    @Override
    public void doBuild() throws Exception {
        String mavenCommand = getMvnCommand();
        if (StringUtils.isBlank(mavenCommand)) {
            logger.println("maven command is empty, skip maven build");
            return;
        }

        String javaHome = getJavaHome();
        if (StringUtils.isNotBlank(javaHome)) {
            envVars.put("JAVA_HOME", javaHome);
        }

        context.execute(mavenCommand);
        readDockerBuildInfo();
    }

    private void readDockerBuildInfo() throws IOException, InterruptedException {
        EnvVars envVars = context.getEnvVars();
        FilePath dockerBuildInfo = lookupFile(context, DockerConstants.BUILD_INFO_FILE_NAME);
        if (dockerBuildInfo == null) {
            context.log("%s file not exist, skip docker build", DockerConstants.BUILD_INFO_FILE_NAME);
            return;
        }
        // overwriting existing environment variables is not allowed
        FileUtils.filePathToMap(dockerBuildInfo).forEach(envVars::putIfAbsent);
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<MavenConfig> {

    }

}
