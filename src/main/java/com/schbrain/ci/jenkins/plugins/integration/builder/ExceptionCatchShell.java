package com.schbrain.ci.jenkins.plugins.integration.builder;

import hudson.Proc;
import hudson.tasks.Shell;
import jenkins.model.Jenkins;

import java.io.IOException;

/**
 * @author liaozan
 * @since 2022/3/21
 */
public class ExceptionCatchShell extends Shell {

    public ExceptionCatchShell(String command) {
        super(command);
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) Jenkins.get().getDescriptor(Shell.class);
    }

    @Override
    protected int join(Proc process) throws IOException, InterruptedException {
        int existCode = super.join(process);
        if (existCode == 0) {
            return existCode;
        }
        String command = getCommand();
        throw new RuntimeException(String.format("Failed to execute \"%s\", Please review the log to correct the build", command));
    }

}