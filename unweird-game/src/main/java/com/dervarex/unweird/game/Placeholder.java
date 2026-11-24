package com.dervarex.unweird.game;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.Locale;
import java.util.Random;

/**
 * Pictures for sprites that don't have one yet, so a game is playable before anyone draws anything. The kind sets the
 * shape, words in the class name the colors: {@code Grass}, {@code Brick}, {@code Stone}, {@code Ice}, {@code Lava},
 * {@code Gem}, {@code Door}, …
 */
final class Placeholder {

    enum Kind { PLAYER, WALL, PLATFORM, PICKUP, HAZARD, GOAL, ENEMY, SHOT, THING }

    private Placeholder() {
    }

    static BufferedImage draw(Kind kind, String name, int size) {
        int s = Math.max(8, size);
        BufferedImage image = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        String n = name.toLowerCase(Locale.ROOT);
        float u = s / 16f; // one "pixel" of a 16-pixel tile
        switch (kind) {
            case PLAYER -> person(g, s, u, hashColor(n, new Color(0x3d7bd9), n.contains("hero") || n.contains("player")));
            case ENEMY -> blob(g, s, u, hashColor(n, new Color(0x9b3fc4), n.contains("enemy") || n.contains("slime")));
            case WALL -> wall(g, s, u, n);
            case PLATFORM -> platform(g, s, u, n);
            case PICKUP -> pickup(g, s, u, n);
            case HAZARD -> hazard(g, s, u, n);
            case GOAL -> goal(g, s, u, n);
            case SHOT -> shot(g, s, n);
            case THING -> thing(g, s, u, n);
        }
        g.dispose();
        return image;
    }

    /** A color from the name (stable), or {@code preferred} for typical names. */
    static Color hashColor(String name, Color preferred, boolean usePreferred) {
        if (usePreferred) {
            return preferred;
        }
        float hue = (name.hashCode() & 0xffff) / 65535f;
        return Color.getHSBColor(hue, 0.55f, 0.8f);
    }

    private static void person(Graphics2D g, int s, float u, Color body) {
        g.setColor(body.darker());
        g.fillRoundRect(r(3 * u), r(1 * u), r(10 * u), r(14 * u), r(5 * u), r(5 * u));
        g.setColor(body);
        g.fillRoundRect(r(4 * u), r(1 * u), r(9 * u), r(13 * u), r(5 * u), r(5 * u));
        eyes(g, u, 7, 5);
    }

    private static void blob(Graphics2D g, int s, float u, Color body) {
        g.setColor(body.darker());
        g.fillRoundRect(r(1 * u), r(5 * u), r(14 * u), r(11 * u), r(8 * u), r(8 * u));
        g.setColor(body);
        g.fillRoundRect(r(2 * u), r(5 * u), r(13 * u), r(10 * u), r(8 * u), r(8 * u));
        eyes(g, u, 6, 8);
        g.setColor(new Color(0x2a1030));
        g.setStroke(new BasicStroke(Math.max(1, u)));
        g.drawLine(r(5 * u), r(7 * u), r(8 * u), r(8 * u)); // angry brows
        g.drawLine(r(13 * u), r(7 * u), r(10 * u), r(8 * u));
    }

    private static void eyes(Graphics2D g, float u, int left, int top) {
        g.setColor(Color.WHITE);
        g.fillRect(r(left * u), r(top * u), r(3 * u), r(3 * u));
        g.fillRect(r((left + 4) * u), r(top * u), r(3 * u), r(3 * u));
        g.setColor(new Color(0x18182a));
        g.fillRect(r((left + 1.5f) * u), r((top + 1) * u), r(1.5f * u), r(2 * u));
        g.fillRect(r((left + 5.5f) * u), r((top + 1) * u), r(1.5f * u), r(2 * u));
    }

