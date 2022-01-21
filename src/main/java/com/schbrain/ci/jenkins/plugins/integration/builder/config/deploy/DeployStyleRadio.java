package com.schbrain.ci.jenkins.plugins.integration.builder.config.deploy;

import com.schbrain.ci.jenkins.plugins.integration.builder.BuilderContext;
import com.schbrain.ci.jenkins.plugins.integration.builder.config.entry.Entry;
import hudson.model.Describable;
import hudson.model.Descriptor;

import java.util.List;

/**
 * @author zhangdd on 2022/1/20
 */
public abstract class DeployStyleRadio implements Describable<DeployStyleRadio> {

    public abstract String getDeployFileLocation(BuilderContext builderContext, List<Entry> entries) throws Exception;

    public abstract static class InventoryDescriptor extends Descriptor<DeployStyleRadio> {

    }

}
