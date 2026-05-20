package org.photonvision.tools;

import static org.gradle.api.artifacts.type.ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE;
import static org.gradle.api.artifacts.type.ArtifactTypeDefinition.DIRECTORY_TYPE;
import static org.gradle.api.artifacts.type.ArtifactTypeDefinition.JAR_TYPE;
import static org.gradle.api.artifacts.type.ArtifactTypeDefinition.ZIP_TYPE;

import java.net.URL;
import java.util.Map;
import org.gradle.api.Plugin;
import org.gradle.api.Project;

public class WpilibTools implements Plugin<Project> {
    private static final String VERSIONING_HELPER_RESOURCE = "versioningHelper.gradle";

    @Override
    public void apply(Project project) {
        applyVersioningHelper(project);

        project
                .getDependencies()
                .registerTransform(
                        UnzipTransform.class,
                        transform -> {
                            transform.getFrom().attribute(ARTIFACT_TYPE_ATTRIBUTE, ZIP_TYPE);
                            transform.getTo().attribute(ARTIFACT_TYPE_ATTRIBUTE, DIRECTORY_TYPE);
                        });

        project
                .getDependencies()
                .registerTransform(
                        UnzipTransform.class,
                        transform -> {
                            transform.getFrom().attribute(ARTIFACT_TYPE_ATTRIBUTE, JAR_TYPE);
                            transform.getTo().attribute(ARTIFACT_TYPE_ATTRIBUTE, DIRECTORY_TYPE);
                        });

        project.getExtensions().create("wpilibTools", WpilibToolsExtension.class, project);
    }

    private void applyVersioningHelper(Project project) {
        URL helperScriptUrl =
                WpilibTools.class.getClassLoader().getResource(VERSIONING_HELPER_RESOURCE);
        if (helperScriptUrl == null) {
            project.getLogger().warn("Unable to find {} in plugin resources", VERSIONING_HELPER_RESOURCE);
            return;
        }

        project.apply(Map.of("from", project.getResources().getText().fromUri(helperScriptUrl)));
    }
}
