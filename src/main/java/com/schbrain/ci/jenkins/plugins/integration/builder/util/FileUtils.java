package com.schbrain.ci.jenkins.plugins.integration.builder.util;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.FilePath;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.nio.file.Paths;

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
        if (fileList.length > 1) {
            logger.println("expect match one, but found " + fileList.length + " return the first one");
        }
        logger.printf("lookup for %s content: \n%s", fileName, fileList[0].readToString());
        return fileList[0];
    }

    public static String toRelativePath(FilePath root, FilePath filePath) {
        Path rootPath = Paths.get(root.getRemote());
        Path targetFilePath = Paths.get(filePath.getRemote());
        return rootPath.relativize(targetFilePath).toString();
    }

}
