package com.schbrain.ci.jenkins.plugins.integration.builder.env;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.Build;
import hudson.model.BuildListener;
import hudson.model.Environment;
import hudson.model.Run.RunnerAbortedException;
import hudson.model.listeners.RunListener;

import java.util.HashMap;
import java.util.Map;

/**
 * @author liaozan
 * @since 2022/1/17
 */
@Extension
@SuppressWarnings("unused")
public class EnvContributorRunListener extends RunListener<Build<?, ?>> {

    @Override
    public Environment setUpEnvironment(AbstractBuild build, Launcher launcher, BuildListener listener) throws RunnerAbortedException {
        return new CustomEnvironment();
    }

    public static class CustomEnvironment extends Environment {

        private final Map<String, String> envVars = new HashMap<>();

        @Override
        public void buildEnvVars(Map<String, String> env) {
            env.putAll(envVars);
        }

        public void addEnvVars(Map<String, String> envVars) {
            this.envVars.putAll(envVars);
        }

        public void addEnvVars(String envKey, String value) {
            envVars.put(envKey, value);
        }

    }

}
