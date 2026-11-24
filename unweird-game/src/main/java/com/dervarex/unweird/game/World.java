package com.dervarex.unweird.game;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * The running 2D simulation: what's in the level, movement with walls, touching, the camera. No GL here, so it's
 * tested without a window. UI thread only.
 */
final class World {

    /** Longest physics step; a frame is split into steps no longer than this. */
    static final float STEP = 1f / 120;
    private static final float EPSILON = 1e-4f;
    private static final float MAX_FALL = 30;

    final boolean topDown;
    final int tile;
    final Random random = new Random();
    /** Makes things the world needs (shots), given the game for inner classes. */
    private final Factory factory;

    final List<Sprite> sprites = new ArrayList<>();
    private final List<Sprite> spawned = new ArrayList<>();
    private final Map<Long, List<Sprite>> wallCells = new HashMap<>();
    private boolean wallsDirty = true;

    private Set<Pair> contacts = new HashSet<>();
    private Set<Pair> touchingNow = new HashSet<>();

    /** Level bounds in tiles. */
    float minX, minY, maxX, maxY;
    /** Bottom-left of the camera view, and its size, in tiles. */
    float cameraX, cameraY, viewWidth = 20, viewHeight = 11.25f;
    private boolean cameraPlaced;

    enum Pending { NONE, RESTART, NEXT }

    Pending pending = Pending.NONE;
    float time;

    interface Factory {
        <T> T create(Class<T> type);
    }

    World(boolean topDown, int tile, Factory factory) {
        this.topDown = topDown;
        this.tile = tile;
        this.factory = factory;
    }

    /** Replaces everything with these sprites (a level starting). {@code from/to} areas are filled here. */
    void load(List<Sprite> things) {
        sprites.clear();
        spawned.clear();
        contacts.clear();
        pending = Pending.NONE;
        for (Sprite sprite : things) {
            for (Sprite one : expand(sprite)) {
                add(one);
            }
        }
        wallsDirty = true;
        computeBounds();
        cameraPlaced = false;
    }

    private void add(Sprite sprite) {
        prepare(sprite);
        sprite.world = this;
        sprite.removed = false;
        sprites.add(sprite);
        if (sprite instanceof Wall) {
            wallsDirty = true;
        }
    }

    /** Gives it a size from its picture if it has none. */
    void prepare(Sprite sprite) {
        if (Float.isNaN(sprite.width) || Float.isNaN(sprite.height)) {
            float[] size = naturalSize(sprite.getClass());
            if (Float.isNaN(sprite.width)) {
                sprite.width = size[0];
            }
            if (Float.isNaN(sprite.height)) {
                sprite.height = size[1];
            }
        }
    }

    float[] naturalSize(Class<? extends Sprite> type) {
        Art art = Art.of(type, tile);
        if (art.placeholder) {
            float size = Shot.class.isAssignableFrom(type) ? 0.5f : 1;
            return new float[]{size, size};
        }
        return new float[]{art.pixelWidth() / (float) tile, art.pixelHeight() / (float) tile};
    }

    /** One sprite, or the copies its {@code from(..).to(..)} area asks for. */
    List<Sprite> expand(Sprite sprite) {
        if (Float.isNaN(sprite.toX)) {
            return List.of(sprite);
        }
        prepare(sprite);
        float x0 = Math.min(sprite.x, sprite.toX);
        float x1 = Math.max(sprite.x, sprite.toX);
        float y0 = Math.min(sprite.y, sprite.toY);
        float y1 = Math.max(sprite.y, sprite.toY);
        sprite.toX = Float.NaN;
        sprite.toY = Float.NaN;
        if (sprite instanceof Wall) {
            sprite.x = x0;
            sprite.y = y0;
            sprite.width = x1 - x0 + sprite.width;
            sprite.height = y1 - y0 + sprite.height;
            return List.of(sprite);
        }
        List<Sprite> copies = new ArrayList<>();
        float stepX = Math.max(1, sprite.width);
        float stepY = Math.max(1, sprite.height);
        for (float y = y0; y <= y1 + EPSILON; y += stepY) {
            for (float x = x0; x <= x1 + EPSILON; x += stepX) {
                Sprite one = copies.isEmpty() ? sprite : sprite.copy();
                one.x = x;
                one.y = y;
                copies.add(one);
            }
        }
        return copies;
    }

    /** Adds a sprite while the game runs; it joins after the current step. */
    void spawn(Sprite sprite) {
        spawned.addAll(expand(sprite));
    }

    <T> T create(Class<T> type) {
        return factory.create(type);
    }

    void requestRestart() {
        if (pending == Pending.NONE) {
            pending = Pending.RESTART;
        }
    }

    void requestNextLevel() {
        pending = Pending.NEXT;
    }

    // ---- simulation ----

