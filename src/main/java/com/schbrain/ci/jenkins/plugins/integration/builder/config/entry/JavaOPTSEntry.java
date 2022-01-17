package com.schbrain.ci.jenkins.plugins.integration.builder.config.entry;

import hudson.Extension;
import hudson.model.Descriptor;
import org.kohsuke.stapler.DataBoundConstructor;

import java.util.Map;

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

    @Override
    public void contribute(Map<String, String> options) {
        options.put("JAVA_OPTS", text);
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<Entry> {

        @Override
        public String getDisplayName() {
            return "Java参数配置";
        }

    }

}
