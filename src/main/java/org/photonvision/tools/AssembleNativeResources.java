package org.photonvision.tools;

import javax.inject.Inject;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

public class AssembleNativeResources extends DefaultTask {
    private final ConfigurableFileCollection inputFiles;
    private final DirectoryProperty outputDirectory;

    @Inject
    public AssembleNativeResources() {
        ObjectFactory factory = getProject().getObjects();
        inputFiles = getProject().files();
        outputDirectory = factory.directoryProperty();
    }

    @InputFiles
    public ConfigurableFileCollection getInputFiles() {
        return inputFiles;
    }

    @OutputDirectory
    public DirectoryProperty getOutputDirectory() {
        return outputDirectory;
    }

    @TaskAction
    public void execute() {
        getProject()
                .sync(
                        copySpec -> {
                            copySpec.from(inputFiles);
                            copySpec.into(outputDirectory);
                        });
    }
}
