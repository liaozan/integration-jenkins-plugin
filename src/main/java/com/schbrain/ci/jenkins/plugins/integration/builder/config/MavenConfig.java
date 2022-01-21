package com.schbrain.ci.jenkins.plugins.integration.builder.config;

import hudson.Extension;
import hudson.Util;
import hudson.model.Descriptor;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;

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
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<MavenConfig> {

    }

}
