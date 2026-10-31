package com.example;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtSizeTracker;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.property.Property;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import org.lwjgl.glfw.GLFW;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

public class AutobuilderClient implements ClientModInitializer {
    private static KeyBinding buildKey;
    private static KeyBinding menuKey;
    private static final Queue<BlockData> buildQueue = new LinkedList<>();
    private static boolean isBuilding = false;
    private static boolean isPaused = false;
    private static boolean waitingForAhConfirmation = false;
    
    // Settings (Adjustable via Menu)
    public static int tickDelay = 3; 
    public static float smoothness = 4.0f; 
    public static boolean autoLeaveOnAdmin = false;
    public static boolean showHologram = true;
    public static String selectedSchematicName = "";

    private static int tickCounter = 0;
    private static int stuckTimer = 0;
    private static int safetyCheckTimer = 0;
    private static long buildKeyPressTime = 0;

    private static class BlockData {
        final BlockPos pos;
        final String blockName;
        final NbtCompound propertiesTag;

        BlockData(BlockPos pos, String blockName, NbtCompound propertiesTag) {
            this.pos = pos;
            this.blockName = blockName;
            this.propertiesTag = propertiesTag;
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

        menuKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.autobuilder.menu",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_B,
                "category.autobuilder"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (menuKey.wasPressed()) {
                if (client.player != null) {
                    client.setScreen(new AutobuilderMenuScreen());
                }
            }

            if (autoLeaveOnAdmin && client.world != null && client.player != null && !client.isInSingleplayer()) {
                safetyCheckTimer++;
                if (safetyCheckTimer > 20) {
                    safetyCheckTimer = 0;
                    if (client.getNetworkHandler() != null) {
                        for (PlayerListEntry entry : client.getNetworkHandler().getPlayerList()) {
                            if (entry.getProfile().getId().equals(client.player.getGameProfile().getId())) continue;
                            
                            if (entry.getGameMode() == GameMode.SPECTATOR) {
                                isBuilding = false;
                                isPaused = false;
                                client.player.sendMessage(Text.literal("§c[AutoBuilder Security] Spectator nearby! Leaving server..."), false);
                                client.world.disconnect();
                                client.setScreen(new MultiplayerScreen(null));
                                return;
                            }
                        }
                    }
                }
            }

            if (buildKey.isPressed()) {
                if (buildKeyPressTime == 0) {
                    buildKeyPressTime = System.currentTimeMillis();
                } else if (System.currentTimeMillis() - buildKeyPressTime > 1000) {
                    if (client.player != null && client.world != null) {
                        client.player.sendMessage(Text.literal("§eAutoBuilder: Restarting build from current position!"), false);
                        loadAndParseSchematic(client, false);
                        buildKeyPressTime = -1;
                    }
                }
            } else {
                if (buildKeyPressTime > 0 && buildKeyPressTime != -1) {
                    if (client.player != null && client.world != null) {
                        if (waitingForAhConfirmation) {
                            client.player.playSound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0F, 1.0F);
                            client.player.sendMessage(Text.literal("§aAutoBuilder: /ah purchase approved!"), false);
                            client.player.networkHandler.sendChatCommand("ah");
                            waitingForAhConfirmation = false;
                            isBuilding = false;
                            isPaused = false;
                        } else if (isBuilding) {
                            isBuilding = false;
                            isPaused = true;
                            stopMovement(client);
                            client.player.sendMessage(Text.literal("§eAutoBuilder: Paused! Press N to resume."), false);
                        } else if (isPaused) {
                            isPaused = false;
                            isBuilding = true;
                            client.player.sendMessage(Text.literal("§aAutoBuilder: Resuming build..."), false);
                        } else {
                            client.player.sendMessage(Text.literal("§aAutoBuilder: Loading schematic..."), false);
                            loadAndParseSchematic(client, false);
                        }
                    }
                }
                buildKeyPressTime = 0;
            }

