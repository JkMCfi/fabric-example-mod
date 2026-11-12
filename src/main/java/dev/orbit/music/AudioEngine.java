package dev.orbit.music;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

public final class AudioEngine {
    public enum State {
        IDLE,
        PLAYING,
        PAUSED,
        STOPPED
    }

    private final Deque<Track> queue = new ArrayDeque<>();
    private Track currentTrack;
    private State state = State.IDLE;
    private float volume = 0.8F;
    private long playbackPositionMs = 0L;

    public CompletableFuture<Track> loadTrack(Path path) {
        return CompletableFuture.supplyAsync(() -> {
            if (path == null) {
                return Track.UNKNOWN;
            }

            String displayName = path.getFileName() == null ? "Unknown Track" : path.getFileName().toString();
            return new Track(displayName, "Unknown Artist", "ORBIT", path.toString());
        });
    }

    public void enqueue(Track track) {
        if (track != null) {
            queue.offer(track);
        }
    }

    public void play() {
        if (currentTrack == null && !queue.isEmpty()) {
            currentTrack = queue.poll();
        }
        if (currentTrack != null) {
            state = State.PLAYING;
        }
    }

    public void pause() {
        if (state == State.PLAYING) {
            state = State.PAUSED;
        }
    }

    public void stop() {
        state = State.STOPPED;
        playbackPositionMs = 0L;
    }

    public void next() {
        if (queue.isEmpty()) {
            currentTrack = null;
            state = State.IDLE;
            return;
        }
        currentTrack = queue.poll();
        state = State.PLAYING;
    }

    public void previous() {
        if (currentTrack != null) {
            queue.addFirst(currentTrack);
        }
        currentTrack = queue.poll();
        state = State.PLAYING;
    }

    public void setVolume(float volume) {
        this.volume = Math.max(0.0F, Math.min(1.0F, volume));
    }

    public float getVolume() {
        return volume;
    }

    public Track getCurrentTrack() {
        return currentTrack;
    }

    public State getState() {
        return state;
    }

    public long getPlaybackPositionMs() {
        return playbackPositionMs;
    }

    public void setPlaybackPositionMs(long playbackPositionMs) {
        this.playbackPositionMs = Math.max(0L, playbackPositionMs);
    }

    public void tick() {
        if (state == State.PLAYING) {
            playbackPositionMs += 100L;
        }
    }

    public boolean isPlaying() {
        return state == State.PLAYING;
    }

    @Override
    public String toString() {
        return "AudioEngine{state=" + state + ", currentTrack=" + Objects.toString(currentTrack, "null") + ", volume=" + volume + '}';
    }
}
