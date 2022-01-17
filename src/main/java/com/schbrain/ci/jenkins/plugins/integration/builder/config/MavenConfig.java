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

    public MavenConfig() {
        setDisabled(true);
    }

    @DataBoundConstructor
    public MavenConfig(String mvnCommand) {
        this.mvnCommand = Util.fixEmpty(mvnCommand);
    }

    public String getMvnCommand() {
        return mvnCommand;
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<MavenConfig> {

    }

}
