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
    private static boolean waitingForAhConfirmation = false;
    
    // Asetukset (Säädettävissä menusta)
    public static int tickDelay = 3; 
    public static float smoothness = 4.0f; 
    public static boolean autoLeaveOnAdmin = false; // Admin/Spectator turva päällä/pois

    private static int tickCounter = 0;
    private static int stuckTimer = 0;
    private static int safetyCheckTimer = 0;

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

            // Tarkistetaan turvaetäisyys admin/spectator-pelaajiin vain moninpelissä
            if (autoLeaveOnAdmin && client.world != null && client.player != null && !client.isInSingleplayer()) {
                safetyCheckTimer++;
                if (safetyCheckTimer > 20) { // Tarkistetaan kerran sekunnissa
                    safetyCheckTimer = 0;
                    if (client.getNetworkHandler() != null) {
                        for (PlayerListEntry entry : client.getNetworkHandler().getPlayerList()) {
                            if (entry.getProfile().getId().equals(client.player.getGameProfile().getId())) continue;
                            
                            // Jos pelaaja on spectatorissa tai admin (gamemode/tila tarkistus tab-listasta)
                            if (entry.getGameMode() == GameMode.SPECTATOR) {
                                isBuilding = false;
                                client.player.sendMessage(Text.literal("§c[AutoBuilder Turva] Spectator havaittu lähettyvillä! Poistutaan palvelimelta..."), false);
                                client.world.disconnect();
                                client.setScreen(new MultiplayerScreen(null));
                                return;
                            }
                        }
                    }
                }
            }

            while (buildKey.wasPressed()) {
                if (client.player != null && client.world != null) {
                    if (waitingForAhConfirmation) {
                        client.player.playSound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0F, 1.0F);
                        client.player.sendMessage(Text.literal("§aAutoBuilder: /ah osto hyväksytty!"), false);
                        client.player.networkHandler.sendChatCommand("ah");
                        waitingForAhConfirmation = false;
                        isBuilding = false;
                        return;
                    }

                    if (isBuilding) {
                        isBuilding = false;
                        client.options.forwardKey.setPressed(false);
                        client.options.jumpKey.setPressed(false);
                        client.options.leftKey.setPressed(false);
                        client.options.rightKey.setPressed(false);
                        client.player.sendMessage(Text.literal("§eAutoBuilder: Pausetettu!"), false);
                    } else {
                        client.player.sendMessage(Text.literal("§aAutoBuilder: Ladataan schematiciä..."), false);
                        loadAndParseSchematic(client);
                    }
                }
            }

            if (isBuilding && client.player != null && client.world != null && client.interactionManager != null) {
                if (buildQueue.isEmpty()) {
                    isBuilding = false;
                    client.options.forwardKey.setPressed(false);
                    client.options.jumpKey.setPressed(false);
                    client.options.leftKey.setPressed(false);
                    client.options.rightKey.setPressed(false);
                    client.player.sendMessage(Text.literal("§aAutoBuilder: Rakennus valmis!"), false);
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

                int slot = findItemInHotbar(client, block);
                
                if (slot == -1) {
                    if (client.player.getAbilities().creativeMode) {
                        slot = getFirstAvailableHotbarSlot(client);
                        ItemStack blockStack = new ItemStack(block);
                        client.interactionManager.clickCreativeStack(blockStack, 36 + slot);
                        client.player.getInventory().setStack(slot, blockStack);
                        client.player.sendMessage(Text.literal("§7Haettu (Creative): " + blockData.blockName), true);
                    } else {
                        isBuilding = false;
                        client.options.forwardKey.setPressed(false);
                        client.options.jumpKey.setPressed(false);
                        waitingForAhConfirmation = true;

                        client.player.playSound(SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), 1.0F, 0.3F);
                        client.player.sendMessage(Text.literal("§c[AutoBuilder] Palikat loppu! /ah hinta ylittää mahdollisesti 200k. §ePaina N uudelleen hyväksyksesi."), false);
                        return;
                    }
                }

                client.player.getInventory().selectedSlot = slot;

                Vec3d targetVec = Vec3d.ofCenter(blockData.pos);
                double distance = client.player.getPos().distanceTo(targetVec);

                // Sulava katseen kääntö kohti palikkaa
                smoothLookAt(client, targetVec);

                // Anti-Stuck: jumiutumisen esto
                if (distance > 4.0) {
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
                    return; 
                } else {
                    client.options.forwardKey.setPressed(false);
                    client.options.jumpKey.setPressed(false);
                    client.options.leftKey.setPressed(false);
                    stuckTimer = 0;
                }

                buildQueue.poll();

                BlockState targetState = block.getDefaultState();
                targetState = applyProperties(targetState, blockData.propertiesTag);

                client.world.setBlockState(blockData.pos, targetState);
                client.player.swingHand(Hand.MAIN_HAND);
                client.player.sendMessage(Text.literal("§7Asetettu: " + blockData.blockName), true);
            }
        });
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

    private void loadAndParseSchematic(MinecraftClient client) {
        try {
            File schematicsDir = new File(client.runDirectory, "schematics");
            File[] files = schematicsDir.listFiles((dir, name) -> name.endsWith(".litematic"));

            if (files == null || files.length == 0) {
                client.player.sendMessage(Text.literal("§cVirhe: .litematic tiedostoja ei löydy!"), false);
                return;
            }

            File schematicFile = files[0];
            client.player.sendMessage(Text.literal("§eLöytyi tiedosto: " + schematicFile.getName()), false);

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
                    waitingForAhConfirmation = false;
                    client.player.sendMessage(Text.literal("§aRakennus aloitettu! Palikoita yhteensä: " + buildQueue.size()), false);
                } else {
                    client.player.sendMessage(Text.literal("§cEi rakennettavia palikoita löydynyt!"), false);
                }
            } else {
                client.player.sendMessage(Text.literal("§cVirheellinen litematic: 'Regions' puuttuu!"), false);
            }
        } catch (Exception e) {
            e.printStackTrace();
            if (client.player != null) {
                client.player.sendMessage(Text.literal("§cVirhe luettaessa schematiciä: " + e.getMessage()), false);
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

    // Asetusmenu (Avautuu B-kirjaimella)
    public static class AutobuilderMenuScreen extends Screen {
        public AutobuilderMenuScreen() {
            super(Text.literal("AutoBuilder Asetukset"));
        }

        @Override
        protected void init() {
            int centerX = this.width / 2;
            int centerY = this.height / 2;

            this.addDrawableChild(ButtonWidget.builder(Text.literal("Nopeus (Viive): " + AutobuilderClient.tickDelay), button -> {
                AutobuilderClient.tickDelay = AutobuilderClient.tickDelay >= 6 ? 1 : AutobuilderClient.tickDelay + 1;
                button.setMessage(Text.literal("Nopeus (Viive): " + AutobuilderClient.tickDelay));
            }).dimensions(centerX - 100, centerY - 50, 200, 20).build());

            this.addDrawableChild(ButtonWidget.builder(Text.literal("Katseen sulavuus: " + AutobuilderClient.smoothness), button -> {
                AutobuilderClient.smoothness = AutobuilderClient.smoothness >= 16.0f ? 2.0f : AutobuilderClient.smoothness + 2.0f;
                button.setMessage(Text.literal("Katseen sulavuus: " + AutobuilderClient.smoothness));
            }).dimensions(centerX - 100, centerY - 20, 200, 20).build());

            // Admin / Spectator turva kytkin
            String adminStatus = AutobuilderClient.autoLeaveOnAdmin ? "§aPäällä" : "§cPois";
            this.addDrawableChild(ButtonWidget.builder(Text.literal("Admin/Spectator Turva: " + adminStatus), button -> {
                AutobuilderClient.autoLeaveOnAdmin = !AutobuilderClient.autoLeaveOnAdmin;
                String newStatus = AutobuilderClient.autoLeaveOnAdmin ? "§aPäällä" : "§cPois";
                button.setMessage(Text.literal("Admin/Spectator Turva: " + newStatus));
            }).dimensions(centerX - 100, centerY + 10, 200, 20).build());

            this.addDrawableChild(ButtonWidget.builder(Text.literal("Takaisin peliin"), button -> {
                if (this.client != null) {
                    this.client.setScreen(null);
                }
            }).dimensions(centerX - 100, centerY + 50, 200, 20).build());
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
}
