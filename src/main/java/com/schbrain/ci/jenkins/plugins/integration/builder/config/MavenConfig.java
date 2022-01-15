package com.schbrain.ci.jenkins.plugins.integration.builder.config;

import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * @author liaozan
 * @since 2022/1/16
 */
public class MavenConfig extends AbstractDescribableImpl<MavenConfig> {

    private final String mvnCommand;

    @DataBoundConstructor
    public MavenConfig(String mvnCommand) {
        this.mvnCommand = mvnCommand;
    }

    public String getMvnCommand() {
        return mvnCommand;
    }

    @Extension
    @SuppressWarnings("unused")
    public static class DescriptorImpl extends Descriptor<MavenConfig> {

    }

}
