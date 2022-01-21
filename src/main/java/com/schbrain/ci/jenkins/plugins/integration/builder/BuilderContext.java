package com.schbrain.ci.jenkins.plugins.integration.builder;

import com.schbrain.ci.jenkins.plugins.integration.builder.env.BuildEnvContributor;
import com.schbrain.ci.jenkins.plugins.integration.builder.util.Logger;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.tasks.Shell;

/**
 * @author zhangdd on 2022/1/21
 */
public class BuilderContext {

    private final AbstractBuild<?, ?> build;
    private final Launcher launcher;
    private final FilePath workspace;
    private final BuildListener listener;
    private final Logger logger;
    private final EnvVars envVars;
    private boolean shouldContinue = true;

    private BuilderContext(Builder builder) {
        this.build = builder.build;
        this.launcher = builder.launcher;
        this.workspace = builder.workspace;
        this.listener = builder.listener;
        this.logger = builder.logger;
        this.envVars = builder.envVars;
    }

    public void execute(String command) throws InterruptedException {
        if (!shouldContinue) {
            throw new IllegalStateException("build task has been interrupted");
        }
        log("%s", command);
        BuildEnvContributor.clearEnvVarsFromDisk(getWorkspace().getBaseName());
        BuildEnvContributor.saveEnvVarsToDisk(getEnvVars(), getWorkspace().getBaseName());
        Shell shell = new Shell(command);
        shouldContinue = shell.perform(getBuild(), getLauncher(), getListener());
    }

    public AbstractBuild<?, ?> getBuild() {
        return build;
    }

    public Launcher getLauncher() {
        return launcher;
    }

    public FilePath getWorkspace() {
        return workspace;
    }

    public BuildListener getListener() {
        return listener;
    }

    public Logger getLogger() {
        return logger;
    }

    public EnvVars getEnvVars() {
        return envVars;
    }

    public void log(String template) {
        log(template, (Object) null);
    }

    public void log(String template, Object... arguments) {
        logger.println(template, true, arguments);
    }

    public static class Builder {

        private AbstractBuild<?, ?> build;
        private Launcher launcher;
        private FilePath workspace;
        private BuildListener listener;
        private Logger logger;
        private EnvVars envVars;

        public Builder build(AbstractBuild<?, ?> build) {
            this.build = build;
            return this;
        }

        public Builder launcher(Launcher launcher) {
            this.launcher = launcher;
            return this;
        }

        public Builder workspace(FilePath workspace) {
            this.workspace = workspace;
            return this;
        }

        public Builder listener(BuildListener listener) {
            this.listener = listener;
            return this;
        }

        public Builder logger(Logger logger) {
            this.logger = logger;
            return this;
        }

        public Builder envVars(EnvVars envVars) {
            this.envVars = envVars;
            return this;
        }

        public BuilderContext build() {

            return new BuilderContext(this);
        }

    }

}
