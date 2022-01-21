package com.schbrain.ci.jenkins.plugins.integration.builder.config.deploy;

import com.schbrain.ci.jenkins.plugins.integration.builder.BuilderContext;
import com.schbrain.ci.jenkins.plugins.integration.builder.config.entry.Entry;
import hudson.model.Describable;
import hudson.model.Descriptor;
import jenkins.model.Jenkins;

import java.util.List;

/**
 * @author zhangdd on 2022/1/20
 */
public abstract class DeployStyleRadio implements Describable<DeployStyleRadio> {
    @Override
    public Descriptor<DeployStyleRadio> getDescriptor() {

        return Jenkins.getInstanceOrNull().getDescriptor(DeployStyleRadio.class);
    }

    public abstract static class InventoryDescriptor extends Descriptor<DeployStyleRadio> { }


    public abstract String getDeployFileLocation(BuilderContext builderContext, List<Entry> entries) throws Exception;

}
