package org.photonvision.tools;

import com.google.gson.GsonBuilder;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import org.codehaus.groovy.runtime.EncodingGroovyMethods;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.Directory;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

public class HashNativeResources extends DefaultTask {
    private final DirectoryProperty inputDirectory;
    private final RegularFileProperty hashFile;
    private final RegularFileProperty versionsInput;

    @InputDirectory
    public DirectoryProperty getInputDirectory() {
        return inputDirectory;
    }

    @OutputFile
    public RegularFileProperty getHashFile() {
        return hashFile;
    }

    @InputFile
    public RegularFileProperty getVersionsInput() {
        return versionsInput;
    }

    @Inject
    public HashNativeResources() {
        ObjectFactory factory = getProject().getObjects();
        inputDirectory = factory.directoryProperty();
        hashFile = factory.fileProperty();
        versionsInput = factory.fileProperty();
    }

    @TaskAction
    public void execute() throws NoSuchAlgorithmException, IOException {
        MessageDigest hash = MessageDigest.getInstance("MD5");

        Directory directory = inputDirectory.get();

        Path inputPath = directory.getAsFile().toPath();

        Map<String, Object> platforms = new HashMap<>();

        byte[] buffer = new byte[0xFFFF];
        int readBytes = 0;

        for (File file : directory.getAsFileTree()) {
            if (!file.isFile()) {
                continue;
            }

            Path path = inputPath.relativize(file.toPath());

            try (FileInputStream is = new FileInputStream(file)) {
                while ((readBytes = is.read(buffer)) != -1) {
                    hash.update(buffer, 0, readBytes);
                }
            }

            String platform = path.getName(0).toString();
            String arch = path.getName(1).toString();

            String strPath = "/" + path.toString().replace("\\", "/");

            @SuppressWarnings("unchecked") // This will always be the correct type
            Map<String, List<String>> platformMap = (Map<String, List<String>>) platforms.get(platform);
            if (platformMap == null) {
                platformMap = new HashMap<>();
                List<String> archFiles = new ArrayList<>();
                archFiles.add(strPath);
                platformMap.put(arch, archFiles);
                platforms.put(platform, platformMap);
            } else {
                List<String> archFiles = platformMap.get(arch);
                if (archFiles == null) {
                    archFiles = new ArrayList<>();
                    archFiles.add(strPath);
                    platformMap.put(arch, archFiles);
                } else {
                    archFiles.add(strPath);
                }
            }
        }

        var versions = Files.readAllLines(versionsInput.get().getAsFile().toPath());

        platforms.put("hash", EncodingGroovyMethods.encodeHex(hash.digest()).toString());
        platforms.put("versions", versions);
        GsonBuilder builder = new GsonBuilder();
        builder.setPrettyPrinting();
        var json = builder.create().toJson(platforms);
        Files.writeString(hashFile.get().getAsFile().toPath(), json, Charset.defaultCharset());
    }
}
