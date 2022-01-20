package com.schbrain.ci.jenkins.plugins.integration.builder;

import cn.hutool.core.util.StrUtil;
import com.schbrain.ci.jenkins.plugins.integration.builder.config.DeployToK8sConfig;
import com.schbrain.ci.jenkins.plugins.integration.builder.config.DockerConfig;
import com.schbrain.ci.jenkins.plugins.integration.builder.config.DockerConfig.PushConfig;
import com.schbrain.ci.jenkins.plugins.integration.builder.config.MavenConfig;
import com.schbrain.ci.jenkins.plugins.integration.builder.config.entry.Entry;
import com.schbrain.ci.jenkins.plugins.integration.builder.constants.Constants;
import com.schbrain.ci.jenkins.plugins.integration.builder.env.BuildEnvContributor;
import com.schbrain.ci.jenkins.plugins.integration.builder.util.FileUtils;
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
import hudson.tasks.Shell;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.springframework.lang.Nullable;
import org.springframework.util.CollectionUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
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
    private Logger logger;

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
        this.build = build;
        this.launcher = launcher;
        this.listener = listener;
        this.logger = new Logger(listener.getLogger());
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
        MavenConfig mavenConfig = getMavenConfig();
        if (mavenConfig == null) {
            logger.println("maven build is not checked");
            return;
        }
        String mavenCommand = mavenConfig.getMvnCommand();
        if (StringUtils.isBlank(mavenCommand)) {
            logger.println("maven command is empty, skip maven build");
            return;
        }
        String javaHome = mavenConfig.getJavaHome();
        if (StringUtils.isNotBlank(javaHome)) {
            envVars.put("JAVA_HOME", javaHome);
        }

        execute(mavenCommand, envVars);
    }

    private void performDockerBuild(EnvVars envVars) throws Exception {
        DockerConfig dockerConfig = getDockerConfig();
        if (dockerConfig == null) {
            logger.println("docker build is not checked");
            return;
        }
        if (!dockerConfig.getBuildImage()) {
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

    private void performDockerPush(EnvVars envVars) throws Exception {
        DockerConfig dockerConfig = getDockerConfig();
        if (dockerConfig == null) {
            logger.println("docker build is not checked");
            return;
        }
        if (dockerConfig.getPushConfig() == null) {
            logger.println("docker push is not checked");
            return;
        }
        if (!dockerConfig.getPushConfig().getPushImage()) {
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
        DockerConfig dockerConfig = getDockerConfig();
        if (dockerConfig == null) {
            logger.println("docker build is not checked");
            return;
        }
        execute("docker image prune -f", envVars);
    }

    /**
     * Delete the image produced in the build
     */
    private void deleteImageAfterBuild(EnvVars envVars) throws InterruptedException {
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
    /**
     * 部署镜像到远端
     */
    private void deployToRemote(EnvVars envVars) throws Exception {
        DeployToK8sConfig k8sConfig = getDeployToK8sConfig();
        if (k8sConfig == null) {
            logger.println("k8s deploy is not checked");
            return;
        }
        String imageName = getFullImageName(envVars);
        if (StringUtils.isEmpty(imageName)) {
            logger.println("image name is empty ,skip deploy");
            return;
        }

        String deployFileLocation = k8sConfig.getDeployFileLocation();
        //如果没有指定部署文件，将从模版生成部署文件
        if (StringUtils.isEmpty(deployFileLocation)) {
            Path templatePath = downloadDeployTemplate(envVars);

            deployFileLocation = new File(templatePath.getParent().toString(), Constants.DEPLOY_FILE_NAME).getPath();

            resolveDeployFilePlaceholder(k8sConfig.getEntries(), imageName, envVars,
                    templatePath.getFileName().toString(), deployFileLocation);


        } else {
            String data = StrUtil.format(deployFileLocation);
            logger.printf("deploy info: use point deploy file.\n%s", data);
        }


        String configLocation = k8sConfig.getConfigLocation();
        if (null == configLocation) {
            logger.println("not specified configLocation of k8s config ,will use default config .");
        }


        String command = String.format("kubectl apply -f %s", deployFileLocation);
        if (StringUtils.isNotBlank(configLocation)) {
            command = command + " --kubeconfig " + configLocation;
        }
        logger.println("will execute command:" + command);

        execute(command, envVars);
    }

    private Path downloadDeployTemplate(EnvVars envVars) throws Exception {
        FilePath existDeployTemplate = lookupFile(workspace, Constants.DEPLOY_TEMPLATE_FILE_NAME, logger);
        if (null != existDeployTemplate) {
            existDeployTemplate.delete();
        }
        String command = String.format("wget  %s", Constants.DEPLOY_TEMPLATE_URL);
        execute(command, envVars);

        return Paths.get(workspace.getRemote(), Constants.DEPLOY_TEMPLATE_FILE_NAME);
    }


    private void resolveDeployFilePlaceholder(List<Entry> entries, String imageName, EnvVars envVars,
                                              String templateFileName, String deployFileLocation) throws Exception {
        Map<String, String> param = new HashMap<>();
        param.put("IMAGE", imageName);
        if (envVars != null) {
            param.putAll(envVars);
        }

        if (!CollectionUtils.isEmpty(entries)) {
            for (Entry entry : entries) {
                entry.contribute(param);
            }
        }

        FilePath filePath = lookupFile(workspace, templateFileName, logger);
        if (filePath == null) {
            return;
        }

        String data = StrUtil.format(filePath.readToString(), param);
        logger.printf("resolved k8sDeployFile :\n%s", data);
        File localPath = new File(deployFileLocation);
        if (!localPath.exists()) {
            localPath.createNewFile();
        }
        FileOutputStream fos = new FileOutputStream(localPath);
        fos.write(data.getBytes(StandardCharsets.UTF_8));
        fos.close();
    }

    @Nullable
    private String getFullImageName(EnvVars envVars) {
        DockerConfig dockerConfig = getDockerConfig();
        if (dockerConfig == null) {
            logger.println("getFullImageName docker build step is not checked");
            return null;
        }

        String registry = null;
        PushConfig pushConfig = dockerConfig.getPushConfig();
        if (pushConfig != null) {
            registry = pushConfig.getRegistry();
        }
        if (StringUtils.isBlank(registry)) {
            registry = envVars.get("REGISTRY");
        }

        String appName = envVars.get("APP_NAME");
        String version = envVars.get("VERSION");
        int buildNumber = build.getNumber();
        return String.format("%s/%s:%s-%s", registry, appName, version, buildNumber);
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
