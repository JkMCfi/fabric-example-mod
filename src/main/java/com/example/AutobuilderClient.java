package com.example;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtSizeTracker;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

import java.io.File;
import java.util.LinkedList;
import java.util.Queue;

public class AutobuilderClient implements ClientModInitializer {
    private static KeyBinding buildKey;
    private static final Queue<BlockData> buildQueue = new LinkedList<>();
    private static boolean isBuilding = false;

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

            if (isBuilding && client.player != null && client.world != null && client.interactionManager != null) {
                if (buildQueue.isEmpty()) {
                    isBuilding = false;
                    client.player.sendMessage(Text.literal("§aAutoBuilder: Building complete!"), false);
                    return;
                }

                BlockData blockData = buildQueue.peek();

                if (!client.world.getBlockState(blockData.pos).isAir()) {
                    buildQueue.poll();
                    return;
                }

                Block block = Registries.BLOCK.get(Identifier.tryParse(blockData.blockName));
                if (block == null || block == Blocks.AIR) {
                    buildQueue.poll();
                    return;
                }

                int slot = findItemInHotbar(client, block);
                
                if (slot == -1) {
                    if (client.player.getAbilities().creativeMode) {
                        slot = getFirstAvailableHotbarSlot(client);
                        ItemStack blockStack = new ItemStack(block);
                        client.interactionManager.clickCreativeStack(blockStack, 36 + slot);
                        client.player.getInventory().setStack(slot, blockStack);
                        client.player.sendMessage(Text.literal("§7Auto-fetched: " + blockData.blockName), true);
                    } else {
                        client.player.sendMessage(Text.literal("§cOut of items! Opening /ah..."), false);
                        client.player.networkHandler.sendChatCommand("ah");
                        isBuilding = false;
                        return;
                    }
                }

                client.player.getInventory().selectedSlot = slot;

                Vec3d targetVec = Vec3d.ofCenter(blockData.pos);
                double distance = client.player.getPos().distanceTo(targetVec);

                smoothLookAt(client, targetVec);

                if (distance > 4.0) {
                    client.options.forwardKey.setPressed(true);
                    return; 
                } else {
                    client.options.forwardKey.setPressed(false);
                }

                buildQueue.poll();

                BlockPos supportPos = blockData.pos.down();
                BlockHitResult hitResult = new BlockHitResult(
                        Vec3d.ofCenter(supportPos),
                        Direction.UP,
                        supportPos,
                        false
                );

                ActionResult result = client.interactionManager.interactBlock(
                        client.player,
                        Hand.MAIN_HAND,
                        hitResult
                );

                client.player.swingHand(Hand.MAIN_HAND);

                if (result.isAccepted()) {
                    client.player.sendMessage(Text.literal("§7Placed: " + blockData.blockName), true);
                } else {
                    client.world.setBlockState(blockData.pos, block.getDefaultState());
                    client.player.sendMessage(Text.literal("§7Force-placed: " + blockData.blockName), true);
                }
            }
        });
    }

    private void smoothLookAt(MinecraftClient client, Vec3d target) {
        Vec3d playerPos = client.player.getEyePos();
        Vec3d dir = target.subtract(playerPos);
        
        double diffX = dir.x;
        double diffY = dir.y;
        double diffZ = dir.z;
        double dist = Math.sqrt(diffX * diffX + diffZ * diffZ);

        float targetYaw = (float) (Math.atan2(diffZ, diffX) * (180 / Math.PI)) - 90.0F;
        float targetPitch = (float) (-(Math.atan2(diffY, dist) * (180 / Math.PI)));

        float currentYaw = client.player.getYaw();
        float currentPitch = client.player.getPitch();

        float yawDiff = MathHelper.wrapDegrees(targetYaw - currentYaw);
        float pitchDiff = targetPitch - currentPitch;

        float maxStep = 30.0f;
        float newYaw = currentYaw + MathHelper.clamp(yawDiff, -maxStep, maxStep);
        float newPitch = currentPitch + MathHelper.clamp(pitchDiff, -maxStep, maxStep);

        client.player.setYaw(newYaw);
        client.player.setPitch(newPitch);
    }

    private int findItemInHotbar(MinecraftClient client, Block block) {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = client.player.getInventory().getStack(i);
            if (!stack.isEmpty() && stack.getItem() instanceof BlockItem blockItem) {
                if (blockItem.getBlock() == block) {
                    return i;
                }
            }
        }
        return -1;
    }

    private int getFirstAvailableHotbarSlot(MinecraftClient client) {
        for (int i = 0; i < 9; i++) {
            if (client.player.getInventory().getStack(i).isEmpty()) {
                return i;
            }
        }
        return client.player.getInventory().selectedSlot;
    }

    private void loadAndParseSchematic(MinecraftClient client) {
        try {
            File schematicsDir = new File(client.runDirectory, "schematics");
            File[] files = schematicsDir.listFiles((dir, name) -> name.endsWith(".litematic"));

            if (files == null || files.length == 0) {
                client.player.sendMessage(Text.literal("§cError: No .litematic files found!"), false);
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
                    
                    int sizeX = 0, sizeY = 0, sizeZ = 0;
                    
                    // Luetaan Size turvallisesti riippumatta siitä miten NbtCompound sen tallentaa
                    if (region.contains("Size")) {
                        NbtCompound sizeTag = region.getCompound("Size");
                        sizeX = sizeTag.contains("x") ? sizeTag.getInt("x") : sizeTag.getInt("X");
                        sizeY = sizeTag.contains("y") ? sizeTag.getInt("y") : sizeTag.getInt("Y");
                        sizeZ = sizeTag.contains("z") ? sizeTag.getInt("z") : sizeTag.getInt("Z");
                    }

                    // Varakorjaus, jos Size oli suoraan int-arvoina tai eri muodossa
                    if (sizeX <= 0 || sizeY <= 0 || sizeZ <= 0) {
                        try {
                            sizeX = region.getInt("Width");
                            sizeY = region.getInt("Height");
                            sizeZ = region.getInt("Length");
                        } catch (Exception ignored) {}
                    }

                    if (sizeX <= 0 || sizeY <= 0 || sizeZ <= 0) {
                        client.player.sendMessage(Text.literal("§cCould not read region dimensions!"), false);
                        continue;
                    }

                    NbtList palette = region.getList("BlockStatePalette", 10);
                    long[] blockStates = region.getLongArray("BlockStates");

                    if (palette.isEmpty() || blockStates.length == 0) {
                        client.player.sendMessage(Text.literal("§cPalette or BlockStates empty!"), false);
                        continue;
                    }

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

                if (!buildQueue.isEmpty()) {
                    isBuilding = true;
                    client.player.sendMessage(Text.literal("§aBuilding started! Total blocks: " + buildQueue.size()), false);
                } else {
                    client.player.sendMessage(Text.literal("§cNo non-air blocks found in schematic!"), false);
                }
            } else {
                client.player.sendMessage(Text.literal("§cInvalid litematic: No 'Regions' found!"), false);
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
