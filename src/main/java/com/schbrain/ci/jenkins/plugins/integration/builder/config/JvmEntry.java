package com.schbrain.ci.jenkins.plugins.integration.builder.config;

import com.schbrain.ci.jenkins.plugins.integration.builder.Entry;
import hudson.Extension;
import hudson.model.Descriptor;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * @author liaozan
 * @since 2022/1/16
 */
@SuppressWarnings("unused")
public class JvmEntry extends Entry {

    private final String text;

    @DataBoundConstructor
    public JvmEntry(String text) {
        this.text = text;
    }

    public String getText() {
        return text;
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<Entry> {

        @Override
        public String getDisplayName() {
            return "Jvm配置";
        }

    }

}
