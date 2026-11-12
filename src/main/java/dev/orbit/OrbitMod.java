package dev.orbit;

import net.fabricmc.api.ModInitializer;
import dev.orbit.music.PartySync;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OrbitMod implements ModInitializer {
    public static final String MOD_ID = "orbit";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        PartySync.initialize();
        LOGGER.info("ORBIT initialized.");
    }
}
