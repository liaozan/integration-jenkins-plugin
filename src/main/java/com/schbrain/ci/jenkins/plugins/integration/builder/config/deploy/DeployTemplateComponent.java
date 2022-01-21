package com.schbrain.ci.jenkins.plugins.integration.builder.config.deploy;

import cn.hutool.core.util.StrUtil;
import com.schbrain.ci.jenkins.plugins.integration.builder.BuilderContext;
import com.schbrain.ci.jenkins.plugins.integration.builder.config.entry.Entry;
import com.schbrain.ci.jenkins.plugins.integration.builder.constants.Constants;
import com.schbrain.ci.jenkins.plugins.integration.builder.util.Logger;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.springframework.util.CollectionUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
        EnvVars envVars = builderContext.getEnvVars();
        FilePath workspace = builderContext.getWorkspace();
        Logger logger = builderContext.getLogger();

        Path templatePath = downloadDeployTemplate(builderContext, workspace, logger);

        String deployFileLocation = new File(templatePath.getParent().toString(), Constants.DEPLOY_FILE_NAME).getPath();

        resolveDeployFilePlaceholder(entries, envVars,
                templatePath.getFileName().toString(), deployFileLocation, workspace, logger);
        return deployFileLocation;
    }

    private Path downloadDeployTemplate(BuilderContext builderContext, FilePath workspace, Logger logger) throws Exception {
        FilePath existDeployTemplate = lookupFile(workspace, Constants.DEPLOY_TEMPLATE_FILE_NAME, logger);
        if (null != existDeployTemplate) {
            existDeployTemplate.delete();
        }
        String command = String.format("wget  %s", Constants.DEPLOY_TEMPLATE_URL);
        builderContext.execute(command);

        return Paths.get(workspace.getRemote(), Constants.DEPLOY_TEMPLATE_FILE_NAME);
    }

    private void resolveDeployFilePlaceholder(List<Entry> entries, EnvVars envVars,
                                              String templateFileName, String deployFileLocation,
                                              FilePath workspace, Logger logger) throws Exception {
        Map<String, String> param = new HashMap<>(envVars);
        param.put("IMAGE", envVars.get("IMAGE_NAME"));
        param.put("NAMESPACE", getNamespace());
        param.put("PORT", getPort());


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

    @Extension
    public static class DescriptorImpl extends InventoryDescriptor {

        @Override
        public String getDisplayName() {
            return "使用默认模版";
        }
    }
}
