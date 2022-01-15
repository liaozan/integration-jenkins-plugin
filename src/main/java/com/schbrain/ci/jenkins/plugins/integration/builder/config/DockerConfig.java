package com.schbrain.ci.jenkins.plugins.integration.builder.config;

import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * @author liaozan
 * @since 2022/1/16
 */
public class DockerConfig extends AbstractDescribableImpl<DockerConfig> {

    private final String registry;
    private final Boolean buildImage;
    private final Boolean pushImage;
    private final Boolean deleteImageAfterBuild;

    @DataBoundConstructor
    public DockerConfig(String registry, Boolean buildImage, Boolean pushImage, Boolean deleteImageAfterBuild) {
        this.registry = registry;
        this.buildImage = buildImage;
        this.pushImage = pushImage;
        this.deleteImageAfterBuild = deleteImageAfterBuild;
    }

    public String getRegistry() {
        return registry;
    }

    public Boolean getBuildImage() {
        return buildImage;
    }

    public Boolean getPushImage() {
        return pushImage;
    }

    public Boolean getDeleteImageAfterBuild() {
        return deleteImageAfterBuild;
    }

    @Extension
    @SuppressWarnings("unused")
    public static class DescriptorImpl extends Descriptor<DockerConfig> {

    }

}
