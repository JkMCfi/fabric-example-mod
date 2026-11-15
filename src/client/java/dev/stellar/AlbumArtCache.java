package dev.stellar;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Downloads Spotify album art off-thread and registers textures on Minecraft's render thread. */
public final class AlbumArtCache {
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "Stellar-Album-Art"); thread.setDaemon(true); return thread;
    });
    private static final Map<String, Identifier> READY = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> PENDING = new ConcurrentHashMap<>();
    private static final int MAX_BYTES = 8 * 1024 * 1024;

    private AlbumArtCache() { }

    /** Returns null until the image is fetched and uploaded. Only Spotify's public image host is allowed. */
    public static Identifier get(String url) {
        if (url == null || url.isBlank()) return null;
        Identifier ready = READY.get(url);
        if (ready != null) return ready;
        if (PENDING.putIfAbsent(url, Boolean.TRUE) == null) WORKER.execute(() -> download(url));
        return null;
    }

    private static void download(String url) {
        try {
            URI uri = URI.create(url);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || !"i.scdn.co".equalsIgnoreCase(uri.getHost())) return;
            HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(12))
                    .header("User-Agent", "StellarMinecraftMod/1.0").GET().build();
            HttpResponse<InputStream> response = HTTP.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200) { response.body().close(); return; }
            byte[] data;
            try (InputStream input = response.body(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192]; int read; int total = 0;
                while ((read = input.read(buffer)) != -1) {
                    total += read; if (total > MAX_BYTES) return;
                    output.write(buffer, 0, read);
                }
                data = output.toByteArray();
            }
            MinecraftClient.getInstance().execute(() -> register(url, data));
        } catch (Exception ignored) {
            // Fail closed; the placeholder art remains visible and a later screen visit may retry.
        } finally {
            PENDING.remove(url);
        }
    }

    private static void register(String url, byte[] bytes) {
        if (READY.containsKey(url)) return;
        try {
            NativeImage image = NativeImage.read(new ByteArrayInputStream(bytes));
            Identifier id = Identifier.of("stellar", "album_art/" + Integer.toUnsignedString(url.hashCode(), 16));
            NativeImageBackedTexture texture = new NativeImageBackedTexture(() -> "Stellar album art", image);
            MinecraftClient.getInstance().getTextureManager().registerTexture(id, texture);
            READY.put(url, id);
        } catch (Exception ignored) {
            // Invalid or unsupported image data leaves the fallback cover in place.
        }
    }
}
