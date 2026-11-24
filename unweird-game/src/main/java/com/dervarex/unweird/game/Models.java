package com.dervarex.unweird.game;

import com.dervarex.unweird.scene3d.Material;
import com.dervarex.unweird.scene3d.Mesh;
import com.dervarex.unweird.scene3d.Node;
import com.dervarex.unweird.scene3d.Texture;
import com.dervarex.unweird.style.Color;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** What things look like: an .obj model found by name, or a placeholder. */
final class Models {

    /** The scene node for a thing; {@code legs} gets the leg nodes of a placeholder animal (for walking). */
    record Model(Node root, Node[] legs) {
    }

    private Models() {
    }

    static Model of(Thing thing) {
        Class<?> type = thing.getClass();
        String name = Assets.snake(type.getSimpleName());
        URL obj = Assets.find(type, name, "obj");
        if (obj != null) {
            Mesh mesh = loadObj(obj);
            URL png = Assets.find(type, name, "png");
            Material material = png != null ? Material.texture(Texture.of(Art.decode(png)).nearest()).cutout()
                    : Material.color(color(name, 0xb0b0b0));
            return new Model(Node.group().add(new Node(mesh, material)), new Node[0]);
        }
        if (thing instanceof Monster) {
            return figure(name, thing.body);
        }
        if (thing instanceof Creature) {
            return animal(name, thing.body);
        }
        Node box = new Node(Mesh.box((float) thing.body.width, (float) thing.body.height, (float) thing.body.width),
                Material.color(color(name, -1)));
        box.position(0, (float) thing.body.height / 2, 0);
        return new Model(Node.group().add(box), new Node[0]);
    }

    /** A four-legged animal made of boxes, colored by name (pig pink, cow brown, sheep white, ...). */
    private static Model animal(String name, Body body) {
        String n = name.toLowerCase(Locale.ROOT);
        Color fur = color(n, -1);
        Color dark = fur.mix(Color.BLACK, 0.25f);
        boolean small = n.contains("chicken") || n.contains("duck") || n.contains("bird") || n.contains("rabbit") || n.contains("bunny");
        float s = small ? 0.5f : 1;
        body.width = 0.9 * s;
        body.height = 0.95 * s;
        Node root = Node.group();
        Node torso = new Node(Mesh.box(0.62f * s, 0.5f * s, 1.0f * s), Material.color(fur)).position(0, 0.62f * s, 0);
        Node head = new Node(Mesh.box(0.46f * s, 0.44f * s, 0.42f * s), Material.color(fur)).position(0, 0.82f * s, 0.6f * s);
        Node eyeLeft = new Node(Mesh.box(0.08f * s, 0.08f * s, 0.02f * s), Material.color(Color.hex("#1b1b25")).unlit())
                .position(-0.12f * s, 0.88f * s, 0.815f * s);
        Node eyeRight = new Node(Mesh.box(0.08f * s, 0.08f * s, 0.02f * s), Material.color(Color.hex("#1b1b25")).unlit())
                .position(0.12f * s, 0.88f * s, 0.815f * s);
        Node snout = new Node(Mesh.box(0.24f * s, 0.16f * s, 0.06f * s), Material.color(dark)).position(0, 0.74f * s, 0.83f * s);
        root.add(torso, head, eyeLeft, eyeRight, snout);
        Node[] legs = new Node[4];
        float[][] at = {{-0.2f, 0.36f}, {0.2f, 0.36f}, {-0.2f, -0.36f}, {0.2f, -0.36f}};
        for (int i = 0; i < 4; i++) {
            Node hip = Node.group().position(at[i][0] * s, 0.38f * s, at[i][1] * s);
            hip.add(new Node(Mesh.box(0.18f * s, 0.38f * s, 0.18f * s), Material.color(dark)).position(0, -0.19f * s, 0));
            legs[i] = hip;
            root.add(hip);
        }
        return new Model(root, legs);
    }

