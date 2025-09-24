package com.lobby.scoreboard;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class UrlAnimation {

    private static final String URL_TEXT = "heneria.com";
    private static final char[] PALETTE = {'6', '6', 'c', 'c', 'e', '6', '6', 'c', 'c', 'e', '6', '6', 'c', 'c', 'e'};

    private final List<String> frames;
    private int index;

    public UrlAnimation() {
        this.frames = buildFrames();
        this.index = 0;
    }

    public String getCurrentFrame() {
        if (frames.isEmpty()) {
            return "    §7→ §eheneria.com";
        }
        return frames.get(index);
    }

    public String nextFrame() {
        if (frames.isEmpty()) {
            return "    §7→ §eheneria.com";
        }
        index = (index + 1) % frames.size();
        return frames.get(index);
    }

    private List<String> buildFrames() {
        if (URL_TEXT.isEmpty()) {
            return List.of("    §7→ §eheneria.com");
        }
        final int paletteLength = PALETTE.length;
        final List<String> generated = new ArrayList<>(paletteLength);
        for (int offset = 0; offset < paletteLength; offset++) {
            final StringBuilder builder = new StringBuilder("    §7→ ");
            for (int index = 0; index < URL_TEXT.length(); index++) {
                final char color = PALETTE[(index + offset) % paletteLength];
                builder.append('§').append(color).append(URL_TEXT.charAt(index));
            }
            generated.add(builder.toString());
        }
        return Collections.unmodifiableList(generated);
    }
}
