package com.schbrain.ci.jenkins.plugins.integration.builder.config.deploy;

import com.schbrain.ci.jenkins.plugins.integration.builder.BuilderContext;
import hudson.model.Describable;
import hudson.model.Descriptor;
import jenkins.model.Jenkins;

/**
 * @author zhangdd on 2022/1/20
 */
public abstract class DeployStyleRadio implements Describable<DeployStyleRadio> {

    //---------------------------------------------------------------------
    // Abstract methods to be implemented by subclasses
    //---------------------------------------------------------------------

    public abstract String getDeployFileLocation(BuilderContext builderContext) throws Exception;

    @Override
    @SuppressWarnings("unchecked")
    public Descriptor<DeployStyleRadio> getDescriptor() {
        return Jenkins.get().getDescriptor(getClass());
    }

    public abstract static class InventoryDescriptor extends Descriptor<DeployStyleRadio> {

    }

}
