package com.schbrain.ci.jenkins.plugins.integration.builder;

import com.schbrain.ci.jenkins.plugins.integration.builder.config.DeployToK8sConfig;
import com.schbrain.ci.jenkins.plugins.integration.builder.config.DockerConfig;
import com.schbrain.ci.jenkins.plugins.integration.builder.config.MavenConfig;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Launcher.ProcStarter;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.tasks.Maven;
import hudson.tasks.Maven.MavenInstallation;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.apache.commons.io.output.TeeOutputStream;
import org.apache.commons.lang.ArrayUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

import javax.annotation.Nullable;
import java.io.*;
import java.util.Properties;

/**
 * @author liaozan
 * @since 2022/1/14
 */
public class IntegrationBuilder extends Builder {

    private final MavenConfig mavenConfig;
    private final DockerConfig dockerConfig;
    private final DeployToK8sConfig deployToK8sConfig;

    private AbstractBuild<?, ?> build;
    private Launcher launcher;
    private FilePath workspace;
    private BuildListener listener;
    private PrintStream logger;

    private Properties projectInfo;

    @DataBoundConstructor
    public IntegrationBuilder(MavenConfig mavenConfig, DockerConfig dockerConfig, DeployToK8sConfig deployToK8sConfig) {
        this.mavenConfig = mavenConfig;
        this.dockerConfig = dockerConfig;
        this.deployToK8sConfig = deployToK8sConfig;
    }

    public MavenConfig getMavenConfig() {
        return mavenConfig;
    }

    public DockerConfig getDockerConfig() {
        return dockerConfig;
    }

    public DeployToK8sConfig getDeployToK8sConfig() {
        return deployToK8sConfig;
    }

    /**
     * Builder start
     */
    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException {
        this.build = build;
        this.launcher = launcher;
        this.listener = listener;
        this.logger = listener.getLogger();
        this.workspace = checkWorkspaceValid(build.getWorkspace());
        this.doPerformBuild(build);
        return true;
    }

    @Override
    public IntegrationDescriptor getDescriptor() {
        return (IntegrationDescriptor) super.getDescriptor();
    }

    protected void doPerformBuild(AbstractBuild<?, ?> build) {
        try {
            // fail fast if workspace is invalid
            checkWorkspaceValid(build.getWorkspace());
            // maven build
            performMavenBuild();
            // docker build
            performDockerBuild();
            // docker push
            performDockerPush();
            // prune images
            pruneImages();
            // delete the built image if possible
            deleteImageAfterBuild();
            // deploy
            deployToRemote();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Check workspace
     */
    private FilePath checkWorkspaceValid(@Nullable FilePath workspace) throws IOException, InterruptedException {
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
    private void performMavenBuild() throws Exception {
        EnvVars envVars = contributeMavenEnvVars();
        String mavenCommand = getMavenConfig().getMvnCommand();
        ProcStarter mavenProcess = createProc().cmdAsSingleString(mavenCommand).envs(envVars);
        execute(mavenProcess);
    }

    private void performDockerBuild() throws Exception {
        FilePath dockerfile = lookupFile("Dockerfile");
        if (dockerfile == null) {
            logger.println("Dockerfile not exist, skip docker build");
            return;
        }
        readProjectInfo();
        String command = String.format("docker build -t %s -f %s .", getImageName(), dockerfile);
        ProcStarter dockerBuildProc = createProc().cmdAsSingleString(command);
        execute(dockerBuildProc);
    }

    private void readProjectInfo() throws IOException, InterruptedException {
        if (projectInfo == null) {
            FilePath variables = lookupFile("variables");
            if (variables == null) {
                logger.println("variables file not exist, skip docker build");
                return;
            }
            projectInfo = new Properties();
            projectInfo.load(new StringReader(variables.readToString()));
        }
    }

    private void performDockerPush() {

    }

    private void pruneImages() throws Exception {
        ProcStarter dockerImagePruneProcess = createProc().cmdAsSingleString("docker image prune -f");
        execute(dockerImagePruneProcess);
    }

    private ProcStarter createProc() {
        ProcStarter launch = launcher.launch();
        if (launch.pwd() == null) {
            launch.pwd(workspace);
        }
        return launch;
    }

    /**
     * Delete the image produced in the build
     */
    private void deleteImageAfterBuild() throws Exception {
        if (!getDockerConfig().getDeleteImageAfterBuild()) {
            return;
        }
        logger.println("try to delete built image");
        String imageName = getImageName();
        if (imageName == null) {
            return;
        }
        String command = String.format("docker rmi -f %s", imageName);
        ProcStarter dockerRmiProcess = createProc().cmdAsSingleString(command);
        execute(dockerRmiProcess);
    }

    /**
     * 部署镜像到远端
     */
    private void deployToRemote() {
        // TODO: 2022/1/16
    }

    private String getImageName() {
        return String.format("%s/%s", dockerConfig.getRegistry(), projectInfo.getProperty("IMAGE_NAME"));
    }

    /**
     * lookup the special file
     */
    @Nullable
    private FilePath lookupFile(String fileName) throws IOException, InterruptedException {
        FilePath[] fileList = workspace.list("**/" + fileName);
        if (fileList.length == 0) {
            logger.println("could not found matched file: " + fileName);
            return null;
        }
        for (FilePath filePath : fileList) {
            logger.println("found matched file " + filePath);
        }
        if (fileList.length > 1) {
            logger.println("expect match one, but found " + fileList.length + " return the first one");
        }
        return fileList[0];
    }

    /**
     * Collect maven EnvVars, return empty if maven installations is not set up
     */
    private EnvVars contributeMavenEnvVars() throws IOException, InterruptedException {
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

    private void execute(ProcStarter process) throws Exception {
        OutputStream stdout = new TeeOutputStream(logger, new ByteArrayOutputStream());
        OutputStream stderr = new TeeOutputStream(logger, new ByteArrayOutputStream());
        process.stdout(stdout).stderr(stderr).start().join();
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

        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
            save();
            return super.configure(req, formData);
        }

    }

}
