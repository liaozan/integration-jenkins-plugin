package com.schbrain.ci.jenkins.plugins.integration.builder.util;

import org.apache.commons.lang3.StringUtils;

import java.util.Map;

/**
 * @author liaozan
 * @since 2022/1/23
 */
public class TemplateUtils {

    public static String format(String template, Map<String, String> map) {
        if (null == template) {
            return null;
        }
        if (null == map || map.isEmpty()) {
            return template;
        }

        String replaced = template;
        for (Map.Entry<String, String> entry : map.entrySet()) {
            String value = entry.getValue();
            if (null == entry.getValue()) {
                continue;
            }
            replaced = StringUtils.replace(replaced, "{" + entry.getKey() + "}", value);
        }
        return replaced;
    }

}
