package com.schbrain.ci.jenkins.plugins.integration.builder.config.deploy;

import com.schbrain.ci.jenkins.plugins.integration.builder.BuilderContext;
import com.schbrain.ci.jenkins.plugins.integration.builder.FileManager;
import com.schbrain.ci.jenkins.plugins.integration.builder.constants.Constants.DeployConstants;
import com.schbrain.ci.jenkins.plugins.integration.builder.util.TemplateUtils;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.EnvVars;
import hudson.Extension;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

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
    public String getDeployFileLocation(BuilderContext context) throws Exception {
        Path templateFile = getDeployTemplate(context);
        Path deployFile = Paths.get(templateFile.getParent().toString(), DeployConstants.DEPLOY_FILE_NAME);
        resolveDeployFilePlaceholder(templateFile, deployFile, context);
        return deployFile.toString();
    }

    private Path getDeployTemplate(BuilderContext context) {
        File buildScriptDir = FileManager.getBuildScriptDir(context.getBuild());
        return Paths.get(buildScriptDir.getPath(), DeployConstants.TEMPLATE_FILE_NAME);
    }

    private void resolveDeployFilePlaceholder(Path templateFile, Path deployFile, BuilderContext context) throws Exception {
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


        String templateContent = new String(Files.readAllBytes(templateFile), StandardCharsets.UTF_8);
        String resolved = TemplateUtils.resolve(templateContent, envVars);
        context.getLogger().println("resolved k8sDeployFile :\n" + resolved, false);
        Files.write(deployFile, resolved.getBytes(StandardCharsets.UTF_8));
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
