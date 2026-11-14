package io.github.hello09x.fakeplayer.core.platform;

/** Server threading model, detected before dependency injection starts. */
public enum ServerPlatform {
    PAPER,
    FOLIA;

    public static ServerPlatform detect() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer", false,
                    ServerPlatform.class.getClassLoader());
            return FOLIA;
        } catch (ClassNotFoundException ignored) {
            return PAPER;
        }
    }
}
