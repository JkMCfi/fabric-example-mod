package dev.stellar;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

public final class StellarClient implements ClientModInitializer {
    public static final String MOD_ID = "stellar";
    private static final SpotifyService MUSIC = new SpotifyService();

    @Override
    public void onInitializeClient() {
        MUSIC.start();
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (!(screen instanceof TitleScreen) && !(screen instanceof GameMenuScreen)) return;
            int x = screen.width - 112;
            int y = screen instanceof TitleScreen ? screen.height - 44 : screen.height / 2 + 84;
            screen.addDrawableChild(ButtonWidget.builder(Text.literal("✦ Stellar"), button ->
                    client.setScreen(new StellarScreen(MUSIC))).dimensions(x, y, 100, 20).build());
        });
    }
}
