package edu.wpi.first.tools;

import java.util.NoSuchElementException;

public enum NativePlatforms {
    WIN64("winx64"),
    WINARM64("winarm64"),
    MAC64("macx64"),
    MACARM64("macarm64"),
    LINUX64("linuxx64"),
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
}
