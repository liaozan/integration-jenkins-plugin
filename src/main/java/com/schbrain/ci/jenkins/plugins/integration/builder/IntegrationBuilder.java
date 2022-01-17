package com.schbrain.ci.jenkins.plugins.integration.builder;

import com.schbrain.ci.jenkins.plugins.integration.builder.config.DeployToK8sConfig;
import com.schbrain.ci.jenkins.plugins.integration.builder.config.DockerConfig;
import com.schbrain.ci.jenkins.plugins.integration.builder.config.MavenConfig;
import com.schbrain.ci.jenkins.plugins.integration.builder.config.entry.Entry;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Descriptor;
import hudson.tasks.Builder;
import hudson.tasks.Maven;
import hudson.tasks.Maven.MavenInstallation;
import hudson.tasks.Shell;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.springframework.lang.Nullable;

import java.io.IOException;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * @author liaozan
 * @since 2022/1/14
 */
public class IntegrationBuilder extends Builder {

    @Nullable
    private final MavenConfig mavenConfig;
    @Nullable
    private final DockerConfig dockerConfig;
    @Nullable
    private final DeployToK8sConfig deployToK8sConfig;

    private AbstractBuild<?, ?> build;
    private Launcher launcher;
    private FilePath workspace;
    private BuildListener listener;
    private PrintStream logger;

    @Nullable
    private Properties dockerBuildInfo;

    @DataBoundConstructor
    public IntegrationBuilder(@Nullable MavenConfig mavenConfig,
                              @Nullable DockerConfig dockerConfig,
                              @Nullable DeployToK8sConfig deployToK8sConfig) {
        this.mavenConfig = mavenConfig;
        this.dockerConfig = dockerConfig;
        this.deployToK8sConfig = deployToK8sConfig;
    }

    @CheckForNull
    public MavenConfig getMavenConfig() {
        return mavenConfig;
    }

    @CheckForNull
    public DockerConfig getDockerConfig() {
        return dockerConfig;
    }

    @CheckForNull
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
        if (getMavenConfig() == null) {
            logger.println("maven build is not checked");
            return;
        }
        String mavenCommand = getMavenConfig().getMvnCommand();
        if (StringUtils.isBlank(mavenCommand)) {
            logger.println("maven command is empty, skip maven build");
            return;
        }
        String mavenHome = getMavenHome();
        if (mavenHome != null) {
            mavenCommand = mavenHome + "/bin/" + mavenCommand;
        }
        execute(mavenCommand);
    }

    private void performDockerBuild() throws Exception {
        if (getDockerConfig() == null) {
            logger.println("docker build is not checked");
            return;
        }
        if (!getDockerConfig().getBuildImage()) {
            logger.println("docker build image is skipped");
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
        if (getDockerConfig() == null) {
            logger.println("docker build is not checked");
            return;
        }
        if (!getDockerConfig().getPushImage()) {
            logger.println("docker push image is skipped");
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
        if (getDockerConfig() == null) {
            logger.println("docker build is not checked");
            return;
        }
        execute("docker image prune -f");
    }

    /**
     * Delete the image produced in the build
     */
    private void deleteImageAfterBuild() throws Exception {
        if (getDockerConfig() == null) {
            logger.println("docker build is not checked");
            return;
        }
        if (!getDockerConfig().getDeleteImageAfterBuild()) {
            logger.println("delete built image is skip");
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
            logger.println("k8s deploy is not checked");
            return;
        }
        String deployFileName = k8sConfig.getDeployFileName();
        if (null == deployFileName) {
            logger.println("deploy end, because not specified file name  of k8s deploy .");
            return;
        }
        String imageName = getFullImageName();
        if (StringUtils.isEmpty(imageName)) {
            logger.println("image name is empty ,skip deploy");
            return;
        }

        String location = k8sConfig.getLocation();
        if (null == location) {
            logger.println("not specified location of k8s config ,will use default config .");
        }

        handleDeployFilePlaceholder(k8sConfig, imageName);

        String command = String.format("kubectl apply -f %s", deployFileName);
        if (StringUtils.isNotBlank(location)) {
            command = command + " --kubeconfig " + location;
        }

        logger.println(command);

        execute(command);
    }

    private void handleDeployFilePlaceholder(DeployToK8sConfig k8sConfig, String imageName) throws Exception {
        Map<String, String> param = new HashMap<>();
        for (Entry entry : k8sConfig.getEntries()) {
            entry.contribute(param);
        }

        param.put("IMAGE", imageName);

        FilePath filePath = lookupFile(k8sConfig.getDeployFileName());

        assert filePath != null;

        String data = filePath.readToString();
        for (Map.Entry<String, String> optionEntry : param.entrySet()) {
            data = data.replaceAll(optionEntry.getKey(), optionEntry.getValue());
        }
        filePath.write(data, StandardCharsets.UTF_8.name());
    }

    @CheckForNull
    private String getFullImageName() {
        if (dockerBuildInfo == null) {
            logger.println("docker build info is null");
            return null;
        }
        if (getDockerConfig() == null) {
            logger.println("docker build step is not checked");
            return null;
        }
        String registry = getDockerConfig().getRegistry();
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

    private void execute(String command) throws InterruptedException {
        Shell shell = new Shell(command);
        shell.perform(build, launcher, listener);
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
