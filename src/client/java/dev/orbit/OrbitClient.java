package dev.orbit;

import net.fabricmc.api.ClientModInitializer;

/** Client bootstrap for the client-only ORBIT experience. */
public final class OrbitClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        OrbitMod.LOGGER.info("ORBIT client initialized");
    }
}
