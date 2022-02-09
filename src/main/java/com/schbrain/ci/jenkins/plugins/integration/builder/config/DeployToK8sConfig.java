package com.schbrain.ci.jenkins.plugins.integration.builder.config;

import com.schbrain.ci.jenkins.plugins.integration.builder.config.deploy.DeployStyleRadio;
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
import java.util.List;

/**
 * @author liaozan
 * @since 2022/1/16
 */
@SuppressWarnings("unused")
public class DeployToK8sConfig extends BuildConfig<DeployToK8sConfig> {


    private final String configLocation;

    private final DeployStyleRadio deployStyle;

    @DataBoundConstructor
    public DeployToK8sConfig(String configLocation, DeployStyleRadio deployStyle) {
        this.configLocation = Util.fixNull(configLocation);
        this.deployStyle = deployStyle;
    }



    public String getConfigLocation() {
        return configLocation;
    }

    public DeployStyleRadio getDeployStyle() {
        return deployStyle;
    }

    public void doBuild() throws Exception {
        String imageName = envVars.get(DockerConstants.IMAGE);
        if (StringUtils.isBlank(imageName)) {
            context.log("image name is empty ,skip deploy");
            return;
        }

        DeployStyleRadio deployStyle = getDeployStyle();
        if (null == deployStyle) {
            return;
        }

        String configLocation = getConfigLocation();
        if (null == configLocation) {
            context.log("not specified configLocation of k8s config ,will use default config .");
        }

        String deployFileLocation = deployStyle.getDeployFileLocation(context);
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
