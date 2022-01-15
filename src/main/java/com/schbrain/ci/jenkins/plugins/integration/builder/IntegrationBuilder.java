package com.schbrain.ci.jenkins.plugins.integration.builder;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Launcher.ProcStarter;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Item;
import hudson.plugins.git.BranchSpec;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.UserRemoteConfig;
import hudson.scm.SCMRevisionState;
import hudson.security.ACL;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.tasks.Maven;
import hudson.tasks.Maven.MavenInstallation;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.apache.commons.io.output.TeeOutputStream;
import org.apache.commons.lang.ArrayUtils;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.springframework.util.StringUtils;

import java.io.*;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static java.util.Collections.emptyList;

/**
 * @author liaozan
 * @since 2022/1/14
 */
@SuppressWarnings("unused")
public class IntegrationBuilder extends Builder {

    private final String gitRepositoryUrl;
    private final String gitRepositoryBranch;
    private final String credentialId;

    @DataBoundConstructor
    public IntegrationBuilder(String gitRepositoryUrl, String gitRepositoryBranch, String credentialId) {
        this.gitRepositoryUrl = gitRepositoryUrl;
        this.gitRepositoryBranch = gitRepositoryBranch;
        this.credentialId = credentialId;
    }

    public String getGitRepositoryUrl() {
        return gitRepositoryUrl;
    }

    public String getGitRepositoryBranch() {
        return gitRepositoryBranch;
    }

    public String getCredentialId() {
        return credentialId;
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
        checkout(build, launcher, listener);
        mavenBuild(build, launcher, listener);
        String imageName = lookupImageName(build, listener);
        listener.getLogger().println("imageName: " + imageName);
        return true;
    }

    @Override
    public IntegrationDescriptor getDescriptor() {
        return (IntegrationDescriptor) super.getDescriptor();
    }

    private String lookupImageName(AbstractBuild<?, ?> build, BuildListener listener) throws IOException, InterruptedException {
        FilePath workspace = getWorkspace(build);
        FilePath[] fileList = workspace.list("**/**/image-name");
        if (fileList.length == 0) {
            throw new FileNotFoundException("image-name");
        }
        PrintStream logger = listener.getLogger();
        for (FilePath filePath : fileList) {
            logger.println("found matched file " + filePath.readToString());
        }
        if (fileList.length > 1) {
            logger.println("expect match one, but found " + fileList.length + " return the first one");
        }
        return fileList[0].readToString();
    }

    private FilePath getWorkspace(AbstractBuild<?, ?> build) {
        FilePath workspace = build.getWorkspace();
        if (workspace == null) {
            throw new IllegalStateException("workspace should not be null");
        }
        return workspace;
    }

    private void mavenBuild(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException {
        MavenInstallation installation;
        MavenInstallation[] installations = Jenkins.get().getDescriptorByType(Maven.DescriptorImpl.class).getInstallations();
        if (ArrayUtils.isEmpty(installations)) {
            throw new NoSuchElementException("maven 安装不可用");
        } else {
            installation = installations[0];
        }

        EnvVars environment = build.getEnvironment(listener);
        EnvVars envVars = new EnvVars();
        envVars.putAllNonNull(environment);
        installation.buildEnvVars(envVars);

        ProcStarter mvnProcess = launcher
                .launch()
                .pwd(getWorkspace(build))
                .cmdAsSingleString("mvn clean package -U -DrunInLocal=false -Ddockerfile.push.skip=true")
                .envs(envVars);
        executeWithLogger(listener.getLogger(), mvnProcess);
    }

    private void checkout(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException {
        SCMRevisionState revisionState = Optional.ofNullable(build.getPreviousBuild())
                .map(prev -> prev.getAction(SCMRevisionState.class))
                .orElse(null);
        List<UserRemoteConfig> remoteConfigs = GitSCM.createRepoList(gitRepositoryUrl, credentialId);
        GitSCM git = new GitSCM(remoteConfigs, branchSpecs(), null, null, emptyList());
        git.checkout(build, launcher, build.getWorkspace(), listener, null, revisionState);
    }

    private List<BranchSpec> branchSpecs() {
        return Collections.singletonList(new BranchSpec(gitRepositoryBranch));
    }

    private void executeWithLogger(OutputStream outputStream, ProcStarter starter) throws IOException, InterruptedException {
        OutputStream stdout = new TeeOutputStream(outputStream, new ByteArrayOutputStream());
        OutputStream stderr = new TeeOutputStream(outputStream, new ByteArrayOutputStream());
        starter.stdout(stdout).stderr(stderr).start().join();
    }

    // can not move outside builder class
    @Extension
    @SuppressWarnings({"unused", "deprecation"})
    public static class IntegrationDescriptor extends BuildStepDescriptor<Builder> {

        public IntegrationDescriptor() {
            load();
        }

        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            return true;
        }

        /**
         * This human readable name is used in the configuration screen.
         */
        public String getDisplayName() {
            return "发布集成";
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
            return super.configure(req, formData);
        }

        public FormValidation doCheckGitRepositoryUrl(@QueryParameter String value) {
            if (!StringUtils.hasText(value)) {
                return FormValidation.error("gitRepositoryUrl is empty");
            }
            return FormValidation.ok();
        }

        public FormValidation doCheckGitRepositoryBranch(@QueryParameter String value) {
            if (!StringUtils.hasText(value)) {
                return FormValidation.error("gitRepositoryBranch is empty");
            }
            return FormValidation.ok();
        }

        public FormValidation doCheckCredentialId(@QueryParameter String value) {
            if (!StringUtils.hasText(value)) {
                return FormValidation.error("credentialId is empty");
            }
            return FormValidation.ok();
        }

        public ListBoxModel doFillCredentialIdItems(@AncestorInPath Item item, @QueryParameter String credentialsId) {
            StandardListBoxModel result = new StandardListBoxModel();
            if (item == null) {
                if (!Jenkins.get().hasPermission(Jenkins.ADMINISTER)) {
                    return result.includeCurrentValue(credentialsId);
                }
            } else {
                if (!item.hasPermission(Item.EXTENDED_READ)
                        && !item.hasPermission(CredentialsProvider.USE_ITEM)) {
                    return result.includeCurrentValue(credentialsId);
                }
            }
            return result.includeEmptyValue()
                    .includeMatchingAs(ACL.SYSTEM, item, StandardCredentials.class, emptyList(), GitClient.CREDENTIALS_MATCHER)
                    .includeCurrentValue(credentialsId);
        }

    }

}
