package com.schbrain.ci.jenkins.plugins.integration.builder.util;

import com.schbrain.ci.jenkins.plugins.integration.builder.BuilderContext;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.FilePath;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
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
    public static FilePath lookupFile(FilePath searchLocation, String fileName, Logger logger) throws IOException, InterruptedException {
        if (searchLocation == null || !searchLocation.exists()) {
            logger.println("searchLocation not exist", true);
            return null;
        }
        FilePath[] fileList = searchLocation.list("**/" + fileName);
        if (fileList.length == 0) {
            logger.println("could not found matched file: %s", fileName);
            return null;
        }
        FilePath matchedFile = getTheClosestFile(fileList);
        String relativePath = toRelativePath(searchLocation, matchedFile);
        String fileContent = matchedFile.readToString();
        logger.println("looking for the file of %s found at %s", fileName, relativePath, fileContent);
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

    public static void writeUtf8String(String content, File file) {
        writeUtf8String(content, file.getPath());
    }

    public static void writeUtf8String(String content, String path) {
        writeString(content, path, StandardCharsets.UTF_8);
    }

    public static void writeString(String content, String path, Charset charset) {
        try {
            Path filePath = getFilePath(path);
            Files.write(filePath, content.getBytes(charset));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public static void writeUtf8Map(Map<String, String> variables, File file, String delimiter) {
        try {
            Path filePath = getFilePath(file.getPath());
            BufferedWriter writer = Files.newBufferedWriter(filePath);
            for (Entry<String, String> entry : variables.entrySet()) {
                String content = String.format("%s%s%s", entry.getKey(), delimiter, entry.getValue());
                writer.write(content);
                writer.newLine();
                writer.flush();
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public static List<String> readUtf8Lines(File file) {
        try {
            Path filePath = getFilePath(file.getPath());
            return Files.readAllLines(filePath);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Path getFilePath(String path) throws IOException {
        Path filePath = Paths.get(path);
        if (Files.notExists(filePath)) {
            Files.createFile(filePath);
        }
        return filePath;
    }

}
