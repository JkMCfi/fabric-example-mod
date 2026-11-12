package dev.orbit.music;

public record Track(String title, String artist, String album, String path) {
    public static final Track UNKNOWN = new Track("Unknown Title", "Unknown Artist", "Unknown Album", "");

    public static Track fallback(String path) {
        return new Track("New Orbit Track", "Unknown Artist", "ORBIT", path);
    }
}
