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
import net.minecraft.util.math.BlockPos;
import org.lwjgl.glfw.GLFW;

import java.io.File;
import java.util.LinkedList;
import java.util.Queue;

public class AutobuilderClient implements ClientModInitializer {
    private static KeyBinding buildKey;
    private static final Queue<BlockData> buildQueue = new LinkedList<>();
    private static boolean isBuilding = false;

    // Apuluokka lohkotiedolle
    private static class BlockData {
        final BlockPos pos;
        final String blockName;

        BlockData(BlockPos pos, String blockName) {
            this.pos = pos;
            this.blockName = blockName;
        }
    }

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

            if (isBuilding && client.player != null && client.world != null) {
                for (int i = 0; i < 5 && !buildQueue.isEmpty(); i++) {
                    BlockData blockData = buildQueue.poll();
                    
                    // Tulostetaan chattiin mitä palikkaa ollaan rakentamassa mihinkin koordinaattiin
                    client.player.sendMessage(Text.literal("§7Placing " + blockData.blockName + " at " + blockData.pos.toShortString()), true);
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
                buildQueue.clear();
                BlockPos playerPos = client.player.getBlockPos();

                for (String regionName : regions.getKeys()) {
                    NbtCompound region = regions.getCompound(regionName);
                    
                    int sizeX = region.getCompound("Size").getInt("x");
                    int sizeY = region.getCompound("Size").getInt("y");
                    int sizeZ = region.getCompound("Size").getInt("z");

                    NbtList palette = region.getList("Palette", 10);
                    long[] blockStates = region.getLongArray("BlockStates");

                    int minBits = Math.max(2, 32 - Integer.numberOf
