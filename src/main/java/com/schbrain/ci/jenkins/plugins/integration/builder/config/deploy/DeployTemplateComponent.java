package com.schbrain.ci.jenkins.plugins.integration.builder.config.deploy;

import com.schbrain.ci.jenkins.plugins.integration.builder.BuilderContext;
import com.schbrain.ci.jenkins.plugins.integration.builder.config.entry.Entry;
import com.schbrain.ci.jenkins.plugins.integration.builder.constants.Constants.DeployConstants;
import com.schbrain.ci.jenkins.plugins.integration.builder.constants.Constants.DockerConstants;
import com.schbrain.ci.jenkins.plugins.integration.builder.util.TemplateUtils;
import hudson.EnvVars;
import hudson.Extension;
import org.kohsuke.stapler.DataBoundConstructor;
import org.springframework.util.CollectionUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.List;

/**
 * @author zhangdd on 2022/1/20
 */
public class DeployTemplateComponent extends DeployStyleRadio {

    private final String namespace;
    private final String replicas;
    private final String port;

    @DataBoundConstructor
    public DeployTemplateComponent(String namespace, String replicas, String port) {
        this.namespace = namespace;
        this.replicas = replicas;
        this.port = port;
    }

    public String getNamespace() {
        return namespace;
    }

    public String getReplicas() {
        return replicas;
    }

    public String getPort() {
        return port;
    }

    @Override
    public String getDeployFileLocation(BuilderContext context, List<Entry> entries) throws Exception {
        Path templateFile = getDeployTemplate(context);
        Path deployFile = Paths.get(templateFile.getParent().toString(), DeployConstants.DEPLOY_FILE_NAME);
        resolveDeployFilePlaceholder(templateFile, deployFile, entries, context);
        return deployFile.toString();
    }

    private Path getDeployTemplate(BuilderContext context) {
        String buildScriptDirectory = context.getEnvVars().get(DockerConstants.BUILD_SCRIPT);
        return Paths.get(buildScriptDirectory, DeployConstants.TEMPLATE_FILE_NAME);
    }

    private void resolveDeployFilePlaceholder(Path templateFile, Path deployFile,
                                              List<Entry> entries, BuilderContext context) throws Exception {
        if (templateFile == null) {
            return;
        }
        if (Files.notExists(deployFile)) {
            Files.createFile(deployFile);
        }

        EnvVars envVars = context.getEnvVars();
        envVars.put("NAMESPACE", getNamespace());
        envVars.put("PORT", getPort());
        envVars.put("REPLICAS", getReplicas());

        if (!CollectionUtils.isEmpty(entries)) {
            for (Entry entry : entries) {
                entry.contribute(envVars);
            }
        }
        String templateContent = new String(Files.readAllBytes(templateFile), StandardCharsets.UTF_8);
        String data = TemplateUtils.format(templateContent, envVars);
        context.getLogger().println("resolved k8sDeployFile :\n" + data, false);
        Files.write(deployFile, data.getBytes(StandardCharsets.UTF_8));
    }

    @Extension
    public static class DescriptorImpl extends InventoryDescriptor {

        @Override
        public String getDisplayName() {
            return "使用默认模版";
        }

    }

}