    /** Advances by {@code dt} seconds (split into short steps). */
    void update(float dt) {
        int steps = Math.max(1, (int) Math.ceil(dt / STEP));
        float step = dt / steps;
        for (int i = 0; i < steps && pending == Pending.NONE; i++) {
            step(step);
        }
        time += dt;
        for (Sprite sprite : List.copyOf(sprites)) {
            if (!sprite.removed) {
                sprite.animation += dt;
                Hooks.of(sprite.getClass()).call(sprite, "tick");
            }
        }
        flush();
        followCamera(dt);
    }

    private void step(float dt) {
        if (wallsDirty) {
            indexWalls();
        }
        touchingNow = new HashSet<>();
        for (Sprite sprite : sprites) {
            if (sprite.removed || sprite instanceof Wall) {
                continue;
            }
            sprite.behave(this, dt);
            if (sprite.falls()) {
                sprite.velocityY = Math.max(-MAX_FALL, sprite.velocityY - Player.GRAVITY * dt);
            }
            move(sprite, sprite.velocityX * dt, true);
            sprite.onGround = false;
            move(sprite, sprite.velocityY * dt, false);
            if (!topDown && sprite.y + sprite.height < minY - 4) {
                fellOut(sprite);
            }
        }
        touches();
        contacts = touchingNow;
        flush();
    }

    private void fellOut(Sprite sprite) {
        if (sprite instanceof Player player) {
            player.invulnerable = 0;
            player.damage();
            requestRestart(); // it can't get back up
        } else {
            sprite.remove();
        }
    }

    /** Moves along one axis, stopping at walls. */
    private void move(Sprite sprite, float distance, boolean horizontal) {
        if (distance == 0) {
            return;
        }
        if (!sprite.blockedByWalls()) {
            if (horizontal) sprite.x += distance;
            else sprite.y += distance;
            return;
        }
        float left = sprite.x + (horizontal ? Math.min(0, distance) : 0);
        float right = sprite.x + sprite.width + (horizontal ? Math.max(0, distance) : 0);
        float bottom = sprite.y + (horizontal ? 0 : Math.min(0, distance));
        float top = sprite.y + sprite.height + (horizontal ? 0 : Math.max(0, distance));
        Sprite hit = null;
        for (Sprite wall : wallsNear(left, bottom, right, top)) {
            if (wall.removed || wall == sprite) {
                continue;
            }
            boolean oneWay = wall instanceof Platform;
            if (horizontal) {
                if (oneWay || !overlaps(sprite.y, sprite.y + sprite.height, wall.y, wall.y + wall.height)) {
                    continue;
                }
                if (distance > 0 && sprite.x + sprite.width <= wall.x + EPSILON && sprite.x + sprite.width + distance > wall.x) {
                    distance = wall.x - sprite.x - sprite.width;
                    hit = wall;
                } else if (distance < 0 && sprite.x >= wall.x + wall.width - EPSILON && sprite.x + distance < wall.x + wall.width) {
                    distance = wall.x + wall.width - sprite.x;
                    hit = wall;
                }
            } else {
                if (!overlaps(sprite.x, sprite.x + sprite.width, wall.x, wall.x + wall.width)) {
                    continue;
                }
                if (distance > 0 && !oneWay && sprite.y + sprite.height <= wall.y + EPSILON && sprite.y + sprite.height + distance > wall.y) {
                    distance = wall.y - sprite.y - sprite.height;
                    hit = wall;
                } else if (distance < 0 && sprite.y >= wall.y + wall.height - EPSILON && sprite.y + distance < wall.y + wall.height) {
                    distance = wall.y + wall.height - sprite.y;
                    hit = wall;
                }
            }
        }
        if (horizontal) {
            sprite.x += distance;
        } else {
            sprite.y += distance;
        }
        if (hit != null) {
            if (horizontal) {
                sprite.velocityX = 0;
            } else {
                if (sprite.velocityY <= 0) {
                    sprite.onGround = true;
                }
                sprite.velocityY = 0;
            }
            touchingNow.add(new Pair(sprite, hit));
            Pair pair = new Pair(sprite, hit);
            if (!contacts.contains(pair)) {
                contacts.add(pair); // report once per contact, even if it's hit again in this step
                sprite.bumped(hit, horizontal);
                Hooks.of(sprite.getClass()).call(sprite, "touch", hit);
                Hooks.of(hit.getClass()).call(hit, "touch", sprite);
            }
        }
    }

    private static boolean overlaps(float a0, float a1, float b0, float b1) {
        return a0 < b1 - EPSILON && b0 < a1 - EPSILON;
    }

    /** Pairs that started touching this step: the kinds react first, then the game's touch methods. */
    private void touches() {
        List<Sprite> movers = new ArrayList<>();
        for (Sprite sprite : sprites) {
            if (!sprite.removed && !(sprite instanceof Wall)) {
                movers.add(sprite);
            }
        }
        for (int i = 0; i < movers.size(); i++) {
            Sprite a = movers.get(i);
            for (int j = i + 1; j < movers.size(); j++) {
                Sprite b = movers.get(j);
                if (a.removed || b.removed || !a.touches(b) || shotOwnHit(a, b)) {
                    continue;
                }
                Pair pair = new Pair(a, b);
                touchingNow.add(pair);
                if (contacts.contains(pair)) {
                    continue;
                }
                a.touched(b);
                b.touched(a);
                Hooks.of(a.getClass()).call(a, "touch", b);
                Hooks.of(b.getClass()).call(b, "touch", a);
            }
        }
    }

