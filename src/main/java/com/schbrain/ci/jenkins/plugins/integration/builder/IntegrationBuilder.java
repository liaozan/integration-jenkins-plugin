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
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import javax.annotation.Nullable;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.NoSuchElementException;

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

    @DataBoundConstructor
    public IntegrationBuilder(String mvnCommand, Boolean buildImage, Boolean pushImage, Boolean deletePushedImage) {
        this.mvnCommand = Util.fixNull(mvnCommand);
        this.buildImage = Util.fixNull(buildImage, true);
        this.pushImage = Util.fixNull(pushImage, true);
        this.deletePushedImage = Util.fixNull(deletePushedImage, true);
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

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) {
        try {
            checkWorkspace(build.getWorkspace());
            // mavenBuild(build, launcher, listener);
            clean(build, launcher, listener);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return true;
    }

    @Override
    public IntegrationDescriptor getDescriptor() {
        return (IntegrationDescriptor) super.getDescriptor();
    }

    private void clean(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws Exception {
        if (deletePushedImage) {
            PrintStream logger = listener.getLogger();
            logger.println("delete pushed image...");
            String imageName = lookupImageName(build, listener);
            if (imageName == null) {
                return;
            }
            ProcStarter dockerProcess = launcher
                    .launch()
                    .cmdAsSingleString(String.format("docker rmi -f %s", imageName));
            executeWithLogger(logger, dockerProcess);
        }

    }

    private void checkWorkspace(@Nullable FilePath workspace) throws Exception {
        if (workspace == null) {
            throw new IllegalStateException("workspace is null");
        }
        if (!workspace.exists()) {
            throw new IllegalStateException("workspace is not exist");
        }
    }

    @Nullable
    private String lookupImageName(AbstractBuild<?, ?> build, BuildListener listener) throws IOException, InterruptedException {
        String imageNameFileName = "image-name";
        FilePath workspace = getWorkspace(build);
        FilePath[] fileList = workspace.list("**/**/" + imageNameFileName);
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

    private FilePath getWorkspace(AbstractBuild<?, ?> build) {
        FilePath workspace = build.getWorkspace();
        if (workspace == null) {
            throw new IllegalStateException("workspace should not be null");
        }
        return workspace;
    }

    private void mavenBuild(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws Exception {
        MavenInstallation installation;
        MavenInstallation[] installations = Jenkins.get().getDescriptorByType(Maven.DescriptorImpl.class).getInstallations();
        if (ArrayUtils.isEmpty(installations)) {
            throw new NoSuchElementException("maven 安装不可用");
        } else {
            installation = installations[0];
        }

        EnvVars environment = build.getEnvironment(listener);
        installation.buildEnvVars(environment);

        String mavenCommand = buildMavenCommand(build, environment);
        ProcStarter mvnProcess = launcher
                .launch()
                .pwd(getWorkspace(build))
                .cmdAsSingleString(mavenCommand)
                .envs(environment);
        executeWithLogger(listener.getLogger(), mvnProcess);
    }

    private String buildMavenCommand(AbstractBuild<?, ?> build, EnvVars environment) {
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

    private void executeWithLogger(OutputStream outputStream, ProcStarter starter) throws IOException, InterruptedException {
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
        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            return true;
        }

        @Override
        public String getDisplayName() {
            return "发布集成";
        }

        public FormValidation doCheckMvnCommand(@QueryParameter String value) {
            if (StringUtils.isEmpty(value)) {
                return FormValidation.error("mvnCommand is empty");
            }
            return FormValidation.ok();
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
            return super.configure(req, formData);
        }

    }

}
