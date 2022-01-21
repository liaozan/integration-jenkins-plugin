package com.schbrain.ci.jenkins.plugins.integration.builder;

import com.schbrain.ci.jenkins.plugins.integration.builder.config.DeployToK8sConfig;
import com.schbrain.ci.jenkins.plugins.integration.builder.config.DockerConfig;
import com.schbrain.ci.jenkins.plugins.integration.builder.config.DockerConfig.PushConfig;
import com.schbrain.ci.jenkins.plugins.integration.builder.config.MavenConfig;
import com.schbrain.ci.jenkins.plugins.integration.builder.util.Logger;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Descriptor;
import hudson.tasks.Builder;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.springframework.lang.Nullable;

import java.io.IOException;
import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static com.schbrain.ci.jenkins.plugins.integration.builder.util.FileUtils.lookupFile;

/**
 * @author liaozan
 * @since 2022/1/14
 */
public class IntegrationBuilder extends Builder {

    private final MavenConfig mavenConfig;
    private final DockerConfig dockerConfig;
    private final DeployToK8sConfig deployToK8sConfig;



    @DataBoundConstructor
    public IntegrationBuilder(@Nullable MavenConfig mavenConfig,
                              @Nullable DockerConfig dockerConfig,
                              @Nullable DeployToK8sConfig deployToK8sConfig) {
        this.mavenConfig = mavenConfig;
        this.dockerConfig = dockerConfig;
        this.deployToK8sConfig = deployToK8sConfig;
    }

    @Nullable
    public MavenConfig getMavenConfig() {
        return mavenConfig;
    }

    @Nullable
    public DockerConfig getDockerConfig() {
        return dockerConfig;
    }

    @Nullable
    public DeployToK8sConfig getDeployToK8sConfig() {
        return deployToK8sConfig;
    }

    /**
     * Builder start
     */
    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException {

        EnvVars envVars = new EnvVars();

        BuilderContext builderContext = new BuilderContext.Builder()
                .build(build)
                .launcher(launcher)
                .listener(listener)
                .logger(new Logger(listener.getLogger()))
                .workspace(checkWorkspaceValid(build.getWorkspace()))
                .envVars(envVars)
                .build();

        this.doPerformBuild(builderContext);

        return true;
    }

    @Override
    public IntegrationDescriptor getDescriptor() {
        return (IntegrationDescriptor) super.getDescriptor();
    }

    protected void doPerformBuild(BuilderContext builderContext) {
        try {
            AbstractBuild<?, ?> build = builderContext.getBuild();

            // fail fast if workspace is invalid
            checkWorkspaceValid(build.getWorkspace());
            // maven build
            performMavenBuild(builderContext);
            // read dockerInfo
            readDockerBuildInfo(builderContext);
            // docker build
            performDockerBuild(builderContext);
            // docker push
            performDockerPush(builderContext);
            // prune images
            pruneImages(builderContext);
            // delete the built image if possible
            deleteImageAfterBuild(builderContext);
            // deploy
            deployToRemote(builderContext);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Check workspace
     */
    private FilePath checkWorkspaceValid(@CheckForNull FilePath workspace) throws IOException, InterruptedException {
        if (workspace == null) {
            throw new IllegalStateException("workspace is null");
        }
        if (!workspace.exists()) {
            throw new IllegalStateException("workspace is not exist");
        }
        return workspace;
    }

    /**
     * Build project through maven
     */
    private void performMavenBuild(BuilderContext builderContext) throws Exception {
        MavenConfig mavenConfig = getMavenConfig();
        if (mavenConfig == null) {
            builderContext.getLogger().println("maven build is not checked");
            return;
        }

        mavenConfig.build(builderContext);
    }

    private void performDockerBuild(BuilderContext builderContext) throws Exception {
        DockerConfig dockerConfig = getDockerConfig();
        Logger logger = builderContext.getLogger();
        if (dockerConfig == null) {
            logger.println("docker build is not checked");
            return;
        }

        dockerConfig.build(builderContext);
    }

    private void readDockerBuildInfo(BuilderContext builderContext) throws IOException, InterruptedException {
        FilePath workspace = builderContext.getWorkspace();
        Logger logger = builderContext.getLogger();
        EnvVars envVars = builderContext.getEnvVars();
        FilePath dockerBuildInfo = lookupFile(workspace, "dockerBuildInfo", logger);
        if (dockerBuildInfo == null) {
            logger.println("dockerBuildInfo file not exist, skip docker build");
            return;
        }
        // overwriting existing environment variables is not allowed
        filePathToMap(dockerBuildInfo).forEach(envVars::putIfAbsent);
    }

    private Map<String, String> filePathToMap(FilePath lookupFile) throws IOException, InterruptedException {
        Map<String, String> result = new HashMap<>();
        Properties properties = new Properties();
        properties.load(new StringReader(lookupFile.readToString()));
        for (String propertyName : properties.stringPropertyNames()) {
            result.put(propertyName, properties.getProperty(propertyName));
        }
        return result;
    }

    private void performDockerPush(BuilderContext builderContext) throws Exception {
        Logger logger = builderContext.getLogger();
        DockerConfig dockerConfig = getDockerConfig();
        if (dockerConfig == null) {
            logger.println("docker build is not checked");
            return;
        }
        PushConfig pushConfig = dockerConfig.getPushConfig();
        if (pushConfig == null) {
            logger.println("docker push is not checked");
            return;
        }

        pushConfig.build(builderContext);
    }

    private void pruneImages(BuilderContext builderContext) throws Exception {
        Logger logger = builderContext.getLogger();
        DockerConfig dockerConfig = getDockerConfig();
        if (dockerConfig == null) {
            logger.println("docker build is not checked");
            return;
        }
        builderContext.execute("docker image prune -f");
    }

    /**
     * Delete the image produced in the build
     */
    private void deleteImageAfterBuild(BuilderContext builderContext) throws InterruptedException {
        Logger logger = builderContext.getLogger();
        EnvVars envVars = builderContext.getEnvVars();
        DockerConfig dockerConfig = getDockerConfig();

        if (dockerConfig == null) {
            logger.println("docker build is not checked");
            return;
        }
        if (!dockerConfig.getDeleteImageAfterBuild()) {
            logger.println("delete built image is skip");
            return;
        }

        logger.println("try to delete built image");
        String imageName = envVars.get("IMAGE_NAME");
        if (imageName == null) {
            return;
        }
        String command = String.format("docker rmi -f %s", imageName);

        builderContext.execute(command);
    }


    /**
     * 部署镜像到远端
     */
    private void deployToRemote(BuilderContext builderContext) throws Exception {
        DeployToK8sConfig k8sConfig = getDeployToK8sConfig();
        if (k8sConfig == null) {
            builderContext.getLogger().println("k8s deploy is not checked");
            return;
        }

        k8sConfig.build(builderContext);
    }



    // can not move outside builder class
    @Extension
    @SuppressWarnings({"unused"})
    public static class IntegrationDescriptor extends Descriptor<Builder> {

        public IntegrationDescriptor() {
            load();
        }

        @Override
        public String getDisplayName() {
            return "发布集成";
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
            save();
            return super.configure(req, formData);
        }

    }

}
