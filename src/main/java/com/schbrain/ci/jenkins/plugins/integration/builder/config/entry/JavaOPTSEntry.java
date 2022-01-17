package com.schbrain.ci.jenkins.plugins.integration.builder.config.entry;

import hudson.Extension;
import hudson.model.Descriptor;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * @author liaozan
 * @since 2022/1/16
 */
@SuppressWarnings("unused")
public class JavaOPTSEntry extends Entry {

    private final String text;

    @DataBoundConstructor
    public JavaOPTSEntry(String text) {
        this.text = text;
    }

    public String getText() {
        return text;
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<Entry> {

        @Override
        public String getDisplayName() {
            return "JAVA_OPTS";
        }

    }

}
