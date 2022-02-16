package com.schbrain.ci.jenkins.plugins.integration.action;

import hudson.FilePath;
import hudson.model.AbstractBuild;
import hudson.model.DirectoryBrowserSupport;
import hudson.model.Run;
import jenkins.model.RunAction2;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import java.io.File;

/**
 * @author liaozan
 * @since 2022/2/16
 */
public class ViewBuildScriptAction implements RunAction2 {

    private final String buildScriptDir;

    private transient Run<?, ?> run;

    public ViewBuildScriptAction(File buildScriptDir) {
        this.buildScriptDir = buildScriptDir.getAbsolutePath();
    }

    @Override
    public String getIconFileName() {
        return "document.png";
    }

    @Override
    public String getDisplayName() {
        return "构建脚本";
    }

    @Override
    public String getUrlName() {
        return "build-scripts";
    }

    @Override
    public void onAttached(Run<?, ?> run) {
        this.run = run;
    }

    @Override
    public void onLoad(Run<?, ?> run) {
        this.run = run;
    }

    public Run<?, ?> getRun() {
        return run;
    }

    @SuppressWarnings({"unused", "rawtypes"})
    public DirectoryBrowserSupport doList(StaplerRequest request, StaplerResponse response) {
        AbstractBuild build = (AbstractBuild) request.findAncestor(AbstractBuild.class).getObject();
        FilePath filePath = new FilePath(new File(buildScriptDir));
        return new DirectoryBrowserSupport(build, filePath, "构建脚本", "folder.png", true);
    }

}