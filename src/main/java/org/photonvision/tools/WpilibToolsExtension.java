package org.photonvision.tools;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import javax.inject.Inject;

import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskProvider;

public class WpilibToolsExtension {
    private final Project project;
    private final PlatformMapper platformMapper;
    private final NativeConfigurator nativeConfigurator;

    @Inject
    public WpilibToolsExtension(Project project) {
        this.project = project;
        this.platformMapper = new PlatformMapper(project);
        this.nativeConfigurator = new NativeConfigurator(platformMapper, project.getDependencies());
    }

    public PlatformMapper getPlatformMapper() {
        return platformMapper;
    }

    public NativeConfigurator getDeps() {
        return nativeConfigurator;
    }

    public NativePlatforms getCurrentPlatform() {
        return platformMapper.getCurrentPlatform();
    }

    public class NewTaskSetConfiguration {
        public String taskPostfix;
        public String configurationName;
        public DirectoryProperty rootTaskFolder;
        public String resourceFileName;
    }

    public class NewTaskSet {
        public TaskProvider<ExtractConfiguration> extractConfiguration;
        public TaskProvider<FixupNativeResources> fixup;
        public TaskProvider<HashNativeResources> hash;
        public TaskProvider<AssembleNativeResources> assemble;

        public void addToSourceSetResources(SourceSet sourceSet) {
            Map<String, Object> map = new HashMap<>();
            map.put("builtBy", assemble);
            sourceSet.getOutput().dir(map, assemble);
        }
    }

    public NewTaskSet createExtractionTasks(Action<NewTaskSetConfiguration> configuration) {
        NewTaskSetConfiguration newConfig = new NewTaskSetConfiguration();
        newConfig.rootTaskFolder = project.getObjects().directoryProperty();
        configuration.execute(newConfig);

        newConfig.taskPostfix = Objects.requireNonNullElse(newConfig.taskPostfix, "Main");
        if (!newConfig.rootTaskFolder.isPresent()) {
            newConfig.rootTaskFolder.set(project.getLayout().getBuildDirectory().dir("NativeMain"));
        }
        newConfig.resourceFileName = Objects.requireNonNullElse(newConfig.resourceFileName, "ResourceInformation.json");

        final NewTaskSet retSet = new NewTaskSet();
        retSet.extractConfiguration = project.getTasks().register("extractNativeConfiguration" + newConfig.taskPostfix, ExtractConfiguration.class);
        retSet.extractConfiguration.configure(c -> {
            if (newConfig.configurationName != null) {
                c.addConfigurationToView(newConfig.configurationName);
            }
            c.getOutputDirectory().set(newConfig.rootTaskFolder.dir("RawRuntimeLibs"));
            c.getVersionsFile().set(newConfig.rootTaskFolder.file("RuntimeLibVersions.txt"));
        });

        retSet.fixup = project.getTasks().register("fixupNativeResources" + newConfig.taskPostfix, FixupNativeResources.class);
        retSet.fixup.configure(c -> {
            c.dependsOn(retSet.extractConfiguration);
            c.getInputDirectory().set(retSet.extractConfiguration.get().getOutputDirectory());
            c.getOutputDirectory().set(newConfig.rootTaskFolder.dir("RuntimeLibs"));
        });

        retSet.hash = project.getTasks().register("hashNativeResources" + newConfig.taskPostfix, HashNativeResources.class);
        retSet.hash.configure(c -> {
            c.dependsOn(retSet.fixup);
            c.dependsOn(retSet.extractConfiguration);
            c.getInputDirectory().set(retSet.fixup.get().getOutputDirectory());
            c.getHashFile().set(newConfig.rootTaskFolder.file(newConfig.resourceFileName));
            c.getVersionsInput().set(retSet.extractConfiguration.get().getVersionsFile());
        });

        retSet.assemble = project.getTasks().register("assembleNativeResources" + newConfig.taskPostfix, AssembleNativeResources.class);
        retSet.assemble.configure(c -> {
            c.from(retSet.fixup.get());
            c.from(retSet.hash.get());
            c.into(newConfig.rootTaskFolder.dir("AssembledResources"));
        });
        return retSet;
    }
}
