package dev.orbit;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Common ORBIT mod bootstrap. Client-specific features belong in {@link OrbitClient}. */
public final class OrbitMod implements ModInitializer {
    public static final String MOD_ID = "orbit";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("ORBIT {} initialized", "0.1.0");
    }
}
