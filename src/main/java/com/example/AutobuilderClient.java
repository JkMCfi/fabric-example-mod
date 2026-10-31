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
        // 1. Rekisteröidään näppäin (oletuksena N-kirjain)
        buildKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.autobuilder.build", // Kieli-id
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_N,         // Mikä näppäin (tässä N)
                "category.autobuilder"   // Asetusvalikon kategoria pelissä
        ));

        // 2. Kuunnellaan, milloin näppäintä painetaan pelin aikana
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (buildKey.wasPressed()) {
                if (client.player != null) {
                    // Lähettää viestin pelin chatissa pelaajalle
                    client.player.sendMessage(Text.literal("§aAutoBuilder-toiminto aktivoitu!"), false);
                    
                    // Tänne lisätään myöhemmin litematic-tiedoston luku- ja rakennuslogiikka
                }
            }
        });
    }
}