    private static void wall(Graphics2D g, int s, float u, String n) {
        Random random = new Random(n.hashCode());
        if (n.contains("brick")) {
            g.setColor(new Color(0x9c4a32));
            g.fillRect(0, 0, s, s);
            g.setColor(new Color(0xd8c8b0));
            for (int row = 0; row < 4; row++) {
                int y = r(row * 4 * u);
                g.fillRect(0, y, s, Math.max(1, r(u)));
                int offset = row % 2 == 0 ? 0 : r(4 * u);
                for (int x = offset; x < s; x += r(8 * u)) {
                    g.fillRect(x, y, Math.max(1, r(u)), r(4 * u));
                }
            }
            return;
        }
        Color base;
        Color top = null;
        if (n.contains("grass") || n.contains("ground") || n.contains("dirt") || n.contains("floor") || n.contains("earth")) {
            base = new Color(0x8a5a35);
            top = n.contains("dirt") ? null : new Color(0x55a630);
        } else if (n.contains("ice")) {
            base = new Color(0xa8dcf0);
        } else if (n.contains("sand")) {
            base = new Color(0xe0c98a);
        } else if (n.contains("wood") || n.contains("plank") || n.contains("crate") || n.contains("box")) {
            base = new Color(0xb07a42);
        } else if (n.contains("stone") || n.contains("rock") || n.contains("wall") || n.contains("block")) {
            base = new Color(0x8a8f98);
        } else {
            base = hashColor(n, null, false);
        }
        g.setColor(base);
        g.fillRect(0, 0, s, s);
        for (int i = 0; i < 10; i++) { // speckles
            g.setColor(random.nextBoolean() ? base.darker() : brighter(base));
            g.fillRect(r(random.nextInt(15) * u), r(random.nextInt(15) * u), Math.max(1, r(u)), Math.max(1, r(u)));
        }
        if (top != null) {
            g.setColor(top);
            g.fillRect(0, 0, s, r(4 * u));
            for (int x = 0; x < 16; x += 2) {
                g.fillRect(r(x * u), r(4 * u), r(u), r((1 + random.nextInt(2)) * u));
            }
            g.setColor(brighter(top));
            g.fillRect(0, 0, s, Math.max(1, r(u)));
        }
        g.setColor(new Color(0, 0, 0, 40));
        g.fillRect(0, s - Math.max(1, r(u)), s, Math.max(1, r(u)));
    }

    private static void platform(Graphics2D g, int s, float u, String n) {
        Color wood = n.contains("cloud") ? new Color(0xf4f6fb) : new Color(0xb07a42);
        g.setColor(wood.darker());
        g.fillRect(0, 0, s, r(5 * u));
        g.setColor(wood);
        g.fillRect(0, 0, s, r(4 * u));
        g.setColor(brighter(wood));
        g.fillRect(0, 0, s, Math.max(1, r(u)));
    }

