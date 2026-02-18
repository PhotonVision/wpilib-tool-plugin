package org.photonvision.tools;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;

import javax.inject.Inject;

import com.google.gson.GsonBuilder;

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
    
    record PlatformData(Map<String, Map<String, String>> architectures) {}
    
    record HashOutput(Map<String, PlatformData> platforms, String hash, java.util.List<String> versions) {}
    
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
        MessageDigest combinedHash = MessageDigest.getInstance("MD5");

        Directory directory = inputDirectory.get();

        Path inputPath = directory.getAsFile().toPath();

        Map<String, PlatformData> platforms = new HashMap<>();

        byte[] buffer = new byte[0xFFFF];
        int readBytes = 0;

        for (File file : directory.getAsFileTree()) {
            if (!file.isFile()) {
                continue;
            }

            Path path = inputPath.relativize(file.toPath());

            // Compute individual file hash
            MessageDigest fileHash = MessageDigest.getInstance("MD5");
            try (FileInputStream is = new FileInputStream(file)) {
                while ((readBytes = is.read(buffer)) != -1) {
                    fileHash.update(buffer, 0, readBytes);
                    combinedHash.update(buffer, 0, readBytes);
                }
            }

            String platform = path.getName(0).toString();
            String arch = path.getName(1).toString();

            String strPath = "/" + path.toString().replace("\\", "/");
            String hexFileHash = HexFormat.of().formatHex(fileHash.digest());

            PlatformData platformData = platforms.get(platform);
            if (platformData == null) {
                Map<String, String> archFiles = new HashMap<>();
                archFiles.put(strPath, hexFileHash);
                Map<String, Map<String, String>> architectures = new HashMap<>();
                architectures.put(arch, archFiles);
                platforms.put(platform, new PlatformData(architectures));
            } else {
                Map<String, Map<String, String>> architectures = platformData.architectures();
                Map<String, String> archFiles = architectures.get(arch);
                if (archFiles == null) {
                    archFiles = new HashMap<>();
                    archFiles.put(strPath, hexFileHash);
                    architectures.put(arch, archFiles);
                } else {
                    archFiles.put(strPath, hexFileHash);
                }
            }
        }

        var versions = Files.readAllLines(versionsInput.get().getAsFile().toPath());

        String hash = HexFormat.of().formatHex(combinedHash.digest());
        HashOutput output = new HashOutput(platforms, hash, versions);
        
        GsonBuilder builder = new GsonBuilder();
        builder.setPrettyPrinting();
        var json = builder.create().toJson(output);
        Files.writeString(hashFile.get().getAsFile().toPath(), json, Charset.defaultCharset());
    }
}
