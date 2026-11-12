package dev.orbit.ui;

import dev.orbit.settings.SettingsManager;

import java.util.List;

public final class OrbitTheme {
    private static final List<Palette> PALETTES = List.of(
        new Palette("ORBIT Purple", 0xFF100D18, 0xFF191522, 0xCC211C2B, 0xEE292334, 0xFFA66CFF, 0xFF8752E8, 0xFFD4B8FF, 0xFFF4F0FA, 0xFFA79AB9, 0xFF746A82, 0xFF42D98A, 0x80494052),
        new Palette("Cyberpunk Neon", 0xFF080B18, 0xFF101226, 0xCC191A33, 0xEE252547, 0xFF00F5D4, 0xFFFF2E93, 0xFFFFB8F3, 0xFFF4F3FF, 0xFFB2B4D2, 0xFF777B9D, 0xFF42D98A, 0x80505A83),
        new Palette("Deep Ocean Blue", 0xFF07121D, 0xFF0D1D2B, 0xCC13283A, 0xEE1B354A, 0xFF37B8FF, 0xFF176D9D, 0xFFA8E3FF, 0xFFF1F8FC, 0xFFA4BDCC, 0xFF6D8798, 0xFF42D98A, 0x80435F72),
        new Palette("Emerald Dark", 0xFF09150F, 0xFF102219, 0xCC193328, 0xEE224333, 0xFF42D98A, 0xFF21875D, 0xFFB3FFD8, 0xFFF0FAF4, 0xFFA6C4B2, 0xFF718B7D, 0xFF42D98A, 0x80445F50),
        new Palette("Sunset Orange", 0xFF1A100F, 0xFF281916, 0xCC3B231D, 0xEE4A2C22, 0xFFFF9B54, 0xFFE55D5D, 0xFFFFD0A8, 0xFFFFF4ED, 0xFFD0AEA0, 0xFF96776D, 0xFF7AE0A3, 0x806B4C43)
    );

    private OrbitTheme() {
    }

    public static Palette current() {
        String themeName = SettingsManager.get().getSettings().theme;
        return PALETTES.stream()
            .filter(palette -> palette.name().equals(themeName))
            .findFirst()
            .orElse(PALETTES.getFirst());
    }

    public static Palette cycle() {
        Palette current = current();
        int nextIndex = (PALETTES.indexOf(current) + 1) % PALETTES.size();
        return select(nextIndex);
    }

    public static int paletteCount() {
        return PALETTES.size();
    }

    public static Palette paletteAt(int index) {
        return PALETTES.get(index);
    }

    public static Palette select(int index) {
        if (index < 0 || index >= PALETTES.size()) {
            throw new IllegalArgumentException("Invalid Orbit theme index: " + index);
        }
        Palette next = PALETTES.get(index);
        SettingsManager.get().getSettings().theme = next.name();
        SettingsManager.get().save();
        return next;
    }

    public record Palette(
        String name,
        int background,
        int secondaryBackground,
        int panel,
        int elevatedPanel,
        int primary,
        int secondary,
        int lavender,
        int textMain,
        int textSecondary,
        int textMuted,
        int success,
        int border
    ) {
    }
}
