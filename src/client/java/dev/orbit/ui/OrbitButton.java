package dev.orbit.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.joml.Matrix3x2fStack;

public final class OrbitButton extends AbstractWidget {
    public enum Icon {
        ORBIT,
        PALETTE,
        CLOSE
    }

    private final Runnable action;
    private final Icon icon;
    private float hoverScale = 1.0F;
    private long lastRenderNanos;
    private long rippleStartedAt;

    public OrbitButton(int x, int y, int width, int height, Component label, Icon icon, Runnable action) {
        super(x, y, width, height, label);
        this.icon = icon;
        this.action = action;
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        OrbitTheme.Palette palette = OrbitTheme.current();
        long now = System.nanoTime();
        float frameSeconds = lastRenderNanos == 0L ? 0.016F
            : Math.min(0.05F, Math.max(0.0F, (now - lastRenderNanos) / 1_000_000_000.0F));
        lastRenderNanos = now;
        float targetScale = isHoveredOrFocused() ? 1.035F : 1.0F;
        hoverScale += (targetScale - hoverScale) * (1.0F - (float) Math.exp(-frameSeconds * 14.0F));

        Matrix3x2fStack pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(getX() + width / 2.0F, getY() + height / 2.0F);
        pose.scale(hoverScale, hoverScale);
        pose.translate(-(getX() + width / 2.0F), -(getY() + height / 2.0F));

        int fill = isHoveredOrFocused() ? palette.elevatedPanel() : palette.panel();
        OrbitRender.roundedRect(graphics, getX(), getY(), width, height, 8, fill);
        int border = isHoveredOrFocused() ? OrbitRender.alpha(palette.primary(), 0.9F) : palette.border();
        graphics.fill(getX() + 8, getY(), getX() + width - 8, getY() + 1, border);
        graphics.fill(getX() + 8, getY() + height - 1, getX() + width - 8, getY() + height, border);
        graphics.fill(getX(), getY() + 8, getX() + 1, getY() + height - 8, border);
        graphics.fill(getX() + width - 1, getY() + 8, getX() + width, getY() + height - 8, border);

        drawRipple(graphics, palette, now);
        drawIcon(graphics, palette);
        if (icon != Icon.CLOSE) {
            var font = Minecraft.getInstance().font;
            graphics.drawString(
                font,
                getMessage(),
                getX() + 34,
                getY() + (height - font.lineHeight) / 2,
                palette.textMain()
            );
        }
        pose.popMatrix();
    }

    private void drawRipple(GuiGraphics graphics, OrbitTheme.Palette palette, long now) {
        if (rippleStartedAt == 0L) {
            return;
        }
        float progress = (now - rippleStartedAt) / 420_000_000.0F;
        if (progress >= 1.0F) {
            rippleStartedAt = 0L;
            return;
        }

        int radius = Math.max(1, Math.round(Math.max(width, height) * progress));
        int centerX = getX() + width / 2;
        int centerY = getY() + height / 2;
        int color = OrbitRender.alpha(palette.primary(), (1.0F - progress) * 0.32F);
        for (int row = Math.max(-radius, -height / 2); row <= Math.min(radius, height / 2); row++) {
            int halfWidth = (int) Math.sqrt(Math.max(0, radius * radius - row * row));
            int left = Math.max(getX(), centerX - halfWidth);
            int right = Math.min(getX() + width, centerX + halfWidth + 1);
            if (right > left) {
                graphics.fill(left, centerY + row, right, centerY + row + 1, color);
            }
        }
    }

    private void drawIcon(GuiGraphics graphics, OrbitTheme.Palette palette) {
        int centerX = getX() + 17;
        int centerY = getY() + height / 2;
        int primary = palette.primary();
        switch (icon) {
            case ORBIT -> {
                OrbitRender.circle(graphics, centerX, centerY, 8, OrbitRender.alpha(primary, 0.22F));
                OrbitRender.circle(graphics, centerX, centerY, 5, palette.secondaryBackground());
                OrbitRender.line(graphics, centerX - 7, centerY, centerX + 7, centerY, 1, palette.lavender());
                OrbitRender.circle(graphics, centerX, centerY, 2, primary);
            }
            case PALETTE -> {
                graphics.fill(centerX - 6, centerY - 6, centerX - 1, centerY - 1, primary);
                graphics.fill(centerX + 1, centerY - 6, centerX + 6, centerY - 1, palette.secondary());
                graphics.fill(centerX - 6, centerY + 1, centerX - 1, centerY + 6, palette.lavender());
                graphics.fill(centerX + 1, centerY + 1, centerX + 6, centerY + 6, palette.success());
            }
            case CLOSE -> {
                OrbitRender.line(graphics, centerX - 5, centerY - 5, centerX + 5, centerY + 5, 2, palette.textSecondary());
                OrbitRender.line(graphics, centerX + 5, centerY - 5, centerX - 5, centerY + 5, 2, palette.textSecondary());
            }
        }
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        rippleStartedAt = System.nanoTime();
        action.run();
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
