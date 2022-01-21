package com.schbrain.ci.jenkins.plugins.integration.builder.config.deploy;

import com.schbrain.ci.jenkins.plugins.integration.builder.BuilderContext;
import com.schbrain.ci.jenkins.plugins.integration.builder.config.entry.Entry;
import hudson.Extension;
import hudson.model.Descriptor;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.DataBoundConstructor;

import java.util.List;

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
    public String getDeployFileLocation(BuilderContext builderContext, List<Entry> entries) {
        return getDeployFileLocation();
    }

    @Override
    @SuppressWarnings({"unchecked"})
    public Descriptor<DeployStyleRadio> getDescriptor() {
        return Jenkins.get().getDescriptor(PointDeployFileComponent.class);
    }

    @Extension
    public static class DescriptorImpl extends InventoryDescriptor {

        @Override
        public String getDisplayName() {
            return "指定部署文件位置";
        }

    }

}
