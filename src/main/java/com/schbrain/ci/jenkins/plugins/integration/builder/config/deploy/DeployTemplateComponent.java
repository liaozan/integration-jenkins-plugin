package com.schbrain.ci.jenkins.plugins.integration.builder.config.deploy;

import cn.hutool.core.util.StrUtil;
import com.schbrain.ci.jenkins.plugins.integration.builder.BuilderContext;
import com.schbrain.ci.jenkins.plugins.integration.builder.config.entry.Entry;
import com.schbrain.ci.jenkins.plugins.integration.builder.constants.Constants.DeployConstants;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.springframework.util.CollectionUtils;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static com.schbrain.ci.jenkins.plugins.integration.builder.util.FileUtils.lookupFile;

/**
 * @author zhangdd on 2022/1/20
 */
public class DeployTemplateComponent extends DeployStyleRadio {

    private final String namespace;
    private final String port;

    @DataBoundConstructor
    public DeployTemplateComponent(String namespace, String port) {
        this.namespace = namespace;
        this.port = port;
    }

    public String getNamespace() {
        return namespace;
    }

    public String getPort() {
        return port;
    }

    @Override
    public String getDeployFileLocation(BuilderContext builderContext, List<Entry> entries) throws Exception {
        Path templatePath = downloadDeployTemplate(builderContext);
        String deployFileLocation = new File(templatePath.getParent().toString(), DeployConstants.DEPLOY_FILE_NAME).getPath();
        resolveDeployFilePlaceholder(entries, templatePath.getFileName().toString(), deployFileLocation, builderContext);
        return deployFileLocation;
    }

    private Path downloadDeployTemplate(BuilderContext builderContext) throws Exception {
        FilePath workspace = builderContext.getWorkspace();
        FilePath existDeployTemplate = lookupFile(builderContext, DeployConstants.TEMPLATE_FILE_NAME);
        if (null != existDeployTemplate) {
            existDeployTemplate.delete();
        }
        String command = String.format("wget  %s", DeployConstants.TEMPLATE_URL);
        builderContext.execute(command);

        return Paths.get(workspace.getRemote(), DeployConstants.TEMPLATE_FILE_NAME);
    }

    private void resolveDeployFilePlaceholder(List<Entry> entries, String templateFileName,
                                              String deployFileLocation, BuilderContext builderContext) throws Exception {
        EnvVars envVars = builderContext.getEnvVars();
        envVars.put("IMAGE", envVars.get("IMAGE_NAME"));
        envVars.put("NAMESPACE", getNamespace());
        envVars.put("PORT", getPort());

        if (!CollectionUtils.isEmpty(entries)) {
            for (Entry entry : entries) {
                entry.contribute(envVars);
            }
        }

        FilePath templateFile = lookupFile(builderContext, templateFileName);
        if (templateFile == null) {
            return;
        }

        String data = StrUtil.format(templateFile.readToString(), envVars);
        builderContext.log("resolved k8sDeployFile :\n%s", data);
        Path resolvedLocation = Paths.get(deployFileLocation);
        if (Files.notExists(resolvedLocation)) {
            Files.createFile(resolvedLocation);
        }
        Files.write(resolvedLocation, data.getBytes(StandardCharsets.UTF_8));
    }

    @Extension
    public static class DescriptorImpl extends InventoryDescriptor {

        @Override
        public String getDisplayName() {
            return "使用默认模版";
        }

    }

}
