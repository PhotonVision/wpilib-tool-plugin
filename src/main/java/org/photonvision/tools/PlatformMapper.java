package org.photonvision.tools;

import javax.inject.Inject;
import org.gradle.api.Project;

public class PlatformMapper {
    private NativePlatforms currentPlatform;
    private final Project project;

    @Inject
    public PlatformMapper(Project project) {
        this.project = project;
        getCurrentPlatform();
    }

    public synchronized NativePlatforms getCurrentPlatform() {
        if (currentPlatform != null) {
            return currentPlatform;
        }

        Object override = project.findProperty("ArchOverride");

        if (override != null) {
            System.out.println("Overwriting platform to " + override);
            currentPlatform = NativePlatforms.forName((String) override);
            return currentPlatform;
        }

        String osName = System.getProperty("os.name").toLowerCase();
        String os = "";

        if (osName.contains("windows")) {
            os = "win";
        } else if (osName.contains("mac")) {
            os = "mac";
        } else if (osName.contains("linux")) {
            os = "linux";
        } else {
            throw new UnsupportedOperationException("Unknown OS: " + osName);
        }

        String osArch = System.getProperty("os.arch");
        String arch = "";

        if (osArch.contains("x86_64") || osArch.contains("amd64")) {
            arch = "x64";
        } else if (osArch.contains("x86")) {
            arch = "x32";
        } else if (osArch.contains("arm64") || osArch.contains("aarch64")) {
            arch = "arm64";
        } else if (osArch.contains("arm")) {
            arch = "arm32";
        } else {
            throw new UnsupportedOperationException(osArch);
        }

        currentPlatform = NativePlatforms.forName(os + arch);
        return currentPlatform;
    }

    public boolean isPlatformArm() {
        NativePlatforms platform = getCurrentPlatform();
        return platform == NativePlatforms.LINUXARM64 || platform == NativePlatforms.LINUXARM32;
    }

    public String getWpilibClassifier() {
        NativePlatforms platform = getCurrentPlatform();
        return switch (platform) {
            case WIN64 -> "windowsx86-64";
            case WINARM64 -> "windowsarm64";
            case MAC64 -> "osxuniversal";
            case MACARM64 -> "osxuniversal";
            case LINUX64 -> "linuxx86-64";
            case LINUXARM64 -> "linuxarm64";
            case LINUXARM32 -> "linuxarm32";
            case LINUXATHENA -> "linuxathena";
        };
    }

    public String getPlatformPath() {
        NativePlatforms platform = getCurrentPlatform();
        return switch (platform) {
            case WIN64 -> "windows/x86-64";
            case WINARM64 -> "windows/arm64";
            case MAC64 -> "osx/universal";
            case MACARM64 -> "osx/universal";
            case LINUX64 -> "linux/x86-64";
            case LINUXARM64 -> "linux/arm64";
            case LINUXARM32 -> "linux/arm32";
            case LINUXATHENA -> "linux/athena";
        };
    }
}
