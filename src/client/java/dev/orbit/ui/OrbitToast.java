package dev.orbit.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

public final class OrbitToast {
    private static volatile String message = "";
    private static volatile long shownAtMillis;

    private OrbitToast() {
    }

    public static void show(String text) {
        message = text;
        shownAtMillis = System.currentTimeMillis();
    }

    public static void render(GuiGraphics graphics, Font font, int screenWidth, int screenHeight) {
        String currentMessage = message;
        long age = System.currentTimeMillis() - shownAtMillis;
        if (currentMessage.isEmpty() || age < 0L || age >= 3_000L) {
            return;
        }

        OrbitTheme.Palette palette = OrbitTheme.current();
        int width = 220;
        int height = 44;
        float progress = Math.min(1.0F, age / 220.0F);
        float slide = 1.0F - progress * progress * (3.0F - 2.0F * progress);
        float fade = age > 2_500L ? (3_000L - age) / 500.0F : 1.0F;
        int x = screenWidth - 24 - width + Math.round(slide * (width + 20));
        int y = screenHeight - 60;

        OrbitRender.roundedRect(graphics, x, y, width, height, 8, OrbitRender.alpha(0xE616131E, fade));
        OrbitRender.roundedRect(graphics, x + 8, y + 10, 3, 24, 2, OrbitRender.alpha(palette.primary(), fade));
        String text = font.plainSubstrByWidth(currentMessage, width - 32);
        graphics.drawString(font, text, x + 18, y + 17, OrbitRender.alpha(palette.textMain(), fade));
    }
}
