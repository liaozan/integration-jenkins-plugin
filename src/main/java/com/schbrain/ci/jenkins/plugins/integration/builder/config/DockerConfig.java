package com.schbrain.ci.jenkins.plugins.integration.builder.config;

import hudson.Extension;
import hudson.Util;
import hudson.model.Descriptor;
import org.kohsuke.stapler.DataBoundConstructor;
import org.springframework.lang.Nullable;

/**
 * @author liaozan
 * @since 2022/1/16
 */
@SuppressWarnings("unused")
public class DockerConfig extends BuildConfig<DockerConfig> {

    private final Boolean buildImage;
    private final PushConfig pushConfig;
    private final Boolean deleteImageAfterBuild;

    @DataBoundConstructor
    public DockerConfig(Boolean buildImage, PushConfig pushConfig, Boolean deleteImageAfterBuild) {
        this.buildImage = Util.fixNull(buildImage, false);
        this.pushConfig = pushConfig;
        this.deleteImageAfterBuild = Util.fixNull(deleteImageAfterBuild, false);
    }

    @Nullable
    public PushConfig getPushConfig() {
        return pushConfig;
    }

    public Boolean getBuildImage() {
        return buildImage;
    }

    public Boolean getDeleteImageAfterBuild() {
        return deleteImageAfterBuild;
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<DockerConfig> {

    }

    public static class PushConfig extends BuildConfig<PushConfig> {

        private final Boolean pushImage;
        private final String registry;

        @DataBoundConstructor
        public PushConfig(Boolean pushImage, String registry) {
            this.pushImage = pushImage;
            this.registry = registry;
        }

        public Boolean getPushImage() {
            return pushImage;
        }

        public String getRegistry() {
            return registry;
        }

        @Extension
        @SuppressWarnings("unused")
        public static class DescriptorImpl extends Descriptor<PushConfig> {

        }

    }

}
