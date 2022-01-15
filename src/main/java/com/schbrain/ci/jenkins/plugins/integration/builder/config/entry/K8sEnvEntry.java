package com.schbrain.ci.jenkins.plugins.integration.builder.config.entry;

import hudson.Extension;
import hudson.model.Descriptor;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * @author liaozan
 * @since 2022/1/16
 */
@SuppressWarnings("unused")
public class K8sEnvEntry extends Entry {

    private final String text;

    @DataBoundConstructor
    public K8sEnvEntry(String text) {
        this.text = text;
    }

    public String getText() {
        return text;
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<Entry> {

        @Override
        public String getDisplayName() {
            return "k8s环境变量配置";
        }

    }

}
