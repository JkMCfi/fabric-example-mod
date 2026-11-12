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

    public static void render(GuiGraphics graphics, Font font, int screenWidth) {
        String currentMessage = message;
        long age = System.currentTimeMillis() - shownAtMillis;
        if (currentMessage.isEmpty() || age < 0L || age >= 3_000L) {
            return;
        }

        OrbitTheme.Palette palette = OrbitTheme.current();
        int width = Math.min(360, Math.max(180, font.width(currentMessage) + 32));
        float progress = Math.min(1.0F, age / 220.0F);
        float slide = 1.0F - progress * progress * (3.0F - 2.0F * progress);
        float fade = age > 2_500L ? (3_000L - age) / 500.0F : 1.0F;
        int x = screenWidth - 18 - width + Math.round(slide * (width + 20));
        int y = 18;

        OrbitRender.roundedRect(graphics, x, y, width, 38, 10, OrbitRender.alpha(palette.elevatedPanel(), fade * 0.96F));
        OrbitRender.roundedRect(graphics, x + 7, y + 8, 3, 22, 2, OrbitRender.alpha(palette.primary(), fade));
        graphics.drawString(font, currentMessage, x + 18, y + 13, OrbitRender.alpha(palette.textMain(), fade));
    }
}
