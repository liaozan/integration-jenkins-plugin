package com.schbrain.ci.jenkins.plugins.integration.builder;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.StrUtil;
import com.schbrain.ci.jenkins.plugins.integration.builder.config.DeployToK8sConfig;
import com.schbrain.ci.jenkins.plugins.integration.builder.config.DockerConfig;
import com.schbrain.ci.jenkins.plugins.integration.builder.config.MavenConfig;
import com.schbrain.ci.jenkins.plugins.integration.builder.config.entry.Entry;
import com.schbrain.ci.jenkins.plugins.integration.builder.env.BuildEnvContributor;
import com.schbrain.ci.jenkins.plugins.integration.builder.util.FileUtils;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.*;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Descriptor;
import hudson.tasks.Builder;
import hudson.tasks.Shell;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.springframework.lang.Nullable;
import org.springframework.util.CollectionUtils;

import java.io.IOException;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

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
            // create context envVars
            EnvVars envVars = new EnvVars();
            // fail fast if workspace is invalid
            checkWorkspaceValid(build.getWorkspace());
            // maven build
            performMavenBuild(envVars);
            // read dockerInfo
            readDockerBuildInfo(envVars);
            // docker build
            performDockerBuild(envVars);
            // docker push
            performDockerPush(envVars);
            // prune images
            pruneImages(envVars);
            // delete the built image if possible
            deleteImageAfterBuild(envVars);
            // deploy
            deployToRemote(envVars);
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
    private void performMavenBuild(EnvVars envVars) throws Exception {
        if (getMavenConfig() == null) {
            logger.println("maven build is not checked");
            return;
        }
        String mavenCommand = getMavenConfig().getMvnCommand();
        if (StringUtils.isBlank(mavenCommand)) {
            logger.println("maven command is empty, skip maven build");
            return;
        }
        String javaHome = getMavenConfig().getJavaHome();
        if (StringUtils.isNotBlank(javaHome)) {
            envVars.put("JAVA_HOME", javaHome);
        }

        execute(mavenCommand, envVars);
    }

    private void performDockerBuild(EnvVars envVars) throws Exception {
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
        String imageName = getFullImageName(envVars);
        if (imageName == null) {
            return;
        }
        String command = String.format("docker build -t %s -f %s .", imageName, FileUtils.toRelativePath(workspace, dockerfile));
        execute(command, envVars);
    }

    private void readDockerBuildInfo(EnvVars envVars) throws IOException, InterruptedException {
        FilePath dockerBuildInfo = lookupFile(workspace, "dockerBuildInfo", logger);
        if (dockerBuildInfo == null) {
            logger.println("dockerBuildInfo file not exist, skip docker build");
            return;
        }
        envVars.putAll(filePathToMap(dockerBuildInfo));
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

    private void performDockerPush(EnvVars envVars) throws Exception {
        if (getDockerConfig().isDisabled()) {
            logger.println("docker build is not checked");
            return;
        }
        if (!getDockerConfig().getPushConfig().getPushImage()) {
            logger.println("docker push image is skipped");
            return;
        }
        String imageName = getFullImageName(envVars);
        if (imageName == null) {
            return;
        }
        String command = String.format("docker push %s", imageName);
        execute(command, envVars);
    }

    private void pruneImages(EnvVars envVars) throws Exception {
        if (getDockerConfig().isDisabled()) {
            logger.println("docker build is not checked");
            return;
        }
        execute("docker image prune -f", envVars);
    }

    /**
     * Delete the image produced in the build
     */
    private void deleteImageAfterBuild(EnvVars envVars) throws InterruptedException {
        if (getDockerConfig().isDisabled()) {
            logger.println("docker build is not checked");
            return;
        }
        if (!getDockerConfig().getDeleteImageAfterBuild()) {
            logger.println("delete built image is skip");
            return;
        }
        logger.println("try to delete built image");
        String imageName = getFullImageName(envVars);
        if (imageName == null) {
            return;
        }
        String command = String.format("docker rmi -f %s", imageName);
        execute(command, envVars);
    }

    /**
     * 部署镜像到远端
     */
    private void deployToRemote(EnvVars envVars) throws Exception {
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
        String imageName = getFullImageName(envVars);
        if (StringUtils.isEmpty(imageName)) {
            logger.println("image name is empty ,skip deploy");
            return;
        }

        String configLocation = k8sConfig.getConfigLocation();
        if (null == configLocation) {
            logger.println("not specified configLocation of k8s config ,will use default config .");
        }

        resolveDeployFilePlaceholder(k8sConfig, imageName, envVars);

        String command = String.format("kubectl apply -f %s", deployFileName);
        if (StringUtils.isNotBlank(configLocation)) {
            command = command + " --kubeconfig " + configLocation;
        }

        execute(command, envVars);
    }

    private void resolveDeployFilePlaceholder(DeployToK8sConfig k8sConfig, String imageName, EnvVars envVars) throws Exception {
        Map<String, String> param = new HashMap<>();
        param.put("IMAGE", imageName);
        if (envVars != null) {
            param.putAll(envVars);
        }

        List<Entry> entries = k8sConfig.getEntries();
        if (!CollectionUtils.isEmpty(entries)) {
            for (Entry entry : entries) {
                entry.contribute(param);
            }
        }

        FilePath filePath = lookupFile(workspace, k8sConfig.getDeployFileName(), logger);
        if (filePath == null) {
            return;
        }

        String data = StrUtil.format(filePath.readToString(), param);
        logger.printf("resolved k8sDeployFile :\n%s", data);
        filePath.write(data, StandardCharsets.UTF_8.name());
    }

    @Nullable
    private String getFullImageName(EnvVars envVars) {
        if (getDockerConfig().isDisabled()) {
            logger.println("docker build step is not checked");
            return null;
        }
        String registry = getDockerConfig().getPushConfig().getRegistry();
        if (StringUtils.isBlank(registry)) {
            registry = envVars.get("REGISTRY");
        }
        String appName = envVars.get("APP_NAME");
        int buildNumber = build.getNumber();
        Date buildStartTime = build.getTime();
        return String.format("%s/%s:%d-%s", registry, appName, buildNumber, DateUtil.format(buildStartTime, "yyyyMMddHHmmss"));
    }

    private void execute(String command, EnvVars envVars) throws InterruptedException {
        BuildEnvContributor.clearEnvVarsFromDisk(workspace.getBaseName());
        BuildEnvContributor.saveEnvVarsToDisk(envVars, workspace.getBaseName());
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
