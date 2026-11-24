package com.dervarex.unweird.game;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.net.URL;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * Block textures: found by name ({@code grass_top}, {@code grass_side}, {@code grass_bottom}, {@code grass}) or drawn
 * as placeholders, packed into one atlas picture with the texture coordinates of every face.
 */
final class BlockArt {

    static final int TOP = 0;
    static final int SIDE = 1;
    static final int BOTTOM = 2;

    /** The atlas picture. */
    final BufferedImage atlas;
    /** Per block id, per face kind (top, side, bottom): u0, v0, u1, v1. */
    final float[][][] uv;
    /** Per block id: the side picture, for the hotbar. */
    final BufferedImage[] icons;

    BlockArt(List<Block> blocks) {
        int cell = 16;
        List<BufferedImage> images = new ArrayList<>();
        Map<BufferedImage, Integer> slot = new IdentityHashMap<>();
        BufferedImage[][] faces = new BufferedImage[blocks.size() + 1][];
        icons = new BufferedImage[blocks.size() + 1];
        for (Block block : blocks) {
            BufferedImage[] f = faces(block);
            faces[block.id] = f;
            icons[block.id] = f[SIDE];
            for (BufferedImage image : f) {
                if (!slot.containsKey(image)) {
                    slot.put(image, images.size());
                    images.add(image);
                    cell = Math.max(cell, Math.max(image.getWidth(), image.getHeight()));
                }
            }
        }
        int columns = Math.max(1, (int) Math.ceil(Math.sqrt(Math.max(1, images.size()))));
        int size = 1;
        while (size < columns * cell) {
            size *= 2;
        }
        columns = size / cell;
        atlas = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = atlas.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        float[][] rects = new float[images.size()][];
        float inset = 0.02f / size;
        for (int i = 0; i < images.size(); i++) {
            int x = (i % columns) * cell;
            int y = (i / columns) * cell;
            g.drawImage(images.get(i), x, y, cell, cell, null);
            rects[i] = new float[]{(float) x / size + inset, (float) y / size + inset, (float) (x + cell) / size - inset, (float) (y + cell) / size - inset};
        }
        g.dispose();
        uv = new float[blocks.size() + 1][][];
        for (Block block : blocks) {
            BufferedImage[] f = faces[block.id];
            uv[block.id] = new float[][]{rects[slot.get(f[TOP])], rects[slot.get(f[SIDE])], rects[slot.get(f[BOTTOM])]};
        }
    }

    /** Top, side, bottom pictures of a block. */
    private static BufferedImage[] faces(Block block) {
        Class<?> type = block.getClass();
        String name = Assets.snake(type.getSimpleName());
        BufferedImage all = load(type, name);
        BufferedImage top = load(type, name + "_top");
        BufferedImage side = load(type, name + "_side");
        BufferedImage bottom = load(type, name + "_bottom");
        if (all == null && top == null && side == null && bottom == null) {
            BufferedImage[] drawn = placeholder(block, name);
            return drawn;
        }
        BufferedImage fallback = all != null ? all : side != null ? side : top != null ? top : bottom;
        return new BufferedImage[]{top != null ? top : fallback, side != null ? side : fallback, bottom != null ? bottom : fallback};
    }

    private static BufferedImage load(Class<?> type, String name) {
        URL url = Assets.find(type, name, "png");
        return url == null ? null : Art.decode(url);
    }

    // ---- placeholders ----

