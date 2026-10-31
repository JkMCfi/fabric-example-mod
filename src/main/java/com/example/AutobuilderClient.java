package com.example;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtSizeTracker;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.io.File;
import java.nio.file.Path;

public class AutobuilderClient implements ClientModInitializer {
    private static KeyBinding buildKey;

    @Override
    public void onInitializeClient() {
        buildKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.autobuilder.build",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_N,
                "category.autobuilder"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (buildKey.wasPressed()) {
                if (client.player != null && client.world != null) {
                    client.player.sendMessage(Text.literal("§aAutoBuilder:trying to load the schematic..."), false);
                    loadAndParseSchematic(client);
                }
            }
        });
    }

    private void loadAndParseSchematic(net.minecraft.client.MinecraftClient client) {
        try {
            // Etsitään pelikansiosta schematics-kansio
            File schematicsDir = new File(client.runDirectory, "schematics");
            File[] files = schematicsDir.listFiles((dir, name) -> name.endsWith(".litematic"));

            if (files == null || files.length == 0) {
                client.player.sendMessage(Text.literal("§cVirhe: no files found!"), false);
                return;
            }

            // Otetaan ensimmäinen vastaan tullut litematic-tiedosto
            File schematicFile = files[0];
            client.player.sendMessage(Text.literal("§eLöytyi tiedosto: " + schematicFile.getName()), false);

            // Luetaan NBT-data turvallisesti
            NbtCompound nbt = NbtIo.readCompressed(schematicFile.toPath(), NbtSizeTracker.ofUnlimitedBytes());
            
            if (nbt != null) {
                client.player.sendMessage(Text.literal("§aLitematic succesfully saved to the mod!"), false);
            }
        } catch (Exception e) {
            e.printStackTrace();
            if (client.player != null) {
                client.player.sendMessage(Text.literal("§cError: " + e.getMessage()), false);
            }
        }
    }
}
