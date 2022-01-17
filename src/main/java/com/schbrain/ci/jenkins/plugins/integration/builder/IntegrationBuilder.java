package com.schbrain.ci.jenkins.plugins.integration.builder;

import com.schbrain.ci.jenkins.plugins.integration.builder.config.DeployToK8sConfig;
import com.schbrain.ci.jenkins.plugins.integration.builder.config.DockerConfig;
import com.schbrain.ci.jenkins.plugins.integration.builder.config.MavenConfig;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Launcher.ProcStarter;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Descriptor;
import hudson.tasks.Builder;
import hudson.tasks.Maven;
import hudson.tasks.Maven.MavenInstallation;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.apache.commons.io.output.TeeOutputStream;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.springframework.lang.Nullable;

import java.io.*;
import java.util.Optional;
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

    @Nullable
    private Properties dockerBuildInfo;

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
            // read dockerInfo
            readDockerBuildInfo();
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
    private void performMavenBuild() throws Exception {
        String mavenCommand = getMavenConfig().getMvnCommand();
        if (StringUtils.isBlank(mavenCommand)) {
            logger.println("maven command is empty, skip maven build");
            return;
        }
        String mavenHome = getMavenHome();
        if (mavenHome != null) {
            mavenCommand = mavenHome + "/bin/" + mavenCommand;
        }
        ProcStarter mavenProcess = createProc().cmdAsSingleString(mavenCommand);
        execute(mavenProcess);
    }

    private void performDockerBuild() throws Exception {
        if (!getDockerConfig().getBuildImage()) {
            logger.println("docker build is skipped");
            return;
        }
        FilePath dockerfile = lookupFile("Dockerfile");
        if (dockerfile == null) {
            logger.println("Dockerfile not exist, skip docker build");
            return;
        }
        String imageName = getFullImageName();
        if (imageName == null) {
            return;
        }
        String command = String.format("docker build -t %s -f %s .", imageName, dockerfile);
        execute(command);
    }

    private void readDockerBuildInfo() throws IOException, InterruptedException {
        if (dockerBuildInfo == null) {
            FilePath lookupFile = lookupFile("dockerBuildInfo");
            if (lookupFile == null) {
                logger.println("dockerBuildInfo file not exist, skip docker build");
                return;
            }
            this.dockerBuildInfo = new Properties();
            this.dockerBuildInfo.load(new StringReader(lookupFile.readToString()));
        }
    }

    private void performDockerPush() throws Exception {
        if (!getDockerConfig().getPushImage()) {
            logger.println("docker push is skipped");
            return;
        }
        String imageName = getFullImageName();
        if (imageName == null) {
            return;
        }
        String command = String.format("docker push %s", imageName);
        execute(command);
    }

    private void pruneImages() throws Exception {
        execute("docker image prune -f");
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
        String imageName = getFullImageName();
        if (imageName == null) {
            return;
        }
        String command = String.format("docker rmi -f %s", imageName);
        execute(command);
    }

    /**
     * 部署镜像到远端
     */
    private void deployToRemote() throws Exception {
        DeployToK8sConfig k8sConfig = getDeployToK8sConfig();
        if (null == k8sConfig) {
            logger.println("not selected deploy to k8s .");
            return;
        }
        String deployFileName = k8sConfig.getDeployFileName();
        if (null == deployFileName) {
            logger.println("deploy end, because not specified file name  of k8s deploy .");
            return;
        }

        String location = k8sConfig.getLocation();
        if (null == location) {
            logger.println("not specified location of k8s config ,will use default config .");
        }

        String command = "kubectl " +
                (StringUtils.isNotBlank(location) ? " --kubeconfig ".concat(location) : "") +
                " apply -f ".concat(deployFileName);

        logger.println(command);

        execute(command);
    }

    @CheckForNull
    private String getFullImageName() {
        if (dockerBuildInfo == null) {
            logger.println("docker build info is null");
            return null;
        }
        String registry = dockerConfig.getRegistry();
        if (StringUtils.isBlank(registry)) {
            registry = dockerBuildInfo.getProperty("REGISTRY");
        }
        String appName = dockerBuildInfo.getProperty("APP_NAME");
        String version = dockerBuildInfo.getProperty("VERSION");
        return String.format("%s/%s:%s", registry, appName, version);
    }

    /**
     * lookup the special file
     */
    @CheckForNull
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
        logger.println(fileList[0].readToString());
        return fileList[0];
    }

    /**
     * getMavenHome
     */
    private String getMavenHome() throws IOException, InterruptedException {
        MavenInstallation[] installations = Jenkins.get().getDescriptorByType(Maven.DescriptorImpl.class).getInstallations();
        if (ArrayUtils.isEmpty(installations)) {
            logger.println("maven installations is empty, will execute in workspace directly");
            // maven installations is not set up, return directly with empty
        } else {
            for (MavenInstallation installation : installations) {
                installation = installation.forNode(build.getBuiltOn(), listener);
                if (installation.getExists()) {
                    return installation.getHome();
                }
            }
        }
        return null;
    }

    private void execute(String command) throws Exception {
        ProcStarter process = createProc().cmdAsSingleString(command);
        execute(process);
    }

    private void execute(ProcStarter process) throws Exception {
        OutputStream stdout = new TeeOutputStream(logger, new ByteArrayOutputStream());
        OutputStream stderr = new TeeOutputStream(logger, new ByteArrayOutputStream());
        process.stdout(stdout).stderr(stderr).start().join();
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
