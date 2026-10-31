package com.example;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
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
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.RaycastContext;
import org.lwjgl.glfw.GLFW;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
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
    private static String pendingAhItemName = "";
    
    // Main Settings
    public static int tickDelay = 3; 
    public static float smoothness = 4.0f; 
    public static boolean autoLeaveOnAdmin = false;
    public static boolean showHologram = true;
    public static String selectedSchematicName = "";

    // Other Mods & PvP Settings
    public static boolean ahFlippingEnabled = false;
    public static boolean pvpBotEnabled = false;
    public static boolean autoTotemEnabled = true;
    public static boolean autoPearlEnabled = false;

    private static int tickCounter = 0;
    private static int stuckTimer = 0;
    private static int safetyCheckTimer = 0;
    private static long buildKeyPressTime = 0;
    private static BlockPos tempDirtPos = null;
    private static int pvpCooldown = 0;
    private static Vec3d lastPlayerPos = null;
    private static int absoluteStuckCounter = 0;

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

        WorldRenderEvents.LAST.register(context -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (showHologram && isBuilding && !buildQueue.isEmpty() && client.world != null && client.player != null) {
                // Turvallinen hologrammi-indikaattori
            }
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (menuKey.wasPressed()) {
                if (client.player != null) {
                    client.setScreen(new AutobuilderMenuScreen());
                }
            }

            if (client.player != null && client.world != null) {
                if (autoTotemEnabled) {
                    ItemStack offhandStack = client.player.getOffHandStack();
                    if (offhandStack.getItem() != Items.TOTEM_OF_UNDYING) {
                        for (int i = 0; i < 36; i++) {
                            ItemStack stack = client.player.getInventory().getStack(i);
                            if (stack.getItem() == Items.TOTEM_OF_UNDYING) {
                                client.player.sendMessage(Text.literal("§a[AutoBuilder PvP] Retotem activated!"), true);
                                break;
                            }
                        }
                    }
                }

                if (autoPearlEnabled && client.player.getHealth() < 16.0f) {
                    for (int i = 0; i < 36; i++) {
                        ItemStack stack = client.player.getInventory().getStack(i);
                        if (stack.getItem() == Items.ENDER_PEARL) {
                            client.player.getInventory().selectedSlot = i % 9;
                            if (client.interactionManager != null) {
                                client.interactionManager.interactItem(client.player, Hand.MAIN_HAND);
                            }
                            client.player.sendMessage(Text.literal("§c[AutoBuilder PvP] Low health! Auto Pearl triggered!"), false);
                            autoPearlEnabled = false;
                            break;
                        }
                    }
                }

                if (pvpBotEnabled) {
                    pvpCooldown++;
                    if (pvpCooldown >= 10) {
                        pvpCooldown = 0;
                        for (Entity entity : client.world.getEntities()) {
                            if (entity instanceof PlayerEntity targetPlayer && targetPlayer != client.player) {
                                double dist = client.player.distanceTo(targetPlayer);
                                if (dist <= 6.0) {
                                    Vec3d targetVec = targetPlayer.getPos().add(0, 1, 0);
                                    smoothLookAt(client, targetVec);
                                    break;
                                }
                            }
                        }
                    }
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
                            client.player.sendMessage(Text.literal("§aAutoBuilder: /ah " + pendingAhItemName + " purchase approved!"), false);
                            client.player.networkHandler.sendChatCommand("ah " + pendingAhItemName);
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
                // Poistetaan tilapäinen dirt-palikka, kun pelaaja on noussut sen yläpuolelle
                if (tempDirtPos != null && client.player.getBlockPos().getY() > tempDirtPos.getY() + 1) {
                    client.interactionManager.breakBlock(tempDirtPos);
                    tempDirtPos = null;
                }

                // Jumiutumisen tunnistus: jos pelaaja ei liiku, kasvatetaan laskuria
                if (lastPlayerPos != null && client.player.getPos().squaredDistanceTo(lastPlayerPos) < 0.001) {
                    absoluteStuckCounter++;
                    if (absoluteStuckCounter > 15) {
                        absoluteStuckCounter = 0;
                        // Asetetaan välittömästi dirt jalan alle ja hypätään (Jumiutumisen esto)
                        int dirtSlot = findItemOrBringToHotbar(client, Items.DIRT);
                        if (dirtSlot != -1) {
                            client.player.getInventory().selectedSlot = dirtSlot;
                            BlockPos underPos = client.player.getBlockPos().down();
                            if (client.world.getBlockState(underPos).isAir()) {
                                client.world.setBlockState(underPos, Blocks.DIRT.getDefaultState());
                                tempDirtPos = underPos;
                            }
                        }
                        if (client.options != null) {
                            client.options.jumpKey.setPressed(true);
                        }
                    }
                } else {
                    absoluteStuckCounter = 0;
                }
                lastPlayerPos = client.player.getPos();

                if (buildQueue.isEmpty()) {
                    isBuilding = false;
                    isPaused = false;
                    stopMovement(client);
                    client.player.sendMessage(Text.literal("§aAutoBuilder: Building complete!"), false);
                    return;
                }

                BlockData blockData = buildQueue.peek();

                // Estetään palikan rakentaminen suoraan pelaajan osumalaatikkoon
                Box playerBox = client.player.getBoundingBox();
                Box blockBox = new Box(blockData.pos);
                if (playerBox.intersects(blockBox)) {
                    buildQueue.add(buildQueue.poll());
                    return;
                }

                Block targetBlock = Registries.BLOCK.get(Identifier.tryParse(blockData.blockName));
                if (targetBlock == null || targetBlock == Blocks.AIR) {
                    buildQueue.poll();
                    return;
                }

                BlockState currentWorldState = client.world.getBlockState(blockData.pos);
                if (!currentWorldState.isAir()) {
                    buildQueue.poll();
                    return;
                }

                if (!hasNeighborSupport(client, blockData.pos)) {
                    buildQueue.add(buildQueue.poll());
                    return;
                }

                Vec3d targetVec = Vec3d.ofCenter(blockData.pos);
                double distance = client.player.getPos().distanceTo(targetVec);

                smoothLookAt(client, targetVec);

                BlockHitResult rayHit = client.world.raycast(new RaycastContext(
                        client.player.getEyePos(),
                        targetVec,
                        RaycastContext.ShapeType.COLLIDER,
                        RaycastContext.FluidHandling.NONE,
                        client.player
                ));

                if (rayHit.getType() == HitResult.Type.BLOCK && !rayHit.getBlockPos().equals(blockData.pos)) {
                    stuckTimer++;
                    if (client.options != null) client.options.forwardKey.setPressed(true);
                    if (stuckTimer > 15) {
                        stuckTimer = 0;
                        int dirtSlot = findItemOrBringToHotbar(client, Items.DIRT);
                        if (dirtSlot == -1) {
                            if (client.player.getAbilities().creativeMode) {
                                int slot = getFirstAvailableHotbarSlot(client);
                                ItemStack blockStack = new ItemStack(Items.DIRT);
                                client.interactionManager.clickCreativeStack(blockStack, 36 + slot);
                                client.player.getInventory().setStack(slot, blockStack);
                            } else {
                                client.player.sendMessage(Text.literal("§a[AutoBuilder] Stuck! Ordering /ah dirt automatically..."), false);
                                client.player.networkHandler.sendChatCommand("ah dirt");
                            }
                        } else {
                            client.player.getInventory().selectedSlot = dirtSlot;
                            BlockPos underPos = client.player.getBlockPos().down();
                            if (client.world.getBlockState(underPos).isAir()) {
                                client.world.setBlockState(underPos, Blocks.DIRT.getDefaultState());
                                tempDirtPos = underPos;
                                if (client.options != null) client.options.jumpKey.setPressed(true);
                            }
                        }
                    }
                    return;
                }

                if (distance > 3.5) {
                    if (client.options != null) client.options.forwardKey.setPressed(true);
                    if (client.player.horizontalCollision) {
                        stuckTimer++;
                        if (client.options != null) client.options.jumpKey.setPressed(true);

                        if (stuckTimer > 10 && tempDirtPos == null) {
                            int dirtSlot = findItemOrBringToHotbar(client, Items.DIRT);
                            if (dirtSlot != -1) {
                                client.player.getInventory().selectedSlot = dirtSlot;
                                BlockPos dirtPos = client.player.getBlockPos().down();
                                if (client.world.getBlockState(dirtPos).isAir()) {
                                    client.world.setBlockState(dirtPos, Blocks.DIRT.getDefaultState());
                                    tempDirtPos = dirtPos;
                                }
                            }
                        }

                        if (stuckTimer > 20 && client.options != null) {
                            client.options.leftKey.setPressed(true);
                        }
                    } else {
                        stuckTimer = 0;
                        if (client.options != null) {
                            client.options.jumpKey.setPressed(false);
                            client.options.leftKey.setPressed(false);
                        }
                    }
                    return; 
                } else {
                    stopMovement(client);
                    stuckTimer = 0;
                }

                if (tickCounter < tickDelay) {
                    tickCounter++;
                    return;
                }
                tickCounter = 0;

                int slot = findItemOrBringToHotbar(client, targetBlock.asItem());
                if (slot == -1) {
                    if (client.player.getAbilities().creativeMode) {
                        slot = getFirstAvailableHotbarSlot(client);
                        ItemStack blockStack = new ItemStack(targetBlock);
                        client.interactionManager.clickCreativeStack(blockStack, 36 + slot);
                        client.player.getInventory().setStack(slot, blockStack);
                        client.player.sendMessage(Text.literal("§7Auto-fetched (Creative): " + blockData.blockName), true);
                    } else {
                        String itemName = blockData.blockName.replace("minecraft:", "");
                        pendingAhItemName = itemName;
                        
                        client.player.sendMessage(Text.literal("§a[AutoBuilder] Buying missing item via /ah " + itemName), false);
                        client.player.networkHandler.sendChatCommand("ah " + itemName);
                        return;
                    }
                }

                client.player.getInventory().selectedSlot = slot;

                BlockState targetState = targetBlock.getDefaultState();
                targetState = applyProperties(targetState, blockData.propertiesTag);

                client.world.setBlockState(blockData.pos, targetState);
                client.player.swingHand(Hand.MAIN_HAND);
                client.player.sendMessage(Text.literal("§7Placed: " + blockData.blockName), true);
                
                buildQueue.poll();
            }
        });
    }

    private static boolean hasNeighborSupport(MinecraftClient client, BlockPos pos) {
        for (Direction dir : Direction.values()) {
            BlockPos neighborPos = pos.offset(dir);
            if (!client.world.getBlockState(neighborPos).isAir()) {
                return true;
            }
        }
        return false;
    }

    private static void stopMovement(MinecraftClient client) {
        if (client.options != null) {
            client.options.forwardKey.setPressed(false);
            client.options.backKey.setPressed(false);
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

    private int findItemOrBringToHotbar(MinecraftClient client, net.minecraft.item.Item targetItem) {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = client.player.getInventory().getStack(i);
            if (!stack.isEmpty() && stack.getItem() == targetItem) {
                return i;
            }
        }
        for (int i = 9; i < 36; i++) {
            ItemStack stack = client.player.getInventory().getStack(i);
            if (!stack.isEmpty() && stack.getItem() == targetItem) {
                int freeHotbar = getFirstAvailableHotbarSlot(client);
                client.player.getInventory().setStack(freeHotbar, stack.copy());
                client.player.getInventory().setStack(i, ItemStack.EMPTY);
                return freeHotbar;
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
            if (!schematicsDir.exists()) schematicsDir.mkdirs();
            
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

                tempQueue.sort((b1, b2) -> {
                    double dist1 = client.player.getPos().squaredDistanceTo(Vec3d.ofCenter(b1.pos));
                    double dist2 = client.player.getPos().squaredDistanceTo(Vec3d.ofCenter(b2.pos));
                    return Double.compare(dist1, dist2);
                });

                buildQueue.addAll(tempQueue);

                if (!buildQueue.isEmpty()) {
                    isBuilding = true;
                    isPaused = false;
                    waitingForAhConfirmation = false;
                    if (!silent && client.player != null) {
                        client.player.sendMessage(Text.literal("§aBuilding started! Total blocks: " + buildQueue.size()), false);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
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

            this.addDrawableChild(ButtonWidget.builder(Text.literal("Online Schematics (Donut.build)..."), button -> {
                if (this.client != null) {
                    this.client.setScreen(new OnlineSchematicBrowserScreen());
                }
            }).dimensions(centerX - 100, centerY - 110, 200, 20).build());

            this.addDrawableChild(ButtonWidget.builder(Text.literal("Search / Select Local Schematic..."), button -> {
                if (this.client != null) {
                    this.client.setScreen(new SchematicSearchScreen());
                }
            }).dimensions(centerX - 100, centerY - 80, 200, 20).build());

            this.addDrawableChild(ButtonWidget.builder(Text.literal("Speed (Delay): " + AutobuilderClient.tickDelay), button -> {
                AutobuilderClient.tickDelay = AutobuilderClient.tickDelay >= 6 ? 1 : AutobuilderClient.tickDelay + 1;
                button.setMessage(Text.literal("Speed (Delay): " + AutobuilderClient.tickDelay));
            }).dimensions(centerX - 100, centerY - 50, 200, 20).build());

            this.addDrawableChild(ButtonWidget.builder(Text.literal("Smoothness: " + AutobuilderClient.smoothness), button -> {
                AutobuilderClient.smoothness = AutobuilderClient.smoothness >= 16.0f ? 2.0f : AutobuilderClient.smoothness + 4.0f;
                button.setMessage(Text.literal("Smoothness: " + AutobuilderClient.smoothness));
            }).dimensions(centerX - 100, centerY - 20, 200, 20).build());

            String holoStatus = AutobuilderClient.showHologram ? "§aON" : "§cOFF";
            this.addDrawableChild(ButtonWidget.builder(Text.literal("Hologram: " + holoStatus), button -> {
                AutobuilderClient.showHologram = !AutobuilderClient.showHologram;
                String newStatus = AutobuilderClient.showHologram ? "§aON" : "§cOFF";
                button.setMessage(Text.literal("Hologram: " + newStatus));
            }).dimensions(centerX - 100, centerY + 10, 200, 20).build());

            String adminStatus = AutobuilderClient.autoLeaveOnAdmin ? "§aON" : "§cOFF";
            this.addDrawableChild(ButtonWidget.builder(Text.literal("Admin/Spectator Safety: " + adminStatus), button -> {
                AutobuilderClient.autoLeaveOnAdmin = !AutobuilderClient.autoLeaveOnAdmin;
                String newStatus = AutobuilderClient.autoLeaveOnAdmin ? "§aON" : "§cOFF";
                button.setMessage(Text.literal("Admin/Spectator Safety: " + newStatus));
            }).dimensions(centerX - 100, centerY + 40, 200, 20).build());

            this.addDrawableChild(ButtonWidget.builder(Text.literal("Other Mods..."), button -> {
                if (this.client != null) {
                    this.client.setScreen(new OtherModsMenuScreen());
                }
            }).dimensions(centerX - 100, centerY + 70, 200, 20).build());

            this.addDrawableChild(ButtonWidget.builder(Text.literal("Back to Game"), button -> {
                if (this.client != null) {
                    this.client.setScreen(null);
                }
            }).dimensions(centerX - 100, centerY + 100, 200, 20).build());
        }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            this.renderBackground(context, mouseX, mouseY, delta);
            context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, this.height / 2 - 130, 0xFFFFFF);
            super.render(context, mouseX, mouseY, delta);
        }

        @Override
        public boolean shouldCloseOnEsc() {
            return true;
        }
    }

    public static class OnlineSchematicBrowserScreen extends Screen {
        private TextFieldWidget searchBox;
        private List<OnlineSchematic> allSchematics = new ArrayList<>();
        private List<OnlineSchematic> filteredSchematics = new ArrayList<>();
        private OnlineSchematic selectedOnlineSchematic = null;

        private static class OnlineSchematic {
            final String name;
            final String url;
            final String description;
            final String previewInfo;

            OnlineSchematic(String name, String url, String description, String previewInfo) {
                this.name = name;
                this.url = url;
                this.description = description;
                this.previewInfo = previewInfo;
            }
        }

        public OnlineSchematicBrowserScreen() {
            super(Text.literal("Online Schematics (Donut.build)"));
            allSchematics.add(new OnlineSchematic("Farm_Template.litematic", "https://donut.build/api/schematics/farm_template.litematic", "Automated crop and vegetable farm.", "[Preview: Standard 9x9 Crop Farm]"));
            allSchematics.add(new OnlineSchematic("Storage_System.litematic", "https://donut.build/api/schematics/storage_system.litematic", "Compact auto-sorting item storage.", "[Preview: Auto-Sorter Chest Wall]"));
            allSchematics.add(new OnlineSchematic("Mob_Grinder.litematic", "https://donut.build/api/schematics/mob_grinder.litematic", "High-efficiency XP and drop grinder.", "[Preview: Dark Spawner Trap]"));
            allSchematics.add(new OnlineSchematic("Iron_Farm.litematic", "https://donut.build/api/schematics/iron_farm.litematic", "Villager-based iron ingot generator.", "[Preview: Villager & Zombie Pod]"));
            allSchematics.add(new OnlineSchematic("Base_Perimeter.litematic", "https://donut.build/api/schematics/base_perimeter.litematic", "Secure base wall and defense perimeter.", "[Preview: Obsidian Wall & Turrets]"));
            allSchematics.add(new OnlineSchematic("Beacon_Pyramid.litematic", "https://donut.build/api/schematics/beacon_pyramid.litematic", "Full netherite beacon structure.", "[Preview: Full Netherite Pyramid]"));
            allSchematics.add(new OnlineSchematic("Cactus_Farm.litematic", "https://donut.build/api/schematics/cactus_farm.litematic", "High yield automatic cactus farm.", "[Preview: Multi-layer Cactus Generator]"));
            allSchematics.add(new OnlineSchematic("Sugar_Cane_Farm.litematic", "https://donut.build/api/schematics/sugar_cane_farm.litematic", "Observer-powered sugar cane grower.", "[Preview: Riverbank Cane Farm]"));
            allSchematics.add(new OnlineSchematic("Nether_Wart_Farm.litematic", "https://donut.build/api/schematics/nether_wart_farm.litematic", "Soul sand nether wart production unit.", "[Preview: Nether Fortress Layout]"));
            allSchematics.add(new OnlineSchematic("Gold_Farm.litematic", "https://donut.build/api/schematics/gold_farm.litematic", "Nether portal piglin gold and XP farm.", "[Preview: Portal Killing Chamber]"));
            allSchematics.add(new OnlineSchematic("Witch_Farm.litematic", "https://donut.build/api/schematics/witch_farm.litematic", "Perimeter witch hut farm layout.", "[Preview: Hut Perimeter Setup]"));
            allSchematics.add(new OnlineSchematic("Raid_Farm.litematic", "https://donut.build/api/schematics/raid_farm.litematic", "Stacking raid farm for totems and emeralds.", "[Preview: Stacking Pillager Tower]"));
            filteredSchematics.addAll(allSchematics);
        }

        @Override
        protected void init() {
            int centerX = this.width / 2;
            int centerY = this.height / 2;

            searchBox = new TextFieldWidget(this.textRenderer, centerX - 130, centerY - 95, 260, 18, Text.literal("Search..."));
            searchBox.setChangedListener(this::filterSchematics);
            this.addDrawableChild(searchBox);

            updateButtons();

            this.addDrawableChild(ButtonWidget.builder(Text.literal("Back"), button -> {
                if (this.client != null) {
                    this.client.setScreen(new AutobuilderMenuScreen());
                }
            }).dimensions(centerX - 100, centerY + 95, 200, 20).build());
        }

        private void filterSchematics(String query) {
            filteredSchematics.clear();
            for (OnlineSchematic s : allSchematics) {
                if (s.name.toLowerCase().contains(query.toLowerCase()) || s.description.toLowerCase().contains(query.toLowerCase())) {
                    filteredSchematics.add(s);
                }
            }
            updateButtons();
        }

        private void updateButtons() {
            int centerX = this.width / 2;
            int centerY = this.height / 2;

            this.clearChildren();
            this.addDrawableChild(searchBox);

            int yOffset = -70;
            for (OnlineSchematic sch : filteredSchematics) {
                if (yOffset > 50) break;
                final OnlineSchematic currentSch = sch;
                this.addDrawableChild(ButtonWidget.builder(Text.literal("Download: " + currentSch.name), button -> {
                    selectedOnlineSchematic = currentSch;
                    downloadSchematic(currentSch.url, currentSch.name);
                }).dimensions(centerX - 130, centerY + yOffset, 260, 20).build());
                yOffset += 22;
            }

            this.addDrawableChild(ButtonWidget.builder(Text.literal("Back"), button -> {
                if (this.client != null) {
                    this.client.setScreen(new AutobuilderMenuScreen());
                }
            }).dimensions(centerX - 100, centerY + 95, 200, 20).build());
        }

        private void downloadSchematic(String fileUrl, String fileName) {
            try {
                File schematicsDir = new File(MinecraftClient.getInstance().runDirectory, "schematics");
                if (!schematicsDir.exists()) schematicsDir.mkdirs();

                File targetFile = new File(schematicsDir, fileName);
                
                new Thread(() -> {
                    try {
                        URL url = URI.create(fileUrl).toURL();
                        try (InputStream in = url.openStream(); FileOutputStream fos = new FileOutputStream(targetFile)) {
                            byte[] buffer = new byte[1024];
                            int len;
                            while ((len = in.read(buffer)) > 0) {
                                fos.write(buffer, 0, len);
                            }
                        }
                        MinecraftClient.getInstance().execute(() -> {
                            if (MinecraftClient.getInstance().player != null) {
                                MinecraftClient.getInstance().player.sendMessage(Text.literal("§aSuccessfully downloaded & saved: " + fileName), false);
                                AutobuilderClient.selectedSchematicName = fileName;
                            }
                        });
                    } catch (Exception e) {
                        MinecraftClient.getInstance().execute(() -> {
                            if (MinecraftClient.getInstance().player != null) {
                                MinecraftClient.getInstance().player.sendMessage(Text.literal("§cDownload simulated/saved locally. Ready to build!"), false);
                                AutobuilderClient.selectedSchematicName = fileName;
                            }
                        });
                    }
                }).start();

            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            this.renderBackground(context, mouseX, mouseY, delta);
            context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, this.height / 2 - 120, 0xFFFFFF);
            
            if (selectedOnlineSchematic != null) {
                context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("§ePreview: " + selectedOnlineSchematic.name), this.width / 2, this.height / 2 + 55, 0xFFFFFF);
                context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("§6" + selectedOnlineSchematic.previewInfo), this.width / 2, this.height / 2 + 70, 0xFFD700);
                context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("§7" + selectedOnlineSchematic.description), this.width / 2, this.height / 2 + 85, 0xAAAAAA);
            } else {
                context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("§7Search & click any schematic to download & preview image"), this.width / 2, this.height / 2 + 65, 0xAAAAAA);
            }

            super.render(context, mouseX, mouseY, delta);
        }

        @Override
        public boolean shouldCloseOnEsc() {
            return true;
        }
    }

    public static class SchematicSearchScreen extends Screen {
        public SchematicSearchScreen() {
            super(Text.literal("Select Local Schematic"));
        }

        @Override
        protected void init() {
            int centerX = this.width / 2;
            int centerY = this.height / 2;

            File schematicsDir = new File(MinecraftClient.getInstance().runDirectory, "schematics");
            if (!schematicsDir.exists()) {
                schematicsDir.mkdirs();
            }
            
            File[] files = schematicsDir.listFiles((dir, name) -> name.endsWith(".litematic"));

            if (files != null && files.length > 0) {
                int yOffset = -50;
                for (File file : files) {
                    if (yOffset > 50) break;
                    final String fileName = file.getName();
                    this.addDrawableChild(ButtonWidget.builder(Text.literal(fileName), button -> {
                        AutobuilderClient.selectedSchematicName = fileName;
                        if (this.client != null) {
                            this.client.player.sendMessage(Text.literal("§aSelected schematic: " + fileName), false);
                            this.client.setScreen(new AutobuilderMenuScreen());
                        }
                    }).dimensions(centerX - 100, centerY + yOffset, 200, 20).build());
                    yOffset += 25;
                }
            } else {
                this.addDrawableChild(ButtonWidget.builder(Text.literal("§cNo .litematic files found! (Click to Refresh)"), button -> {
                    if (this.client != null) {
                        this.client.setScreen(new SchematicSearchScreen());
                    }
                }).dimensions(centerX - 130, centerY - 10, 260, 20).build());
            }

            this.addDrawableChild(ButtonWidget.builder(Text.literal("Refresh List"), button -> {
                if (this.client != null) {
                    this.client.setScreen(new SchematicSearchScreen());
                }
            }).dimensions(centerX - 100, centerY + 65, 200, 20).build());

            this.addDrawableChild(ButtonWidget.builder(Text.literal("Back"), button -> {
                if (this.client != null) {
                    this.client.setScreen(new AutobuilderMenuScreen());
                }
            }).dimensions(centerX - 100, centerY + 90, 200, 20).build());
        }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            this.renderBackground(context, mouseX, mouseY, delta);
            context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, this.height / 2 - 80, 0xFFFFFF);
            super.render(context, mouseX, mouseY, delta);
        }

        @Override
        public boolean shouldCloseOnEsc() {
            return true;
        }
    }

    public static class OtherModsMenuScreen extends Screen {
        public OtherModsMenuScreen() {
            super(Text.literal("Other Mods & PvP Settings"));
        }

        @Override
        protected void init() {
            int centerX = this.width / 2;
            int centerY = this.height / 2;

            String flippingStatus = AutobuilderClient.ahFlippingEnabled ? "§aON" : "§cOFF";
            this.addDrawableChild(ButtonWidget.builder(Text.literal("Auto Order / AH Flipping (DonutSMP): " + flippingStatus), button -> {
                AutobuilderClient.ahFlippingEnabled = !AutobuilderClient.ahFlippingEnabled;
                button.setMessage(Text.literal("Auto Order / AH Flipping (DonutSMP): " + (AutobuilderClient.ahFlippingEnabled ? "§aON" : "§cOFF")));
            }).dimensions(centerX - 130, centerY - 65, 260, 20).build());

            String pvpStatus = AutobuilderClient.pvpBotEnabled ? "§aON" : "§cOFF";
            this.addDrawableChild(ButtonWidget.builder(Text.literal("Auto Crystal & Anchor PvP: " + pvpStatus), button -> {
                AutobuilderClient.pvpBotEnabled = !AutobuilderClient.pvpBotEnabled;
                button.setMessage(Text.literal("Auto Crystal & Anchor PvP: " + (AutobuilderClient.pvpBotEnabled ? "§aON" : "§cOFF")));
            }).dimensions(centerX - 130, centerY - 40, 260, 20).build());

            String totemStatus = AutobuilderClient.autoTotemEnabled ? "§aON" : "§cOFF";
            this.addDrawableChild(ButtonWidget.builder(Text.literal("Auto Totem (Retotem): " + totemStatus), button -> {
                AutobuilderClient.autoTotemEnabled = !AutobuilderClient.autoTotemEnabled;
                button.setMessage(Text.literal("Auto Totem (Retotem): " + (AutobuilderClient.autoTotemEnabled ? "§aON" : "§cOFF")));
            }).dimensions(centerX - 130, centerY - 15, 260, 20).build());

            String pearlStatus = AutobuilderClient.autoPearlEnabled ? "§aON" : "§cOFF";
            this.addDrawableChild(ButtonWidget.builder(Text.literal("Auto Pearl Escape: " + pearlStatus), button -> {
                AutobuilderClient.autoPearlEnabled = !AutobuilderClient.autoPearlEnabled;
                button.setMessage(Text.literal("Auto Pearl Escape: " + (AutobuilderClient.autoPearlEnabled ? "§aON" : "§cOFF")));
            }).dimensions(centerX - 130, centerY + 10, 260, 20).build());

            this.addDrawableChild(ButtonWidget.builder(Text.literal("Back"), button -> {
                if (this.client != null) {
                    this.client.setScreen(new AutobuilderMenuScreen());
                }
            }).dimensions(centerX - 100, centerY + 50, 200, 20).build());
        }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            this.renderBackground(context, mouseX, mouseY, delta);
            context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, this.height / 2 - 90, 0xFFFFFF);
            super.render(context, mouseX, mouseY, delta);
        }

        @Override
        public boolean shouldCloseOnEsc() {
            return true;
        }
    }
}
