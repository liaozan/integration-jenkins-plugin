package com.schbrain.ci.jenkins.plugins.integration.builder.config.deploy;

import com.schbrain.ci.jenkins.plugins.integration.builder.BuilderContext;
import com.schbrain.ci.jenkins.plugins.integration.builder.config.entry.Entry;
import hudson.Extension;
import org.kohsuke.stapler.DataBoundConstructor;

import java.util.List;

/**
 * @author zhangdd on 2022/1/20
 */
public class PointDeployFileComponent extends DeployStyleRadio {

    private final String deployFileLocation;

    @DataBoundConstructor
    public PointDeployFileComponent(String deployFileLocation) {
        this.deployFileLocation = deployFileLocation;
    }

    public String getDeployFileLocation() {
        return deployFileLocation;
    }

    @Override
    public String getDeployFileLocation(BuilderContext builderContext, List<Entry> entries) throws Exception {
        return getDeployFileLocation();
    }

    @Extension
    public static class DescriptorImpl extends InventoryDescriptor {

        @Override
        public String getDisplayName() {
            return "指定部署文件位置";
        }

    }
}