    private static BufferedImage[] placeholder(Block block, String name) {
        String n = name.toLowerCase(Locale.ROOT);
        Random random = new Random(n.hashCode());
        if (block.plant) {
            BufferedImage image = plant(n, random);
            return new BufferedImage[]{image, image, image};
        }
        if (n.contains("grass")) {
            BufferedImage dirt = speckled(new Color(0x8a5a35), random, 0.15f);
            BufferedImage top = speckled(new Color(0x5fa83a), random, 0.12f);
            BufferedImage side = speckled(new Color(0x8a5a35), random, 0.15f);
            Graphics2D g = side.createGraphics();
            for (int x = 0; x < 16; x++) {
                int depth = 3 + random.nextInt(2);
                for (int y = 0; y < depth; y++) {
                    side.setRGB(x, y, shade(new Color(0x5fa83a), random, 0.12f).getRGB());
                }
            }
            g.dispose();
            return new BufferedImage[]{top, side, dirt};
        }
        if (n.contains("log") || n.contains("trunk")) {
            BufferedImage side = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
            for (int x = 0; x < 16; x++) {
                Color bark = x % 4 == 0 ? new Color(0x4f3520) : new Color(0x6b4a2c);
                for (int y = 0; y < 16; y++) {
                    side.setRGB(x, y, shade(bark, random, 0.08f).getRGB());
                }
            }
            BufferedImage top = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
            for (int x = 0; x < 16; x++) {
                for (int y = 0; y < 16; y++) {
                    double d = Math.max(Math.abs(x - 7.5), Math.abs(y - 7.5));
                    Color c = d > 6.5 ? new Color(0x4f3520) : ((int) d) % 2 == 0 ? new Color(0xb08a58) : new Color(0x9a7546);
                    top.setRGB(x, y, c.getRGB());
                }
            }
            return new BufferedImage[]{top, side, top};
        }
        if (n.contains("leaves") || n.contains("leaf") || n.contains("foliage")) {
            BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
            for (int x = 0; x < 16; x++) {
                for (int y = 0; y < 16; y++) {
                    if (block.seeThrough && random.nextFloat() < 0.18f) {
                        continue; // holes
                    }
                    image.setRGB(x, y, shade(new Color(0x3f8f34), random, 0.2f).getRGB());
                }
            }
            return same(image);
        }
        if (n.contains("glass") || n.contains("window")) {
            BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
            for (int i = 0; i < 16; i++) {
                int edge = new Color(0xd8eef5).getRGB();
                image.setRGB(i, 0, edge);
                image.setRGB(i, 15, edge);
                image.setRGB(0, i, edge);
                image.setRGB(15, i, edge);
            }
            image.setRGB(3, 3, 0xffffffff);
            image.setRGB(4, 4, 0xffffffff);
            image.setRGB(4, 3, 0xffffffff);
            return same(image);
        }
        if (n.contains("water")) {
            return same(speckled(new Color(0x2f6fd0, false), random, 0.06f, 0.7f));
        }
        if (n.contains("lava")) {
            return same(speckled(new Color(0xe8611a), random, 0.25f, 0.95f));
        }
        if (n.contains("plank") || n.contains("wood")) {
            BufferedImage image = speckled(new Color(0xb3864e), random, 0.06f);
            for (int x = 0; x < 16; x++) {
                for (int row = 3; row < 16; row += 4) {
                    image.setRGB(x, row, new Color(0x7d5a31).getRGB());
                }
            }
            return same(image);
        }
        if (n.contains("brick")) {
            BufferedImage image = speckled(new Color(0x9c4a32), random, 0.1f);
            for (int row = 0; row < 16; row += 4) {
                for (int x = 0; x < 16; x++) {
                    image.setRGB(x, row, 0xffc8b8a0);
                }
                for (int x = row % 8 == 0 ? 0 : 4; x < 16; x += 8) {
                    for (int y = row; y < row + 4; y++) {
                        image.setRGB(x, y, 0xffc8b8a0);
                    }
                }
            }
            return same(image);
        }
        Color ore = null;
        if (n.contains("coal")) ore = new Color(0x222222);
        else if (n.contains("iron")) ore = new Color(0xd8af93);
        else if (n.contains("gold")) ore = new Color(0xf5d33b);
        else if (n.contains("diamond")) ore = new Color(0x4fe0e0);
        else if (n.contains("ruby") || n.contains("redstone")) ore = new Color(0xd02030);
        else if (n.contains("emerald")) ore = new Color(0x30c860);
        if (ore != null) {
            BufferedImage image = speckled(new Color(0x8a8f98), random, 0.12f);
            for (int i = 0; i < 6; i++) {
                int x = 1 + random.nextInt(13);
                int y = 1 + random.nextInt(13);
                image.setRGB(x, y, ore.getRGB());
                image.setRGB(x + 1, y, ore.getRGB());
                image.setRGB(x, y + 1, ore.brighter().getRGB());
            }
            return same(image);
        }
        Color base;
        if (n.contains("dirt") || n.contains("mud") || n.contains("soil")) base = new Color(0x8a5a35);
        else if (n.contains("bedrock")) base = new Color(0x3a3a3e);
        else if (n.contains("cobble")) base = new Color(0x7a7f88);
        else if (n.contains("stone") || n.contains("rock")) base = new Color(0x8a8f98);
        else if (n.contains("gravel")) base = new Color(0x857f7a);
        else if (n.contains("sand")) base = new Color(0xe3d39b);
        else if (n.contains("snow")) base = new Color(0xf2f6fa);
        else if (n.contains("ice")) base = new Color(0x9fd0f0);
        else if (n.contains("clay")) base = new Color(0xa0a6b4);
        else base = Placeholder.hashColor(n, null, false);
        float amount = n.contains("cobble") || n.contains("gravel") ? 0.3f : 0.12f;
        return same(speckled(base, random, amount));
    }

