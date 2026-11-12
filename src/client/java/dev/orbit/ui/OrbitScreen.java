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

import java.util.ArrayList;
import java.util.List;

public class OrbitScreen extends Screen {
    private static final int CANVAS_WIDTH = 1280;
    private static final int CANVAS_HEIGHT = 720;
    private static final int MAIN_X = 352;
    private static final int MAIN_Y = 24;
    private static final int MAIN_WIDTH = 904;
    private static final int MAIN_HEIGHT = 672;
    private static final int SIDEBAR_WIDTH = 210;
    private static final int CONTENT_X = 588;
    private static final int CONTENT_WIDTH = 642;
    private static final Tab[] NAVIGATION = {
        Tab.OVERVIEW, Tab.SKIES, Tab.LYRICS, Tab.STYLES,
        Tab.HUD, Tab.PERFORMANCE, Tab.SETTINGS, Tab.INFORMATION
    };

    private final List<ScreenButton> buttons = new ArrayList<>();
    private Tab activeTab = Tab.OVERVIEW;
    private float scaleFactor = 1.0F;
    private float canvasOffsetX;
    private float canvasOffsetY;
    private int logoClicks;
    private boolean moderatorPanelOpen;

    public OrbitScreen() {
        super(Component.literal("ORBIT"));
    }

    @Override
    protected void init() {
        super.init();
        buttons.clear();
        scaleFactor = Math.max(0.01F, Math.min(this.width / (float) CANVAS_WIDTH, this.height / (float) CANVAS_HEIGHT) * 0.92F);
        canvasOffsetX = (this.width / scaleFactor - CANVAS_WIDTH) / 2.0F;
        canvasOffsetY = (this.height / scaleFactor - CANVAS_HEIGHT) / 2.0F;

        addButton(41, 42, 48, 34, ButtonStyle.BRAND, "ob", "OB", "", null, this::onLogoClicked, null);
        addButton(1210, 40, 28, 28, ButtonStyle.CONTROL, "close", "", "", null, this::onClose, null);

        for (int index = 0; index < NAVIGATION.length; index++) {
            Tab tab = NAVIGATION[index];
            addButton(374, 126 + index * 46, 166, 38, ButtonStyle.NAVIGATION,
                tab.icon, tab.title, "", tab, () -> activeTab = tab, null);
        }

        String[] featureLabels = {"Skies", "Lyrics", "Styles", "Performance", "HUD", "Settings"};
        String[] featureDetails = {
            "20 skies - Synthwave, Aurora, Sakura...",
            "Your synced lyrics float in the sky",
            "15+ styles per item, plus your own packs",
            "FPS, graphics, Chill / PvP and effects",
            "Top bar, glass hotbar and accent color",
            "Menu, sounds and keys"
        };
        Tab[] featureTabs = {Tab.SKIES, Tab.LYRICS, Tab.STYLES, Tab.PERFORMANCE, Tab.HUD, Tab.SETTINGS};
        for (int index = 0; index < featureTabs.length; index++) {
            int column = index % 3;
            int row = index / 3;
            int x = CONTENT_X + column * 214;
            int y = 252 + row * 116;
            Tab tab = featureTabs[index];
            addButton(x, y, 204, 104, ButtonStyle.FEATURE, tab.icon, featureLabels[index],
                featureDetails[index], tab, () -> activeTab = tab, Tab.OVERVIEW);
        }

        SkyType[] skies = SkyType.values();
        for (int index = 0; index < skies.length; index++) {
            SkyType sky = skies[index];
            int column = index % 4;
            int row = index / 4;
            addButton(CONTENT_X + column * 158, 158 + row * 62, 150, 52, ButtonStyle.SKY,
                "star", sky.name().replace('_', ' '), "", null, () -> selectSky(sky), Tab.SKIES);
        }

        for (int index = 0; index < OrbitTheme.paletteCount(); index++) {
            int column = index % 2;
            int row = index / 2;
            int paletteIndex = index;
            addButton(CONTENT_X + column * 242, 204 + row * 68, 228, 56, ButtonStyle.ACTION,
                "wand", OrbitTheme.paletteAt(paletteIndex).name(), "Apply this accent palette",
                null, () -> selectTheme(paletteIndex), Tab.STYLES);
        }

        for (int index = 0; index < PerformancePreset.values().length; index++) {
            PerformancePreset preset = PerformancePreset.values()[index];
            addButton(CONTENT_X + index * 126, 202, 116, 44, ButtonStyle.PILL,
                "bolt", titleCase(preset.name()), "", null, () -> selectPerformance(preset), Tab.PERFORMANCE);
        }

        addButton(CONTENT_X, 198, 300, 58, ButtonStyle.ACTION, "monitor", "HUD widget",
            "Show track information in-game", null, this::toggleHud, Tab.HUD);
        addButton(CONTENT_X + 314, 198, 300, 58, ButtonStyle.ACTION, "loop", "Repeat",
            "Loop the current track", null, this::toggleRepeat, Tab.HUD);
        addButton(CONTENT_X, 198, 300, 58, ButtonStyle.ACTION, "shuffle", "Shuffle",
            "Mix the local track queue", null, this::toggleShuffle, Tab.SETTINGS);
        addButton(CONTENT_X + 314, 198, 300, 58, ButtonStyle.ACTION, "loop", "Repeat",
            "Loop the current track", null, this::toggleRepeat, Tab.SETTINGS);
        addButton(CONTENT_X, 282, 300, 58, ButtonStyle.ACTION, "palette", "Color theme",
            "Cycle the five Orbit palettes", null, this::cycleTheme, Tab.SETTINGS);
        addButton(CONTENT_X + 506, 540, 28, 28, ButtonStyle.CONTROL, "prev", "", "",
            null, () -> showPartyControlMessage("Previous track"), null);
        addButton(CONTENT_X + 542, 540, 28, 28, ButtonStyle.CONTROL, "play", "", "",
            null, this::togglePartyPlayback, null);
        addButton(CONTENT_X + 578, 540, 28, 28, ButtonStyle.CONTROL, "next", "", "",
            null, () -> showPartyControlMessage("Next track"), null);
        addButton(67, 630, 38, 38, ButtonStyle.CONTROL, "prev", "", "",
            null, () -> showPartyControlMessage("Previous track"), null);
        addButton(115, 630, 38, 38, ButtonStyle.CONTROL, "play", "", "",
            null, this::togglePartyPlayback, null);
        addButton(163, 630, 38, 38, ButtonStyle.CONTROL, "next", "", "",
            null, () -> showPartyControlMessage("Next track"), null);
        addButton(211, 630, 38, 38, ButtonStyle.CONTROL, "loop", "", "",
            null, this::toggleRepeat, null);
        addButton(374, 644, 166, 28, ButtonStyle.FOOTER, "ob", "Orbit  v1.0.0", "",
            null, () -> activeTab = Tab.INFORMATION, null);
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
        Tab onlyVisibleOn
    ) {
        ScreenButton button = new ScreenButton(x, y, width, height, style, icon, label, detail, destination, action, onlyVisibleOn);
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
        SettingsManager.OrbitSettings settings = SettingsManager.get().getSettings();
        settings.activeSky = sky.name();
        SkyRenderer.setActiveSky(sky);
        SettingsManager.get().save();
        OrbitToast.show("Sky selected: " + sky.name().replace('_', ' '));
    }

