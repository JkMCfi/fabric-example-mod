package com.example;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;

import java.util.LinkedList;
import java.util.Queue;

public class ExampleMod implements ModInitializer {

    public static final String MOD_ID = "autobuilder";
    private boolean isBuildingActive = true;
    private long lastPurchaseTime = 0;
    private final long PURCHASE_COOLDOWN_MS = 2500;

    // Rakennusjono koordinaateille
    private final Queue<BlockPos> buildQueue = new LinkedList<>();

    @Override
    public void onInitialize() {
        System.out.println("[AutoBuilder] Mod initialized successfully!");

        // Lisätään testiksi muutama koordinaatti jonoon
        buildQueue.add(new BlockPos(0, 64, 0));
        buildQueue.add(new BlockPos(1, 64, 0));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (isBuildingActive && client.player != null) {
                runAutoBuilderLoop(client);
            }
        });
    }

    private void runAutoBuilderLoop(MinecraftClient client) {
        String requiredItem = "quartz";

        // 1. Jos tavara puuttuu inventaariosta, ostetaan se /ah kautta
        if (!hasItemInInventory(client, requiredItem)) {
            buyItemFromAuctionHouse(client, requiredItem);
            return;
        }

        // 2. Jos jono on tyhjä, ei tehdä mitään
        if (buildQueue.isEmpty()) {
            return;
        }

        // 3. Otetaan seuraava kohde jonosta ja rakennetaan
        BlockPos nextTarget = buildQueue.peek();
        if (nextTarget != null) {
            placeNextBlock(client, nextTarget);
        }
    }

    private boolean hasItemInInventory(MinecraftClient client, String itemName) {
        for (ItemStack stack : client.player.getInventory().main) {
            if (!stack.isEmpty() && stack.getItem().toString().equalsIgnoreCase(itemName)) {
                return true;
            }
        }
        return false;
    }

    private void buyItemFromAuctionHouse(MinecraftClient client, String itemName) {
        long currentTime = System.currentTimeMillis();

        if (currentTime - lastPurchaseTime < PURCHASE_COOLDOWN_MS) {
            return;
        }

        System.out.println("[AutoBuilder] Missing " + itemName + "! Executing /ah...");

        if (client.player.networkHandler != null) {
            client.player.networkHandler.sendChatCommand("ah " + itemName.toLowerCase());
        }

        lastPurchaseTime = currentTime;
    }

    private void placeNextBlock(MinecraftClient client, BlockPos target) {
        if (client.player == null || client.world == null) return;

        // Käännytään kohteeseen koordinaattien perusteella
        smoothLookAt(client, target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5);

        System.out.println("[AutoBuilder] Placing block at: " + target.toShortString());

        // Poistetaan tehty kohde jonosta, jotta siirrytään seuraavaan
        buildQueue.poll();
    }

    private void smoothLookAt(MinecraftClient client, double targetX, double targetY, double targetZ) {
        if (client.player == null) return;

        double dx = targetX - client.player.getX();
        double dy = targetY - (client.player.getY() + client.player.getEyeHeight(client.player.getPose()));
        double dz = targetZ - client.player.getZ();

        double distanceXZ = Math.sqrt(dx * dx + dz * dz);

        float targetYaw = (float) (Math.atan2(dz, dx) * (180 / Math.PI)) - 90.0F;
        float targetPitch = (float) (-(Math.atan2(dy, distanceXZ) * (180 / Math.PI)));

        float currentYaw = client.player.getYaw();
        float currentPitch = client.player.getPitch();

        float speed = 15.0F;
        client.player.setYaw(currentYaw + limitAngleChange(targetYaw - currentYaw, speed));
        client.player.setPitch(currentPitch + limitAngleChange(targetPitch - currentPitch, speed));
    }

    private float limitAngleChange(float diff, float maxSpeed) {
        if (diff < -maxSpeed) return -maxSpeed;
        if (diff > maxSpeed) return maxSpeed;
        return diff;
    }
}
