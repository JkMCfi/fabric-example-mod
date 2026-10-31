package com.example;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtSizeTracker;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.io.File;

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
                    client.player.sendMessage(Text.literal("§aAutoBuilder: Trying to load schematic..."), false);
                    loadAndParseSchematic(client);
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
                
                for (String regionName : regions.getKeys()) {
                    NbtCompound region = regions.getCompound(regionName);
                    
                    int sizeX = region.getCompound("Size").getInt("x");
                    int sizeY = region.getCompound("Size").getInt("y");
                    int sizeZ = region.getCompound("Size").getInt("z");
                    
                    client.player.sendMessage(Text.literal("§eRegion Size: " + sizeX + "x" + sizeY + "x" + sizeZ), false);

                    // 1. Luetaan Paletti (mitä palikoita rakennuksessa on)
                    if (region.contains("Palette")) {
                        NbtList palette = region.getList("Palette", 10); // 10 tarkoittaa NbtCompound-tyyppiä
                        client.player.sendMessage(Text.literal("§aPalette loaded! Unique blocks: " + palette.size()), false);
                    }

                    // 2. Luetaan BlockStates (bittitaulukko, joka kertoo sijainnit)
                    if (region.contains("BlockStates")) {
                        long[] blockStates = region.getLongArray("BlockStates");
                        client.player.sendMessage(Text.literal("§aBlockStates loaded! Array size: " + blockStates.length), false);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            if (client.player != null) {
                client.player.sendMessage(Text.literal("§cError reading schematic: " + e.getMessage()), false);
            }
        }
    }
}
