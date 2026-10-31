package com.example;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtSizeTracker;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
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
        // Rekisteröidään N-näppäin
        buildKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.autobuilder.build",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_N,
                "category.autobuilder"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // Kun N-näppäintä painetaan
            while (buildKey.wasPressed()) {
                if (client.player != null && client.world != null) {
                    client.player.sendMessage(Text.literal("§aAutoBuilder: Trying to load schematic..."), false);
                    loadAndParseSchematic(client);
                }
            }

            // Jos rakennus on käynnissä, asetetaan 5 palikkaa per tick
            if (isBuilding && client.player != null && client.world != null) {
                for (int i = 0; i < 5 && !buildQueue.isEmpty(); i++) {
                    BlockData blockData = buildQueue.poll();
                    
                    try {
                        Block block = Registries.BLOCK.get(Identifier.tryParse(blockData.blockName));
                        if (block != null && block != Blocks.AIR) {
                            client.world.setBlockState(blockData.pos, block.getDefaultState());
                            client.player.sendMessage(Text.literal("§7Built: " + blockData.blockName), true);
                        }
                    } catch (Exception e) {
                        client.player.sendMessage(Text.literal("§cFailed to place: " + blockData.blockName), true);
                    }
                }

                if (buildQueue.isEmpty()) {
                    isBuilding = false;
                    client.player.sendMessage(Text.literal("§aAutoBuilder: Building complete!"), false);
                }
            }
        });
    }

    private void loadAndParseSchematic(MinecraftClient client) {
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

                    int minBits = Math.max(2, 32 - Integer.numberOfLeadingZeros(palette.size() - 1));

                    int index = 0;
                    for (int y = 0; y < sizeY; y++) {
                        for (int z = 0; z < sizeZ; z++) {
                            for (int x = 0; x < sizeX; x++) {
                                int paletteIndex = getPaletteIndex(blockStates, index, minBits);
                                if (paletteIndex >= 0 && paletteIndex < palette.size()) {
                                    NbtCompound blockState = palette.getCompound(paletteIndex);
                                    String blockName = blockState.getString("Name");

                                    if (!blockName.equals("minecraft:air")) {
                                        BlockPos worldPos = playerPos.add(x, y, z);
                                        buildQueue.add(new BlockData(worldPos, blockName));
                                    }
                                }
                                index++;
                            }
                        }
                    }
                }

                isBuilding = true;
                client.player.sendMessage(Text.literal("§aBuilding started! Total blocks: " + buildQueue.size()), false);
            }
        } catch (Exception e) {
            e.printStackTrace();
            if (client.player != null) {
                client.player.sendMessage(Text.literal("§cError reading schematic: " + e.getMessage()), false);
            }
        }
    }

    private int getPaletteIndex(long[] blockStates, int index, int bitsPerEntry) {
        long bitIndex = (long) index * bitsPerEntry;
        int longIndex = (int) (bitIndex / 64);
        int bitOffset = (int) (bitIndex % 64);

        if (longIndex >= blockStates.length) {
            return 0;
        }

        long value = blockStates[longIndex] >>> bitOffset;
        if (bitOffset + bitsPerEntry > 64 && longIndex + 1 < blockStates.length) {
            value |= blockStates[longIndex + 1] << (64 - bitOffset);
        }

        return (int) (value & ((1L << bitsPerEntry) - 1));
    }
}
