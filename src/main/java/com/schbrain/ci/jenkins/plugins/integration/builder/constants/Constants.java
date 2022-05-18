package com.schbrain.ci.jenkins.plugins.integration.builder.constants;

/**
 * @author zhangdd on 2022/1/20
 */
public class Constants {

    public static class BuildConstants {

        public static final String SCRIPT_GIT_REPO = "git@gitlab.schbrain.com:tools/build-script.git";
        public static final String SCRIPT_GIT_BRANCH = "main";
        public static final String SCRIPT_NAME = "build-script.zip";

    }

    public static class DeployConstants {

        public static final String DEPLOYMENT_TEMPLATE_FILE_NAME = "k8s-deploy-template.yaml";
        public static final String SERVICE_TEMPLATE_FILE_NAME = "k8s-service-template.yaml";
        public static final String DEPLOYMENT_DEPLOY_FILE_NAME = "deployment-deploy.yaml";
        public static final String SERVICE_DEPLOY_FILE_NAME = "service-deploy.yaml";
        public static final String K8S_POD_NAMESPACE = "NAMESPACE";
        public static final String K8S_POD_PORT = "PORT";
        public static final String K8S_POD_REPLICAS = "REPLICAS";
        public static final String K8S_POD_MEMORY_LIMIT = "MEMORY_LIMIT";
        public static final String K8S_POD_MEMORY_REQUEST = "MEMORY_REQUEST";
        public static final String K8S_POD_NODE_POOL_ID = "NODE_POOL_ID";
        public static final String K8S_SERVICE_MODE = "SERVICE_MODE";
        public static final String K8S_SERVICE_NAMESPACE = "SERVICE_NAMESPACE";
        public static final String K8S_SERVICE_NAME = "SERVICE_NAME";
        public static final String K8S_SERVICE_PORT = "SERVICE_PORT";

    }

    public static class DockerConstants {

        public static final String BUILD_INFO_FILE_NAME = "dockerBuildInfo";
        public static final String DOCKERFILE_NAME = "Dockerfile";
        public static final String IMAGE = "IMAGE";
        public static final String REGISTRY = "REGISTRY";
        public static final String APP_NAME = "APP_NAME";
        public static final String VERSION = "VERSION";
        public static final String JAVA_OPTS = "JAVA_OPTS";

    }

    public static class GitConstants {

        public static final String GIT_PROPERTIES_FILE = "git.properties";
        public static final String GIT_BRANCH = "git.branch";
        public static final String GIT_COMMITTER = "git.commit.user.name";

    }

}