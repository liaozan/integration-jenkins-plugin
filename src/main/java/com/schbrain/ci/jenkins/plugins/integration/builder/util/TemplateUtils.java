package com.schbrain.ci.jenkins.plugins.integration.builder.util;

import com.schbrain.ci.jenkins.plugins.integration.builder.BuilderContext;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.Velocity;

import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author liaozan
 * @since 2022/1/23
 */
public class TemplateUtils {

    public static void resolveDeployFilePlaceholder(Path templateFile, Path deployFile, BuilderContext context) throws Exception {
        if (templateFile == null) {
            return;
        }
        if (Files.notExists(deployFile)) {
            Files.createFile(deployFile);
        }

        String templateContent = new String(Files.readAllBytes(templateFile), StandardCharsets.UTF_8);
        String resolved = resolve(templateContent, context.getEnvVars());
        Files.write(deployFile, resolved.getBytes(StandardCharsets.UTF_8));
    }

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