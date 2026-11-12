package dev.orbit.ui;

import dev.orbit.music.PartyStatePayload;
import dev.orbit.music.PartySync;
import dev.orbit.music.PartySyncClient;
import dev.orbit.performance.PerformancePreset;
import dev.orbit.settings.SettingsManager;
import dev.orbit.skies.SkyRenderer;
import dev.orbit.skies.SkyType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class OrbitScreen extends Screen {
    private static final int CANVAS_WIDTH = 1280;
    private static final int CANVAS_HEIGHT = 720;
    private static final int WINDOW_X = 370;
    private static final int WINDOW_Y = 60;
    private static final int WINDOW_WIDTH = 870;
    private static final int WINDOW_HEIGHT = 600;
    private static final int SIDEBAR_WIDTH = 210;
    private static final int CONTENT_X = 604;
    private static final int CONTENT_WIDTH = 612;
    private static final Tab[] TABS = {
        Tab.OVERVIEW, Tab.SKIES, Tab.LYRICS, Tab.STYLES, Tab.COMMUNITY,
        Tab.HUD, Tab.PERFORMANCE, Tab.SETTINGS, Tab.INFORMATION
    };

    private final List<ScreenButton> buttons = new ArrayList<>();
    private Tab activeTab = Tab.OVERVIEW;
    private float scaleFactor = 1.0F;
    private float offsetX;
    private float offsetY;
    private int logoClicks;
    private boolean moderatorPanelOpen;
    private int pointerX;
    private int pointerY;

    public OrbitScreen() {
        super(Component.literal("ORBIT"));
    }

    @Override
    protected void init() {
        super.init();
        buttons.clear();
        scaleFactor = Math.min(this.width / (float) CANVAS_WIDTH, this.height / (float) CANVAS_HEIGHT);
        if (!Float.isFinite(scaleFactor) || scaleFactor <= 0.0F) {
            scaleFactor = 0.001F;
        }
        offsetX = (this.width - CANVAS_WIDTH * scaleFactor) / 2.0F;
        offsetY = (this.height - CANVAS_HEIGHT * scaleFactor) / 2.0F;

        addButton(390, 82, 44, 34, ButtonStyle.BRAND, "ob", "OB", "", null, this::onLogoClicked, null);
        addButton(1176, 80, 28, 28, ButtonStyle.CONTROL, "close", "", "", null, this::onClose, null);

        for (int index = 0; index < TABS.length; index++) {
            Tab tab = TABS[index];
            addButton(390, 146 + index * 38, 170, 32, ButtonStyle.NAVIGATION,
                tab.icon, tab.title, "", tab, () -> activeTab = tab, null);
        }

        String[] featureLabels = {"Skies", "Lyrics", "Styles", "Performance", "HUD", "Settings"};
        String[] featureDetails = {
            "20 skies - Synthwave, Aurora, Sakura and more",
            "Follow synchronized lyrics while you play",
            "Switch accent themes and tune the look",
            "Balance visual effects and frame rate",
            "Show or hide the in-game player widget",
            "Manage playback and interface preferences"
        };
        Tab[] featureTabs = {Tab.SKIES, Tab.LYRICS, Tab.STYLES, Tab.PERFORMANCE, Tab.HUD, Tab.SETTINGS};
        for (int index = 0; index < featureTabs.length; index++) {
            int column = index % 3;
            int row = index / 3;
            Tab tab = featureTabs[index];
            addButton(CONTENT_X + column * 200, 276 + row * 114, 190, 104, ButtonStyle.FEATURE,
                tab.icon, featureLabels[index], featureDetails[index], tab, () -> activeTab = tab, Tab.OVERVIEW);
        }

        SkyType[] skies = SkyType.values();
        for (int index = 0; index < skies.length; index++) {
            SkyType sky = skies[index];
            addButton(CONTENT_X + index % 4 * 153, 164 + index / 4 * 58, 147, 50, ButtonStyle.SKY,
                "star", sky.name().replace('_', ' '), "", null, () -> selectSky(sky), Tab.SKIES);
        }

        for (int index = 0; index < OrbitTheme.paletteCount(); index++) {
            int paletteIndex = index;
            int column = index % 2;
            int row = index / 2;
            addButton(CONTENT_X + column * 270, 180 + row * 68, 258, 56, ButtonStyle.ACTION,
                "wand", OrbitTheme.paletteAt(index).name(), "Apply this accent palette",
                null, () -> selectTheme(paletteIndex), Tab.STYLES);
        }

        for (int index = 0; index < PerformancePreset.values().length; index++) {
            PerformancePreset preset = PerformancePreset.values()[index];
            addButton(CONTENT_X + index * 118, 190, 110, 48, ButtonStyle.PILL,
                "bolt", titleCase(preset.name()), "", null, () -> selectPerformance(preset), Tab.PERFORMANCE);
        }

        addButton(CONTENT_X, 188, 294, 62, ButtonStyle.ACTION, "monitor", "HUD widget",
            "Show or hide the in-game track display", null, this::toggleHud, Tab.HUD);
        addButton(CONTENT_X + 306, 188, 294, 62, ButtonStyle.ACTION, "loop", "Repeat",
            "Repeat the current playlist", null, this::toggleRepeat, Tab.HUD);
        addButton(CONTENT_X, 188, 294, 62, ButtonStyle.ACTION, "shuffle", "Shuffle",
            "Mix the local track queue", null, this::toggleShuffle, Tab.SETTINGS);
        addButton(CONTENT_X + 306, 188, 294, 62, ButtonStyle.ACTION, "loop", "Repeat",
            "Repeat the current playlist", null, this::toggleRepeat, Tab.SETTINGS);
        addButton(CONTENT_X, 270, 294, 62, ButtonStyle.ACTION, "palette", "Color theme",
            "Choose a saved accent palette", null, () -> activeTab = Tab.STYLES, Tab.SETTINGS);
        addButton(CONTENT_X + 306, 270, 294, 62, ButtonStyle.ACTION, "gear", "Open controls",
            "Use O to open Orbit; Escape closes it", null, () -> OrbitToast.show("Open Orbit with O"), Tab.SETTINGS);

        addButton(1080, 546, 28, 28, ButtonStyle.CONTROL, "prev", "", "",
            null, () -> showPartyControlMessage("Previous track"), null);
        addButton(1116, 542, 36, 36, ButtonStyle.CONTROL, "play", "", "",
            null, this::togglePartyPlayback, null);
        addButton(1160, 546, 28, 28, ButtonStyle.CONTROL, "next", "", "",
            null, () -> showPartyControlMessage("Next track"), null);
        addButton(52, 620, 34, 30, ButtonStyle.CONTROL, "prev", "", "",
            null, () -> showPartyControlMessage("Previous track"), null);
        addButton(96, 617, 36, 36, ButtonStyle.CONTROL, "play", "", "",
            null, this::togglePartyPlayback, null);
        addButton(142, 620, 34, 30, ButtonStyle.CONTROL, "next", "", "",
            null, () -> showPartyControlMessage("Next track"), null);
        addButton(188, 620, 34, 30, ButtonStyle.CONTROL, "loop", "", "",
            null, this::toggleRepeat, null);
    }

    private ScreenButton addButton(
        int x,
        int y,
        int width,
        int height,
        ButtonStyle style,
        String icon,
        String label,
        String detail,
        Tab destination,
        Runnable action,
        Tab visibleOn
    ) {
        ScreenButton button = new ScreenButton(x, y, width, height, style, icon, label, detail, destination, action, visibleOn);
        buttons.add(button);
        return addRenderableWidget(button);
    }

    private void onLogoClicked() {
        logoClicks = Math.min(20, logoClicks + 1);
        if (logoClicks < 20) {
            OrbitToast.show("ORBIT logo: " + logoClicks + "/20");
            return;
        }
        if (hasModeratorAccess()) {
            moderatorPanelOpen = true;
            activeTab = Tab.INFORMATION;
            OrbitToast.show("Moderator tools unlocked");
        } else {
            OrbitToast.show("Moderator tools require single-player owner permissions");
        }
    }

    private boolean hasModeratorAccess() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player != null
            && minecraft.hasSingleplayerServer()
            && minecraft.getSingleplayerServer() != null
            && minecraft.getSingleplayerServer().getPlayerList().isOp(minecraft.player.nameAndId());
    }

    private void selectSky(SkyType sky) {
        SettingsManager.get().getSettings().activeSky = sky.name();
        SkyRenderer.setActiveSky(sky);
        SettingsManager.get().save();
        OrbitToast.show("Sky selected: " + sky.name().replace('_', ' '));
    }

    private void selectTheme(int index) {
        OrbitTheme.Palette palette = OrbitTheme.select(index);
        OrbitToast.show("Theme: " + palette.name());
    }

    private void selectPerformance(PerformancePreset preset) {
        SettingsManager.get().getSettings().performancePreset = preset.name();
        SettingsManager.get().save();
        OrbitToast.show("Performance: " + titleCase(preset.name()));
    }

    private void toggleHud() {
        SettingsManager.OrbitSettings settings = SettingsManager.get().getSettings();
        settings.hudEnabled = !settings.hudEnabled;
        SettingsManager.get().save();
        OrbitToast.show("HUD " + (settings.hudEnabled ? "enabled" : "disabled"));
    }

    private void toggleRepeat() {
        SettingsManager.OrbitSettings settings = SettingsManager.get().getSettings();
        settings.repeat = !settings.repeat;
        SettingsManager.get().save();
        OrbitToast.show("Repeat " + (settings.repeat ? "enabled" : "disabled"));
    }

    private void toggleShuffle() {
        SettingsManager.OrbitSettings settings = SettingsManager.get().getSettings();
        settings.shuffle = !settings.shuffle;
        SettingsManager.get().save();
        OrbitToast.show("Shuffle " + (settings.shuffle ? "enabled" : "disabled"));
    }

    private void togglePartyPlayback() {
        Minecraft minecraft = Minecraft.getInstance();
        PartyStatePayload state = PartySyncClient.getLatestState();
        if (minecraft.player == null || state == null || state.trackId().isEmpty()) {
            OrbitToast.show("Join a party and select a track first");
            return;
        }
        String command = state.playbackState() == PartySync.PLAYING ? "pause" : "resume";
        minecraft.player.connection.sendCommand("orbit party " + command);
    }

    private void showPartyControlMessage(String control) {
        if (PartySyncClient.getLatestState() == null) {
            OrbitToast.show("Join an Orbit party to use " + control.toLowerCase(Locale.ROOT));
        } else {
            OrbitToast.show("Party sync does not have a track queue yet");
        }
    }

    private void drawBackdrop(GuiGraphics graphics, OrbitTheme.Palette palette) {
        graphics.fillGradient(0, 0, this.width, this.height,
            OrbitRender.alpha(palette.background(), 0.72F),
            OrbitRender.alpha(palette.secondaryBackground(), 0.88F));
    }

    private void drawWindow(GuiGraphics graphics, OrbitTheme.Palette palette) {
        OrbitRender.roundedRect(graphics, WINDOW_X + 4, WINDOW_Y + 6, WINDOW_WIDTH, WINDOW_HEIGHT, 22,
            OrbitRender.alpha(0xFF000000, 0.42F));
        OrbitRender.roundedRect(graphics, WINDOW_X, WINDOW_Y, WINDOW_WIDTH, WINDOW_HEIGHT, 19,
            OrbitRender.alpha(0xFFFFFFFF, 0.2F));
        OrbitRender.roundedRect(graphics, WINDOW_X + 1, WINDOW_Y + 1, WINDOW_WIDTH - 2, WINDOW_HEIGHT - 2, 18,
            OrbitRender.alpha(0xFF16131E, 0.91F));
        OrbitRender.roundedRect(graphics, WINDOW_X + 1, WINDOW_Y + 1, SIDEBAR_WIDTH, WINDOW_HEIGHT - 2, 17,
            OrbitRender.alpha(0xFF120F1A, 0.95F));
        graphics.fill(WINDOW_X + SIDEBAR_WIDTH, WINDOW_Y + 24, WINDOW_X + SIDEBAR_WIDTH + 1,
            WINDOW_Y + WINDOW_HEIGHT - 24, OrbitRender.alpha(0xFFFFFFFF, 0.08F));
        drawFloatingPlayer(graphics, palette);
        drawBrand(graphics, palette);
        drawHeader(graphics, palette);
        drawFooter(graphics, palette);
        drawMiniPlayer(graphics, palette);
    }

    private void drawBrand(GuiGraphics graphics, OrbitTheme.Palette palette) {
        OrbitRender.roundedRect(graphics, 390, 82, 38, 38, 11, OrbitRender.alpha(palette.primary(), 0.25F));
        OrbitRender.roundedRect(graphics, 393, 85, 32, 32, 9, palette.secondary());
        graphics.drawCenteredString(this.font, "OB", 409, 96, 0xFFFFFFFF);
        graphics.drawString(this.font, "Orbit", 438, 87, 0xFFFFFFFF);
        graphics.drawString(this.font, "More than just a mod.", 438, 104, 0x8A8299);
        graphics.fill(390, 130, 560, 131, OrbitRender.alpha(0xFFFFFFFF, 0.08F));
    }

    private void drawFloatingPlayer(GuiGraphics graphics, OrbitTheme.Palette palette) {
        PartyStatePayload party = PartySyncClient.getLatestState();
        boolean playing = party != null && party.playbackState() == PartySync.PLAYING;
        graphics.drawString(this.font, "ORBIT  /  NOW PLAYING", 54, 92, palette.textMuted());
        drawVinyl(graphics, 245, 208, 65, playing, palette);
        drawAlbumArt(graphics, 68, 138, 140, palette, pointerX, pointerY);
        graphics.drawString(this.font, trackTitle(party), 68, 300, palette.textMain());
        graphics.drawString(this.font, party == null ? "Your local listening space" : "Party: " + party.partyName(),
            68, 321, palette.textSecondary());
        graphics.drawString(this.font, "SYNCED LYRICS", 68, 361, palette.primary());
        graphics.drawString(this.font, "No synced lyrics loaded", 68, 384, palette.textSecondary());
        graphics.drawString(this.font, "Load an .lrc file for live lines", 68, 405, OrbitRender.alpha(0xFFFFFFFF, 0.42F));
        graphics.drawString(this.font, "Lyrics will appear here", 68, 426, OrbitRender.alpha(0xFFFFFFFF, 0.42F));
        graphics.drawString(this.font, "AUDIO PREVIEW", 68, 566, palette.textMuted());
        drawVisualizer(graphics, playing, palette);
    }

    private void drawHeader(GuiGraphics graphics, OrbitTheme.Palette palette) {
        graphics.drawString(this.font, activeTab.title, CONTENT_X, 82, 0xFFFFFFFF);
        graphics.drawString(this.font, headerSubtitle(), CONTENT_X, 101, 0xFF8A8299);
        graphics.fill(1044, 82, 1046, 110, palette.primary());
        graphics.drawString(this.font, "Your game.", 1058, 82, palette.textSecondary());
        graphics.drawString(this.font, "Our extension.", 1058, 97, palette.textMuted());
    }

    private void drawPage(GuiGraphics graphics, OrbitTheme.Palette palette) {
        switch (activeTab) {
            case OVERVIEW -> drawOverview(graphics, palette);
            case SKIES -> drawSkies(graphics, palette);
            case LYRICS -> drawLyrics(graphics, palette);
            case STYLES -> drawStyles(graphics, palette);
            case COMMUNITY -> drawCommunity(graphics, palette);
            case HUD -> drawHud(graphics, palette);
            case PERFORMANCE -> drawPerformance(graphics, palette);
            case SETTINGS -> drawSettings(graphics, palette);
            case INFORMATION -> drawInformation(graphics, palette);
        }
    }

    private void drawOverview(GuiGraphics graphics, OrbitTheme.Palette palette) {
        int x = CONTENT_X;
        int y = 132;
        OrbitRender.roundedRect(graphics, x, y, CONTENT_WIDTH, 120, 15, 0xFF181426);
        graphics.fillGradient(x + 1, y + 1, x + CONTENT_WIDTH - 1, y + 119,
            OrbitRender.alpha(palette.secondary(), 0.2F), OrbitRender.alpha(0xFF161321, 0.9F));
        for (int star = 0; star < 36; star++) {
            int starX = x + 12 + star * 83 % (CONTENT_WIDTH - 24);
            int starY = y + 7 + star * 31 % 104;
            graphics.fill(starX, starY, starX + 1, starY + 1,
                OrbitRender.alpha(0xFFFFFFFF, 0.18F + (star % 4) * 0.1F));
        }
        OrbitRender.roundedRect(graphics, x + 24, y + 30, 42, 42, 12, palette.primary());
        graphics.drawCenteredString(this.font, "OB", x + 45, y + 45, 0xFFFFFFFF);
        graphics.drawString(this.font, "Orbit", x + 82, y + 31, 0xFFFFFFFF);
        graphics.drawString(this.font, "More than just a mod.", x + 82, y + 51, palette.textSecondary());
        graphics.drawString(this.font, "Music, skies and atmosphere in one place.", x + 24, y + 87, palette.textMuted());
        drawSaturn(graphics, x + CONTENT_WIDTH - 82, y + 60, palette);
        graphics.drawString(this.font, "QUICK ACCESS", CONTENT_X, 263, palette.textMuted());
    }

    private void drawSkies(GuiGraphics graphics, OrbitTheme.Palette palette) {
        graphics.drawString(this.font, "Choose an atmosphere", CONTENT_X, 137, palette.textMain());
        graphics.drawString(this.font, "20 presets available. Your selection is saved locally.", CONTENT_X, 155, palette.textMuted());
        graphics.drawString(this.font, "ACTIVE  /  " + SettingsManager.get().getSettings().activeSky.replace('_', ' '),
            CONTENT_X, 480, palette.primary());
    }

    private void drawLyrics(GuiGraphics graphics, OrbitTheme.Palette palette) {
        drawInfoCard(graphics, CONTENT_X, 144, CONTENT_WIDTH, 330, palette);
        PartyStatePayload party = PartySyncClient.getLatestState();
        graphics.drawCenteredString(this.font, party == null ? "No track selected" : party.trackId(),
            CONTENT_X + CONTENT_WIDTH / 2, 227, palette.textMain());
        graphics.drawCenteredString(this.font, "Load a local .lrc file to show synchronized lyrics here.",
            CONTENT_X + CONTENT_WIDTH / 2, 256, palette.textSecondary());
        graphics.drawCenteredString(this.font, "Past and upcoming lines will fade around the active lyric.",
            CONTENT_X + CONTENT_WIDTH / 2, 278, palette.textMuted());
        graphics.drawCenteredString(this.font, "Party position: " + formatTime(PartySyncClient.getEstimatedPositionMillis()),
            CONTENT_X + CONTENT_WIDTH / 2, 326, palette.primary());
    }

    private void drawStyles(GuiGraphics graphics, OrbitTheme.Palette palette) {
        graphics.drawString(this.font, "Accent themes", CONTENT_X, 140, palette.textMain());
        graphics.drawString(this.font, "Select a palette to update the entire Orbit interface.", CONTENT_X, 159, palette.textMuted());
        graphics.drawString(this.font, "CURRENT  /  " + palette.name(), CONTENT_X, 484, palette.primary());
    }

    private void drawCommunity(GuiGraphics graphics, OrbitTheme.Palette palette) {
        drawInfoCard(graphics, CONTENT_X, 150, CONTENT_WIDTH, 300, palette);
        graphics.drawCenteredString(this.font, "Community catalog is not connected", CONTENT_X + CONTENT_WIDTH / 2, 222,
            palette.textMain());
        graphics.drawCenteredString(this.font, "A catalog endpoint and API contract are required before downloads or uploads can be enabled.",
            CONTENT_X + CONTENT_WIDTH / 2, 253, palette.textSecondary());
        graphics.drawCenteredString(this.font, "No presets are downloaded from an unverified source.", CONTENT_X + CONTENT_WIDTH / 2,
            282, palette.textMuted());
    }

    private void drawHud(GuiGraphics graphics, OrbitTheme.Palette palette) {
        drawInfoCard(graphics, CONTENT_X, 150, CONTENT_WIDTH, 310, palette);
        graphics.drawString(this.font, "In-game player widget", CONTENT_X + 24, 178, palette.textMain());
        graphics.drawString(this.font, "Visibility: " + (SettingsManager.get().getSettings().hudEnabled ? "Enabled" : "Disabled"),
            CONTENT_X + 24, 265, palette.primary());
        graphics.drawString(this.font, "Repeat: " + (SettingsManager.get().getSettings().repeat ? "On" : "Off"),
            CONTENT_X + 24, 291, palette.textSecondary());
    }

    private void drawPerformance(GuiGraphics graphics, OrbitTheme.Palette palette) {
        graphics.drawString(this.font, "Performance preset", CONTENT_X, 143, palette.textMain());
        graphics.drawString(this.font, "Lower presets reduce visual effects to protect frame rate.", CONTENT_X, 162, palette.textMuted());
        drawInfoCard(graphics, CONTENT_X, 288, CONTENT_WIDTH, 130, palette);
        graphics.drawString(this.font, "Current: " + titleCase(SettingsManager.get().getSettings().performancePreset),
            CONTENT_X + 24, 320, palette.primary());
        graphics.drawString(this.font, "Audio controls and party synchronization remain available in every preset.",
            CONTENT_X + 24, 351, palette.textSecondary());
    }

    private void drawSettings(GuiGraphics graphics, OrbitTheme.Palette palette) {
        graphics.drawString(this.font, "Playback and interface", CONTENT_X, 143, palette.textMain());
        graphics.drawString(this.font, "Preferences are stored in config/orbit.json.", CONTENT_X, 162, palette.textMuted());
        drawInfoCard(graphics, CONTENT_X, 370, CONTENT_WIDTH, 105, palette);
        graphics.drawString(this.font, "Party sync shares playback state; each listener needs a matching local track.",
            CONTENT_X + 24, 411, palette.textSecondary());
    }

    private void drawInformation(GuiGraphics graphics, OrbitTheme.Palette palette) {
        drawInfoCard(graphics, CONTENT_X, 145, CONTENT_WIDTH, 330, palette);
        graphics.drawString(this.font, "ORBIT  /  v1.0.0", CONTENT_X + 24, 173, palette.textMain());
        graphics.drawString(this.font, "A music and atmosphere interface for Minecraft.", CONTENT_X + 24, 202, palette.textSecondary());
        graphics.drawString(this.font, "Open the menu with O. Close it with Escape.", CONTENT_X + 24, 236, palette.textMuted());
        graphics.drawString(this.font, "World: " + worldLabel(), CONTENT_X + 24, 275, palette.textMuted());
        if (moderatorPanelOpen) {
            drawModeratorInfo(graphics, CONTENT_X + 24, 330, palette);
        }
    }

    private void drawInfoCard(GuiGraphics graphics, int x, int y, int width, int height, OrbitTheme.Palette palette) {
        OrbitRender.roundedRect(graphics, x, y + 3, width, height, 14, OrbitRender.alpha(0xFF000000, 0.22F));
        OrbitRender.roundedRect(graphics, x, y, width, height, 14, OrbitRender.alpha(0xFFFFFFFF, 0.12F));
        OrbitRender.roundedRect(graphics, x + 1, y + 1, width - 2, height - 2, 13,
            OrbitRender.alpha(palette.elevatedPanel(), 0.93F));
    }

    private void drawFooter(GuiGraphics graphics, OrbitTheme.Palette palette) {
        graphics.fill(390, 642, 560, 643, OrbitRender.alpha(0xFFFFFFFF, 0.08F));
        graphics.drawString(this.font, "Orbit  v1.0.0", 390, 650, palette.textMuted());
        int center = CONTENT_X + CONTENT_WIDTH / 2;
        graphics.fill(CONTENT_X + 32, 635, center - 80, 636, OrbitRender.alpha(palette.border(), 0.55F));
        graphics.drawCenteredString(this.font, "discord.gg/orbitmod", center, 629, palette.textMuted());
        graphics.fill(center + 80, 635, CONTENT_X + CONTENT_WIDTH - 32, 636, OrbitRender.alpha(palette.border(), 0.55F));
    }

    private void drawMiniPlayer(GuiGraphics graphics, OrbitTheme.Palette palette) {
        PartyStatePayload party = PartySyncClient.getLatestState();
        graphics.drawString(this.font, "Now playing", CONTENT_X, 508, palette.textMuted());
        OrbitRender.roundedRect(graphics, CONTENT_X, 529, CONTENT_WIDTH, 72, 12, 0x26FFFFFF);
        OrbitRender.roundedRect(graphics, CONTENT_X + 1, 530, CONTENT_WIDTH - 2, 70, 11, 0xFF1A1624);
        drawAlbumArt(graphics, CONTENT_X + 12, 541, 44, palette, pointerX, pointerY);
        graphics.drawString(this.font, trackTitle(party), CONTENT_X + 68, 540, 0xFFFFFFFF);
        graphics.drawString(this.font, party == null ? "Orbit player ready" : "Party: " + party.partyName(),
            CONTENT_X + 68, 557, palette.textMuted());
        graphics.drawString(this.font, party == null ? "No synced track" : playbackLabel(party),
            CONTENT_X + 68, 578, party != null && party.playbackState() == PartySync.PLAYING ? palette.success() : palette.textMuted());
        graphics.drawString(this.font, party == null ? "--:--" : formatTime(PartySyncClient.getEstimatedPositionMillis()),
            CONTENT_X + 300, 557, palette.textMuted());
        OrbitRender.roundedRect(graphics, CONTENT_X + 12, 596, CONTENT_WIDTH - 24, 2, 1,
            OrbitRender.alpha(palette.primary(), 0.55F));
    }

    private void drawAlbumArt(GuiGraphics graphics, int x, int y, int size, OrbitTheme.Palette palette, int mouseX, int mouseY) {
        int centerX = x + size / 2;
        int centerY = y + size / 2;
        boolean hovered = mouseX >= x && mouseX <= x + size && mouseY >= y && mouseY <= y + size;
        float lean = hovered ? (mouseX - centerX) / (float) size : 0.0F;
        float scaleX = 1.0F + lean * 0.07F;
        graphics.pose().pushMatrix();
        graphics.pose().translate(centerX, centerY);
        graphics.pose().scale(scaleX, 1.0F - Math.abs(lean) * 0.025F);
        graphics.pose().translate(-centerX, -centerY);
        OrbitRender.roundedRect(graphics, x - 2, y - 2, size + 4, size + 4, 8, OrbitRender.alpha(palette.primary(), 0.4F));
        OrbitRender.roundedRect(graphics, x, y, size, size, 7, 0xFF171221);
        graphics.fillGradient(x + 1, y + 1, x + size - 1, y + size - 1,
            OrbitRender.alpha(palette.secondary(), 0.95F), OrbitRender.alpha(palette.background(), 0.98F));
        int radius = Math.max(3, size * 30 / 100);
        OrbitRender.circle(graphics, centerX, centerY, radius, OrbitRender.alpha(palette.primary(), 0.2F));
        graphics.drawCenteredString(this.font, "OB", centerX, centerY - 4, 0xFFFFFFFF);
        graphics.pose().popMatrix();
    }

    private void drawVinyl(GuiGraphics graphics, int centerX, int centerY, int radius, boolean playing, OrbitTheme.Palette palette) {
        OrbitRender.circle(graphics, centerX + 2, centerY + 3, radius, OrbitRender.alpha(0xFF000000, 0.3F));
        OrbitRender.circle(graphics, centerX, centerY, radius, 0xFF100E15);
        for (int ring = 1; ring <= 4; ring++) {
            int ringRadius = radius * (ring + 1) / 6;
            drawCircleOutline(graphics, centerX, centerY, ringRadius,
                OrbitRender.alpha(ring % 2 == 0 ? palette.lavender() : 0xFFFFFFFF, 0.18F));
        }
        OrbitRender.circle(graphics, centerX, centerY, Math.max(4, radius / 3), palette.secondary());
        OrbitRender.circle(graphics, centerX, centerY, 2, palette.lavender());
        double angle = System.nanoTime() / (playing ? 289_372_500.0 : 1_500_000_000.0);
        OrbitRender.circle(graphics, centerX + (int) Math.round(Math.cos(angle) * radius * 0.72),
            centerY + (int) Math.round(Math.sin(angle) * radius * 0.72), 2, palette.primary());
    }

    private void drawCircleOutline(GuiGraphics graphics, int centerX, int centerY, int radius, int color) {
        for (int row = -radius; row <= radius; row++) {
            int halfWidth = (int) Math.sqrt(Math.max(0, radius * radius - row * row));
            int innerHalfWidth = (int) Math.sqrt(Math.max(0, (radius - 1) * (radius - 1) - row * row));
            graphics.fill(centerX - halfWidth, centerY + row, centerX - innerHalfWidth, centerY + row + 1, color);
            graphics.fill(centerX + innerHalfWidth + 1, centerY + row, centerX + halfWidth + 1, centerY + row + 1, color);
        }
    }

    private void drawSaturn(GuiGraphics graphics, int centerX, int centerY, OrbitTheme.Palette palette) {
        drawEllipse(graphics, centerX, centerY, 58, 18, OrbitRender.alpha(palette.lavender(), 0.75F));
        drawEllipse(graphics, centerX, centerY, 48, 14, OrbitRender.alpha(palette.primary(), 0.9F));
        OrbitRender.circle(graphics, centerX, centerY, 25, 0xFFB88CFA);
        OrbitRender.circle(graphics, centerX - 7, centerY - 5, 18, 0xFFD4B8FF);
        graphics.fill(centerX - 16, centerY + 2, centerX + 17, centerY + 9, 0xFF9A69D8);
        drawEllipse(graphics, centerX, centerY, 58, 18, OrbitRender.alpha(0xFFFFFFFF, 0.6F));
    }

    private void drawEllipse(GuiGraphics graphics, int centerX, int centerY, int radiusX, int radiusY, int color) {
        for (int row = -radiusY; row <= radiusY; row++) {
            double ratio = row / (double) Math.max(1, radiusY);
            int halfWidth = (int) Math.round(radiusX * Math.sqrt(Math.max(0.0, 1.0 - ratio * ratio)));
            int innerX = Math.max(0, halfWidth - 2);
            graphics.fill(centerX - halfWidth, centerY + row, centerX - innerX, centerY + row + 1, color);
            graphics.fill(centerX + innerX, centerY + row, centerX + halfWidth + 1, centerY + row + 1, color);
        }
    }

    private void drawVisualizer(GuiGraphics graphics, boolean playing, OrbitTheme.Palette palette) {
        long time = System.nanoTime();
        for (int bar = 0; bar < 24; bar++) {
            float wave = (float) (Math.sin(time * (playing ? 0.000000008 : 0.000000002) + bar * 0.7) * 0.5 + 0.5);
            int height = 3 + Math.round(wave * (playing ? 15 : 5));
            OrbitRender.roundedRect(graphics, 58 + bar * 9, 584 - height, 5, height, 2,
                OrbitRender.alpha(palette.lavender(), playing ? 0.9F : 0.45F));
        }
    }

    private void drawModeratorInfo(GuiGraphics graphics, int x, int y, OrbitTheme.Palette palette) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        String dimension = minecraft.level == null ? "Not in a world" : minecraft.level.dimension().identifier().toString();
        graphics.drawString(this.font, "Moderator tools (read-only)", x, y, palette.primary());
        graphics.drawString(this.font, "Player: " + minecraft.player.getName().getString(), x, y + 20, palette.textSecondary());
        graphics.drawString(this.font, "Dimension: " + dimension + "  Position: " + minecraft.player.blockPosition().toShortString(),
            x, y + 40, palette.textMuted());
    }

    private void syncButtonVisibility() {
        SettingsManager.OrbitSettings settings = SettingsManager.get().getSettings();
        String activeSky = settings.activeSky.replace('_', ' ');
        for (ScreenButton button : buttons) {
            button.visible = button.visibleOn == null || button.visibleOn == activeTab;
            button.active = button.visible;
            button.selected = button.destination == activeTab;
            if (button.style == ButtonStyle.SKY) {
                button.selected = button.label.equalsIgnoreCase(activeSky);
            } else if (button.style == ButtonStyle.PILL) {
                button.selected = button.label.equalsIgnoreCase(titleCase(settings.performancePreset));
            } else if (button.visibleOn == Tab.STYLES) {
                button.selected = button.label.equals(OrbitTheme.current().name());
            } else if (button.label.equals("HUD widget")) {
                button.selected = settings.hudEnabled;
            } else if (button.label.equals("Repeat")) {
                button.selected = settings.repeat;
            } else if (button.label.equals("Shuffle")) {
                button.selected = settings.shuffle;
            }
        }
    }

    private String trackTitle(PartyStatePayload party) {
        return party == null || party.trackId().isEmpty() ? "Nothing playing" : party.trackId();
    }

    private String playbackLabel(PartyStatePayload state) {
        return switch (state.playbackState()) {
            case PartySync.PLAYING -> "Party synced";
            case PartySync.PAUSED -> "Party paused";
            case PartySync.STOPPED -> "Party stopped";
            default -> "Party idle";
        };
    }

    private String headerSubtitle() {
        return activeTab == Tab.OVERVIEW ? "Welcome to Orbit!" : activeTab.subtitle;
    }

    private String worldLabel() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return "Not in a world";
        }
        return minecraft.level.dimension().identifier().toString();
    }

    private String formatTime(long timeMillis) {
        long seconds = Math.max(0L, timeMillis) / 1000L;
        return String.format("%d:%02d", seconds / 60L, seconds % 60L);
    }

    private String titleCase(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String lower = value.toLowerCase(Locale.ROOT);
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    private void toCanvasCoordinates(MouseButtonEvent event) {
        pointerX = (int) Math.round((event.x() - offsetX) / scaleFactor);
        pointerY = (int) Math.round((event.y() - offsetY) / scaleFactor);
    }

    private double toCanvasX(double mouseX) {
        return (mouseX - offsetX) / scaleFactor;
    }

    private double toCanvasY(double mouseY) {
        return (mouseY - offsetY) / scaleFactor;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        pointerX = (int) Math.round(toCanvasX(mouseX));
        pointerY = (int) Math.round(toCanvasY(mouseY));
        OrbitTheme.Palette palette = OrbitTheme.current();
        drawBackdrop(graphics, palette);

        graphics.pose().pushMatrix();
        graphics.pose().translate(offsetX, offsetY);
        graphics.pose().scale(scaleFactor, scaleFactor);
        drawWindow(graphics, palette);
        drawPage(graphics, palette);
        syncButtonVisibility();
        super.render(graphics, pointerX, pointerY, delta);
        OrbitToast.render(graphics, this.font, CANVAS_WIDTH, CANVAS_HEIGHT);
        graphics.pose().popMatrix();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        toCanvasCoordinates(event);
        MouseButtonEvent transformed = new MouseButtonEvent(toCanvasX(event.x()), toCanvasY(event.y()), event.buttonInfo());
        return super.mouseClicked(transformed, doubleClick);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        toCanvasCoordinates(event);
        MouseButtonEvent transformed = new MouseButtonEvent(toCanvasX(event.x()), toCanvasY(event.y()), event.buttonInfo());
        return super.mouseReleased(transformed);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        toCanvasCoordinates(event);
        MouseButtonEvent transformed = new MouseButtonEvent(toCanvasX(event.x()), toCanvasY(event.y()), event.buttonInfo());
        return super.mouseDragged(transformed, dragX / scaleFactor, dragY / scaleFactor);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        return super.mouseScrolled(toCanvasX(mouseX), toCanvasY(mouseY), horizontalAmount, verticalAmount);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private enum Tab {
        OVERVIEW("Overview", "House", "Your music and atmosphere at a glance.", "home"),
        SKIES("Skies", "Skies", "Choose a world atmosphere.", "moon"),
        LYRICS("Lyrics", "Lyrics", "Follow synchronized lyrics.", "note"),
        STYLES("Styles", "Styles", "Make Orbit feel like yours.", "wand"),
        COMMUNITY("Community", "Community", "Explore shared Orbit presets.", "globe"),
        HUD("HUD", "HUD", "Customize your in-game player widget.", "monitor"),
        PERFORMANCE("Performance", "Performance", "Balance visual effects and frame rate.", "bolt"),
        SETTINGS("Settings", "Settings", "Manage playback and interface preferences.", "gear"),
        INFORMATION("Information", "Information", "About Orbit and your current session.", "info");

        private final String title;
        private final String subtitle;
        private final String description;
        private final String icon;

        Tab(String title, String subtitle, String description, String icon) {
            this.title = title;
            this.subtitle = subtitle;
            this.description = description;
            this.icon = icon;
        }
    }

    private enum ButtonStyle {
        BRAND,
        NAVIGATION,
        FEATURE,
        SKY,
        ACTION,
        PILL,
        CONTROL
    }

    private final class ScreenButton extends AbstractWidget {
        private final ButtonStyle style;
        private final String icon;
        private final String label;
        private final String detail;
        private final Tab destination;
        private final Runnable action;
        private final Tab visibleOn;
        private boolean selected;
        private float hoverScale = 1.0F;
        private long lastRenderNanos;
        private long rippleStartedAt;
        private double rippleX;
        private double rippleY;

        private ScreenButton(
            int x,
            int y,
            int width,
            int height,
            ButtonStyle style,
            String icon,
            String label,
            String detail,
            Tab destination,
            Runnable action,
            Tab visibleOn
        ) {
            super(x, y, width, height, Component.literal(label));
            this.style = style;
            this.icon = icon;
            this.label = label;
            this.detail = detail;
            this.destination = destination;
            this.action = action;
            this.visibleOn = visibleOn;
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
            long now = System.nanoTime();
            float elapsed = lastRenderNanos == 0L ? 0.016F
                : Math.min(0.05F, Math.max(0.0F, (now - lastRenderNanos) / 1_000_000_000.0F));
            lastRenderNanos = now;
            float targetScale = isHoveredOrFocused() && (style == ButtonStyle.NAVIGATION || style == ButtonStyle.FEATURE)
                ? 1.02F : 1.0F;
            hoverScale += (targetScale - hoverScale) * (1.0F - (float) Math.exp(-elapsed * 14.0F));

            graphics.pose().pushMatrix();
            graphics.pose().translate(getX() + width / 2.0F, getY() + height / 2.0F);
            graphics.pose().scale(hoverScale, hoverScale);
            graphics.pose().translate(-(getX() + width / 2.0F), -(getY() + height / 2.0F));
            renderStyle(graphics, now);
            drawRipple(graphics, now);
            graphics.pose().popMatrix();
        }

        private void renderStyle(GuiGraphics graphics, long now) {
            OrbitTheme.Palette palette = OrbitTheme.current();
            boolean hovered = isHoveredOrFocused();
            switch (style) {
                case BRAND -> {
                    OrbitRender.roundedRect(graphics, getX(), getY(), width, height, 10,
                        OrbitRender.alpha(palette.primary(), hovered ? 0.5F : 0.25F));
                    graphics.drawCenteredString(OrbitScreen.this.font, "OB", getX() + width / 2, getY() + 12, 0xFFFFFFFF);
                }
                case NAVIGATION -> {
                    if (selected) {
                        OrbitRender.roundedRect(graphics, getX(), getY(), width, height, 10, palette.secondary());
                        graphics.fillGradient(getX() + 8, getY() + 1, getX() + width - 8, getY() + height - 1,
                            palette.secondary(), palette.primary());
                    } else if (hovered) {
                        OrbitRender.roundedRect(graphics, getX(), getY(), width, height, 10, 0x1AFFFFFF);
                        drawRoundedBorder(graphics, getX(), getY(), width, height, 10,
                            OrbitRender.alpha(palette.primary(), 0.5F));
                    }
                    drawIcon(graphics, icon, getX() + 20, getY() + height / 2, selected ? 0xFFFFFFFF : palette.textSecondary());
                    graphics.drawString(OrbitScreen.this.font, label, getX() + 42, getY() + 11,
                        selected ? 0xFFFFFFFF : 0xFFA79AB9);
                }
                case FEATURE -> {
                    OrbitRender.roundedRect(graphics, getX(), getY() + 3, width, height, 13,
                        OrbitRender.alpha(0xFF000000, 0.24F));
                    OrbitRender.roundedRect(graphics, getX(), getY(), width, height, 13,
                        hovered ? OrbitRender.alpha(palette.primary(), 0.5F) : 0x26FFFFFF);
                    OrbitRender.roundedRect(graphics, getX() + 1, getY() + 1, width - 2, height - 2, 12,
                        hovered ? 0xFF292334 : 0xFF1D1927);
                    drawIcon(graphics, icon, getX() + 25, getY() + 30, palette.primary());
                    graphics.drawString(OrbitScreen.this.font, label, getX() + 48, getY() + 22, 0xFFFFFFFF);
                    drawDetail(graphics, getX() + 18, getY() + 57, width - 36, detail, palette.textMuted(), 3);
                    graphics.drawString(OrbitScreen.this.font, "->", getX() + width - 32, getY() + height - 24,
                        palette.lavender());
                }
                case SKY, ACTION -> {
                    OrbitRender.roundedRect(graphics, getX(), getY(), width, height, 11,
                        selected || hovered ? OrbitRender.alpha(palette.primary(), 0.5F) : 0x26FFFFFF);
                    OrbitRender.roundedRect(graphics, getX() + 1, getY() + 1, width - 2, height - 2, 10,
                        OrbitRender.alpha(palette.elevatedPanel(), 0.95F));
                    drawIcon(graphics, icon, getX() + 23, getY() + height / 2, palette.primary());
                    graphics.drawString(OrbitScreen.this.font, label, getX() + 44,
                        getY() + (detail.isEmpty() ? (height - 9) / 2 : 13), 0xFFFFFFFF);
                    if (!detail.isEmpty()) {
                        drawDetail(graphics, getX() + 44, getY() + 34, width - 56, detail, palette.textMuted(), 2);
                    }
                }
                case PILL -> {
                    OrbitRender.roundedRect(graphics, getX(), getY(), width, height, 12,
                        selected ? palette.primary() : hovered ? palette.elevatedPanel() : palette.panel());
                    drawRoundedBorder(graphics, getX(), getY(), width, height, 12,
                        selected ? OrbitRender.alpha(palette.lavender(), 0.8F) : palette.border());
                    drawIcon(graphics, icon, getX() + 22, getY() + height / 2, 0xFFFFFFFF);
                    graphics.drawString(OrbitScreen.this.font, label, getX() + 41, getY() + 19, 0xFFFFFFFF);
                }
                case CONTROL -> {
                    if (icon.equals("play")) {
                        OrbitRender.circle(graphics, getX() + width / 2, getY() + height / 2,
                            Math.min(width, height) / 2, hovered ? palette.lavender() : palette.primary());
                        drawPlayGlyph(graphics, getX() + width / 2, getY() + height / 2, isPartyPlaying(), 0xFF171221);
                    } else {
                        OrbitRender.roundedRect(graphics, getX(), getY(), width, height, height / 2,
                            hovered ? palette.elevatedPanel() : OrbitRender.alpha(palette.elevatedPanel(), 0.8F));
                        drawIcon(graphics, icon, getX() + width / 2, getY() + height / 2, palette.textMain());
                    }
                }
            }
        }

        private void drawDetail(GuiGraphics graphics, int x, int y, int maxWidth, String text, int color, int maxLines) {
            List<FormattedCharSequence> lines = OrbitScreen.this.font.split(Component.literal(text), maxWidth);
            for (int index = 0; index < Math.min(maxLines, lines.size()); index++) {
                graphics.drawString(OrbitScreen.this.font, lines.get(index), x, y + index * 12, color);
            }
        }

        private void drawRipple(GuiGraphics graphics, long now) {
            if (rippleStartedAt == 0L) {
                return;
            }
            float progress = (now - rippleStartedAt) / 200_000_000.0F;
            if (progress >= 1.0F) {
                rippleStartedAt = 0L;
                return;
            }
            float fade = 1.0F - progress;
            int radius = Math.max(1, Math.round(Math.max(width, height) * progress));
            int color = OrbitRender.alpha(0xFFFFFFFF, 0.35F * fade);
            int centerX = (int) Math.round(rippleX);
            int centerY = (int) Math.round(rippleY);
            for (int row = Math.max(getY(), centerY - radius); row <= Math.min(getY() + height, centerY + radius); row++) {
                int dy = row - centerY;
                int halfWidth = (int) Math.sqrt(Math.max(0, radius * radius - dy * dy));
                int left = Math.max(getX(), centerX - halfWidth);
                int right = Math.min(getX() + width, centerX + halfWidth + 1);
                if (right > left) {
                    graphics.fill(left, row, right, row + 1, color);
                }
            }
        }

        @Override
        public void onClick(MouseButtonEvent event, boolean doubleClick) {
            rippleX = event.x();
            rippleY = event.y();
            rippleStartedAt = System.nanoTime();
            action.run();
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }

    private void drawRoundedBorder(GuiGraphics graphics, int x, int y, int width, int height, int radius, int color) {
        OrbitRender.roundedRect(graphics, x, y, width, height, radius, color);
        OrbitRender.roundedRect(graphics, x + 1, y + 1, width - 2, height - 2, Math.max(0, radius - 1),
            OrbitRender.alpha(OrbitTheme.current().panel(), 0.98F));
    }

    private void drawPlayGlyph(GuiGraphics graphics, int centerX, int centerY, boolean playing, int color) {
        if (playing) {
            graphics.fill(centerX - 4, centerY - 6, centerX - 1, centerY + 6, color);
            graphics.fill(centerX + 2, centerY - 6, centerX + 5, centerY + 6, color);
        } else {
            OrbitRender.line(graphics, centerX - 4, centerY - 6, centerX + 6, centerY, 2, color);
            OrbitRender.line(graphics, centerX + 6, centerY, centerX - 4, centerY + 6, 2, color);
            OrbitRender.line(graphics, centerX - 4, centerY + 6, centerX - 4, centerY - 6, 2, color);
        }
    }

    private boolean isPartyPlaying() {
        PartyStatePayload state = PartySyncClient.getLatestState();
        return state != null && state.playbackState() == PartySync.PLAYING;
    }

    private void drawIcon(GuiGraphics graphics, String icon, int centerX, int centerY, int color) {
        switch (icon) {
            case "home" -> {
                OrbitRender.line(graphics, centerX - 7, centerY, centerX, centerY - 7, 2, color);
                OrbitRender.line(graphics, centerX, centerY - 7, centerX + 7, centerY, 2, color);
                graphics.fill(centerX - 5, centerY, centerX + 6, centerY + 7, color);
                graphics.fill(centerX - 1, centerY + 3, centerX + 2, centerY + 7, 0xFF211C2B);
            }
            case "moon" -> {
                OrbitRender.circle(graphics, centerX, centerY, 7, color);
                OrbitRender.circle(graphics, centerX + 4, centerY - 3, 6, 0xFF211C2B);
                OrbitRender.circle(graphics, centerX + 8, centerY - 7, 1, color);
            }
            case "note" -> {
                graphics.fill(centerX + 3, centerY - 7, centerX + 6, centerY + 4, color);
                graphics.fill(centerX - 2, centerY - 7, centerX + 6, centerY - 4, color);
                OrbitRender.circle(graphics, centerX - 2, centerY + 5, 3, color);
                OrbitRender.circle(graphics, centerX + 4, centerY + 3, 3, color);
            }
            case "wand" -> {
                OrbitRender.line(graphics, centerX - 6, centerY + 6, centerX + 5, centerY - 5, 3, color);
                OrbitRender.line(graphics, centerX + 4, centerY - 8, centerX + 4, centerY - 3, 1, color);
                OrbitRender.line(graphics, centerX + 1, centerY - 5, centerX + 7, centerY - 5, 1, color);
            }
            case "monitor" -> {
                OrbitRender.roundedRect(graphics, centerX - 8, centerY - 6, 16, 11, 3, color);
                graphics.fill(centerX - 6, centerY - 4, centerX + 6, centerY + 3, 0xFF211C2B);
                graphics.fill(centerX - 1, centerY + 5, centerX + 2, centerY + 8, color);
                graphics.fill(centerX - 5, centerY + 7, centerX + 6, centerY + 9, color);
            }
            case "bolt" -> {
                OrbitRender.line(graphics, centerX + 1, centerY - 8, centerX - 5, centerY + 1, 3, color);
                OrbitRender.line(graphics, centerX - 5, centerY + 1, centerX, centerY + 1, 3, color);
                OrbitRender.line(graphics, centerX, centerY + 1, centerX - 2, centerY + 8, 3, color);
                OrbitRender.line(graphics, centerX - 2, centerY + 8, centerX + 6, centerY - 2, 3, color);
            }
            case "gear" -> {
                OrbitRender.circle(graphics, centerX, centerY, 7, color);
                OrbitRender.circle(graphics, centerX, centerY, 3, 0xFF211C2B);
                graphics.fill(centerX - 1, centerY - 10, centerX + 2, centerY - 6, color);
                graphics.fill(centerX - 1, centerY + 6, centerX + 2, centerY + 10, color);
                graphics.fill(centerX - 10, centerY - 1, centerX - 6, centerY + 2, color);
                graphics.fill(centerX + 6, centerY - 1, centerX + 10, centerY + 2, color);
            }
            case "info" -> {
                OrbitRender.circle(graphics, centerX, centerY, 8, color);
                OrbitRender.circle(graphics, centerX, centerY, 6, 0xFF211C2B);
                OrbitRender.circle(graphics, centerX, centerY - 3, 1, color);
                graphics.fill(centerX - 1, centerY - 1, centerX + 2, centerY + 5, color);
            }
            case "prev" -> {
                graphics.fill(centerX - 7, centerY - 7, centerX - 5, centerY + 7, color);
                OrbitRender.line(graphics, centerX + 5, centerY - 6, centerX - 3, centerY, 2, color);
                OrbitRender.line(graphics, centerX - 3, centerY, centerX + 5, centerY + 6, 2, color);
            }
            case "next" -> {
                graphics.fill(centerX + 5, centerY - 7, centerX + 7, centerY + 7, color);
                OrbitRender.line(graphics, centerX - 5, centerY - 6, centerX + 3, centerY, 2, color);
                OrbitRender.line(graphics, centerX + 3, centerY, centerX - 5, centerY + 6, 2, color);
            }
            case "play" -> drawPlayGlyph(graphics, centerX, centerY, false, color);
            case "loop" -> {
                OrbitRender.line(graphics, centerX - 6, centerY - 4, centerX + 5, centerY - 4, 2, color);
                OrbitRender.line(graphics, centerX + 5, centerY - 4, centerX + 3, centerY - 7, 2, color);
                OrbitRender.line(graphics, centerX + 5, centerY - 4, centerX + 3, centerY - 1, 2, color);
                OrbitRender.line(graphics, centerX + 6, centerY + 4, centerX - 5, centerY + 4, 2, color);
                OrbitRender.line(graphics, centerX - 5, centerY + 4, centerX - 3, centerY + 1, 2, color);
                OrbitRender.line(graphics, centerX - 5, centerY + 4, centerX - 3, centerY + 7, 2, color);
            }
            case "star" -> {
                OrbitRender.line(graphics, centerX, centerY - 8, centerX, centerY + 8, 1, color);
                OrbitRender.line(graphics, centerX - 8, centerY, centerX + 8, centerY, 1, color);
                OrbitRender.line(graphics, centerX - 5, centerY - 5, centerX + 5, centerY + 5, 1, color);
                OrbitRender.line(graphics, centerX + 5, centerY - 5, centerX - 5, centerY + 5, 1, color);
            }
            case "close" -> {
                OrbitRender.line(graphics, centerX - 4, centerY - 4, centerX + 4, centerY + 4, 2, color);
                OrbitRender.line(graphics, centerX + 4, centerY - 4, centerX - 4, centerY + 4, 2, color);
            }
            case "shuffle" -> {
                OrbitRender.line(graphics, centerX - 7, centerY - 5, centerX + 6, centerY + 5, 1, color);
                OrbitRender.line(graphics, centerX - 7, centerY + 5, centerX + 6, centerY - 5, 1, color);
                OrbitRender.line(graphics, centerX + 3, centerY - 6, centerX + 7, centerY - 5, 1, color);
                OrbitRender.line(graphics, centerX + 7, centerY - 5, centerX + 5, centerY - 1, 1, color);
            }
            case "palette" -> {
                OrbitRender.circle(graphics, centerX - 3, centerY - 3, 3, color);
                OrbitRender.circle(graphics, centerX + 4, centerY - 2, 3, 0xFF37B8FF);
                OrbitRender.circle(graphics, centerX - 3, centerY + 4, 3, 0xFF42D98A);
            }
            case "globe" -> {
                drawCircleOutline(graphics, centerX, centerY, 8, color);
                graphics.fill(centerX - 7, centerY, centerX + 8, centerY + 1, color);
                graphics.fill(centerX, centerY - 7, centerX + 1, centerY + 8, color);
            }
            case "ob" -> graphics.drawCenteredString(this.font, "OB", centerX, centerY - 4, color);
            default -> OrbitRender.circle(graphics, centerX, centerY, 5, color);
        }
    }
}
