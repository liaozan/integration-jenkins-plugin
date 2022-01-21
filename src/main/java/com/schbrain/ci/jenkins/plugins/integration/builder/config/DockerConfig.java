package com.schbrain.ci.jenkins.plugins.integration.builder.config;

import com.schbrain.ci.jenkins.plugins.integration.builder.BuilderContext;
import com.schbrain.ci.jenkins.plugins.integration.builder.util.FileUtils;
import com.schbrain.ci.jenkins.plugins.integration.builder.util.Logger;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.Descriptor;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.springframework.lang.Nullable;

import static com.schbrain.ci.jenkins.plugins.integration.builder.util.FileUtils.lookupFile;

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

    @Override
    public void doBuild() throws Exception {

        if (!getBuildImage()) {
            logger.println("docker build image is skipped");
            return;
        }


        FilePath dockerfile = lookupFile(workspace, "Dockerfile", logger);
        if (dockerfile == null) {
            logger.println("Dockerfile not exist, skip docker build");
            return;
        }
        String imageName = getFullImageName(envVars, build);
        if (imageName == null) {
            return;
        }
        envVars.put("IMAGE_NAME", imageName);
        String command = String.format("docker build -t %s -f %s .", imageName, FileUtils.toRelativePath(workspace, dockerfile));

        context.execute(command);
    }

    @Nullable
    private String getFullImageName(EnvVars envVars, AbstractBuild<?, ?> build) {


        String registry = null;
        PushConfig pushConfig = getPushConfig();
        if (pushConfig != null) {
            registry = pushConfig.getRegistry();
        }
        if (StringUtils.isBlank(registry)) {
            registry = envVars.get("REGISTRY");
        }

        String appName = envVars.get("APP_NAME");
        String version = envVars.get("VERSION");
        int buildNumber = build.getNumber();
        return String.format("%s/%s:%s-%s", registry, appName, version, buildNumber);
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

        @Override
        public void doBuild() throws Exception {
            if (!getPushImage()) {
                logger.println("docker push image is skipped");
                return;
            }

            String imageName = envVars.get("IMAGE_NAME");
            if (imageName == null) {
                return;
            }
            String command = String.format("docker push %s", imageName);

            context.execute(command);
        }

        @Extension
        @SuppressWarnings("unused")
        public static class DescriptorImpl extends Descriptor<PushConfig> {

        }

    }

}
