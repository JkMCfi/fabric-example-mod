package dev.stellar;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import com.sun.net.httpserver.HttpServer;
import net.minecraft.client.MinecraftClient;

import java.awt.Desktop;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Spotify PKCE, playback polling, transport/search, LRCLIB sync and local config. */
public final class SpotifyService {
    private static final String REDIRECT_URI = "http://127.0.0.1:8888/callback";
    private static final String API = "https://api.spotify.com/v1";
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(12)).build();
    private static final Pattern LRC_TIME = Pattern.compile("\\[(\\d{1,2}):(\\d{2})(?:\\.(\\d{1,3}))?]\\s*(.*)");
    private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "Stellar-Music"); thread.setDaemon(true); return thread;
    });
    private final AtomicReference<Track> current = new AtomicReference<>(Track.empty());
    private final AtomicReference<String> status = new AtomicReference<>("Not connected");
    private volatile String clientId = "";
    private volatile String accessToken = "";
    private volatile String refreshToken = "";
    private volatile long expiresAt;
    private volatile String lyricsTrackId = "";
    private volatile TreeMap<Long, String> lyrics = new TreeMap<>();
    private volatile boolean repeat;
    private volatile long lastPoll;
    private final Path configPath = MinecraftClient.getInstance().runDirectory.toPath().resolve("config/stellar.json");

    public record Track(String id, String title, String artist, String album, String imageUrl,
                        long durationMs, long progressMs, boolean playing) {
        static Track empty() { return new Track("", "Nothing playing", "Connect Spotify to begin", "", "", 1, 0, false); }
        Track withProgress(long progress) { return new Track(id, title, artist, album, imageUrl, durationMs, progress, playing); }
    }

    public void start() {
        loadConfig();
        executor.scheduleAtFixedRate(this::pollSafely, 1, 2, TimeUnit.SECONDS);
    }
    public Track track() { return current.get(); }
    public String status() { return status.get(); }
    public String clientId() { return clientId; }
    public void setClientId(String value) { clientId = value == null ? "" : value.trim(); saveConfig(); }
    public boolean connected() { return !accessToken.isBlank() && expiresAt > System.currentTimeMillis(); }
    public Map.Entry<Long, String> lyricAt(long progress) {
        TreeMap<Long, String> snapshot = lyrics;
        Map.Entry<Long, String> line = snapshot.floorEntry(progress);
        return line == null ? null : line;
    }
    public TreeMap<Long, String> lyrics() { return new TreeMap<>(lyrics); }

    public void connect() {
        if (clientId.isBlank()) { status.set("Enter your Spotify Client ID in Settings first"); return; }
        executor.execute(() -> {
            try {
                byte[] bytes = new byte[32]; new SecureRandom().nextBytes(bytes);
                String verifier = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
                String challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(
                        MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));
                String state = randomToken();
                HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 8888), 0);
                server.createContext("/callback", exchange -> {
                    String query = exchange.getRequestURI().getRawQuery();
                    String code = queryValue(query, "code");
                    String returnedState = queryValue(query, "state");
                    byte[] message = (code != null && state.equals(returnedState)
                            ? "Spotify connected. You can return to Minecraft." : "Spotify authorization failed. Return to Minecraft.")
                            .getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
                    exchange.sendResponseHeaders(code == null ? 400 : 200, message.length);
                    exchange.getResponseBody().write(message); exchange.close(); server.stop(0);
                    if (code != null && state.equals(returnedState)) exchangeCode(code, verifier);
                    else status.set("Spotify authorization failed");
                });
                server.setExecutor(executor); server.start();
                String scopes = "user-read-playback-state user-read-currently-playing user-modify-playback-state";
                String auth = "https://accounts.spotify.com/authorize?response_type=code&client_id=" + enc(clientId)
                        + "&redirect_uri=" + enc(REDIRECT_URI) + "&scope=" + enc(scopes)
                        + "&code_challenge_method=S256&code_challenge=" + enc(challenge) + "&state=" + enc(state);
                Desktop.getDesktop().browse(URI.create(auth)); status.set("Waiting for Spotify authorization…");
            } catch (Exception exception) { status.set("Could not start Spotify sign-in: " + shortError(exception)); }
        });
    }

    private void exchangeCode(String code, String verifier) {
        try {
            String body = "grant_type=authorization_code&code=" + enc(code) + "&redirect_uri=" + enc(REDIRECT_URI)
                    + "&client_id=" + enc(clientId) + "&code_verifier=" + enc(verifier);
            HttpResponse<String> response = form("https://accounts.spotify.com/api/token", body);
            JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
            accessToken = json.get("access_token").getAsString();
            if (json.has("refresh_token")) refreshToken = json.get("refresh_token").getAsString();
            expiresAt = System.currentTimeMillis() + json.get("expires_in").getAsLong() * 1000L;
            status.set("Connected to Spotify"); saveConfig(); pollSafely();
        } catch (Exception exception) { status.set("Spotify token exchange failed: " + shortError(exception)); }
    }

    private HttpResponse<String> form(String url, String body) throws Exception {
        return HTTP.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }

    private void pollSafely() {
        try {
            if (!accessToken.isBlank() && expiresAt - System.currentTimeMillis() < 60_000 && !refreshToken.isBlank()) refresh();
            if (!connected()) return;
            HttpResponse<String> response = request("GET", API + "/me/player", null);
            lastPoll = System.currentTimeMillis();
            if (response.statusCode() == 204) { current.set(Track.empty()); return; }
            if (response.statusCode() == 401) { status.set("Spotify authorization expired — reconnect in Settings"); return; }
            if (response.statusCode() < 200 || response.statusCode() >= 300) return;
            JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
            JsonObject item = root.has("item") && root.get("item").isJsonObject() ? root.getAsJsonObject("item") : null;
            if (item == null) { current.set(Track.empty()); return; }
            String id = string(item, "id", "");
            String title = string(item, "name", "Unknown track");
            JsonArray artists = item.getAsJsonArray("artists");
            StringBuilder artist = new StringBuilder();
            if (artists != null) for (JsonElement element : artists) {
                if (artist.length() > 0) artist.append(", "); artist.append(string(element.getAsJsonObject(), "name", ""));
            }
            JsonObject album = item.getAsJsonObject("album"); String image = "";
            if (album != null && album.has("images") && album.getAsJsonArray("images").size() > 0)
                image = string(album.getAsJsonArray("images").get(0).getAsJsonObject(), "url", "");
            long duration = item.has("duration_ms") ? item.get("duration_ms").getAsLong() : 1;
            long progress = root.has("progress_ms") && !root.get("progress_ms").isJsonNull() ? root.get("progress_ms").getAsLong() : 0;
            boolean playing = root.has("is_playing") && root.get("is_playing").getAsBoolean();
            Track old = current.get();
            current.set(new Track(id, title, artist.toString(), string(album, "name", ""), image, duration, progress, playing));
            if (!id.isBlank() && !id.equals(lyricsTrackId)) { lyricsTrackId = id; fetchLyrics(title, artist.toString(), duration); }
            status.set("Connected to Spotify");
        } catch (Exception ignored) { }
    }

    private void refresh() throws Exception {
        String body = "grant_type=refresh_token&refresh_token=" + enc(refreshToken) + "&client_id=" + enc(clientId);
        JsonObject json = JsonParser.parseString(form("https://accounts.spotify.com/api/token", body).body()).getAsJsonObject();
        accessToken = json.get("access_token").getAsString();
        if (json.has("refresh_token")) refreshToken = json.get("refresh_token").getAsString();
        expiresAt = System.currentTimeMillis() + json.get("expires_in").getAsLong() * 1000L; saveConfig();
    }

    public void playPause() { Track t = track(); spotify("PUT", API + "/me/player/" + (t.playing() ? "pause" : "play"), null); }
    public void next() { spotify("POST", API + "/me/player/next", null); }
    public void previous() { spotify("POST", API + "/me/player/previous", null); }
    public void toggleRepeat() { repeat = !repeat; spotify("PUT", API + "/me/player/repeat?state=" + (repeat ? "track" : "off"), null); }
    public void seek(long positionMs) { long bounded = Math.max(0, Math.min(track().durationMs(), positionMs)); spotify("PUT", API + "/me/player/seek?position_ms=" + bounded, null); }
    public void playTrack(String trackUri) {
        JsonObject body = new JsonObject(); JsonArray uris = new JsonArray(); uris.add(trackUri); body.add("uris", uris);
        spotify("PUT", API + "/me/player/play", body.toString());
    }
    public void search(String query, java.util.function.Consumer<String> result) {
        executor.execute(() -> {
            try {
                HttpResponse<String> response = request("GET", API + "/search?type=track&limit=1&q=" + enc(query), null);
                if (response.statusCode() != 200) { result.accept("Spotify search failed (" + response.statusCode() + ")"); return; }
                JsonArray tracks = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("tracks").getAsJsonArray("items");
                if (tracks.isEmpty()) { result.accept("No matching track found"); return; }
                JsonObject track = tracks.get(0).getAsJsonObject(); String uri = string(track, "uri", "");
                playTrack(uri); result.accept("Playing search result: " + string(track, "name", ""));
            } catch (Exception exception) { result.accept("Search failed: " + shortError(exception)); }
        });
    }
    private void spotify(String method, String url, String body) {
        executor.execute(() -> {
            try { HttpResponse<String> response = request(method, url, body);
                if (response.statusCode() == 401) status.set("Spotify token expired — reconnect in Settings");
                else if (response.statusCode() >= 300) status.set("Spotify request returned " + response.statusCode());
                else pollSafely();
            } catch (Exception exception) { status.set("Spotify request failed: " + shortError(exception)); }
        });
    }
    private HttpResponse<String> request(String method, String url, String body) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(15))
                .header("Authorization", "Bearer " + accessToken);
        if (body == null) builder.method(method, HttpRequest.BodyPublishers.noBody());
        else builder.header("Content-Type", "application/json").method(method, HttpRequest.BodyPublishers.ofString(body));
        return HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private void fetchLyrics(String title, String artist, long durationMs) {
        executor.execute(() -> {
            try {
                String url = "https://lrclib.net/api/get?track_name=" + enc(title) + "&artist_name=" + enc(artist)
                        + "&duration=" + Math.round(durationMs / 1000.0);
                HttpResponse<String> response = HTTP.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(12))
                        .header("User-Agent", "StellarMinecraftMod/1.0 (lyrics sync)").GET().build(), HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 200) { lyrics = new TreeMap<>(); return; }
                JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                String lrc = json.has("syncedLyrics") && !json.get("syncedLyrics").isJsonNull() ? json.get("syncedLyrics").getAsString() : "";
                TreeMap<Long, String> parsed = parseLrc(lrc); lyrics = parsed;
            } catch (Exception ignored) { lyrics = new TreeMap<>(); }
        });
    }
    public static TreeMap<Long, String> parseLrc(String source) {
        TreeMap<Long, String> out = new TreeMap<>(); if (source == null) return out;
        for (String line : source.split("\\R")) { Matcher matcher = LRC_TIME.matcher(line); if (!matcher.matches()) continue;
            long min = Long.parseLong(matcher.group(1)), sec = Long.parseLong(matcher.group(2));
            String fraction = matcher.group(3); long ms = fraction == null ? 0 : Long.parseLong((fraction + "00").substring(0, 3));
            out.put((min * 60 + sec) * 1000 + ms, matcher.group(4).trim());
        }
        return out;
    }
    public void importLrc(Path path) {
        try { lyrics = parseLrc(Files.readString(path)); lyricsTrackId = current.get().id(); status.set("Imported lyrics: " + path.getFileName()); }
        catch (IOException exception) { status.set("Could not read LRC file: " + shortError(exception)); }
    }
    private void loadConfig() {
        try { if (!Files.exists(configPath)) return; JsonObject json = JsonParser.parseString(Files.readString(configPath)).getAsJsonObject();
            clientId = string(json, "clientId", ""); accessToken = string(json, "accessToken", "");
            refreshToken = string(json, "refreshToken", ""); expiresAt = json.has("expiresAt") ? json.get("expiresAt").getAsLong() : 0;
        } catch (Exception ignored) { }
    }
    private synchronized void saveConfig() {
        try { Files.createDirectories(configPath.getParent()); JsonObject json = new JsonObject(); json.addProperty("clientId", clientId);
            json.addProperty("accessToken", accessToken); json.addProperty("refreshToken", refreshToken); json.addProperty("expiresAt", expiresAt);
            Files.writeString(configPath, json.toString());
        } catch (IOException exception) { status.set("Could not save Stellar config: " + shortError(exception)); }
    }
    private static String queryValue(String query, String key) {
        if (query == null) return null; for (String part : query.split("&")) { String[] pair = part.split("=", 2);
            if (pair.length == 2 && pair[0].equals(key)) return java.net.URLDecoder.decode(pair[1], StandardCharsets.UTF_8); } return null;
    }
    private static String randomToken() { byte[] bytes = new byte[24]; new SecureRandom().nextBytes(bytes); return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
    private static String enc(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
    private static String string(JsonObject object, String key, String fallback) { return object != null && object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : fallback; }
    private static String shortError(Exception exception) { String message = exception.getMessage(); return message == null ? exception.getClass().getSimpleName() : message; }
}
