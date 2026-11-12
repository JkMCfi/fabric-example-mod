package dev.orbit.lyrics;

import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LrcParser {
    private static final Pattern TIMESTAMP_PATTERN = Pattern.compile("\\[(\\d+):(\\d+)(?:\\.(\\d+))?\\]\\s*(.*)");

    private LrcParser() {
    }

    public static Map<Long, String> parse(String lyrics) {
        Map<Long, String> entries = new TreeMap<>();
        if (lyrics == null || lyrics.isBlank()) {
            return entries;
        }

        String[] lines = lyrics.split("\\R");
        for (String rawLine : lines) {
            Matcher matcher = TIMESTAMP_PATTERN.matcher(rawLine);
            if (!matcher.find()) {
                continue;
            }

            int minutes = Integer.parseInt(matcher.group(1));
            int seconds = Integer.parseInt(matcher.group(2));
            int centiseconds = matcher.group(3) == null ? 0 : Integer.parseInt(matcher.group(3));
            String text = matcher.group(4) == null ? "" : matcher.group(4).trim();

            long timeMs = ((minutes * 60L) + seconds) * 1000L + (centiseconds * 10L);
            entries.put(timeMs, text);
        }

        return entries;
    }
}
