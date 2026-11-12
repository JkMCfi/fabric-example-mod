package dev.orbit.skies;

public final class SkyRenderer {
    private static SkyType activeSky = SkyType.ORBIT_ORIGINAL;

    private SkyRenderer() {
    }

    public static SkyType getActiveSky() {
        return activeSky;
    }

    public static void setActiveSky(SkyType activeSky) {
        SkyRenderer.activeSky = activeSky == null ? SkyType.ORBIT_ORIGINAL : activeSky;
    }

    public static String getLabel() {
        return activeSky.name().replace('_', ' ');
    }
}
