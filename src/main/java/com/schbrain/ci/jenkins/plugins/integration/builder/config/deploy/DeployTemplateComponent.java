package com.schbrain.ci.jenkins.plugins.integration.builder.config.deploy;

import com.schbrain.ci.jenkins.plugins.integration.builder.BuilderContext;
import com.schbrain.ci.jenkins.plugins.integration.builder.FileManager;
import com.schbrain.ci.jenkins.plugins.integration.builder.util.TemplateUtils;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.EnvVars;
import hudson.Extension;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

import static com.schbrain.ci.jenkins.plugins.integration.builder.constants.Constants.DeployConstants.*;

/**
 * @author zhangdd on 2022/1/20
 */
@SuppressWarnings("unused")
public class DeployTemplateComponent extends DeployStyleRadio {

    private final String namespace;
    private final String replicas;
    private final String memoryRequest;
    private final String memoryLimit;
    private final String nodePoolId;
    private final String port;

    @DataBoundConstructor
    public DeployTemplateComponent(String namespace, String replicas, String memoryRequest, String memoryLimit, String nodePoolId, String port) {
        this.namespace = namespace;
        this.replicas = replicas;
        this.memoryRequest = memoryRequest;
        this.memoryLimit = memoryLimit;
        this.nodePoolId = nodePoolId;
        this.port = port;
    }

    public String getNamespace() {
        return namespace;
    }

    public String getReplicas() {
        return replicas;
    }

    public String getMemoryRequest() {
        return memoryRequest;
    }

    public String getMemoryLimit() {
        return memoryLimit;
    }

    public String getNodePoolId() {
        return nodePoolId;
    }

    public String getPort() {
        return port;
    }

    @Override
    public String getDeployFileLocation(BuilderContext context) throws Exception {
        Path templateFile = getDeployTemplate(context);
        Path deployFile = Paths.get(templateFile.getParent().toString(), DEPLOYMENT_DEPLOY_FILE_NAME);
        contributeEnv(context.getEnvVars());
        TemplateUtils.resolveDeployFilePlaceholder(templateFile, deployFile, context);
        return deployFile.toString();
    }

    private void contributeEnv(EnvVars envVars) {
        envVars.put(K8S_POD_NAMESPACE, getNamespace());
        envVars.put(K8S_POD_PORT, getPort());
        envVars.put(K8S_POD_REPLICAS, getReplicas());
        envVars.put(K8S_POD_MEMORY_REQUEST, getMemoryRequest());
        envVars.put(K8S_POD_MEMORY_LIMIT, getMemoryLimit());
        envVars.put(K8S_POD_NODE_POOL_ID, getNodePoolId());
    }

    private Path getDeployTemplate(BuilderContext context) {
        File buildScriptDir = FileManager.getBuildScriptDir(context.getBuild());
        return Paths.get(buildScriptDir.getPath(), DEPLOYMENT_TEMPLATE_FILE_NAME);
    }

    @Extension
    public static class DescriptorImpl extends InventoryDescriptor {

        @NonNull
        @Override
        public String getDisplayName() {
            return "使用默认模版";
        }

    }

}