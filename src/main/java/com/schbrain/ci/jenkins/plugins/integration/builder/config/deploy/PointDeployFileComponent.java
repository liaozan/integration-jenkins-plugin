package com.schbrain.ci.jenkins.plugins.integration.builder.config.deploy;

import com.schbrain.ci.jenkins.plugins.integration.builder.BuilderContext;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * @author zhangdd on 2022/1/20
 */
@SuppressWarnings("unused")
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
    public String getDeployFileLocation(BuilderContext builderContext) {
        return getDeployFileLocation();
    }

    @Extension
    public static class DescriptorImpl extends InventoryDescriptor {

        @NonNull
        @Override
        public String getDisplayName() {
            return "指定部署文件位置";
        }

    }

}