    private static void pickup(Graphics2D g, int s, float u, String n) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        if (n.contains("gem") || n.contains("diamond") || n.contains("crystal") || n.contains("jewel")) {
            Color c = n.contains("ruby") ? new Color(0xe0294a) : new Color(0x3fd0e0);
            diamond(g, u, c);
        } else if (n.contains("heart") || n.contains("life") || n.contains("health")) {
            g.setColor(new Color(0xe0294a));
            g.fillOval(r(3 * u), r(4 * u), r(6 * u), r(6 * u));
            g.fillOval(r(7 * u), r(4 * u), r(6 * u), r(6 * u));
            g.fillPolygon(new Polygon(new int[]{r(3 * u), r(13 * u), r(8 * u)}, new int[]{r(8 * u), r(8 * u), r(14 * u)}, 3));
        } else if (n.contains("key")) {
            g.setColor(new Color(0xf2c230));
            g.fillOval(r(2 * u), r(5 * u), r(6 * u), r(6 * u));
            g.fillRect(r(7 * u), r(7 * u), r(7 * u), r(2 * u));
            g.fillRect(r(12 * u), r(9 * u), r(2 * u), r(3 * u));
        } else {
            Color gold = n.contains("coin") || n.contains("gold") ? new Color(0xf2c230) : hashColor(n, new Color(0xf2c230), true);
            g.setColor(gold.darker());
            g.fillOval(r(3 * u), r(2 * u), r(10 * u), r(12 * u));
            g.setColor(gold);
            g.fillOval(r(4 * u), r(2 * u), r(8 * u), r(11 * u));
            g.setColor(brighter(brighter(gold)));
            g.fillRect(r(6 * u), r(4 * u), r(1.5f * u), r(6 * u));
        }
    }

    private static void diamond(Graphics2D g, float u, Color c) {
        g.setColor(c.darker());
        g.fillPolygon(new Polygon(new int[]{r(8 * u), r(14 * u), r(8 * u), r(2 * u)}, new int[]{r(2 * u), r(7 * u), r(14 * u), r(7 * u)}, 4));
        g.setColor(c);
        g.fillPolygon(new Polygon(new int[]{r(8 * u), r(12 * u), r(8 * u), r(4 * u)}, new int[]{r(3 * u), r(7 * u), r(12 * u), r(7 * u)}, 4));
    }

    private static void hazard(Graphics2D g, int s, float u, String n) {
        if (n.contains("lava") || n.contains("fire") || n.contains("acid") || n.contains("water")) {
            Color hot = n.contains("acid") ? new Color(0x7ad13a) : n.contains("water") ? new Color(0x2f7fd8) : new Color(0xf05a1a);
            g.setColor(hot);
            g.fillRect(0, r(5 * u), s, s - r(5 * u));
            g.setColor(brighter(hot));
            for (int x = 0; x < 16; x += 4) {
                g.fillOval(r(x * u), r(3 * u), r(4 * u), r(4 * u));
            }
            return;
        }
        for (int i = 0; i < 4; i++) {
            int left = r(i * 4 * u);
            g.setColor(new Color(0x6c7280));
            g.fillPolygon(new Polygon(new int[]{left, left + r(4 * u), left + r(2 * u)}, new int[]{s, s, r(6 * u)}, 3));
            g.setColor(new Color(0xc9ced8));
            g.fillPolygon(new Polygon(new int[]{left + r(u), left + r(2 * u), left + r(2 * u)}, new int[]{s, s, r(7 * u)}, 3));
        }
    }

    private static void goal(Graphics2D g, int s, float u, String n) {
        if (n.contains("door") || n.contains("exit") || n.contains("portal")) {
            Color door = n.contains("portal") ? new Color(0x8f5ae0) : new Color(0x7a4a22);
            g.setColor(door.darker());
            g.fillRoundRect(r(3 * u), r(1 * u), r(10 * u), r(15 * u), r(8 * u), r(8 * u));
            g.setColor(door);
            g.fillRoundRect(r(4 * u), r(2 * u), r(8 * u), r(14 * u), r(6 * u), r(6 * u));
            g.setColor(new Color(0xf2c230));
            g.fillRect(r(10 * u), r(9 * u), r(u), r(u));
            return;
        }
        g.setColor(new Color(0xdedede));
        g.fillRect(r(3 * u), r(1 * u), Math.max(1, r(1.5f * u)), s - r(u));
        g.setColor(new Color(0x2fbf55));
        g.fillPolygon(new Polygon(new int[]{r(4.5f * u), r(14 * u), r(4.5f * u)}, new int[]{r(1 * u), r(4.5f * u), r(8 * u)}, 3));
    }

    private static void shot(Graphics2D g, int s, String n) {
        Color c = n.contains("ice") || n.contains("frost") ? new Color(0x8fd8f8) : n.contains("laser") ? new Color(0xff3b5c) : new Color(0xff9a1f);
        g.setColor(c.darker());
        g.fillOval(s / 4, s / 4, s / 2, s / 2);
        g.setColor(brighter(c));
        g.fillOval(s / 4 + Math.max(1, s / 10), s / 4 + Math.max(1, s / 10), s / 3, s / 3);
    }

    private static void thing(Graphics2D g, int s, float u, String n) {
        if (n.contains("tree") || n.contains("bush")) {
            g.setColor(new Color(0x7a4a22));
            g.fillRect(r(7 * u), r(9 * u), r(2 * u), r(7 * u));
            g.setColor(new Color(0x3c8f3c));
            g.fillOval(r(2 * u), r(1 * u), r(12 * u), r(10 * u));
            return;
        }
        if (n.contains("cloud")) {
            g.setColor(new Color(255, 255, 255, 220));
            g.fillOval(r(1 * u), r(6 * u), r(8 * u), r(6 * u));
            g.fillOval(r(5 * u), r(3 * u), r(8 * u), r(8 * u));
            g.fillOval(r(8 * u), r(6 * u), r(7 * u), r(6 * u));
            return;
        }
        Color c = hashColor(n, null, false);
        g.setColor(c.darker());
        g.fillRoundRect(r(u), r(u), r(14 * u), r(14 * u), r(4 * u), r(4 * u));
        g.setColor(c);
        g.fillRoundRect(r(2 * u), r(u), r(13 * u), r(13 * u), r(4 * u), r(4 * u));
    }

    private static Color brighter(Color c) {
        return new Color(Math.min(255, c.getRed() + 40), Math.min(255, c.getGreen() + 40), Math.min(255, c.getBlue() + 40), c.getAlpha());
    }

    private static int r(float v) {
        return Math.round(v);
    }
}
