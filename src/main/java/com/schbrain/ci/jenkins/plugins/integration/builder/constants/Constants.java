package com.schbrain.ci.jenkins.plugins.integration.builder.constants;

/**
 * @author zhangdd on 2022/1/20
 */
public class Constants {

    public static final String DEPLOY_TEMPLATE_URL = "http://gitlab.schbrain.com/gitlab/zhangdongdong/deploy-script/-/raw/master/k8s-deploy-template.yaml";
    public static final String DEPLOY_TEMPLATE_FILE_NAME = DEPLOY_TEMPLATE_URL.substring(DEPLOY_TEMPLATE_URL.lastIndexOf("/"));
    public static final String DEPLOY_FILE_NAME = "deploy.yaml";
}
