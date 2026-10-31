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
import net.minecraft.util.math.BlockPos;
import org.lwjgl.glfw.GLFW;

import java.io.File;
import java.util.LinkedList;
import java.util.Queue;

public class AutobuilderClient implements ClientModInitializer {
    private static KeyBinding buildKey;
    private static final Queue<BlockPos> buildQueue = new LinkedList<>();
    private static boolean isBuilding = false;

    @Override
    public void onInitializeClient() {
        buildKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.autobuilder.build",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_N,
                "category.autobuilder"
        ));

        // Tick-tapahtuma joka ajaa rakennusjonoa eteenpäin pelin pyöriessä
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (buildKey.wasPressed()) {
                if (client.player != null && client.world != null) {
                    client.player.sendMessage(Text.literal("§aAutoBuilder: Trying to load schematic..."), false);
                    loadAndParseSchematic(client);
                }
            }

            // Jos rakennus on käynnissä, asetetaan muutama palikka per tick
            if (isBuilding && client.player != null && client.world != null) {
                for (int i = 0; i < 5 && !buildQueue.isEmpty(); i++) {
                    BlockPos pos = buildQueue.poll();
                    
                    // Tässä kohtaa lähetetään palikan sijoituspyyntö tai simuloidaan
                    // Esimerkiksi tulostetaan chattiin koordinaatti testiksi:
                    client.player.sendMessage(Text.literal("§7Building block at: " + pos.toShortString()), true);
                }

                if (buildQueue.isEmpty()) {
                    isBuilding = false;
                    client.player.sendMessage(Text.literal("§aAutoBuilder: Building complete!"), false);
                }
            }
        });
    }

    private void loadAndParseSchematic(net.minecraft.client.MinecraftClient client) {
        try {
            File schematicsDir = new File(client.runDirectory, "schematics");
            File[] files = schematicsDir.listFiles((dir, name) -> name.endsWith(".litematic"));

            if (files == null || files.length == 0) {
                client.player.sendMessage(Text.literal("§cError: No .litematic files found in schematics folder!"), false);
                return;
            }

            File schematicFile = files[0];
            client.player.sendMessage(Text.literal("§eFound file: " + schematicFile.getName()), false);

            NbtCompound nbt = NbtIo.readCompressed(schematicFile.toPath(), NbtSizeTracker.ofUnlimitedBytes());
            
            if (nbt != null && nbt.contains("Regions")) {
                NbtCompound regions = nbt.getCompound("Regions");
                client.player.sendMessage(Text.literal("§aRegions found in schematic!"), false);
                
                buildQueue.clear();
                BlockPos playerPos = client.player.getBlockPos();

                for (String regionName : regions.getKeys()) {
                    NbtCompound region = regions.getCompound(regionName);
                    
                    int sizeX = region.getCompound("Size").getInt("x");
                    int sizeY = region.getCompound("Size").getInt("y");
                    int sizeZ = region.getCompound("Size").getInt("z");
                    
                    // Täytetään jono esimerkkikoordinaateilla pelaajan ympärille
                    for (int x = 0; x < Math.min(sizeX, 10); x++) {
                        for (int y = 0; y < Math.min(sizeY, 5); y++) {
                            for (int z = 0; z < Math.min(sizeZ, 10); z++) {
                                buildQueue.add(playerPos.add(x, y, z));
                            }
                        }
                    }
                }

                isBuilding = true;
                client.player.sendMessage(Text.literal("§aBuilding started! Queue size: " + buildQueue.size()), false);
            }
        } catch (Exception e) {
            e.printStackTrace();
            if (client.player != null) {
                client.player.sendMessage(Text.literal("§cError reading schematic: " + e.getMessage()), false);
            }
        }
    }
}
