package com.schbrain.ci.jenkins.plugins.integration.builder;

import cn.hutool.core.util.StrUtil;
import com.schbrain.ci.jenkins.plugins.integration.builder.config.DeployToK8sConfig;
import com.schbrain.ci.jenkins.plugins.integration.builder.config.DockerConfig;
import com.schbrain.ci.jenkins.plugins.integration.builder.config.MavenConfig;
import com.schbrain.ci.jenkins.plugins.integration.builder.config.entry.Entry;
import com.schbrain.ci.jenkins.plugins.integration.builder.env.EnvContributorRunListener.DockerBuildInfoAwareEnvironment;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.model.*;
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
import org.springframework.util.CollectionUtils;

import java.io.IOException;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
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
        this.mavenConfig = Util.fixNull(mavenConfig, new MavenConfig());
        this.dockerConfig = Util.fixNull(dockerConfig, new DockerConfig());
        this.deployToK8sConfig = Util.fixNull(deployToK8sConfig, new DeployToK8sConfig());
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

    @CheckForNull
    public Properties getDockerBuildInfo() {
        return dockerBuildInfo;
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
            refreshEnv();
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

    private void refreshEnv() {
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
        if (getDockerConfig().isDisabled()) {
            logger.println("docker build is not checked");
            return;
        }
        if (!getDockerConfig().getBuildImage()) {
            logger.println("docker build image is skipped");
            return;
        }
        FilePath dockerfile = lookupFile(workspace, "Dockerfile", logger);
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
            FilePath lookupFile = lookupFile(workspace, "dockerBuildInfo", logger);
            if (lookupFile == null) {
                logger.println("dockerBuildInfo file not exist, skip docker build");
                return;
            }
            this.dockerBuildInfo = new Properties();
            this.dockerBuildInfo.load(new StringReader(lookupFile.readToString()));
        }
    }

    private void performDockerPush() throws Exception {
        if (getDockerConfig().isDisabled()) {
            logger.println("docker build is not checked");
            return;
        }
        if (!getDockerConfig().getPushConfig().getPushImage()) {
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
        if (getDockerConfig().isDisabled()) {
            logger.println("docker build is not checked");
            return;
        }
        execute("docker image prune -f");
    }

    /**
     * Delete the image produced in the build
     */
    private void deleteImageAfterBuild() throws Exception {
        if (getDockerConfig().isDisabled()) {
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
        if (k8sConfig.isDisabled()) {
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

        String configLocation = k8sConfig.getConfigLocation();
        if (null == configLocation) {
            logger.println("not specified configLocation of k8s config ,will use default config .");
        }

        resolveDeployFilePlaceholder(k8sConfig, imageName);

        String command = String.format("kubectl apply -f %s", deployFileName);
        if (StringUtils.isNotBlank(configLocation)) {
            command = command + " --kubeconfig " + configLocation;
        }

        execute(command);
    }

    private void resolveDeployFilePlaceholder(DeployToK8sConfig k8sConfig, String imageName) throws Exception {
        Map<String, String> param = new HashMap<>();

        List<Entry> entries = k8sConfig.getEntries();
        if (!CollectionUtils.isEmpty(entries)) {
            for (Entry entry : entries) {
                entry.contribute(param);
            }
        }

        param.put("IMAGE", imageName);

        FilePath filePath = lookupFile(workspace, k8sConfig.getDeployFileName(), logger);

        if (filePath == null) {
            return;
        }

        String data = filePath.readToString();
        StrUtil.format(data, param);

        filePath.write(data, StandardCharsets.UTF_8.name());
    }

    @CheckForNull
    private String getFullImageName() {
        if (getDockerBuildInfo() == null) {
            logger.println("docker build info is null");
            return null;
        }
        if (getDockerConfig().isDisabled()) {
            logger.println("docker build step is not checked");
            return null;
        }
        String registry = getDockerConfig().getPushConfig().getRegistry();
        if (StringUtils.isBlank(registry)) {
            registry = getDockerBuildInfo().getProperty("REGISTRY");
        }
        String appName = getDockerBuildInfo().getProperty("APP_NAME");
        String version = getDockerBuildInfo().getProperty("VERSION");
        return String.format("%s/%s:%s", registry, appName, version);
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
        EnvironmentList environments = build.getEnvironments();
        for (Environment environment : environments) {
            if (environment instanceof DockerBuildInfoAwareEnvironment) {
                ((DockerBuildInfoAwareEnvironment) environment).setDockerInfo(getDockerBuildInfo());
            }
        }
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