    private static boolean shotOwnHit(Sprite a, Sprite b) {
        return a instanceof Shot shot && shot.shooter == b || b instanceof Shot other && other.shooter == a;
    }

    /** Adds what was spawned, drops what was removed. */
    private void flush() {
        if (!spawned.isEmpty()) {
            List<Sprite> joining = new ArrayList<>(spawned);
            spawned.clear();
            joining.forEach(this::add);
        }
        boolean wallGone = sprites.removeIf(s -> s.removed && s instanceof Wall);
        sprites.removeIf(s -> s.removed);
        if (wallGone) {
            wallsDirty = true;
        }
    }

    // ---- queries ----

    /** The first player still in the game, or null. */
    Player player() {
        for (Sprite sprite : sprites) {
            if (sprite instanceof Player player && !player.removed) {
                return player;
            }
        }
        return null;
    }

    /** Whether a wall (or the top of a platform) covers the point. */
    boolean solidAt(float x, float y) {
        if (wallsDirty) {
            indexWalls();
        }
        for (Sprite wall : wallsNear(x, y, x, y)) {
            if (!wall.removed && x >= wall.x && x < wall.x + wall.width && y >= wall.y && y < wall.y + wall.height) {
                return true;
            }
        }
        return false;
    }

    /** The front-most sprite at a point, or null. */
    Sprite spriteAt(float x, float y) {
        Sprite best = null;
        for (Sprite sprite : sprites) {
            if (!sprite.removed && x >= sprite.x && x < sprite.x + sprite.width && y >= sprite.y && y < sprite.y + sprite.height
                    && (best == null || sprite.layer() >= best.layer())) {
                best = sprite;
            }
        }
        return best;
    }

    private void indexWalls() {
        wallCells.clear();
        for (Sprite sprite : sprites) {
            if (sprite instanceof Wall && !sprite.removed) {
                for (int cx = (int) Math.floor(sprite.x); cx <= (int) Math.floor(sprite.x + sprite.width); cx++) {
                    for (int cy = (int) Math.floor(sprite.y); cy <= (int) Math.floor(sprite.y + sprite.height); cy++) {
                        wallCells.computeIfAbsent(cell(cx, cy), k -> new ArrayList<>()).add(sprite);
                    }
                }
            }
        }
        wallsDirty = false;
    }

    private Set<Sprite> wallsNear(float left, float bottom, float right, float top) {
        Set<Sprite> result = new java.util.LinkedHashSet<>();
        for (int cx = (int) Math.floor(left) - 1; cx <= (int) Math.floor(right) + 1; cx++) {
            for (int cy = (int) Math.floor(bottom) - 1; cy <= (int) Math.floor(top) + 1; cy++) {
                List<Sprite> walls = wallCells.get(cell(cx, cy));
                if (walls != null) {
                    result.addAll(walls);
                }
            }
        }
        return result;
    }

    private static long cell(int x, int y) {
        return ((long) x << 32) | (y & 0xffffffffL);
    }

    // ---- camera ----

    private void computeBounds() {
        if (sprites.isEmpty()) {
            minX = minY = 0;
            maxX = viewWidth;
            maxY = viewHeight;
            return;
        }
        minX = minY = Float.MAX_VALUE;
        maxX = maxY = -Float.MAX_VALUE;
        for (Sprite sprite : sprites) {
            minX = Math.min(minX, sprite.x);
            minY = Math.min(minY, sprite.y);
            maxX = Math.max(maxX, sprite.x + sprite.width);
            maxY = Math.max(maxY, sprite.y + sprite.height);
        }
    }

    /** Centers on the player, smoothly, without showing much outside the level. */
    void followCamera(float dt) {
        Player player = player();
        float targetX = player != null ? player.centerX() - viewWidth / 2 : (minX + maxX - viewWidth) / 2;
        float targetY = player != null ? player.centerY() - viewHeight / 2 : (minY + maxY - viewHeight) / 2;
        targetX = clampAxis(targetX, minX, maxX, viewWidth, false);
        targetY = clampAxis(targetY, minY, maxY, viewHeight, !topDown);
        if (!cameraPlaced) {
            cameraX = targetX;
            cameraY = targetY;
            cameraPlaced = true;
        } else {
            float follow = Math.min(1, dt * 8);
            cameraX += (targetX - cameraX) * follow;
            cameraY += (targetY - cameraY) * follow;
        }
    }

    private static float clampAxis(float value, float min, float max, float view, boolean bottomAligned) {
        if (max - min <= view) {
            return bottomAligned ? min : (min + max - view) / 2;
        }
        return Math.max(min, Math.min(max - view, value));
    }

    /** Two sprites touching each other; identity of both. */
    private record Pair(Sprite a, Sprite b) {
    }
}
