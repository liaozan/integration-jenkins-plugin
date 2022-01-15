package com.schbrain.ci.jenkins.plugins.integration.builder;

import hudson.*;
import hudson.Launcher.ProcStarter;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.tasks.Maven;
import hudson.tasks.Maven.MavenInstallation;
import hudson.util.FormValidation;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.apache.commons.io.output.TeeOutputStream;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import javax.annotation.Nullable;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;

/**
 * @author liaozan
 * @since 2022/1/14
 */
@SuppressWarnings("unused")
public class IntegrationBuilder extends Builder {

    private final String mvnCommand;
    private final Boolean buildImage;
    private final Boolean pushImage;
    private final Boolean deletePushedImage;
    private final Boolean deployToK8s;
    private final String configLocation;

    public IntegrationBuilder(String mvnCommand, Boolean buildImage, Boolean pushImage, Boolean deletePushedImage, String configLocation) {
        this.mvnCommand = Util.fixNull(mvnCommand);
        this.buildImage = Util.fixNull(buildImage, true);
        this.pushImage = Util.fixNull(pushImage, true);
        this.deletePushedImage = Util.fixNull(deletePushedImage, true);
        this.configLocation = Util.fixNull(configLocation);
        this.deployToK8s = StringUtils.isNotBlank(this.configLocation);
    }

    public String getMvnCommand() {
        return mvnCommand;
    }

    public Boolean getBuildImage() {
        return buildImage;
    }

    public Boolean getPushImage() {
        return pushImage;
    }

    public Boolean getDeletePushedImage() {
        return deletePushedImage;
    }

    public Boolean getDeployToK8s() {
        return deployToK8s;
    }

    public String getConfigLocation() {
        return configLocation;
    }

