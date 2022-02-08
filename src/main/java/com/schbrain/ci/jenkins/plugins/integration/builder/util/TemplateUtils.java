package com.schbrain.ci.jenkins.plugins.integration.builder.util;

import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.Velocity;

import java.io.StringWriter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author liaozan
 * @since 2022/1/23
 */
public class TemplateUtils {

    public static String resolve(String template, Map<String, String> variables) {
        if (null == template) {
            return null;
        }
        if (null == variables || variables.isEmpty()) {
            return template;
        }

        Map<String, Object> params = new LinkedHashMap<>(variables);
        VelocityContext velocityContext = new VelocityContext(params);
        StringWriter writer = new StringWriter();
        Velocity.evaluate(velocityContext, writer, "Template Evaluate", template);
        return writer.getBuffer().toString();
    }

}