    private static BufferedImage plant(String n, Random random) {
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Color stem = new Color(0x3f8f34);
        if (n.contains("grass") || n.contains("fern")) {
            for (int blade = 0; blade < 6; blade++) {
                int x = 2 + random.nextInt(12);
                int height = 6 + random.nextInt(9);
                for (int y = 15; y > 15 - height; y--) {
                    image.setRGB(Math.max(0, Math.min(15, x + (15 - y) / 5 * (blade % 2 == 0 ? 1 : -1))), y, shade(stem, random, 0.2f).getRGB());
                }
            }
            return image;
        }
        Color petal = n.contains("rose") || n.contains("red") ? new Color(0xd8283a) : n.contains("blue") ? new Color(0x4a6ee0)
                : n.contains("mushroom") ? new Color(0xc23a2a) : new Color(0xf3d23a);
        for (int y = 7; y < 16; y++) {
            image.setRGB(7, y, stem.getRGB());
            image.setRGB(8, y, stem.darker().getRGB());
        }
        image.setRGB(6, 11, stem.getRGB());
        image.setRGB(9, 12, stem.getRGB());
        for (int x = 5; x <= 10; x++) {
            for (int y = 3; y <= 7; y++) {
                if ((x == 5 || x == 10) && (y == 3 || y == 7)) {
                    continue;
                }
                image.setRGB(x, y, shade(petal, random, 0.1f).getRGB());
            }
        }
        image.setRGB(7, 5, 0xfff8e080);
        image.setRGB(8, 5, 0xfff8e080);
        return image;
    }

    private static BufferedImage[] same(BufferedImage image) {
        return new BufferedImage[]{image, image, image};
    }

    private static BufferedImage speckled(Color base, Random random, float amount) {
        return speckled(base, random, amount, 1);
    }

    private static BufferedImage speckled(Color base, Random random, float amount, float alpha) {
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int x = 0; x < 16; x++) {
            for (int y = 0; y < 16; y++) {
                Color c = shade(base, random, amount);
                image.setRGB(x, y, new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.round(alpha * 255)).getRGB());
            }
        }
        return image;
    }

    private static Color shade(Color base, Random random, float amount) {
        float f = 1 + (random.nextFloat() * 2 - 1) * amount;
        return new Color(clamp(base.getRed() * f), clamp(base.getGreen() * f), clamp(base.getBlue() * f));
    }

    private static int clamp(float v) {
        return Math.max(0, Math.min(255, Math.round(v)));
    }
}
