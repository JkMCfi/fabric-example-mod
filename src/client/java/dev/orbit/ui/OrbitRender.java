package dev.orbit.ui;

import net.minecraft.client.gui.GuiGraphics;

public final class OrbitRender {
    private OrbitRender() {
    }

    public static void roundedRect(GuiGraphics graphics, int x, int y, int width, int height, int radius, int color) {
        int corner = Math.max(0, Math.min(radius, Math.min(width, height) / 2));
        for (int row = 0; row < height; row++) {
            double edgeDistance = row < corner ? corner - row - 0.5
                : row >= height - corner ? row - (height - corner) + 0.5
                : 0;
            int inset = edgeDistance == 0 ? 0
                : corner - (int) Math.floor(Math.sqrt(Math.max(0, corner * corner - edgeDistance * edgeDistance)));
            graphics.fill(x + inset, y + row, x + width - inset, y + row + 1, color);
        }
    }

    public static void circle(GuiGraphics graphics, int centerX, int centerY, int radius, int color) {
        for (int row = -radius; row <= radius; row++) {
            int halfWidth = (int) Math.sqrt(Math.max(0, radius * radius - row * row));
            graphics.fill(centerX - halfWidth, centerY + row, centerX + halfWidth + 1, centerY + row + 1, color);
        }
    }

    public static void line(GuiGraphics graphics, int x1, int y1, int x2, int y2, int thickness, int color) {
        int dx = Math.abs(x2 - x1);
        int dy = Math.abs(y2 - y1);
        int steps = Math.max(dx, dy);
        if (steps == 0) {
            graphics.fill(x1, y1, x1 + thickness, y1 + thickness, color);
            return;
        }
        for (int step = 0; step <= steps; step++) {
            int x = x1 + (x2 - x1) * step / steps;
            int y = y1 + (y2 - y1) * step / steps;
            graphics.fill(x, y, x + thickness, y + thickness, color);
        }
    }

    public static int alpha(int color, float alpha) {
        int a = Math.round(Math.max(0.0F, Math.min(1.0F, alpha)) * 255.0F);
        return (a << 24) | (color & 0x00FFFFFF);
    }
}
