package com.schbrain.ci.jenkins.plugins.integration.builder.config;

import com.schbrain.ci.jenkins.plugins.integration.builder.constants.Constants.DockerConstants;
import com.schbrain.ci.jenkins.plugins.integration.builder.util.FileUtils;
import hudson.*;
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
            context.log("docker build image is skipped");
            return;
        }

        FilePath dockerfile = lookupFile(context, "Dockerfile");
        if (dockerfile == null) {
            context.log("Dockerfile not exist, skip docker build");
            return;
        }
        String imageName = getFullImageName(envVars, build);
        if (imageName == null) {
            return;
        }
        envVars.put(DockerConstants.IMAGE, imageName);

        String relativePath = FileUtils.toRelativePath(workspace, dockerfile);
        String command = String.format("docker build -t %s -f %s .", imageName, relativePath);
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
            registry = envVars.get(DockerConstants.REGISTRY);
        }
        if (StringUtils.isBlank(registry)) {
            throw new IllegalArgumentException("REGISTRY is null or empty");
        }

        String appName = envVars.get(DockerConstants.APP_NAME);
        String version = envVars.get(DockerConstants.VERSION);
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

            String imageName = envVars.get(DockerConstants.IMAGE);
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
