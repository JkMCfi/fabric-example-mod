package dev.orbit;

import com.mojang.blaze3d.platform.InputConstants;
import dev.orbit.music.PartySyncClient;
import dev.orbit.ui.OrbitScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public class OrbitClient implements ClientModInitializer {
    private static final KeyMapping.Category ORBIT_CATEGORY = KeyMapping.Category.register(
        Identifier.fromNamespaceAndPath(OrbitMod.MOD_ID, "main")
    );
    private static final KeyMapping OPEN_ORBIT = KeyBindingHelper.registerKeyBinding(
        new KeyMapping("key.orbit.open", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, ORBIT_CATEGORY)
    );

    @Override
    public void onInitializeClient() {
        PartySyncClient.initialize();
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (OPEN_ORBIT.consumeClick()) {
                client.setScreen(new OrbitScreen());
            }
        });
    }
}
