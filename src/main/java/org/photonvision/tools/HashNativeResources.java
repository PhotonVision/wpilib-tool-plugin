package org.photonvision.tools;

import com.google.gson.GsonBuilder;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
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

    /**
     * Architecture-specific information containing file hashes for a specific CPU architecture (e.g.,
     * x86-64, arm64).
     */
    public record ArchInfo(Map<String, String> fileHashes) {}

    /**
     * Platform-specific information containing architectures for a specific OS platform (e.g., linux,
     * windows).
     */
    public record PlatformInfo(Map<String, ArchInfo> architectures) {}

    /** Overall resource information to be serialized */
    public record ResourceInformation(
            // Combined MD5 hash of all native resource files
            String hash,
            // Platform-specific native libraries organized by platform then architecture
            Map<String, PlatformInfo> platforms,
            // List of supported versions for these native resources
            List<String> versions) {}

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

        Map<String, PlatformInfo> platforms = new HashMap<>();

        for (File file : directory.getAsFileTree()) {
            if (!file.isFile()) {
                continue;
            }

            Path path = inputPath.relativize(file.toPath());

            // Compute individual file hash
            MessageDigest fileHash = MessageDigest.getInstance("MD5");
            try (var dis =
                    new DigestInputStream(
                            new DigestInputStream(new BufferedInputStream(new FileInputStream(file)), fileHash),
                            combinedHash)) {
                dis.transferTo(OutputStream.nullOutputStream());
            }

            String platform = path.getName(0).toString();
            String arch = path.getName(1).toString();

            String strPath = "/" + path.toString().replace("\\", "/");
            String hexFileHash = HexFormat.of().formatHex(fileHash.digest());

            PlatformInfo platformInfo = platforms.get(platform);
            if (platformInfo == null) {
                Map<String, String> fileHashes = new HashMap<>();
                fileHashes.put(strPath, hexFileHash);
                Map<String, ArchInfo> architectures = new HashMap<>();
                architectures.put(arch, new ArchInfo(fileHashes));
                platforms.put(platform, new PlatformInfo(architectures));
            } else {
                Map<String, ArchInfo> architectures = platformInfo.architectures();
                ArchInfo archInfo = architectures.get(arch);
                if (archInfo == null) {
                    Map<String, String> fileHashes = new HashMap<>();
                    fileHashes.put(strPath, hexFileHash);
                    architectures.put(arch, new ArchInfo(fileHashes));
                } else {
                    archInfo.fileHashes().put(strPath, hexFileHash);
                }
            }
        }

        String hash = HexFormat.of().formatHex(combinedHash.digest());

        List<String> versions = Files.readAllLines(versionsInput.get().getAsFile().toPath());
        ResourceInformation output = new ResourceInformation(hash, platforms, versions);

        GsonBuilder builder = new GsonBuilder();
        builder.setPrettyPrinting();
        var json = builder.create().toJson(output);
        Files.writeString(hashFile.get().getAsFile().toPath(), json, Charset.defaultCharset());
    }
}
