package com.schbrain.ci.jenkins.plugins.integration.builder;

import com.schbrain.ci.jenkins.plugins.integration.action.ViewBuildScriptAction;
import com.schbrain.ci.jenkins.plugins.integration.builder.config.DeployToK8sConfig;
import com.schbrain.ci.jenkins.plugins.integration.builder.config.DockerConfig;
import com.schbrain.ci.jenkins.plugins.integration.builder.config.DockerConfig.PushConfig;
import com.schbrain.ci.jenkins.plugins.integration.builder.config.MavenConfig;
import com.schbrain.ci.jenkins.plugins.integration.builder.util.FileUtils;
import com.schbrain.ci.jenkins.plugins.integration.builder.util.Logger;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.*;
import hudson.tasks.Builder;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.springframework.lang.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import static com.schbrain.ci.jenkins.plugins.integration.builder.constants.Constants.BuildConstants.*;
import static com.schbrain.ci.jenkins.plugins.integration.builder.constants.Constants.*;
import static com.schbrain.ci.jenkins.plugins.integration.builder.util.FileUtils.lookupFile;

/**
 * @author liaozan
 * @since 2022/1/14
 */
@SuppressWarnings("unused")
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
        File buildScriptDir = FileManager.getBuildScriptDir(build);
        build.addAction(new ViewBuildScriptAction(buildScriptDir));
        BuilderContext builderContext = new BuilderContext.Builder()
                .build(build)
                .launcher(launcher)
                .listener(listener)
                .logger(Logger.of(listener.getLogger()))
                .workspace(checkWorkspaceValid(build.getWorkspace()))
                .envVars(createEnvVars(build))
                .build();
        try {
            this.doPerformBuild(builderContext);
        } catch (Exception e) {
            e.printStackTrace(listener.getLogger());
            return false;
        }
        return true;
    }

    @Override
    public IntegrationDescriptor getDescriptor() {
        return (IntegrationDescriptor) super.getDescriptor();
    }

    protected void doPerformBuild(BuilderContext context) throws Exception {
        try {
            // maven build
            performMavenBuild(context);
            // read maven build-info
            readMavenBuildInfo(context);
            // download build-script
            downloadBuildScript(context);
            // docker build
            performDockerBuild(context);
            // docker push
            performDockerPush(context);
            // deploy
            deployToRemote(context);
        } finally {
            if (context.isImageHasBeenBuilt()) {
                // delete the built image if possible
                deleteImageAfterBuild(context);
                // prune image
                pruneImageCache(context);
            }
            // setup description
            setBuildDescription(context);
        }
    }

    private EnvVars createEnvVars(AbstractBuild<?, ?> build) {
        EnvVars envVars = new EnvVars();
        ParametersAction parametersAction = build.getAction(ParametersAction.class);
        List<ParameterValue> allParameters = parametersAction.getAllParameters();
        for (ParameterValue parameter : allParameters) {
            if (parameter.getValue() == null) {
                continue;
            }
            envVars.put(parameter.getName(), parameter.getValue().toString());
        }
        return envVars;
    }

    private void downloadBuildScript(BuilderContext context) throws InterruptedException, IOException {
        File buildScriptDir = FileManager.getBuildScriptDir(context.getBuild());
        File buildScripts = new File(buildScriptDir, SCRIPT_NAME);
        String archiveCommand = String.format("git archive -o %s --format=zip --remote=%s %s", SCRIPT_NAME, SCRIPT_GIT_REPO, SCRIPT_GIT_BRANCH);
        context.execute(archiveCommand);
        String moveCommand = String.format("mv %s %s", SCRIPT_NAME, buildScripts);
        context.execute(moveCommand);
        String unzipCommand = String.format("cd %s && unzip %s", buildScriptDir, SCRIPT_NAME);
        context.execute(unzipCommand);
    }

    private void setBuildDescription(BuilderContext context) throws IOException, InterruptedException {
        FilePath gitPropertiesFile = lookupFile(context, GitConstants.GIT_PROPERTIES_FILE);
        if (gitPropertiesFile == null) {
            return;
        }
        Map<String, String> gitProperties = FileUtils.filePathToMap(gitPropertiesFile);
        String author = gitProperties.get(GitConstants.GIT_COMMITTER);
        String branch = gitProperties.get(GitConstants.GIT_BRANCH);
        AbstractBuild<?, ?> build = context.getBuild();
        String description = String.format("author: %s, branch: %s", author, branch);
        build.setDescription(description);
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

    private void performMavenBuild(BuilderContext context) throws Exception {
        MavenConfig mavenConfig = getMavenConfig();
        if (mavenConfig == null) {
            context.log("maven build is not checked");
            return;
        }

        mavenConfig.build(context);
    }

    private void readMavenBuildInfo(BuilderContext context) throws IOException, InterruptedException {
        EnvVars envVars = context.getEnvVars();
        FilePath dockerBuildInfo = lookupFile(context, DockerConstants.BUILD_INFO_FILE_NAME);
        if (dockerBuildInfo == null) {
            context.log("%s file not exist, skip docker build", DockerConstants.BUILD_INFO_FILE_NAME);
            return;
        }
        // overwriting existing environment variables is not allowed
        FileUtils.filePathToMap(dockerBuildInfo).forEach(envVars::putIfAbsent);
    }

    private void performDockerBuild(BuilderContext context) throws Exception {
        DockerConfig dockerConfig = getDockerConfig();
        if (dockerConfig == null) {
            context.log("docker build is not checked");
            return;
        }

        dockerConfig.build(context);
    }

    private void performDockerPush(BuilderContext context) throws Exception {
        DockerConfig dockerConfig = getDockerConfig();
        if (dockerConfig == null) {
            context.log("docker build is not checked");
            return;
        }
        PushConfig pushConfig = dockerConfig.getPushConfig();
        if (pushConfig == null) {
            context.log("docker push is not checked");
            return;
        }

        pushConfig.build(context);
    }

    /**
     * Delete the image produced in the build
     */
    private void deleteImageAfterBuild(BuilderContext context) throws InterruptedException, IOException {
        DockerConfig dockerConfig = getDockerConfig();
        if (dockerConfig == null) {
            context.log("docker build is not checked");
            return;
        }
        if (!dockerConfig.getDeleteImageAfterBuild()) {
            context.log("delete built image is skip");
            return;
        }

        String imageName = context.getEnvVars().get(DockerConstants.IMAGE);
        if (imageName == null) {
            return;
        }

        String command = String.format("docker rmi -f %s", imageName);
        context.execute(command);
    }

    private void pruneImageCache(BuilderContext context) throws IOException, InterruptedException {
        context.execute("docker image prune -f");
    }

    /**
     * 部署镜像到远端
     */
    private void deployToRemote(BuilderContext context) throws Exception {
        DeployToK8sConfig k8sConfig = getDeployToK8sConfig();
        if (k8sConfig == null) {
            context.log("k8s deploy is not checked");
            return;
        }

        k8sConfig.build(context);
    }

    // can not move outside builder class
    @Extension
    @SuppressWarnings("unused")
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