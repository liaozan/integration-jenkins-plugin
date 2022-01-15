package com.schbrain.ci.jenkins.plugins.integration.builder.config;

import com.schbrain.ci.jenkins.plugins.integration.builder.Entry;
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
@SuppressWarnings("unused")
public class DeployToK8sConfig extends AbstractDescribableImpl<DeployToK8sConfig> {

    private final List<Entry> entries;

    private final String location;

    @DataBoundConstructor
    public DeployToK8sConfig(List<Entry> entries, String location) {
        this.entries = entries != null ? new ArrayList<>(entries) : Collections.emptyList();
        this.location = location;
    }

    public List<Entry> getEntries() {
        return Collections.unmodifiableList(entries);
    }

    public String getLocation() {
        return location;
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<DeployToK8sConfig> {

    }

}