    /**
     * Builder start
     */
    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) {
        try {
            // fail fast if workspace is invalid
            checkWorkspaceValid(build.getWorkspace());
            // maven build & docker build (push)
            performMavenBuild(build, launcher, listener);
            // delete the built image if possible
            tryDeletePushedImage(build, launcher, listener);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return true;
    }

    @Override
    public IntegrationDescriptor getDescriptor() {
        return (IntegrationDescriptor) super.getDescriptor();
    }

    /**
     * 部署镜像到远端
     */
    private void deployToRemote(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) {
        if (deployToK8s) {
            System.out.println(configLocation);
        }
    }

    /**
     * Delete the image produced in the build
     */
    private void tryDeletePushedImage(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws Exception {
        if (deletePushedImage) {
            PrintStream logger = listener.getLogger();
            logger.println("try to delete pushed image");
            String imageName = lookupImageName(build, listener);
            if (imageName == null) {
                return;
            }
            ProcStarter dockerRmiProcess = launcher
                    .launch()
                    .cmdAsSingleString(String.format("docker rmi -f %s", imageName));
            executeWithLogger(logger, dockerRmiProcess);

            ProcStarter dockerImagePruneProcess = launcher
                    .launch()
                    .cmdAsSingleString("docker image prune -f");
            executeWithLogger(logger, dockerImagePruneProcess);
        }

    }

    /**
     * Check workspace
     */
    private FilePath checkWorkspaceValid(@Nullable FilePath workspace) throws Exception {
        if (workspace == null) {
            throw new IllegalStateException("workspace is null");
        }
        if (!workspace.exists()) {
            throw new IllegalStateException("workspace is not exist");
        }
        return workspace;
    }

    /**
     * lookup the special file create from dockerfile-maven-plugin
     * <p>
     * default location is rootModule/executableSubModule/target/docker/image-name
     */
    @Nullable
    private String lookupImageName(AbstractBuild<?, ?> build, BuildListener listener) throws Exception {
        String imageNameFileName = "image-name";
        FilePath workspace = getWorkspace(build);
        FilePath[] fileList = workspace.list("**/" + imageNameFileName);
        PrintStream logger = listener.getLogger();
        if (fileList.length == 0) {
            logger.println("could not found matched file: " + imageNameFileName);
            return null;
        }
        for (FilePath filePath : fileList) {
            logger.println("found matched file " + filePath);
        }
        if (fileList.length > 1) {
            logger.println("expect match one, but found " + fileList.length + " return the first one");
        }
        return fileList[0].readToString();
    }

    private FilePath getWorkspace(AbstractBuild<?, ?> build) throws Exception {
        FilePath workspace = build.getWorkspace();
        return checkWorkspaceValid(workspace);
    }

    /**
     * Build project through maven
     */
    private void performMavenBuild(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws Exception {
        EnvVars environment = contributeMavenEnvVars(build, listener);
        String mavenCommand = buildMavenCommand(build);
        ProcStarter mvnProcess = launcher
                .launch()
                .pwd(getWorkspace(build))
                .cmdAsSingleString(mavenCommand)
                .envs(environment);
        executeWithLogger(listener.getLogger(), mvnProcess);
    }

    /**
     * Collect maven EnvVars, return empty if maven installations is not set up
     */
    private EnvVars contributeMavenEnvVars(AbstractBuild<?, ?> build, BuildListener listener) throws IOException, InterruptedException {
        MavenInstallation installation;
        MavenInstallation[] installations = Jenkins.get().getDescriptorByType(Maven.DescriptorImpl.class).getInstallations();
        if (ArrayUtils.isEmpty(installations)) {
            // maven installations is not set up, return directly with empty envVar
            return new EnvVars();
        } else {
            installation = installations[0];
            EnvVars environment = build.getEnvironment(listener);
            installation.buildEnvVars(environment);
            return environment;
        }
    }

    /**
     * Build maven command to execute
     */
    private String buildMavenCommand(AbstractBuild<?, ?> build) {
        StringBuilder mavenCommand = new StringBuilder(getMvnCommand())
                .append(buildSystemEnv("dockerfile.build.skip=" + !buildImage))
                .append(buildSystemEnv("dockerfile.push.skip=" + !pushImage));
        if (buildImage) {
            mavenCommand.append(buildSystemEnv("dockerfile.tag=" + build.getId()));
        }
        return mavenCommand.toString();
    }

    private String buildSystemEnv(String option) {
        return String.format(" -D%s ", option);
    }

    private void executeWithLogger(OutputStream outputStream, ProcStarter starter) throws Exception {
        OutputStream stdout = new TeeOutputStream(outputStream, new ByteArrayOutputStream());
        OutputStream stderr = new TeeOutputStream(outputStream, new ByteArrayOutputStream());
        starter.stdout(stdout).stderr(stderr).start().join();
    }

    // can not move outside builder class
    @Extension
    @SuppressWarnings({"unused"})
    public static class IntegrationDescriptor extends BuildStepDescriptor<Builder> {

        public IntegrationDescriptor() {
            load();
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> clazz) {
            return true;
        }

        @Override
        public String getDisplayName() {
            return "发布集成";
        }

        public FormValidation doCheckMvnCommand(@QueryParameter String value) {
            if (StringUtils.isEmpty(value)) {
                return FormValidation.error("Maven命令不能为空");
            }
            return FormValidation.ok();
        }

        @Override
        public Builder newInstance(StaplerRequest req, JSONObject formData) {
            String mvnCommand = formData.optString("mvnCommand");
            boolean buildImage = formData.optBoolean("buildImage", true);
            boolean pushImage = formData.optBoolean("pushImage", true);
            boolean deletePushedImage = formData.optBoolean("deletePushedImage", true);
            JSONObject deployConfig = formData.optJSONObject("deployConfig");
            String configLocation = deployConfig.containsKey("configLocation") ? deployConfig.getString("configLocation") : null;
            return new IntegrationBuilder(mvnCommand, buildImage, pushImage, deletePushedImage, configLocation);
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
            save();
            return super.configure(req, formData);
        }

    }

}
