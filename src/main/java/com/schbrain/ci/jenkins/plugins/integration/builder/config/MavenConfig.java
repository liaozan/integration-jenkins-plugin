package com.schbrain.ci.jenkins.plugins.integration.builder.config;

import hudson.Extension;
import hudson.Util;
import hudson.model.Descriptor;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * @author liaozan
 * @since 2022/1/16
 */
@SuppressWarnings("unused")
public class MavenConfig extends BuildConfig<MavenConfig> {

    private String mvnCommand;

    private String javaHome;

    public MavenConfig() {
        setDisabled(true);
    }

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

    @Extension
    public static class DescriptorImpl extends Descriptor<MavenConfig> {

    }

}
