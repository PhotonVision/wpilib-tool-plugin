# WPILib Tool Plugin

A Gradle plugin that allows use of WPILib's libraries in Java programs. WPILib's Java libraries are backed by native libraries via JNI. Fat JARs are bad for file size, so this plugin is used to create platform-specific JARs with the native libraries for the specific OS packaged in each JAR. This used to be owned by WPILib, but has been forked by PhotonVision due to WPILib abandoning all Java-based tools, meaning they no longer have a use for it. PhotonVision, however, still requires it for use in building the PhotonVision app.

## Using the plugin

First, add this plugin and GradleRIO to your plugins block:

```groovy
plugins {
    id "org.wpilib.GradleRIO" version "2026.1.1" // Match this to the version of WPILib that you want to use
    id 'org.photonvision.WpilibTools' version '2.2.0-photon'
}
```

GradleRIO is not _strictly_ needed, but it contains all the versions of other WPILib dependencies that are used, like Jackson, so it's good to pull in so that your library versions are in sync.

Set the version of WPILib that you want (typically by using GradleRIO):

```groovy
// Can also be a dev version like 2027.0.0-alpha-3-157-gbcb5c5c. It's the same version used in Maven coordinates.
wpilibTools.deps.wpilibVersion = wpi.versions.wpilibVersion.get()
```

Next, set up a dependency configuration where all the WPILib native dependencies will go. This is important for the tool plugin to correctly assemble the native libraries together.

```groovy
def nativeConfigName = 'wpilibNatives'
def nativeConfig = configurations.create(nativeConfigName)

def nativeTasks = wpilibTools.createExtractionTasks {
    configurationName = nativeConfigName
}

nativeTasks.addToSourceSetResources(sourceSets.main)
```

Finally, specify the dependencies you want to use:

```groovy
nativeConfig.dependencies.add wpilibTools.deps.wpilib("wpimath")
nativeConfig.dependencies.add wpilibTools.deps.wpilib("wpinet")
nativeConfig.dependencies.add wpilibTools.deps.wpilib("wpiutil")
nativeConfig.dependencies.add wpilibTools.deps.wpilib("ntcore")
nativeConfig.dependencies.add wpilibTools.deps.wpilib("cscore")
nativeConfig.dependencies.add wpilibTools.deps.wpilibOpenCv("frc" + wpi.frcYear.get(), wpi.versions.opencvVersion.get())

dependencies {
    implementation wpilibTools.deps.wpilibJava("wpiutil")
    implementation wpilibTools.deps.wpilibJava("wpimath")
    implementation wpilibTools.deps.wpilibJava("wpinet")
    implementation wpilibTools.deps.wpilibJava("ntcore")
    implementation wpilibTools.deps.wpilibJava("cscore")
    implementation wpilibTools.deps.wpilibJava("cameraserver")
    implementation wpilibTools.deps.wpilibOpenCvJava("frc" + wpi.frcYear.get(), wpi.versions.opencvVersion.get())

    implementation group: "com.fasterxml.jackson.core", name: "jackson-annotations", version: wpi.versions.jacksonVersion.get()
    implementation group: "com.fasterxml.jackson.core", name: "jackson-core", version: wpi.versions.jacksonVersion.get()
    implementation group: "com.fasterxml.jackson.core", name: "jackson-databind", version: wpi.versions.jacksonVersion.get()

    implementation group: "org.ejml", name: "ejml-simple", version: wpi.versions.ejmlVersion.get()
    implementation group: "us.hebi.quickbuf", name: "quickbuf-runtime", version: wpi.versions.quickbufVersion.get();
}
```

You'll also want to set the classifier to `wpilibTools.platformMapper.wpilibClassifier`.

## Cross builds

If you're not compiling any native code, building a JAR for another platform can be done trivially by passing `-PArchOverride` and setting it to one of the platforms in [NativePlatforms.java](./src/main/java/org/photonvision/tools/NativePlatforms.java).

## Including another Gradle subproject with native libraries

Assuming you copied WPILib's Gradle files either from the monorepo or via vendor-template, you'll want to go into config.gradle, find the createComponentZipTasks function, replace

```groovy
project.artifacts {
    task
}
```

with

```groovy
// If the zip artifact matches the platform we're building for (either host or whatever the ArchOverride is), and it's a shared library built in release mode, add it to the list of artifacts the project exposes for use
if (key.contains(wpilibTools.getPlatformMapper().getWpilibClassifier()) && !key.contains("debug") && !key.contains("static")) {
    // For more information, see https://docs.gradle.org/current/userguide/variant_model.html and the outgoingVariants task
    project.artifacts.add("wpilibNatives", task)
} else {
    project.artifacts {
        task
    }
}
```

and in a dependencies block, add `wpilibNatives project(path: ':my-subproject', configuration: 'wpilibNatives')`. It **must** be in a dependencies block because that's the only place that `project` will work for using a subproject as a dependency. You'll also want to switch out `def nativeConfig = configurations.create(nativeConfigName)`

with

```groovy
configurations {
    wpilibNatives
}
```

since you need the configuration to exist before everything else. This also means you'll have to move all the dependencies into a dependencies block like so:

```groovy
dependencies {
    wpilibNatives project(path: ':photon-targeting', configuration: 'wpilibNatives')
    wpilibNatives wpilibTools.deps.wpilib("wpimath")
    wpilibNatives wpilibTools.deps.wpilib("wpinet")
    wpilibNatives wpilibTools.deps.wpilib("wpiutil")
    wpilibNatives wpilibTools.deps.wpilib("ntcore")
    wpilibNatives wpilibTools.deps.wpilib("cscore")
    wpilibNatives wpilibTools.deps.wpilib("apriltag")
    wpilibNatives wpilibTools.deps.wpilib("hal")
    wpilibNatives wpilibTools.deps.wpilibOpenCv("frc" + openCVYear, wpi.versions.opencvVersion.get())
}
```