            if (isBuilding && client.player != null && client.world != null && client.interactionManager != null) {
                if (buildQueue.isEmpty()) {
                    isBuilding = false;
                    isPaused = false;
                    stopMovement(client);
                    client.player.sendMessage(Text.literal("§aAutoBuilder: Building complete!"), false);
                    return;
                }

                if (tickCounter < tickDelay) {
                    tickCounter++;
                    return;
                }
                tickCounter = 0;

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

                Vec3d targetVec = Vec3d.ofCenter(blockData.pos);
                double distance = client.player.getPos().distanceTo(targetVec);

                if (distance > 4.5) {
                    client.options.forwardKey.setPressed(true);
                    if (client.player.horizontalCollision) {
                        stuckTimer++;
                        client.options.jumpKey.setPressed(true);
                        if (stuckTimer > 20) {
                            client.options.leftKey.setPressed(true);
                        }
                    } else {
                        stuckTimer = 0;
                        client.options.jumpKey.setPressed(false);
                        client.options.leftKey.setPressed(false);
                    }
                    smoothLookAt(client, targetVec);
                    return; 
                } else {
                    stopMovement(client);
                    stuckTimer = 0;
                }

                int slot = findItemInHotbar(client, block);
                if (slot == -1) {
                    if (client.player.getAbilities().creativeMode) {
                        slot = getFirstAvailableHotbarSlot(client);
                        ItemStack blockStack = new ItemStack(block);
                        client.interactionManager.clickCreativeStack(blockStack, 36 + slot);
                        client.player.getInventory().setStack(slot, blockStack);
                        client.player.sendMessage(Text.literal("§7Auto-fetched (Creative): " + blockData.blockName), true);
                    } else {
                        isBuilding = false;
                        isPaused = true;
                        stopMovement(client);
                        waitingForAhConfirmation = true;

                        client.player.playSound(SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), 1.0F, 0.3F);
                        client.player.sendMessage(Text.literal("§c[AutoBuilder] Out of items! /ah price might exceed 200k. §ePress N to confirm purchase."), false);
                        return;
                    }
                }

                client.player.getInventory().selectedSlot = slot;
                smoothLookAt(client, targetVec);

                buildQueue.poll();

                BlockState targetState = block.getDefaultState();
                targetState = applyProperties(targetState, blockData.propertiesTag);

