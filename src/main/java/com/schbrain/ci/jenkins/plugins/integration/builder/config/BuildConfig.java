package com.schbrain.ci.jenkins.plugins.integration.builder.config;

import hudson.model.AbstractDescribableImpl;

/**
 * @author liaozan
 * @since 2022/1/17
 */
public abstract class BuildConfig<T extends AbstractDescribableImpl<T>> extends AbstractDescribableImpl<T> {

    protected boolean disabled = false;

    public boolean isDisabled() {
        return disabled;
    }

    public void setDisabled(boolean disabled) {
        this.disabled = disabled;
    }

}
