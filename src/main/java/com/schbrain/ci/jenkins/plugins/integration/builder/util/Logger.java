package com.schbrain.ci.jenkins.plugins.integration.builder.util;

import java.io.PrintStream;

/**
 * @author liaozan
 * @since 2022/1/20
 */
public class Logger extends PrintStream {

    private final PrintStream delegate;

    public Logger(PrintStream delegate) {
        super(delegate);
        this.delegate = delegate;
    }

    public void println(String template, Object... args) {
        String content = String.format(template, args);
        println(content);
    }

    @Override
    public void println(String content) {
        String wrappedContent = "【【【 " + content + " 】】】";
        delegate.println(wrappedContent);
    }

}
