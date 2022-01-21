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

    public void println(String content, Object... args) {
        println(content, true, args);
    }

    public void println(String content, boolean format, Object... args) {
        content = String.format(content, args);
        println(content, format);
    }

    public void println(String content, boolean format) {
        if (format) {
            String wrappedContent = "|| " + content + " ||";
            StringBuilder wrapperLine = new StringBuilder();
            for (int i = 0; i < wrappedContent.length(); i++) {
                wrapperLine.append("=");
            }
            delegate.println();
            delegate.println(wrapperLine);
            delegate.println(wrappedContent);
            delegate.println(wrapperLine);
            delegate.println();
        } else {
            delegate.println(content);
        }
    }

}
