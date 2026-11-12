package dev.orbit.ui;

import dev.orbit.music.PartyStatePayload;
import dev.orbit.music.PartySync;
import dev.orbit.music.PartySyncClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class OrbitScreen extends Screen {
    private static final int REQUIRED_LOGO_CLICKS = 20;

    private int logoClicks;
    private boolean moderatorPanelOpen;
    private OrbitButton logoButton;
    private OrbitButton themeButton;
    private float cardScale = 1.0F;
    private long lastRenderNanos;

    public OrbitScreen() {
        super(Component.literal("ORBIT"));
    }

    @Override
    protected void init() {
        int bottom = this.height - 44;
        logoButton = addRenderableWidget(new OrbitButton(
            20, 18, 168, 34, Component.literal("ORBIT"), OrbitButton.Icon.ORBIT, this::onLogoClicked
        ));
        themeButton = addRenderableWidget(new OrbitButton(
            20, bottom, 220, 32, Component.literal("Theme: " + OrbitTheme.current().name()), OrbitButton.Icon.PALETTE, this::cycleTheme
        ));
        addRenderableWidget(new OrbitButton(
            this.width - 54, 18, 34, 34, Component.literal("Close"), OrbitButton.Icon.CLOSE, this::onClose
        ));
    }

    private void onLogoClicked() {
        if (moderatorPanelOpen) {
            return;
        }

        logoClicks = Math.min(logoClicks + 1, REQUIRED_LOGO_CLICKS);
        logoButton.setMessage(Component.literal(logoClicks == 0 ? "ORBIT" : "ORBIT (" + logoClicks + "/20)"));
        if (logoClicks < REQUIRED_LOGO_CLICKS) {
            return;
        }

        if (hasModeratorAccess()) {
            moderatorPanelOpen = true;
            OrbitToast.show("Moderator tools unlocked");
        } else {
            OrbitToast.show("Moderator tools require single-player owner permissions");
        }
    }

    private void cycleTheme() {
        OrbitTheme.Palette palette = OrbitTheme.cycle();
        themeButton.setMessage(Component.literal("Theme: " + palette.name()));
        OrbitToast.show("Theme: " + palette.name());
    }

    private boolean hasModeratorAccess() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player != null
            && minecraft.hasSingleplayerServer()
            && minecraft.getSingleplayerServer() != null
            && minecraft.getSingleplayerServer().getPlayerList().isOp(minecraft.player.nameAndId());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        OrbitTheme.Palette palette = OrbitTheme.current();
        graphics.blurBeforeThisStratum();
        graphics.fillGradient(
            0, 0, this.width, this.height,
            OrbitRender.alpha(palette.background(), 0.88F),
            OrbitRender.alpha(palette.secondaryBackground(), 0.95F)
        );

        int panelX = 20;
        int panelY = 68;
        int panelWidth = Math.max(100, this.width - 40);
        int panelHeight = Math.max(100, this.height - 126);
        drawMainPanel(graphics, panelX, panelY, panelWidth, panelHeight, mouseX, mouseY, palette);

        graphics.drawString(this.font, "MUSIC & ATMOSPHERE", 22, 56, palette.textMuted());
        super.render(graphics, mouseX, mouseY, delta);
        OrbitToast.render(graphics, this.font, this.width);
    }

    private void drawMainPanel(
        GuiGraphics graphics,
        int x,
        int y,
        int width,
        int height,
        int mouseX,
        int mouseY,
        OrbitTheme.Palette palette
    ) {
        long now = System.nanoTime();
        float frameSeconds = lastRenderNanos == 0L ? 0.016F
            : Math.min(0.05F, Math.max(0.0F, (now - lastRenderNanos) / 1_000_000_000.0F));
        lastRenderNanos = now;
        boolean hovering = mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
        float targetScale = hovering ? 1.008F : 1.0F;
        cardScale += (targetScale - cardScale) * (1.0F - (float) Math.exp(-frameSeconds * 8.0F));

        graphics.pose().pushMatrix();
        graphics.pose().translate(x + width / 2.0F, y + height / 2.0F);
        graphics.pose().scale(cardScale, cardScale);
        graphics.pose().translate(-(x + width / 2.0F), -(y + height / 2.0F));
        OrbitRender.roundedRect(graphics, x, y, width, height, 16, palette.panel());
        OrbitRender.roundedRect(graphics, x + 1, y + 1, width - 2, 1, 1, OrbitRender.alpha(palette.primary(), 0.5F));
        graphics.pose().popMatrix();

        if (width >= 600) {
            drawWideLayout(graphics, x, y, width, height, mouseX, mouseY, palette);
        } else {
            drawCompactLayout(graphics, x, y, width, height, mouseX, mouseY, palette);
        }
    }

    private void drawWideLayout(
        GuiGraphics graphics,
        int x,
        int y,
        int width,
        int height,
        int mouseX,
        int mouseY,
        OrbitTheme.Palette palette
    ) {
        int leftWidth = Math.min(300, width * 2 / 5);
        int cardX = x + 16;
        int cardY = y + 16;
        int cardHeight = height - 32;
        OrbitRender.roundedRect(graphics, cardX, cardY, leftWidth, cardHeight, 12, OrbitRender.alpha(palette.elevatedPanel(), 0.85F));

        int centerX = cardX + leftWidth / 2;
        int centerY = cardY + Math.min(cardHeight / 2 - 12, 104);
        int radius = Math.max(22, Math.min(68, Math.min(leftWidth / 2 - 24, cardHeight / 3)));
        drawVinyl(graphics, centerX, centerY, radius, mouseX, mouseY, palette);
        graphics.drawCenteredString(this.font, "ORBIT SESSION", centerX, cardY + cardHeight - 38, palette.textMain());
        graphics.drawCenteredString(this.font, "LOCAL TRACKS / SHARED CONTROLS", centerX, cardY + cardHeight - 22, palette.textMuted());

        int contentX = cardX + leftWidth + 26;
        int contentWidth = Math.max(60, x + width - contentX - 20);
        drawSessionInfo(graphics, contentX, cardY + 18, contentWidth, cardHeight - 36, palette);
    }

    private void drawCompactLayout(
        GuiGraphics graphics,
        int x,
        int y,
        int width,
        int height,
        int mouseX,
        int mouseY,
        OrbitTheme.Palette palette
    ) {
        int centerX = x + width / 2;
        int radius = Math.max(18, Math.min(46, width / 5));
        int centerY = y + radius + 24;
        drawVinyl(graphics, centerX, centerY, radius, mouseX, mouseY, palette);
        int contentY = centerY + radius + 20;
        drawSessionInfo(graphics, x + 16, contentY, width - 32, y + height - contentY - 16, palette);
    }

    private void drawSessionInfo(GuiGraphics graphics, int x, int y, int width, int height, OrbitTheme.Palette palette) {
        PartyStatePayload state = PartySyncClient.getLatestState();
        graphics.drawString(this.font, moderatorPanelOpen ? "Moderator tools" : "Party playback", x, y, palette.textMain());

        int statusY = y + 28;
        if (state == null) {
            drawPlaybackIcon(graphics, x + 8, statusY + 7, PartySync.IDLE, palette);
            graphics.drawString(this.font, "No synced party", x + 24, statusY, palette.textSecondary());
            graphics.drawString(this.font, "Use /orbit party create <name>", x, statusY + 25, palette.textMuted());
            graphics.drawString(this.font, "Others join with /orbit party join <name>", x, statusY + 43, palette.textMuted());
        } else {
            String playback = switch (state.playbackState()) {
                case PartySync.PLAYING -> "Playing";
                case PartySync.PAUSED -> "Paused";
                case PartySync.STOPPED -> "Stopped";
                default -> "Idle";
            };
            drawPlaybackIcon(graphics, x + 8, statusY + 7, state.playbackState(), palette);
            graphics.drawString(this.font, "Party: " + state.partyName(), x + 24, statusY, palette.textSecondary());
            graphics.drawString(this.font, playback + " - " + state.trackId(), x, statusY + 25, palette.lavender());
            graphics.drawString(this.font, "Sync position: " + formatTime(PartySyncClient.getEstimatedPositionMillis()), x, statusY + 43, palette.textMuted());
        }

        if (moderatorPanelOpen && height > 130) {
            drawModeratorInfo(graphics, x, y + height - 82, palette);
        }
    }

    private void drawModeratorInfo(GuiGraphics graphics, int x, int y, OrbitTheme.Palette palette) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        String dimension = minecraft.level == null ? "Not in a world" : minecraft.level.dimension().identifier().toString();
        String position = minecraft.player.blockPosition().toShortString();
        graphics.drawString(this.font, "Player: " + minecraft.player.getName().getString(), x, y, palette.textSecondary());
        graphics.drawString(this.font, "Dimension: " + dimension + "  Position: " + position, x, y + 18, palette.textMuted());
    }

    private void drawVinyl(
        GuiGraphics graphics,
        int centerX,
        int centerY,
        int radius,
        int mouseX,
        int mouseY,
        OrbitTheme.Palette palette
    ) {
        boolean hovering = Math.abs(mouseX - centerX) < radius && Math.abs(mouseY - centerY) < radius;
        float squash = hovering ? 0.92F : 1.0F;
        float tilt = hovering ? (mouseX - centerX) / (float) radius * 0.12F : 0.0F;
        drawEllipse(graphics, centerX + 4, centerY + 6, radius, Math.round(radius * squash), OrbitRender.alpha(0xFF000000, 0.38F));
        drawEllipse(graphics, centerX, centerY, radius, Math.round(radius * squash), 0xFF100E15);
        for (int ring = 1; ring <= 4; ring++) {
            int ringRadius = radius * (ring + 1) / 6;
            drawEllipse(graphics, centerX, centerY, ringRadius, Math.max(1, Math.round(ringRadius * squash)), ring % 2 == 0 ? 0xFF292431 : 0xFF1A1720);
        }

        long now = System.currentTimeMillis();
        PartyStatePayload partyState = PartySyncClient.getLatestState();
        float speed = partyState != null && partyState.playbackState() == PartySync.PLAYING ? 0.0032F : 0.0008F;
        float rotation = (now % 100_000L) * speed + tilt;
        for (int mark = 0; mark < 3; mark++) {
            double angle = rotation + mark * Math.PI * 2.0 / 3.0;
            int x1 = centerX + Math.round((float) Math.cos(angle) * radius * 0.35F);
            int y1 = centerY + Math.round((float) Math.sin(angle) * radius * 0.35F * squash);
            int x2 = centerX + Math.round((float) Math.cos(angle) * radius * 0.83F);
            int y2 = centerY + Math.round((float) Math.sin(angle) * radius * 0.83F * squash);
            OrbitRender.line(graphics, x1, y1, x2, y2, 1, OrbitRender.alpha(palette.lavender(), 0.36F));
        }
        drawEllipse(graphics, centerX, centerY, Math.max(7, radius / 4), Math.max(6, Math.round(radius / 4.0F * squash)), palette.secondary());
        OrbitRender.circle(graphics, centerX, centerY, Math.max(2, radius / 12), palette.lavender());
        OrbitRender.circle(graphics, centerX - radius / 2, centerY - radius / 3, 2, palette.primary());
    }

    private void drawEllipse(GuiGraphics graphics, int centerX, int centerY, int radiusX, int radiusY, int color) {
        for (int row = -radiusY; row <= radiusY; row++) {
            double ratio = row / (double) Math.max(1, radiusY);
            int halfWidth = (int) Math.round(radiusX * Math.sqrt(Math.max(0.0, 1.0 - ratio * ratio)));
            graphics.fill(centerX - halfWidth, centerY + row, centerX + halfWidth + 1, centerY + row + 1, color);
        }
    }

    private void drawPlaybackIcon(GuiGraphics graphics, int centerX, int centerY, int state, OrbitTheme.Palette palette) {
        int color = state == PartySync.PLAYING ? palette.primary() : palette.textSecondary();
        if (state == PartySync.PLAYING) {
            OrbitRender.line(graphics, centerX - 4, centerY - 6, centerX + 6, centerY, 2, color);
            OrbitRender.line(graphics, centerX + 6, centerY, centerX - 4, centerY + 6, 2, color);
            OrbitRender.line(graphics, centerX - 4, centerY + 6, centerX - 4, centerY - 6, 2, color);
        } else if (state == PartySync.PAUSED) {
            graphics.fill(centerX - 5, centerY - 6, centerX - 1, centerY + 7, color);
            graphics.fill(centerX + 2, centerY - 6, centerX + 6, centerY + 7, color);
        } else {
            OrbitRender.circle(graphics, centerX, centerY, 5, OrbitRender.alpha(color, 0.5F));
            OrbitRender.circle(graphics, centerX, centerY, 2, palette.panel());
        }
    }

    private String formatTime(long timeMillis) {
        long seconds = Math.max(0L, timeMillis) / 1000L;
        return String.format("%d:%02d", seconds / 60L, seconds % 60L);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
