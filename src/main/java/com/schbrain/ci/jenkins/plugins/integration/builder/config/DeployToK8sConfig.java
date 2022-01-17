package com.schbrain.ci.jenkins.plugins.integration.builder.config;

import com.schbrain.ci.jenkins.plugins.integration.builder.config.entry.Entry;
import hudson.Extension;
import hudson.Util;
import hudson.model.Descriptor;
import org.kohsuke.stapler.DataBoundConstructor;

import java.util.List;

/**
 * @author liaozan
 * @since 2022/1/16
 */
@SuppressWarnings("unused")
public class DeployToK8sConfig extends BuildConfig<DeployToK8sConfig> {

    private List<Entry> entries;

    private String configLocation;

    private String deployFileName;

    public DeployToK8sConfig() {
        setDisabled(true);
    }

    @DataBoundConstructor
    public DeployToK8sConfig(List<Entry> entries, String configLocation, String deployFileName) {
        this.entries = Util.fixNull(entries);
        this.configLocation = Util.fixNull(configLocation);
        this.deployFileName = Util.fixNull(deployFileName);
    }

    public List<Entry> getEntries() {
        return entries;
    }

    public String getConfigLocation() {
        return configLocation;
    }

    public String getDeployFileName() {
        return deployFileName;
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<DeployToK8sConfig> {

    }

}
