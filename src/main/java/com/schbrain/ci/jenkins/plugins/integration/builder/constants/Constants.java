package com.schbrain.ci.jenkins.plugins.integration.builder.constants;

/**
 * @author zhangdd on 2022/1/20
 */
public class Constants {

    public static class DeployConstants {

        public static final String TEMPLATE_URL = "http://gitlab.schbrain.com/gitlab/tools/build-script/-/raw/master/k8s-deploy-template.yaml?";
        public static final String TEMPLATE_FILE_NAME = TEMPLATE_URL.substring(TEMPLATE_URL.lastIndexOf("/"));
        public static final String DEPLOY_FILE_NAME = "deploy.yaml";

    }

    public static class DockerConstants {

        public static final String BUILD_INFO_FILE_NAME = "dockerBuildInfo";
        public static final String IMAGE = "IMAGE";
        public static final String REGISTRY = "REGISTRY";
        public static final String APP_NAME = "APP_NAME";
        public static final String VERSION = "VERSION";

    }

}
