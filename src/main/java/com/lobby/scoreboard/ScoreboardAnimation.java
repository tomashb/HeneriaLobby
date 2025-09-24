package com.lobby.scoreboard;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Lightweight text animation controller that cycles over a list of frames.
 * Frames can be replaced at runtime allowing the scoreboard manager to reload
 * configuration without recreating the animation task.
 */
public final class ScoreboardAnimation {

    private static final String DEFAULT_FRAME = "";

    private volatile List<String> frames = Collections.emptyList();
    private final AtomicInteger index = new AtomicInteger(0);

    public ScoreboardAnimation(final List<String> frames) {
        setFrames(frames);
    }

    public synchronized void setFrames(final List<String> frames) {
        if (frames == null || frames.isEmpty()) {
            this.frames = Collections.emptyList();
            index.set(0);
            return;
        }
        this.frames = List.copyOf(frames);
        index.set(0);
    }

    public String getCurrentFrame() {
        final List<String> snapshot = frames;
        if (snapshot.isEmpty()) {
            return DEFAULT_FRAME;
        }
        final int current = Math.min(index.get(), snapshot.size() - 1);
        return snapshot.get(current);
    }

    public synchronized String nextFrame() {
        final List<String> snapshot = frames;
        if (snapshot.isEmpty()) {
            return DEFAULT_FRAME;
        }
        final int next = (index.get() + 1) % snapshot.size();
        index.set(next);
        return snapshot.get(next);
    }

    public int frameCount() {
        return frames.size();
    }
}