                client.world.setBlockState(blockData.pos, targetState);
                client.player.swingHand(Hand.MAIN_HAND);
                client.player.sendMessage(Text.literal("§7Placed: " + blockData.blockName), true);
            }
        });
    }

    private static void stopMovement(MinecraftClient client) {
        if (client.options != null) {
            client.options.forwardKey.setPressed(false);
            client.options.jumpKey.setPressed(false);
            client.options.leftKey.setPressed(false);
            client.options.rightKey.setPressed(false);
        }
    }

    private BlockState applyProperties(BlockState state, NbtCompound propertiesTag) {
        if (propertiesTag == null) return state;
        for (String key : propertiesTag.getKeys()) {
            Property<?> property = state.getBlock().getStateManager().getProperty(key);
            if (property != null) {
                String valStr = propertiesTag.getString(key);
                for (Object valObj : property.getValues()) {
                    if (valObj.toString().equalsIgnoreCase(valStr)) {
                        state = state.with((Property) property, (Comparable) valObj);
                        break;
                    }
                }
            }
        }
        return state;
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

        float newYaw = currentYaw + (yawDiff / smoothness);
        float newPitch = currentPitch + (pitchDiff / smoothness);

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

    public static void loadAndParseSchematic(MinecraftClient client, boolean silent) {
        try {
            File schematicsDir = new File(client.runDirectory, "schematics");
            File[] files = schematicsDir.listFiles((dir, name) -> name.endsWith(".litematic"));

            if (files == null || files.length == 0) {
                if (!silent && client.player != null) client.player.sendMessage(Text.literal("§cError: No .litematic files found!"), false);
                return;
            }

            File schematicFile = null;
            if (selectedSchematicName != null && !selectedSchematicName.isEmpty()) {
                for (File f : files) {
                    if (f.getName().equals(selectedSchematicName)) {
                        schematicFile = f;
                        break;
                    }
                }
            }
            if (schematicFile == null) {
                schematicFile = files[0];
                selectedSchematicName = schematicFile.getName();
            }

            if (!silent && client.player != null) {
                client.player.sendMessage(Text.literal("§eFound file: " + schematicFile.getName()), false);
            }

            NbtCompound nbt = NbtIo.readCompressed(schematicFile.toPath(), NbtSizeTracker.ofUnlimitedBytes());
            
            if (nbt != null && nbt.contains("Regions")) {
                NbtCompound regions = nbt.getCompound("Regions");
                buildQueue.clear();
                BlockPos playerPos = client.player.getBlockPos();
                List<BlockData> tempQueue = new ArrayList<>();

                for (String regionName : regions.getKeys()) {
                    NbtCompound region = regions.getCompound(regionName);
                    
                    int sizeX = 0, sizeY = 0, sizeZ = 0;

                    if (region.contains("Size")) {
                        NbtCompound sizeTag = region.getCompound("Size");
                        sizeX = sizeTag.contains("x") ? sizeTag.getInt("x") : sizeTag.getInt("X");
                        sizeY = sizeTag.contains("y") ? sizeTag.getInt("y") : sizeTag.getInt("Y");
                        sizeZ = sizeTag.contains("z") ? sizeTag.getInt("z") : sizeTag.getInt("Z");
                    }

                    if (sizeX <= 0 || sizeY <= 0 || sizeZ <= 0) {
                        sizeX = 18;
                        sizeY = 8;
                        sizeZ = 13;
                    }

                    NbtList palette = region.contains("BlockStatePalette") ? region.getList("BlockStatePalette", 10) : region.getList("Palette", 10);
                    long[] blockStates = region.getLongArray("BlockStates");

                    if (palette.isEmpty() || blockStates.length == 0) {
                        continue;
                    }

                    int minBits = Math.max(2, 32 - Integer.numberOfLeadingZeros(palette.size() - 1));

                    int index = 0;
                    for (int y = 0; y < sizeY; y++) {
                        for (int z = 0; z < sizeZ; z++) {
                            for (int x = 0; x < sizeX; x++) {
                                int paletteIndex = getPaletteIndex(blockStates, index, minBits);
                                if (paletteIndex >= 0 && paletteIndex < palette.size()) {
                                    NbtCompound blockStateTag = palette.getCompound(paletteIndex);
                                    String blockName = blockStateTag.getString("Name");
                                    NbtCompound propertiesTag = blockStateTag.contains("Properties") ? blockStateTag.getCompound("Properties") : null;

                                    if (!blockName.equals("minecraft:air")) {
                                        BlockPos worldPos = playerPos.add(x, y, z);
                                        tempQueue.add(new BlockData(worldPos, blockName, propertiesTag));
                                    }
                                }
                                index++;
                            }
                        }
                    }
                }

                tempQueue.sort(Comparator.comparingDouble(b -> client.player.getPos().squaredDistanceTo(Vec3d.ofCenter(b.pos))));
                buildQueue.addAll(tempQueue);

                if (!buildQueue.isEmpty()) {
                    isBuilding = true;
                    isPaused = false;
                    waitingForAhConfirmation = false;
                    if (!silent && client.player != null) {
                        client.player.sendMessage(Text.literal("§aBuilding started! Total blocks: " + buildQueue.size()), false);
                    }
                } else {
                    if (!silent && client.player != null) {
                        client.player.sendMessage(Text.literal("§cNo non-air blocks found in schematic!"), false);
                    }
                }
            } else {
                if (!silent && client.player != null) {
                    client.player.sendMessage(Text.literal("§cInvalid litematic: 'Regions' missing!"), false);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            if (!silent && client.player != null) {
                client.player.sendMessage(Text.literal("§cError reading schematic: " + e.getMessage()), false);
            }
        }
    }

    private static int getPaletteIndex(long[] blockStates, int index, int bitsPerEntry) {
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

    public static class AutobuilderMenuScreen extends Screen {
        public AutobuilderMenuScreen() {
            super(Text.literal("AutoBuilder Settings"));
        }

        @Override
        protected void init() {
            int centerX = this.width / 2;
            int centerY = this.height / 2;

            this.addDrawableChild(ButtonWidget.builder(Text.literal("Schematic: " + getActiveSchematicDisplayName()), button -> {
                cycleSchematic();
                button.setMessage(Text.literal("Schematic: " + getActiveSchematicDisplayName()));
            }).dimensions(centerX - 100, centerY - 90, 200, 20).build());

            this.addDrawableChild(ButtonWidget.builder(Text.literal("Speed (Delay): " + AutobuilderClient.tickDelay), button -> {
                AutobuilderClient.tickDelay = AutobuilderClient.tickDelay >= 6 ? 1 : AutobuilderClient.tickDelay + 1;
                button.setMessage(Text.literal("Speed (Delay): " + AutobuilderClient.tickDelay));
            }).dimensions(centerX - 100, centerY - 60, 200, 20).build());

            this.addDrawableChild(ButtonWidget.builder(Text.literal("Smoothness: " + AutobuilderClient.smoothness), button -> {
                AutobuilderClient.smoothness = AutobuilderClient.smoothness >= 16.0f ? 2.0f : AutobuilderClient.smoothness + 2.0f;
                button.setMessage(Text.literal("Smoothness: " + AutobuilderClient.smoothness));
            }).dimensions(centerX - 100, centerY - 30, 200, 20).build());

            String holoStatus = AutobuilderClient.showHologram ? "§aON" : "§cOFF";
            this.addDrawableChild(ButtonWidget.builder(Text.literal("Hologram: " + holoStatus), button -> {
                AutobuilderClient.showHologram = !AutobuilderClient.showHologram;
                String newStatus = AutobuilderClient.showHologram ? "§aON" : "§cOFF";
                button.setMessage(Text.literal("Hologram: " + newStatus));
            }).dimensions(centerX - 100, centerY, 200, 20).build());

            String adminStatus = AutobuilderClient.autoLeaveOnAdmin ? "§aON" : "§cOFF";
            this.addDrawableChild(ButtonWidget.builder(Text.literal("Admin/Spectator Safety: " + adminStatus), button -> {
                AutobuilderClient.autoLeaveOnAdmin = !AutobuilderClient.autoLeaveOnAdmin;
                String newStatus = AutobuilderClient.autoLeaveOnAdmin ? "§aON" : "§cOFF";
                button.setMessage(Text.literal("Admin/Spectator Safety: " + newStatus));
            }).dimensions(centerX - 100, centerY + 30, 200, 20).build());

            this.addDrawableChild(ButtonWidget.builder(Text.literal("Back to Game"), button -> {
                if (this.client != null) {
                    this.client.setScreen(null);
                }
            }).dimensions(centerX - 100, centerY + 65, 200, 20).build());
        }

        private String getActiveSchematicDisplayName() {
            if (AutobuilderClient.selectedSchematicName == null || AutobuilderClient.selectedSchematicName.isEmpty()) {
                File schematicsDir = new File(MinecraftClient.getInstance().runDirectory, "schematics");
                File[] files = schematicsDir.listFiles((dir, name) -> name.endsWith(".litematic"));
                if (files != null && files.length > 0) {
                    AutobuilderClient.selectedSchematicName = files[0].getName();
                } else {
                    return "None found";
                }
            }
            return AutobuilderClient.selectedSchematicName;
        }

        private void cycleSchematic() {
            File schematicsDir = new File(MinecraftClient.getInstance().runDirectory, "schematics");
            File[] files = schematicsDir.listFiles((dir, name) -> name.endsWith(".litematic"));
            if (files == null || files.length == 0) return;

            int currentIndex = 0;
            for (int i = 0; i < files.length; i++) {
                if (files[i].getName().equals(AutobuilderClient.selectedSchematicName)) {
                    currentIndex = i;
                    break;
                }
            }
            int nextIndex = (currentIndex + 1) % files.length;
            AutobuilderClient.selectedSchematicName = files[nextIndex].getName();
        }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            this.renderBackground(context, mouseX, mouseY, delta);
            context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, this.height / 2 - 115, 0xFFFFFF);
            super.render(context, mouseX, mouseY, delta);
        }

        @Override
        public boolean shouldCloseOnEsc() {
            return true;
        }
    }
}
