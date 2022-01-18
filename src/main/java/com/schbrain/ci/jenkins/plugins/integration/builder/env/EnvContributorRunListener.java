package com.schbrain.ci.jenkins.plugins.integration.builder.env;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.Build;
import hudson.model.BuildListener;
import hudson.model.Environment;
import hudson.model.Run.RunnerAbortedException;
import hudson.model.listeners.RunListener;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
        return new DockerBuildInfoAwareEnvironment();
    }

    public static class DockerBuildInfoAwareEnvironment extends Environment {

        public static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

        private Map<String, String> dockerBuildInfo;

        @Override
        public void buildEnvVars(Map<String, String> env) {
            env.put("DATE", DATE_TIME_FORMATTER.format(LocalDateTime.now()));
            if (dockerBuildInfo != null) {
                env.putAll(dockerBuildInfo);
            }
        }

        public void setDockerInfo(Map<String, String> dockerBuildInfo) {
            this.dockerBuildInfo = dockerBuildInfo;
        }

    }

}
