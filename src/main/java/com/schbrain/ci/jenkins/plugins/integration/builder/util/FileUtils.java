package com.schbrain.ci.jenkins.plugins.integration.builder.util;

import com.schbrain.ci.jenkins.plugins.integration.builder.BuilderContext;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.FilePath;

import java.io.IOException;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * @author liaozan
 * @since 2022/1/17
 */
public class FileUtils {

    /**
     * lookup the special file
     */
    @CheckForNull
    public static FilePath lookupFile(BuilderContext context, String fileName) throws IOException, InterruptedException {
        return lookupFile(context.getWorkspace(), fileName, context.getLogger());
    }

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
        FilePath matchedFile = getTheClosestFile(fileList);
        String relativePath = toRelativePath(workspace, matchedFile);
        String fileContent = matchedFile.readToString();
        logger.printf("lookup for %s found at %s,  content: \n%s", fileName, relativePath, fileContent);
        return matchedFile;
    }

    public static String toRelativePath(FilePath root, FilePath filePath) {
        Path rootPath = Paths.get(root.getRemote());
        Path targetFilePath = Paths.get(filePath.getRemote());
        return rootPath.relativize(targetFilePath).toString();
    }

    public static FilePath getTheClosestFile(FilePath[] fileList) {
        FilePath matched = fileList[0];
        if (fileList.length == 1) {
            return matched;
        }

        for (FilePath filePath : fileList) {
            String filePathName = filePath.getRemote();
            if (filePathName.length() < matched.getRemote().length()) {
                matched = filePath;
            }
        }
        return matched;
    }

    public static Map<String, String> filePathToMap(FilePath lookupFile) throws IOException, InterruptedException {
        Map<String, String> result = new HashMap<>();
        Properties properties = new Properties();
        properties.load(new StringReader(lookupFile.readToString()));
        for (String propertyName : properties.stringPropertyNames()) {
            result.put(propertyName, properties.getProperty(propertyName));
        }
        return result;
    }

}
