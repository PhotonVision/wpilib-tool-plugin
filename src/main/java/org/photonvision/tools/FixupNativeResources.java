package org.photonvision.tools;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import javax.inject.Inject;
import org.gradle.api.Action;
import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.file.Directory;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.SyncSpec;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.work.DisableCachingByDefault;

@DisableCachingByDefault(because = "Copying doesn't need caching")
public class FixupNativeResources extends DefaultTask {
    private DirectoryProperty inputDirectory;
    private DirectoryProperty outputDirectory;

    @PathSensitive(PathSensitivity.RELATIVE)
    @InputDirectory
    public DirectoryProperty getInputDirectory() {
        return inputDirectory;
    }

    @OutputDirectory
    public DirectoryProperty getOutputDirectory() {
        return outputDirectory;
    }

    @Inject
    public FixupNativeResources() {
        ObjectFactory factory = getProject().getObjects();
        inputDirectory = factory.directoryProperty();
        outputDirectory = factory.directoryProperty();
    }

    @TaskAction
    public void execute() {
        Project project = getProject();

        getProject()
                .sync(
                        new Action<SyncSpec>() {

                            @Override
                            public void execute(SyncSpec copySpec) {
                                copySpec.from(inputDirectory);
                                copySpec.into(outputDirectory);
                            }
                        });

        if (OperatingSystem.current().isMacOsX()) {
            // Set rpath correctly in all binaries
            Directory directory = outputDirectory.get();

            List<String> filesToFixup = new ArrayList<>();

            for (File file : directory.getAsFileTree()) {
                if (!file.isFile()) {
                    continue;
                }

                // Strip binaries
                project
                        .getProviders()
                        .exec(ex -> ex.commandLine("strip", "-x", "-S", file.toString()))
                        .getResult()
                        .get();

                // Get list of all dependent binaries
                var exec =
                        project.getProviders().exec(ex -> ex.commandLine("otool", "-L", file.toString()));

                filesToFixup.clear();

                String outputStr = exec.getStandardOutput().getAsText().get();
                String currentFileName = file.getName();

                // Search dependencies list, look for any non absolute path resolved libraries
                try (Scanner stringScanner = new Scanner(outputStr)) {
                    String currentLine = null;
                    while (stringScanner.hasNextLine()) {
                        currentLine = stringScanner.nextLine();
                        if (currentLine.contains(currentFileName)) {
                            continue;
                        }

                        String trimmedLine = currentLine.trim();

                        if (trimmedLine.startsWith("/")) {
                            continue;
                        }

                        String libName = trimmedLine.split(" ")[0];
                        filesToFixup.add(libName);
                    }
                }

                // Fixup any dependencies
                for (String fixupFile : filesToFixup) {
                    String outputName = fixupFile;
                    // Handle the special case of opencv libraries already containing rpath
                    if (outputName.startsWith("@rpath/")) {
                        outputName = outputName.substring("@rpath/".length());
                    }
                    String outputNameFinal = outputName;
                    project
                            .getProviders()
                            .exec(
                                    ex ->
                                            ex.commandLine(
                                                    "install_name_tool",
                                                    "-change",
                                                    fixupFile,
                                                    "@loader_path/" + outputNameFinal,
                                                    file.toString()))
                            .getResult()
                            .get();
                }

                // Overwrite signature because they were invalidated by strip and
                // install-name-tool.
                project
                        .getProviders()
                        .exec(ex -> ex.commandLine("codesign", "--force", "--sign", "-", file.toString()))
                        .getResult()
                        .get();
            }
        }
    }
}
