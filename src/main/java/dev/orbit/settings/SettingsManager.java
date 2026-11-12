package dev.orbit.settings;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class SettingsManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("orbit.json");

    private static final SettingsManager INSTANCE = new SettingsManager();

    private OrbitSettings settings = new OrbitSettings();

    private SettingsManager() {
        load();
    }

    public static SettingsManager get() {
        return INSTANCE;
    }

    public OrbitSettings getSettings() {
        return settings;
    }

    public void load() {
        try {
            if (Files.exists(CONFIG_PATH)) {
                String raw = Files.readString(CONFIG_PATH, StandardCharsets.UTF_8);
                OrbitSettings loaded = GSON.fromJson(raw, OrbitSettings.class);
                if (loaded != null) {
                    settings = loaded;
                }
            }
        } catch (IOException e) {
            settings = new OrbitSettings();
        }
    }

    public void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            Files.writeString(CONFIG_PATH, GSON.toJson(settings), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to save ORBIT settings", e);
        }
    }

    public static class OrbitSettings {
        public String activeSky = "ORBIT_ORIGINAL";
        public String performancePreset = "BALANCED";
        public float volume = 0.8F;
        public boolean hudEnabled = true;
        public int guiScale = 2;
        public String theme = "Default Orbit Purple";
        public boolean shuffle = false;
        public boolean repeat = false;
    }
}
