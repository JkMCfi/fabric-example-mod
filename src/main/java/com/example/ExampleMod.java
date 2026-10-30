package com.example;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;

public class ExampleMod implements ModInitializer {

    public static final String MOD_ID = "autobuilder";
    private boolean isBuildingActive = true;
    private long lastPurchaseTime = 0;
    private final long PURCHASE_COOLDOWN_MS = 2500;

    @Override
    public void onInitialize() {
        System.out.println("[AutoBuilder] Mod initialized successfully!");

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (isBuildingActive && client.player != null) {
                runAutoBuilderLoop(client);
            }
        });
    }

    private void runAutoBuilderLoop(MinecraftClient client) {
        String requiredItem = "quartz";

        if (!hasItemInInventory(client, requiredItem)) {
            buyItemFromAuctionHouse(client, requiredItem);
            return;
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
}
