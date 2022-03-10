package com.schbrain.ci.jenkins.plugins.integration.builder.config.deploy.service;

import com.schbrain.ci.jenkins.plugins.integration.builder.BuilderContext;
import com.schbrain.ci.jenkins.plugins.integration.builder.FileManager;
import com.schbrain.ci.jenkins.plugins.integration.builder.util.TemplateUtils;
import hudson.EnvVars;
import org.kohsuke.stapler.DataBoundConstructor;
import org.springframework.util.StringUtils;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

import static com.schbrain.ci.jenkins.plugins.integration.builder.constants.Constants.DeployConstants.*;
import static com.schbrain.ci.jenkins.plugins.integration.builder.constants.Constants.DockerConstants.APP_NAME;

/**
 * @author liaozan
 * @since 2022/3/10
 */
public class ServiceDeployConfig {

    private final String serviceNamespace;
    private final String serviceName;
    private final String servicePort;

    @DataBoundConstructor
    public ServiceDeployConfig(String serviceNamespace, String serviceName, String servicePort) {
        this.serviceNamespace = serviceNamespace;
        this.serviceName = serviceName;
        this.servicePort = servicePort;
    }

    public String getServiceNamespace() {
        return serviceNamespace;
    }

    public String getServiceName() {
        return serviceName;
    }

    public String getServicePort() {
        return servicePort;
    }

    public String getServiceDeployFileLocation(BuilderContext context) throws Exception {
        Path serviceTemplate = getServiceDeployTemplate(context);
        Path serviceDeployFile = Paths.get(serviceTemplate.getParent().toString(), SERVICE_DEPLOY_FILE_NAME);
        contributeEnv(context.getEnvVars());
        TemplateUtils.resolveDeployFilePlaceholder(serviceTemplate, serviceDeployFile, context);
        return serviceDeployFile.toString();
    }

    private void contributeEnv(EnvVars envVars) {
        if (StringUtils.hasText(getServiceNamespace())) {
            envVars.put(K8S_SERVICE_NAMESPACE, getServiceNamespace());
        } else {
            envVars.put(K8S_SERVICE_NAMESPACE, envVars.get(K8S_POD_NAMESPACE));
        }

        if (StringUtils.hasText(getServiceName())) {
            envVars.put(K8S_SERVICE_NAME, getServiceName());
        } else {
            envVars.put(K8S_SERVICE_NAME, envVars.get(APP_NAME));
        }

        if (StringUtils.hasText(getServicePort())) {
            envVars.put(K8S_SERVICE_PORT, getServicePort());
        } else {
            envVars.put(K8S_SERVICE_PORT, envVars.get(K8S_POD_PORT));
        }
    }

    private Path getServiceDeployTemplate(BuilderContext context) {
        File buildScriptDir = FileManager.getBuildScriptDir(context.getBuild());
        return Paths.get(buildScriptDir.getPath(), SERVICE_TEMPLATE_FILE_NAME);
    }

}