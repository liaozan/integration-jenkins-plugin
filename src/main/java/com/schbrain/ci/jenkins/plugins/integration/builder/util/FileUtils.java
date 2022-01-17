package com.schbrain.ci.jenkins.plugins.integration.builder.util;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.FilePath;

import java.io.IOException;
import java.io.PrintStream;

/**
 * @author liaozan
 * @since 2022/1/17
 */
public class FileUtils {

    /**
     * lookup the special file
     */
    @CheckForNull
    public static FilePath lookupFile(FilePath workspace, String fileName, PrintStream logger) throws IOException, InterruptedException {
        if (workspace == null || !workspace.exists()) {
            logger.println("workspace not exist");
            return null;
        }
        FilePath[] fileList = workspace.list("**/" + fileName);
        if (fileList.length == 0) {
            logger.println("could not found matched file: " + fileName);
            return null;
        }
        for (FilePath filePath : fileList) {
            logger.println("found matched file " + filePath);
        }
        if (fileList.length > 1) {
            logger.println("expect match one, but found " + fileList.length + " return the first one");
        }
        logger.println(fileList[0].readToString());
        return fileList[0];
    }

}
