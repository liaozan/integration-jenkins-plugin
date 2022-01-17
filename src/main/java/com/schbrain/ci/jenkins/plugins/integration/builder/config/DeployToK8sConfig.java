package com.schbrain.ci.jenkins.plugins.integration.builder.config;

import com.schbrain.ci.jenkins.plugins.integration.builder.config.entry.Entry;
import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import org.kohsuke.stapler.DataBoundConstructor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author liaozan
 * @since 2022/1/16
 */
public class DeployToK8sConfig extends AbstractDescribableImpl<DeployToK8sConfig> {

    private final List<Entry> entries;

    private final String location;

    private final String deployFileName;

    @DataBoundConstructor
    public DeployToK8sConfig(List<Entry> entries, String location, String deployFileName) {
        this.entries = entries != null ? new ArrayList<>(entries) : Collections.emptyList();
        this.location = location;
        this.deployFileName = deployFileName;
    }

    public List<Entry> getEntries() {
        return Collections.unmodifiableList(entries);
    }

    public String getLocation() {
        return location;
    }

    public String getDeployFileName() {
        return deployFileName;
    }

    @Extension
    @SuppressWarnings("unused")
    public static class DescriptorImpl extends Descriptor<DeployToK8sConfig> {

    }

}
