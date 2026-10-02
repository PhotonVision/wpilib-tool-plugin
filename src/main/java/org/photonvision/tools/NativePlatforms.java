package org.photonvision.tools;

import java.util.NoSuchElementException;

public enum NativePlatforms {
    WIN64("winx86-64"),
    WINARM64("winarm64"),
    MAC64("macx86-64"),
    MACARM64("macarm64"),
    LINUX64("linuxx86-64"),
    LINUXARM64("linuxarm64"),
    LINUXARM32("linuxarm32"),
    LINUXATHENA("linuxathena");

    private final String platformName;

    public String getPlatformName() {
        return platformName;
    }

    NativePlatforms(String platformName) {
        this.platformName = platformName;
    }

    public static NativePlatforms forName(String platformName) {
        for (NativePlatforms value : values()) {
            if (value.getPlatformName().equals(platformName)) {
                return value;
            }
        }
        throw new NoSuchElementException(platformName);
    }

    public boolean isArmLinux() {
        return this == LINUXARM64 || this == LINUXARM32;
    }

    public boolean isMac() {
        return this == MAC64 || this == MACARM64;
    }
}
