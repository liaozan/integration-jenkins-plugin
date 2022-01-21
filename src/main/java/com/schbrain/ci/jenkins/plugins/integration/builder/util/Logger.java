package com.schbrain.ci.jenkins.plugins.integration.builder.util;

import java.io.PrintStream;

/**
 * @author liaozan
 * @since 2022/1/20
 */
public class Logger extends PrintStream {

    private final PrintStream delegate;

    private Logger(PrintStream delegate) {
        super(delegate);
        this.delegate = delegate;
    }

    public static Logger of(PrintStream delegate) {
        return new Logger(delegate);
    }

    public void println(String template, Object... args) {
        String content = String.format(template, args);
        println(content);
    }

    @Override
    public void println(String content) {
        String wrappedContent = "|| " + content + " ||";
        StringBuilder wrapperLine = new StringBuilder();
        for (int i = 0; i < wrappedContent.length(); i++) {
            wrapperLine.append("=");
        }
        delegate.println();
        delegate.println(wrapperLine);
        delegate.println(wrappedContent);
        delegate.println(wrapperLine);
    }

}