    /** A standing figure made of boxes: legs, body, arms held forward, head. */
    private static Model figure(String name, Body body) {
        Color skin = color(name, 0x5e9e4a);
        Color cloth = skin.mix(Color.hex("#2b3a6b"), 0.7f);
        body.width = 0.6;
        body.height = 1.8;
        Node root = Node.group();
        Node torso = new Node(Mesh.box(0.5f, 0.7f, 0.28f), Material.color(cloth)).position(0, 1.1f, 0);
        Node head = new Node(Mesh.box(0.46f, 0.46f, 0.46f), Material.color(skin)).position(0, 1.68f, 0);
        Node eyeLeft = new Node(Mesh.box(0.09f, 0.07f, 0.02f), Material.color(Color.hex("#1b1b25")).unlit()).position(-0.1f, 1.72f, 0.235f);
        Node eyeRight = new Node(Mesh.box(0.09f, 0.07f, 0.02f), Material.color(Color.hex("#1b1b25")).unlit()).position(0.1f, 1.72f, 0.235f);
        root.add(torso, head, eyeLeft, eyeRight);
        for (int side = -1; side <= 1; side += 2) {
            Node arm = Node.group().position(side * 0.34f, 1.38f, 0);
            arm.add(new Node(Mesh.box(0.18f, 0.18f, 0.62f), Material.color(skin)).position(0, 0, 0.3f));
            root.add(arm);
        }
        Node[] legs = new Node[2];
        for (int i = 0; i < 2; i++) {
            Node hip = Node.group().position(i == 0 ? -0.13f : 0.13f, 0.75f, 0);
            hip.add(new Node(Mesh.box(0.22f, 0.75f, 0.22f), Material.color(cloth.mix(Color.BLACK, 0.3f))).position(0, -0.375f, 0));
            legs[i] = hip;
            root.add(hip);
        }
        return new Model(root, legs);
    }

    static Color color(String name, int fallback) {
        String n = name.toLowerCase(Locale.ROOT);
        if (n.contains("pig")) return Color.hex("#f0a3ac");
        if (n.contains("cow")) return Color.hex("#6e4a32");
        if (n.contains("sheep")) return Color.hex("#ecebe6");
        if (n.contains("chicken") || n.contains("duck")) return Color.hex("#f4f1ea");
        if (n.contains("wolf") || n.contains("dog")) return Color.hex("#9a9aa0");
        if (n.contains("fox")) return Color.hex("#e07a2e");
        if (n.contains("bear")) return Color.hex("#5a3d28");
        if (n.contains("frog") || n.contains("slime") || n.contains("zombie")) return Color.hex("#5cc04a");
        if (n.contains("skeleton") || n.contains("ghost")) return Color.hex("#d8d8d0");
        if (n.contains("chest") || n.contains("crate")) return Color.hex("#a8743c");
        if (fallback >= 0) {
            return new Color(((fallback >> 16) & 255) / 255f, ((fallback >> 8) & 255) / 255f, (fallback & 255) / 255f, 1);
        }
        java.awt.Color c = Placeholder.hashColor(n, null, false);
        return new Color(c.getRed() / 255f, c.getGreen() / 255f, c.getBlue() / 255f, 1);
    }

    /** A Wavefront .obj (positions, texture coordinates, normals, polygon faces). */
    static Mesh loadObj(URL url) {
        List<float[]> positions = new ArrayList<>();
        List<float[]> uvs = new ArrayList<>();
        List<float[]> normals = new ArrayList<>();
        Mesh.Builder builder = Mesh.builder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream(), StandardCharsets.UTF_8))) {
            for (String line; (line = reader.readLine()) != null; ) {
                String[] parts = line.trim().split("\\s+");
                switch (parts[0]) {
                    case "v" -> positions.add(new float[]{f(parts[1]), f(parts[2]), f(parts[3])});
                    case "vt" -> uvs.add(new float[]{f(parts[1]), parts.length > 2 ? f(parts[2]) : 0});
                    case "vn" -> normals.add(new float[]{f(parts[1]), f(parts[2]), f(parts[3])});
                    case "f" -> {
                        int[] corners = new int[parts.length - 1];
                        for (int i = 1; i < parts.length; i++) {
                            String[] ref = parts[i].split("/");
                            float[] p = positions.get(index(ref[0], positions.size()));
                            float[] t = ref.length > 1 && !ref[1].isEmpty() ? uvs.get(index(ref[1], uvs.size())) : new float[]{0, 0};
                            float[] nrm = ref.length > 2 && !ref[2].isEmpty() ? normals.get(index(ref[2], normals.size())) : new float[]{0, 1, 0};
                            corners[i - 1] = builder.vertex(p[0], p[1], p[2], nrm[0], nrm[1], nrm[2], t[0], 1 - t[1], Color.WHITE);
                        }
                        for (int i = 1; i + 1 < corners.length; i++) {
                            builder.triangle(corners[0], corners[i], corners[i + 1]);
                        }
                    }
                    default -> {
                    }
                }
            }
        } catch (IOException | RuntimeException e) {
            throw new IllegalStateException("unweird-game: can't read the model " + url + ": " + e.getMessage(), e);
        }
        return builder.build();
    }

    private static float f(String s) {
        return Float.parseFloat(s);
    }

    private static int index(String s, int size) {
        int i = Integer.parseInt(s);
        return i < 0 ? size + i : i - 1;
    }
}
