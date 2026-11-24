package com.dervarex.unweird.game;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The pictures of one sprite class, found by its name (see {@link Sprite}): frames per state, or a placeholder.
 * Subclasses without pictures use their superclass's ({@code class BigCoin extends Coin {}} shows {@code coin.png}).
 */
final class Art {

    enum State { IDLE, WALK, JUMP, FALL }

    private static final Map<String, Art> CACHE = new ConcurrentHashMap<>();
    static final float FRAMES_PER_SECOND = 8;

    private final Map<State, List<BufferedImage>> frames = new EnumMap<>(State.class);
    /** Generated because no file was found. */
    final boolean placeholder;

    private Art(Class<? extends Sprite> type, int tile) {
        boolean found = false;
        for (Class<?> c = type; c != null && c != Sprite.class && !isLibrary(c); c = c.getSuperclass()) {
            if (load(type, Assets.snake(c.getSimpleName()))) {
                found = true;
                break;
            }
        }
        placeholder = !found;
        if (!found) {
            Placeholder.Kind kind = kindOf(type);
            frames.put(State.IDLE, List.of(Placeholder.draw(kind, type.getSimpleName(), tile)));
        }
    }

    static Art of(Class<? extends Sprite> type, int tile) {
        return CACHE.computeIfAbsent(type.getName() + "@" + tile, k -> new Art(type, tile));
    }

    private boolean load(Class<?> owner, String base) {
        List<BufferedImage> idle = sequence(owner, base);
        if (idle.isEmpty()) {
            idle = sequence(owner, base + "_idle");
        }
        if (idle.isEmpty()) {
            return false;
        }
        frames.put(State.IDLE, idle);
        put(State.WALK, sequence(owner, base + "_walk"));
        put(State.JUMP, sequence(owner, base + "_jump"));
        put(State.FALL, sequence(owner, base + "_fall"));
        return true;
    }

    private void put(State state, List<BufferedImage> images) {
        if (!images.isEmpty()) {
            frames.put(state, images);
        }
    }

    /** {@code base.png}, or {@code base_1.png}, {@code base_2.png}, … */
    private static List<BufferedImage> sequence(Class<?> owner, String base) {
        List<BufferedImage> result = new ArrayList<>();
        URL single = Assets.find(owner, base, "png");
        if (single != null) {
            BufferedImage image = decode(single);
            if (image != null) {
                result.add(image);
                return result;
            }
        }
        for (int i = 1; ; i++) {
            URL frame = Assets.find(owner, base + "_" + i, "png");
            BufferedImage image = frame == null ? null : decode(frame);
            if (image == null) {
                return result;
            }
            result.add(image);
        }
    }

    static BufferedImage decode(URL url) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(Assets.read(url)));
            if (image == null) {
                System.err.println("unweird-game: can't read the picture " + url + " (use PNG)");
            }
            return image;
        } catch (IOException e) {
            System.err.println("unweird-game: can't read the picture " + url + ": " + e.getMessage());
            return null;
        }
    }

    /** The frame to show for a state at an animation time (seconds); missing states fall back to similar ones. */
    BufferedImage frame(State state, float time) {
        List<BufferedImage> list = frames.get(state);
        if (list == null && state == State.FALL) {
            list = frames.get(State.JUMP);
        }
        if (list == null) {
            list = frames.get(State.IDLE);
        }
        int index = (int) (time * FRAMES_PER_SECOND) % list.size();
        return list.get(Math.max(0, index));
    }

    /** Size of the first picture in pixels. */
    int pixelWidth() {
        return frames.get(State.IDLE).getFirst().getWidth();
    }

    int pixelHeight() {
        return frames.get(State.IDLE).getFirst().getHeight();
    }

    private static boolean isLibrary(Class<?> c) {
        return Members.isLibrary(c);
    }

    static Placeholder.Kind kindOf(Class<? extends Sprite> type) {
        if (Platform.class.isAssignableFrom(type)) return Placeholder.Kind.PLATFORM;
        if (Wall.class.isAssignableFrom(type)) return Placeholder.Kind.WALL;
        if (Player.class.isAssignableFrom(type)) return Placeholder.Kind.PLAYER;
        if (Pickup.class.isAssignableFrom(type)) return Placeholder.Kind.PICKUP;
        if (Hazard.class.isAssignableFrom(type)) return Placeholder.Kind.HAZARD;
        if (Goal.class.isAssignableFrom(type)) return Placeholder.Kind.GOAL;
        if (Enemy.class.isAssignableFrom(type)) return Placeholder.Kind.ENEMY;
        if (Shot.class.isAssignableFrom(type)) return Placeholder.Kind.SHOT;
        return Placeholder.Kind.THING;
    }
}
