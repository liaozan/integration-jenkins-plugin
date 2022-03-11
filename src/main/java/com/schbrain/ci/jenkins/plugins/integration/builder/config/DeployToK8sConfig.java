package com.schbrain.ci.jenkins.plugins.integration.builder.config;

import com.schbrain.ci.jenkins.plugins.integration.builder.config.deploy.DeployStyleRadio;
import com.schbrain.ci.jenkins.plugins.integration.builder.config.deploy.service.ServiceDeployConfig;
import com.schbrain.ci.jenkins.plugins.integration.builder.constants.Constants.DockerConstants;
import com.schbrain.ci.jenkins.plugins.integration.builder.util.FileUtils;
import hudson.Extension;
import hudson.FilePath;
import hudson.Util;
import hudson.model.Descriptor;
import jenkins.model.Jenkins;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * @author liaozan
 * @since 2022/1/16
 */
@SuppressWarnings("unused")
public class DeployToK8sConfig extends BuildConfig<DeployToK8sConfig> {

    private final String configLocation;

    private final DeployStyleRadio deployStyle;

    private final ServiceDeployConfig serviceDeployConfig;

    @DataBoundConstructor
    public DeployToK8sConfig(String configLocation, DeployStyleRadio deployStyle, ServiceDeployConfig serviceDeployConfig) {
        this.configLocation = Util.fixNull(configLocation);
        this.deployStyle = deployStyle;
        this.serviceDeployConfig = serviceDeployConfig;
    }

    public String getConfigLocation() {
        return configLocation;
    }

    public DeployStyleRadio getDeployStyle() {
        return deployStyle;
    }

    public ServiceDeployConfig getServiceDeployConfig() {
        return serviceDeployConfig;
    }

    public void doBuild() throws Exception {
        String imageName = envVars.get(DockerConstants.IMAGE);
        if (StringUtils.isBlank(imageName)) {
            context.log("image name is empty ,skip deploy");
            return;
        }
        // make sure to build Deployment first
        buildDeployment();
        buildService();
    }

    private void buildDeployment() throws Exception {
        DeployStyleRadio deployStyle = getDeployStyle();
        if (null == deployStyle) {
            return;
        }

        String deployFileLocation = deployStyle.getDeployFileLocation(context);
        executeK8sCommand(deployFileLocation);
    }

    private void buildService() throws Exception {
        if (serviceDeployConfig == null) {
            return;
        }
        String deployFileLocation = serviceDeployConfig.getServiceDeployFileLocation(context);
        executeK8sCommand(deployFileLocation);
    }

    private void executeK8sCommand(String deployFileLocation) throws InterruptedException, IOException {
        String configLocation = getConfigLocation();
        if (null == configLocation) {
            context.log("not specified configLocation of k8s config ,will use default config .");
        }

        String deployFileRelativePath = FileUtils.toRelativePath(workspace, new FilePath(new File(deployFileLocation)));
        String command = String.format("kubectl apply -f %s", deployFileRelativePath);
        if (StringUtils.isNotBlank(configLocation)) {
            command = command + " --kubeconfig " + configLocation;
        }
        context.execute(command);
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<DeployToK8sConfig> {

        public List<Descriptor<DeployStyleRadio>> getDeployStyles() {
            return Jenkins.get().getDescriptorList(DeployStyleRadio.class);
        }

    }

}