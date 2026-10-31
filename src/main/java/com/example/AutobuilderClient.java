package com.example;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public class AutobuilderClient implements ClientModInitializer {
    private static KeyBinding buildKey;

    @Override
    public void onInitializeClient() {
        // Rekisteröidään näppäin Minecraft 1.21.4 yhteensopivalla tavalla
        buildKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.autobuilder.build",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_N,
                "category.autobuilder"
        ));

        // Kuunnellaan, milloin näppäintä painetaan pelissä
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (buildKey.wasPressed()) {
                if (client.player != null) {
                    client.player.sendMessage(Text.literal("§aAutoBuilder-toiminto aktivoitu!"), false);
                }
            }
        });
    }
}