    private void selectTheme(int index) {
        OrbitTheme.Palette palette = OrbitTheme.select(index);
        OrbitToast.show("Theme: " + palette.name());
    }

    private void cycleTheme() {
        OrbitTheme.Palette palette = OrbitTheme.cycle();
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
            OrbitToast.show("Join an Orbit party to use " + control.toLowerCase());
        } else {
            OrbitToast.show("Party sync does not have a track queue yet");
        }
    }

    private void drawBackground(GuiGraphics graphics, OrbitTheme.Palette palette) {
        graphics.fillGradient(
            0, 0, this.width, this.height,
            OrbitRender.alpha(palette.background(), 0.64F),
            OrbitRender.alpha(palette.secondaryBackground(), 0.82F)
        );
        graphics.fillGradient(0, 0, this.width, 2,
            OrbitRender.alpha(palette.primary(), 0.0F),
            OrbitRender.alpha(palette.primary(), 0.55F));

        graphics.pose().pushMatrix();
        graphics.pose().translate(canvasOffsetX, canvasOffsetY);
        graphics.pose().scale(scaleFactor, scaleFactor);
        drawCanvas(graphics, palette);
        graphics.pose().popMatrix();
    }

    private void drawCanvas(GuiGraphics graphics, OrbitTheme.Palette palette) {
        drawFloatingPlayer(graphics, palette);
        drawMainWindow(graphics, palette);
        syncButtonVisibility();
    }

    private void drawFloatingPlayer(GuiGraphics graphics, OrbitTheme.Palette palette) {
        PartyStatePayload party = PartySyncClient.getLatestState();
        boolean playing = party != null && party.playbackState() == PartySync.PLAYING;

        OrbitRender.circle(graphics, 150, 178, 105, OrbitRender.alpha(palette.primary(), 0.035F));
        drawVinyl(graphics, 236, 177, 64, playing, palette);
        drawAlbumArt(graphics, 46, 108, 140, palette);

        graphics.drawString(this.font, "ORBIT  /  NOW PLAYING", 48, 76, palette.textMuted());
        graphics.drawString(this.font, trackTitle(party), 48, 270, palette.textMain());
        graphics.drawString(this.font, party == null ? "Your local listening space" : "Party: " + party.partyName(),
            48, 292, palette.textSecondary());
        graphics.drawString(this.font, "SYNCED LYRICS", 48, 334, palette.primary());
        graphics.drawString(this.font, "No track selected", 48, 358, palette.textSecondary());
        graphics.drawString(this.font, "Add a local track to begin", 48, 378, OrbitRender.alpha(0xFFFFFFFF, 0.42F));
        graphics.drawString(this.font, "Lyrics will appear here", 48, 398, OrbitRender.alpha(0xFFFFFFFF, 0.42F));

        drawVisualizer(graphics, playing, palette);
        graphics.drawString(this.font, "AUDIO PREVIEW", 48, 574, palette.textMuted());
        graphics.drawString(this.font, playing ? "PARTY PLAYING" : "PLAYER READY", 193, 574,
            playing ? palette.success() : palette.textMuted());
    }

    private String trackTitle(PartyStatePayload party) {
        return party == null || party.trackId().isEmpty() ? "Nothing playing" : party.trackId();
    }

    private void drawAlbumArt(GuiGraphics graphics, int x, int y, int size, OrbitTheme.Palette palette) {
        OrbitRender.roundedRect(graphics, x - 2, y - 2, size + 4, size + 4, 14,
            OrbitRender.alpha(palette.lavender(), 0.25F));
        OrbitRender.roundedRect(graphics, x, y, size, size, 12, 0xFF171221);
        graphics.fillGradient(x + 2, y + 2, x + size - 2, y + size - 2,
            OrbitRender.alpha(palette.secondary(), 0.85F), OrbitRender.alpha(palette.background(), 0.95F));
        int centerX = x + size / 2;
        int centerY = y + size / 2;
        int radius = Math.max(3, size * 31 / 100);
        OrbitRender.circle(graphics, centerX, centerY, radius + 5, OrbitRender.alpha(palette.primary(), 0.13F));
        OrbitRender.circle(graphics, centerX, centerY, radius - 4, OrbitRender.alpha(palette.lavender(), 0.12F));
        drawEllipse(graphics, centerX, centerY, radius + 7, Math.max(3, radius / 3), 1,
            OrbitRender.alpha(palette.lavender(), 0.7F));
        graphics.drawCenteredString(this.font, "OB", centerX, centerY - 5, palette.textMain());
        OrbitRender.circle(graphics, centerX, centerY, Math.max(1, size / 40), palette.primary());
        if (size >= 80) {
            graphics.drawString(this.font, "ORBIT ORIGINAL", x + 12, y + size - 22,
                OrbitRender.alpha(palette.textMain(), 0.72F));
        }
    }

    private void drawVinyl(GuiGraphics graphics, int centerX, int centerY, int radius, boolean playing, OrbitTheme.Palette palette) {
        drawEllipse(graphics, centerX + 4, centerY + 6, radius, radius, 1, OrbitRender.alpha(0xFF000000, 0.4F));
        drawEllipse(graphics, centerX, centerY, radius, radius, 1, 0xFF100E15);
        for (int ring = 1; ring <= 6; ring++) {
            int ringRadius = radius * (ring + 1) / 8;
            drawEllipse(graphics, centerX, centerY, ringRadius, ringRadius, 1,
                ring % 2 == 0 ? 0xFF292431 : 0xFF1A1720);
        }
        drawEllipse(graphics, centerX, centerY, radius / 3, radius / 3, 1, palette.secondary());
        OrbitRender.circle(graphics, centerX, centerY, Math.max(2, radius / 16), palette.lavender());

        double rotation = (System.currentTimeMillis() % 30_000L) * (playing ? 0.00055 : 0.00008);
        int markerX = centerX + (int) Math.round(Math.cos(rotation) * radius * 0.72);
        int markerY = centerY + (int) Math.round(Math.sin(rotation) * radius * 0.72);
        OrbitRender.circle(graphics, markerX, markerY, 2, palette.primary());
    }

    private void drawVisualizer(GuiGraphics graphics, boolean playing, OrbitTheme.Palette palette) {
        long time = System.currentTimeMillis();
        for (int bar = 0; bar < 24; bar++) {
            float wave = (float) (Math.sin(time * (playing ? 0.008 : 0.002) + bar * 0.71) * 0.5 + 0.5);
            int height = 5 + Math.round(wave * (playing ? 31 : 9));
            int x = 48 + bar * 9;
            OrbitRender.roundedRect(graphics, x, 552 - height, 5, height, 3,
                OrbitRender.alpha(palette.lavender(), playing ? 0.82F : 0.32F));
        }
    }

    private void drawMainWindow(GuiGraphics graphics, OrbitTheme.Palette palette) {
        OrbitRender.roundedRect(graphics, MAIN_X + 3, MAIN_Y + 5, MAIN_WIDTH, MAIN_HEIGHT, 21,
            OrbitRender.alpha(0xFF000000, 0.42F));
        OrbitRender.roundedRect(graphics, MAIN_X, MAIN_Y, MAIN_WIDTH, MAIN_HEIGHT, 19,
            OrbitRender.alpha(0xFFFFFFFF, 0.15F));
        OrbitRender.roundedRect(graphics, MAIN_X + 1, MAIN_Y + 1, MAIN_WIDTH - 2, MAIN_HEIGHT - 2, 18,
            OrbitRender.alpha(0xFF16131E, 0.90F));
        OrbitRender.roundedRect(graphics, MAIN_X + 1, MAIN_Y + 1, SIDEBAR_WIDTH, MAIN_HEIGHT - 2, 17,
            OrbitRender.alpha(0xFF120F1A, 0.94F));

        drawBrand(graphics, palette);
        drawSidebarFooter(graphics, palette);
        drawHeader(graphics, palette);
        drawTabContent(graphics, palette);
        drawMiniPlayer(graphics, palette);
        drawContentFooter(graphics, palette);
    }

    private void drawBrand(GuiGraphics graphics, OrbitTheme.Palette palette) {
        OrbitRender.roundedRect(graphics, 374, 46, 38, 38, 11, OrbitRender.alpha(palette.primary(), 0.22F));
        graphics.fillGradient(378, 50, 408, 80,
            OrbitRender.alpha(palette.secondary(), 0.95F), OrbitRender.alpha(palette.primary(), 0.95F));
        graphics.drawCenteredString(this.font, "OB", 393, 60, palette.textMain());
        graphics.drawString(this.font, "Orbit", 420, 50, palette.textMain());
        graphics.drawString(this.font, "More than just a mod.", 420, 68, palette.textMuted());
        graphics.fill(374, 98, 542, 99, OrbitRender.alpha(0xFFFFFFFF, 0.08F));
    }

    private void drawSidebarFooter(GuiGraphics graphics, OrbitTheme.Palette palette) {
        graphics.fill(374, 632, 542, 633, OrbitRender.alpha(0xFFFFFFFF, 0.08F));
        OrbitRender.roundedRect(graphics, 378, 651, 22, 22, 7, OrbitRender.alpha(palette.primary(), 0.8F));
        graphics.drawCenteredString(this.font, "OB", 389, 657, palette.textMain());
        graphics.drawString(this.font, "Orbit", 408, 656, palette.textSecondary());
        graphics.drawString(this.font, "v1.0.0", 491, 657, palette.textMuted());
    }

    private void drawHeader(GuiGraphics graphics, OrbitTheme.Palette palette) {
        graphics.drawString(this.font, activeTab.title, CONTENT_X, 48, palette.textMain());
        graphics.drawString(this.font, headerSubtitle(), CONTENT_X, 72, palette.textMuted());
        graphics.fill(1090, 46, 1092, 78, palette.primary());
        graphics.drawString(this.font, "Your game.", 1102, 47, palette.textSecondary());
        graphics.drawString(this.font, "Our extension.", 1102, 64, palette.textMuted());
    }

    private String headerSubtitle() {
        return activeTab == Tab.OVERVIEW ? "Welcome to Orbit!" : activeTab.subtitle;
    }

    private void drawTabContent(GuiGraphics graphics, OrbitTheme.Palette palette) {
        switch (activeTab) {
            case OVERVIEW -> drawOverview(graphics, palette);
            case SKIES -> drawSkies(graphics, palette);
            case LYRICS -> drawLyrics(graphics, palette);
            case STYLES -> drawStyles(graphics, palette);
            case HUD -> drawHud(graphics, palette);
            case PERFORMANCE -> drawPerformance(graphics, palette);
            case SETTINGS -> drawSettings(graphics, palette);
            case INFORMATION -> drawInformation(graphics, palette);
        }
    }

    private void drawOverview(GuiGraphics graphics, OrbitTheme.Palette palette) {
        int x = CONTENT_X;
        int y = 126;
        OrbitRender.roundedRect(graphics, x, y, CONTENT_WIDTH, 112, 14, 0xFF181426);
        graphics.fillGradient(x + 1, y + 1, x + CONTENT_WIDTH - 1, y + 111,
            OrbitRender.alpha(palette.secondary(), 0.18F), OrbitRender.alpha(0xFF161321, 0.85F));
        for (int star = 0; star < 30; star++) {
            int starX = x + 10 + (star * 97 % (CONTENT_WIDTH - 20));
            int starY = y + 8 + (star * 43 % 96);
            graphics.fill(starX, starY, starX + 1, starY + 1, OrbitRender.alpha(0xFFFFFFFF, 0.18F + (star % 4) * 0.1F));
        }
        OrbitRender.roundedRect(graphics, x + 20, y + 24, 40, 40, 12, OrbitRender.alpha(palette.primary(), 0.65F));
        graphics.drawCenteredString(this.font, "OB", x + 40, y + 38, palette.textMain());
        graphics.drawString(this.font, "Orbit", x + 74, y + 27, palette.textMain());
        graphics.drawString(this.font, "More than just a mod.", x + 74, y + 49, palette.textSecondary());
        graphics.drawString(this.font, "Music, skies and atmosphere in one place.", x + 20, y + 82, palette.textMuted());
        drawSaturn(graphics, x + CONTENT_WIDTH - 92, y + 56, palette);
    }

    private void drawSaturn(GuiGraphics graphics, int centerX, int centerY, OrbitTheme.Palette palette) {
        drawEllipse(graphics, centerX, centerY, 54, 18, 5, OrbitRender.alpha(palette.lavender(), 0.7F));
        drawEllipse(graphics, centerX, centerY, 44, 13, 3, OrbitRender.alpha(palette.primary(), 0.72F));
        OrbitRender.circle(graphics, centerX, centerY, 25, 0xFFB88CFA);
        OrbitRender.circle(graphics, centerX - 7, centerY - 5, 18, 0xFFD4B8FF);
        graphics.fill(centerX - 17, centerY + 3, centerX + 17, centerY + 10, 0xFF9A69D8);
        drawEllipse(graphics, centerX, centerY, 54, 18, 2, OrbitRender.alpha(palette.textMain(), 0.62F));
    }

    private void drawSkies(GuiGraphics graphics, OrbitTheme.Palette palette) {
        graphics.drawString(this.font, "Choose an atmosphere", CONTENT_X, 130, palette.textMain());
        graphics.drawString(this.font, "Select a saved sky preset.", CONTENT_X, 148, palette.textMuted());
        String activeSky = SettingsManager.get().getSettings().activeSky;
        graphics.drawString(this.font, "ACTIVE  /  " + activeSky.replace('_', ' '), CONTENT_X, 478, palette.primary());
    }

    private void drawLyrics(GuiGraphics graphics, OrbitTheme.Palette palette) {
        drawInformationCard(graphics, CONTENT_X, 142, CONTENT_WIDTH, 310, palette);
        PartyStatePayload party = PartySyncClient.getLatestState();
        String title = party == null || party.trackId().isEmpty() ? "No track selected" : party.trackId();
        graphics.drawCenteredString(this.font, title, CONTENT_X + CONTENT_WIDTH / 2, 220, palette.textMain());
        graphics.drawCenteredString(this.font, "Synced lyrics appear here when an LRC file is loaded.", CONTENT_X + CONTENT_WIDTH / 2, 252,
            palette.textSecondary());
        graphics.drawCenteredString(this.font, "Past and upcoming lines will fade around the active lyric.", CONTENT_X + CONTENT_WIDTH / 2, 274,
            palette.textMuted());
        graphics.drawCenteredString(this.font, "Party position: " + formatTime(PartySyncClient.getEstimatedPositionMillis()),
            CONTENT_X + CONTENT_WIDTH / 2, 324, palette.primary());
    }

    private void drawStyles(GuiGraphics graphics, OrbitTheme.Palette palette) {
        graphics.drawString(this.font, "Choose your accent palette", CONTENT_X, 142, palette.textMain());
        graphics.drawString(this.font, "The selected theme is saved to orbit.json.", CONTENT_X, 162, palette.textMuted());
        graphics.drawString(this.font, "CURRENT  /  " + palette.name(), CONTENT_X, 492, palette.primary());
    }

    private void drawHud(GuiGraphics graphics, OrbitTheme.Palette palette) {
        drawInformationCard(graphics, CONTENT_X, 142, CONTENT_WIDTH, 322, palette);
        graphics.drawString(this.font, "In-game music widget", CONTENT_X + 24, 164, palette.textMain());
        graphics.drawString(this.font, "Show or hide the compact track HUD.", CONTENT_X + 24, 188, palette.textMuted());
        graphics.drawString(this.font, "Current state: " + (SettingsManager.get().getSettings().hudEnabled ? "Enabled" : "Disabled"),
            CONTENT_X + 24, 282, palette.primary());
        graphics.drawString(this.font, "Repeat: " + enabledLabel(SettingsManager.get().getSettings().repeat),
            CONTENT_X + 24, 326, palette.textSecondary());
    }

    private void drawPerformance(GuiGraphics graphics, OrbitTheme.Palette palette) {
        graphics.drawString(this.font, "Tune visual quality", CONTENT_X, 142, palette.textMain());
        graphics.drawString(this.font, "Choose a preset to balance effects and frame rate.", CONTENT_X, 162, palette.textMuted());
        graphics.drawString(this.font, "CURRENT  /  " + titleCase(SettingsManager.get().getSettings().performancePreset),
            CONTENT_X, 282, palette.primary());
        drawInformationCard(graphics, CONTENT_X, 322, CONTENT_WIDTH, 116, palette);
        graphics.drawString(this.font, "Lower presets reduce visual effects. Audio and party controls remain available.",
            CONTENT_X + 20, 362, palette.textSecondary());
    }

    private void drawSettings(GuiGraphics graphics, OrbitTheme.Palette palette) {
        graphics.drawString(this.font, "Playback preferences", CONTENT_X, 142, palette.textMain());
        graphics.drawString(this.font, "These preferences are saved locally.", CONTENT_X, 162, palette.textMuted());
        drawInformationCard(graphics, CONTENT_X, 362, CONTENT_WIDTH, 94, palette);
        graphics.drawString(this.font, "Party controls share playback state only; each listener needs a local copy of the track.",
            CONTENT_X + 20, 399, palette.textSecondary());
    }

    private void drawInformation(GuiGraphics graphics, OrbitTheme.Palette palette) {
        drawInformationCard(graphics, CONTENT_X, 142, CONTENT_WIDTH, 300, palette);
        graphics.drawString(this.font, "ORBIT  /  v1.0.0", CONTENT_X + 24, 166, palette.textMain());
        graphics.drawString(this.font, "A music and atmosphere interface for Minecraft.", CONTENT_X + 24, 192, palette.textSecondary());
        graphics.drawString(this.font, "Open the menu with O. Close it with Escape.", CONTENT_X + 24, 226, palette.textMuted());
        graphics.drawString(this.font, "World: " + worldLabel(), CONTENT_X + 24, 270, palette.textMuted());
        if (moderatorPanelOpen) {
            drawModeratorInfo(graphics, CONTENT_X + 24, 330, palette);
        }
    }

    private void drawInformationCard(GuiGraphics graphics, int x, int y, int width, int height, OrbitTheme.Palette palette) {
        OrbitRender.roundedRect(graphics, x, y + 3, width, height, 14, OrbitRender.alpha(0xFF000000, 0.2F));
        OrbitRender.roundedRect(graphics, x, y, width, height, 14, OrbitRender.alpha(0xFFFFFFFF, 0.09F));
        OrbitRender.roundedRect(graphics, x + 1, y + 1, width - 2, height - 2, 13,
            OrbitRender.alpha(palette.elevatedPanel(), 0.86F));
    }

    private void drawMiniPlayer(GuiGraphics graphics, OrbitTheme.Palette palette) {
        PartyStatePayload party = PartySyncClient.getLatestState();
        graphics.drawString(this.font, "Now playing", CONTENT_X, 498, palette.textMuted());
        OrbitRender.roundedRect(graphics, CONTENT_X, 518, CONTENT_WIDTH, 72, 12, OrbitRender.alpha(0xFFFFFFFF, 0.09F));
        OrbitRender.roundedRect(graphics, CONTENT_X + 1, 519, CONTENT_WIDTH - 2, 70, 11, 0xFF1A1624);
        drawAlbumArt(graphics, CONTENT_X + 12, 531, 42, palette);
        graphics.drawString(this.font, trackTitle(party), CONTENT_X + 66, 530, palette.textMain());
        graphics.drawString(this.font, party == null ? "Orbit player ready" : "Party: " + party.partyName(),
            CONTENT_X + 66, 548, palette.textMuted());
        graphics.drawString(this.font, party == null ? "No synced track" : playbackLabel(party),
            CONTENT_X + 66, 570, party != null && party.playbackState() == PartySync.PLAYING ? palette.success() : palette.textMuted());
        graphics.drawString(this.font, party == null ? "--:--" : formatTime(PartySyncClient.getEstimatedPositionMillis()),
            CONTENT_X + 260, 548, palette.textMuted());
        OrbitRender.roundedRect(graphics, CONTENT_X + 12, 586, CONTENT_WIDTH - 24, 2, 1,
            OrbitRender.alpha(palette.primary(), 0.5F));
    }

    private void drawContentFooter(GuiGraphics graphics, OrbitTheme.Palette palette) {
        int center = CONTENT_X + CONTENT_WIDTH / 2;
        graphics.fill(CONTENT_X + 74, 664, center - 78, 665, OrbitRender.alpha(palette.border(), 0.55F));
        graphics.drawCenteredString(this.font, "discord.gg/orbitmod", center, 658, palette.textMuted());
        graphics.fill(center + 78, 664, CONTENT_X + CONTENT_WIDTH - 74, 665, OrbitRender.alpha(palette.border(), 0.55F));
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
        for (ScreenButton button : buttons) {
            button.visible = button.onlyVisibleOn == null || button.onlyVisibleOn == activeTab;
            button.active = button.visible;
            button.selected = button.destination == activeTab;
            if (button.style == ButtonStyle.SKY) {
                button.selected = button.label.equalsIgnoreCase(settings.activeSky.replace('_', ' '));
            } else if (button.style == ButtonStyle.PILL) {
                button.selected = button.label.equalsIgnoreCase(titleCase(settings.performancePreset));
            } else if (button.onlyVisibleOn == Tab.STYLES) {
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

    private void drawEllipse(GuiGraphics graphics, int centerX, int centerY, int radiusX, int radiusY, int thickness, int color) {
        int innerRadiusX = Math.max(0, radiusX - thickness * 2);
        int innerRadiusY = Math.max(0, radiusY - thickness * 2);
        for (int row = -radiusY; row <= radiusY; row++) {
            double outerRatio = row / (double) Math.max(1, radiusY);
            int outerHalfWidth = (int) Math.round(radiusX * Math.sqrt(Math.max(0.0, 1.0 - outerRatio * outerRatio)));
            int innerHalfWidth = 0;
            if (innerRadiusY > 0 && Math.abs(row) < innerRadiusY) {
                double innerRatio = row / (double) innerRadiusY;
                innerHalfWidth = (int) Math.round(innerRadiusX * Math.sqrt(Math.max(0.0, 1.0 - innerRatio * innerRatio)));
            }
            int leftEdge = centerX - outerHalfWidth;
            int rightEdge = centerX + outerHalfWidth + 1;
            if (innerHalfWidth > 0) {
                graphics.fill(leftEdge, centerY + row, centerX - innerHalfWidth, centerY + row + thickness, color);
                graphics.fill(centerX + innerHalfWidth + 1, centerY + row, rightEdge, centerY + row + thickness, color);
            } else {
                graphics.fill(leftEdge, centerY + row, rightEdge, centerY + row + thickness, color);
            }
        }
    }

    private String worldLabel() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return "Not in a world";
        }
        return minecraft.level.dimension().identifier().toString();
    }

    private String playbackLabel(PartyStatePayload state) {
        return switch (state.playbackState()) {
            case PartySync.PLAYING -> "Playing  /  " + formatTime(PartySyncClient.getEstimatedPositionMillis());
            case PartySync.PAUSED -> "Paused";
            case PartySync.STOPPED -> "Stopped";
            default -> "Idle";
        };
    }

    private String enabledLabel(boolean enabled) {
        return enabled ? "Enabled" : "Disabled";
    }

    private String formatTime(long timeMillis) {
        long seconds = Math.max(0L, timeMillis) / 1000L;
        return String.format("%d:%02d", seconds / 60L, seconds % 60L);
    }

    private String titleCase(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String lower = value.toLowerCase(java.util.Locale.ROOT);
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    private double toCanvasX(double mouseX) {
        return mouseX / scaleFactor - canvasOffsetX;
    }

    private double toCanvasY(double mouseY) {
        return mouseY / scaleFactor - canvasOffsetY;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        OrbitTheme.Palette palette = OrbitTheme.current();
        drawBackground(graphics, palette);

        graphics.pose().pushMatrix();
        graphics.pose().translate(canvasOffsetX, canvasOffsetY);
        graphics.pose().scale(scaleFactor, scaleFactor);
        syncButtonVisibility();
        super.render(graphics, (int) toCanvasX(mouseX), (int) toCanvasY(mouseY), delta);
        OrbitToast.render(graphics, this.font, CANVAS_WIDTH);
        graphics.pose().popMatrix();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        MouseButtonEvent canvasEvent = new MouseButtonEvent(toCanvasX(event.x()), toCanvasY(event.y()), event.buttonInfo());
        return super.mouseClicked(canvasEvent, doubleClick);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        MouseButtonEvent canvasEvent = new MouseButtonEvent(toCanvasX(event.x()), toCanvasY(event.y()), event.buttonInfo());
        return super.mouseReleased(canvasEvent);
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
        CONTROL,
        FOOTER
    }

    private final class ScreenButton extends AbstractWidget {
        private final ButtonStyle style;
        private final String icon;
        private final String label;
        private final String detail;
        private final Tab destination;
        private final Runnable action;
        private final Tab onlyVisibleOn;
        private boolean selected;

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
            Tab onlyVisibleOn
        ) {
            super(x, y, width, height, Component.literal(label));
            this.style = style;
            this.icon = icon;
            this.label = label;
            this.detail = detail;
            this.destination = destination;
            this.action = action;
            this.onlyVisibleOn = onlyVisibleOn;
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
            OrbitTheme.Palette palette = OrbitTheme.current();
            boolean hovered = isHoveredOrFocused();
            switch (style) {
                case BRAND -> {
                    OrbitRender.roundedRect(graphics, getX(), getY(), width, height, 10,
                        OrbitRender.alpha(palette.primary(), hovered ? 0.42F : 0.2F));
                    graphics.drawCenteredString(OrbitScreen.this.font, "OB", getX() + width / 2, getY() + 12,
                        palette.textMain());
                }
                case NAVIGATION -> {
                    if (selected) {
                        OrbitRender.roundedRect(graphics, getX(), getY(), width, height, 10, 0xFF7B4CE2);
                        graphics.fillGradient(getX() + 1, getY() + 1, getX() + width - 1, getY() + height - 1,
                            0xFF7B4CE2, 0xFF9D6BFF);
                    } else if (hovered) {
                        OrbitRender.roundedRect(graphics, getX(), getY(), width, height, 10, 0x1AFFFFFF);
                    }
                    drawIcon(graphics, icon, getX() + 19, getY() + height / 2, selected ? 0xFFFFFFFF : palette.textSecondary());
                    graphics.drawString(OrbitScreen.this.font, label, getX() + 38, getY() + (height - 9) / 2,
                        selected ? 0xFFFFFFFF : palette.textSecondary());
                }
                case FEATURE -> {
                    OrbitRender.roundedRect(graphics, getX(), getY() + 2, width, height, 12,
                        OrbitRender.alpha(0xFF000000, 0.2F));
                    OrbitRender.roundedRect(graphics, getX(), getY(), width, height, 12,
                        hovered ? OrbitRender.alpha(palette.primary(), 0.45F) : OrbitRender.alpha(0xFFFFFFFF, 0.12F));
                    OrbitRender.roundedRect(graphics, getX() + 1, getY() + 1, width - 2, height - 2, 11,
                        hovered ? 0xFF292334 : 0xFF1D1927);
                    drawIcon(graphics, icon, getX() + 24, getY() + 27, palette.primary());
                    graphics.drawString(OrbitScreen.this.font, label, getX() + 46, getY() + 18, palette.textMain());
                    drawWrappedDetail(graphics, getX() + 16, getY() + 48, width - 32, detail, palette.textMuted(), 2);
                    graphics.drawString(OrbitScreen.this.font, "->", getX() + width - 27, getY() + height - 21,
                        OrbitRender.alpha(palette.lavender(), 0.85F));
                }
                case SKY, ACTION -> {
                    OrbitRender.roundedRect(graphics, getX(), getY(), width, height, 10,
                        selected || hovered ? OrbitRender.alpha(palette.primary(), 0.52F) : OrbitRender.alpha(0xFFFFFFFF, 0.1F));
                    OrbitRender.roundedRect(graphics, getX() + 1, getY() + 1, width - 2, height - 2, 9,
                        OrbitRender.alpha(palette.elevatedPanel(), 0.9F));
                    drawIcon(graphics, icon, getX() + 21, getY() + height / 2, palette.primary());
                    graphics.drawString(OrbitScreen.this.font, label, getX() + 40,
                        getY() + (detail.isEmpty() ? (height - 9) / 2 : 13), palette.textMain());
                    if (!detail.isEmpty()) {
                        drawWrappedDetail(graphics, getX() + 40, getY() + 34, width - 52, detail, palette.textMuted(), 2);
                    }
                }
                case PILL -> {
                    int fill = selected ? palette.primary() : hovered ? palette.elevatedPanel() : palette.panel();
                    OrbitRender.roundedRect(graphics, getX(), getY(), width, height, 12, fill);
                    OrbitRender.roundedRect(graphics, getX(), getY(), width, height, 12,
                        OrbitRender.alpha(selected ? palette.lavender() : palette.border(), selected ? 0.28F : 0.55F));
                    drawIcon(graphics, icon, getX() + 18, getY() + height / 2, palette.textMain());
                    graphics.drawString(OrbitScreen.this.font, label, getX() + 34, getY() + 17, palette.textMain());
                }
                case CONTROL -> {
                    if (icon.equals("play")) {
                        OrbitRender.circle(graphics, getX() + width / 2, getY() + height / 2, Math.min(width, height) / 2,
                            hovered ? palette.lavender() : palette.primary());
                        drawPlayGlyph(graphics, getX() + width / 2, getY() + height / 2, isPartyPlaying(), 0xFF171221);
                    } else {
                        OrbitRender.roundedRect(graphics, getX(), getY(), width, height, height / 2,
                            hovered ? palette.elevatedPanel() : OrbitRender.alpha(palette.elevatedPanel(), 0.78F));
                        drawIcon(graphics, icon, getX() + width / 2, getY() + height / 2, palette.textMain());
                    }
                }
                case FOOTER -> {
                    if (hovered) {
                        OrbitRender.roundedRect(graphics, getX(), getY(), width, height, 8,
                            OrbitRender.alpha(palette.primary(), 0.14F));
                    }
                    drawIcon(graphics, icon, getX() + 12, getY() + height / 2, palette.primary());
                    graphics.drawString(OrbitScreen.this.font, label, getX() + 28, getY() + 9, palette.textMuted());
                }
            }
        }

        private void drawWrappedDetail(GuiGraphics graphics, int x, int y, int maxWidth, String text, int color, int maxLines) {
            List<net.minecraft.util.FormattedCharSequence> lines = OrbitScreen.this.font.split(Component.literal(text), maxWidth);
            for (int index = 0; index < Math.min(maxLines, lines.size()); index++) {
                graphics.drawString(OrbitScreen.this.font, lines.get(index), x, y + index * 12, color);
            }
        }

        @Override
        public void onClick(MouseButtonEvent event, boolean doubleClick) {
            action.run();
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
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
                OrbitRender.circle(graphics, centerX - 7, centerY - 6, 1, color);
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
            default -> {
                OrbitRender.circle(graphics, centerX, centerY, 5, color);
            }
        }
    }
}
