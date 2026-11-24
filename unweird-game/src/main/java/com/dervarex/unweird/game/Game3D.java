package com.dervarex.unweird.game;

import com.dervarex.unweird.Canvas;
import com.dervarex.unweird.Cursor;
import com.dervarex.unweird.Keyboard;
import com.dervarex.unweird.Pointer;
import com.dervarex.unweird.Screen;
import com.dervarex.unweird.annotation.Without;
import com.dervarex.unweird.scene3d.Camera;
import com.dervarex.unweird.scene3d.Material;
import com.dervarex.unweird.scene3d.Mesh;
import com.dervarex.unweird.scene3d.Node;
import com.dervarex.unweird.scene3d.Renderer;
import com.dervarex.unweird.scene3d.Scene;
import com.dervarex.unweird.style.Color;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A 3D game in a world of blocks. Like {@link Game} it's a {@link Screen}: {@code Unweird.start(MyWorld.class)}.
 *
 * <pre>{@code
 * public class MyWorld extends Game3D {
 *     Grass grass = new Grass();
 *     Dirt dirt   = new Dirt();
 *     Stone stone = new Stone();
 *
 *     Land land = new Land();
 *     Me me     = new Me();
 *     Pig pig   = new Pig().near(me);
 *
 *     class Land extends Terrain {
 *         Hills hills     = new Hills();
 *         Surface surface = new Surface(grass, dirt, stone);
 *     }
 *
 *     class Grass extends Block {}
 *     class Dirt extends Block {}
 *     class Stone extends Block {}
 *     class Me extends Explorer {}
 *     class Pig extends Creature {}
 * }
 * }</pre>
 *
 * Fields are what's in the world: {@link Block}s (in the order the explorer gets them), one {@link Terrain}, one
 * {@link Explorer}, and any {@link Thing}s. Widgets ({@code Text}, ...) are drawn on top. Without a terrain the world
 * is empty: build it with {@link #place}.
 *
 * <p>Annotations: {@link Sky @Sky}, {@link ViewDistance @ViewDistance}, {@link Seed @Seed}, {@link Saved @Saved}.
 * Optional methods: {@code tick()} every frame, {@code started()} once the world is there.
 */
@Without
public abstract class Game3D extends Screen {

    private static final float MAX_FRAME = 1f / 20;
    private static final float TURN = 0.15f;

    private final Scene scene = new Scene();
    private final Renderer renderer = new Renderer();
    private final Batch hud = new Batch();
    private final Camera camera = Camera.firstPerson(0, 0, 0).fov(70);
    private Voxels voxels;
    private Explorer explorer;
    private final List<Thing> things = new ArrayList<>();
    private final List<Thing> spawned = new ArrayList<>();
    private Set<Long> touching = new HashSet<>();
    private int selected;
    private double selectedAt = -10;
    private Raycast.Hit target;
    private Node outline;
    private double lastTime = -1;
    private double time;
    private float pixelRatio = 1;
    private boolean started;
    private Save save;

    /** Who shows the game: this screen, or a {@link GameView}. Owns the mouse capture. */
    interface Host {
        boolean locked();

        void lock();

        void unlock();
    }

    private Host host = new Host() {
        @Override
        public boolean locked() {
            return pointerLocked();
        }

        @Override
        public void lock() {
            lockPointer();
        }

        @Override
        public void unlock() {
            unlockPointer();
        }
    };

    protected Game3D() {
        whileShown(this::shown, this::hidden);
    }

    void host(Host host) {
        this.host = host;
    }

    void shown() {
        lastTime = -1;
    }

    /** Off screen: lets go of the mouse and saves a {@link Saved @Saved} world. */
    void hidden() {
        host.unlock();
        if (save != null && voxels != null) {
            save.write(voxels, explorer);
        }
    }

    @Override
    protected boolean overlay() {
        return true;
    }

    @Override
    protected String title() {
        return Assets.words(getClass().getSimpleName());
    }

    // ---- for your game code ----

    /** The block at (x, y, z), or null for air (and places that aren't loaded yet). */
    protected final Block block(int x, int y, int z) {
        return world().block(x, y, z);
    }

    /** Puts a block at (x, y, z); null removes it. Only where the world is loaded (near the explorer). */
    protected final void place(int x, int y, int z, Block block) {
        world().set(x, y, z, block);
    }

    /** Puts a thing into the world. */
    protected final void spawn(Thing thing) {
        thing.game = this;
        spawned.add(thing);
    }

    /** The explorer, or null if the game has none. */
    protected final Explorer explorer() {
        world();
        return explorer;
    }

    // ---- starting ----

    private Voxels world() {
        if (voxels == null) {
            start();
        }
        return voxels;
    }

    private void start() {
        Members.nameSounds(this);
        List<Block> blocks = Members.valuesOf(this, Block.class);
        List<Terrain> terrains = Members.valuesOf(this, Terrain.class);
        Terrain terrain = terrains.isEmpty() ? null : terrains.getFirst();
        ViewDistance distance = getClass().getAnnotation(ViewDistance.class);
        int radius = Math.max(2, Math.min(24, distance != null ? distance.value() : 6));
        Seed seed = getClass().getAnnotation(Seed.class);
        long worldSeed = seed != null ? seed.value() : new java.util.Random().nextLong();
        if (getClass().isAnnotationPresent(Saved.class)) {
            save = new Save(getClass(), blocks);
            worldSeed = save.seed(worldSeed);
        }
        voxels = new Voxels(blocks, terrain, worldSeed, radius, scene);
        if (save != null) {
            save.applyEdits(voxels);
        }

        Color sky = skyColor();
        scene.background(sky).fog(sky, radius * Chunk.SIZE * 0.6f, radius * Chunk.SIZE * 0.95f).ambient(0.6f).sun(-0.4f, -1f, -0.3f);
        camera.viewDistance(radius * Chunk.SIZE + 32);
        outline = new Node(outlineMesh(), Material.color(new Color(0.05f, 0.05f, 0.08f, 1)).unlit());
        outline.visible(false);
        scene.add(outline);

        for (Thing thing : Members.valuesOf(this, Thing.class)) {
            add(thing);
            if (explorer == null && thing instanceof Explorer first) {
                explorer = first;
            }
        }
        if (explorer != null && save != null) {
            save.restore(explorer);
        }
    }

    private void add(Thing thing) {
        thing.game = this;
        thing.placed = false;
        thing.removed = false;
        if (!(thing instanceof Explorer)) {
            Models.Model model = Models.of(thing);
            thing.node = model.root();
            thing.node.visible(false);
            thing.legs = model.legs();
            scene.add(thing.node);
        }
        Members.nameSounds(thing);
        things.add(thing);
    }

    /** Puts a thing on its start place once that part of the world is loaded. */
    private void place(Thing thing) {
        double x = thing.startX, z = thing.startZ;
        if (thing.near != null) {
            if (!thing.near.placed) {
                return;
            }
            double angle = Math.abs((thing.hashCode() * 2654435761L) % 360) * Math.PI / 180;
            double distance = 3 + Math.abs(thing.hashCode() % 4);
            x = thing.near.body.x + Math.cos(angle) * distance;
            z = thing.near.body.z + Math.sin(angle) * distance;
        }
        double y = thing.startY;
        if (thing.onGround || Double.isNaN(y)) {
            if (!voxels.shown(Body.floor(x), Body.floor(z))) {
                return;
            }
            y = voxels.ground(Body.floor(x), Body.floor(z)) + 1;
        } else if (!voxels.loaded(Body.floor(x), Body.floor(z))) {
            return;
        }
        thing.body.x = Math.floor(x) + 0.5;
        thing.body.y = y;
        thing.body.z = Math.floor(z) + 0.5;
        thing.placed = true;
        if (thing.node != null) {
            thing.node.visible(true);
        }
    }

    // ---- every frame ----

    @Override
    protected void draw(Canvas canvas) {
        render(canvas, getX(), getY(), getWidth(), getHeight());
        super.draw(canvas);
        requestFrame();
    }

    /** One frame: moves everything, then draws the world and its HUD into the given area. */
    void render(Canvas canvas, float areaX, float areaY, float areaWidth, float areaHeight) {
        Voxels world = world();
        double now = canvas.time();
        double dt = lastTime < 0 ? 0 : Math.min(MAX_FRAME, now - lastTime);
        lastTime = now;
        time += dt;
        pixelRatio = canvas.getPixelRatio();

        double centerX = explorer != null ? explorer.body.x : 0;
        double centerZ = explorer != null ? explorer.body.z : 0;
        world.update(centerX, centerZ);
        if (dt > 0) {
            step(dt);
        }
        if (!started && (explorer == null || explorer.placed)) {
            started = true;
            Hooks.of(getClass()).call(this, "started");
        }
        aim();
        if (explorer != null) {
            double[] eye = explorer.eye();
            camera.position((float) eye[0], (float) eye[1], (float) eye[2]).angles(explorer.yaw, explorer.pitch);
        }
        canvas.gl(this, areaX, areaY, areaWidth, areaHeight, 0, (width, height) -> {
            renderer.render(scene, camera, width, height);
            drawHud(width, height);
        });
        drawLabels(canvas, areaX, areaY, areaWidth, areaHeight);
    }

    private void step(double dt) {
        if (explorer != null && explorer.placed) {
            for (int digit = 1; digit <= 9; digit++) {
                if (Keyboard.pressed(String.valueOf(digit)) && digit <= voxels.blocks.size()) {
                    select(digit - 1);
                }
            }
        }
        if (!spawned.isEmpty()) {
            List<Thing> joining = new ArrayList<>(spawned);
            spawned.clear();
            joining.forEach(this::add);
        }
        for (Thing thing : List.copyOf(things)) {
            if (thing.removed) {
                continue;
            }
            if (!thing.placed) {
                place(thing);
                continue;
            }
            if (!voxels.shown(Body.floor(thing.body.x), Body.floor(thing.body.z))) {
                continue; // its ground isn't there (yet)
            }
            thing.behave(dt);
            thing.body.move(voxels, dt, thing.falls());
            if (thing.body.y < -20) {
                thing.body.y = voxels.ground(Body.floor(thing.body.x), Body.floor(thing.body.z)) + 1; // fell out
                thing.body.vy = 0;
            }
            Hooks.of(thing.getClass()).call(thing, "tick");
        }
        touches();
        things.removeIf(thing -> {
            if (thing.removed && thing.node != null) {
                scene.remove(thing.node);
            }
            return thing.removed;
        });
        for (Thing thing : things) {
            if (thing.node != null && thing.placed) {
                thing.node.position((float) thing.body.x, (float) thing.body.y, (float) thing.body.z).rotation(0, thing.yaw, 0);
                double speed = Math.hypot(thing.body.vx, thing.body.vz);
                thing.walked += (float) (speed * dt);
                float swing = speed > 0.05 ? (float) Math.sin(thing.walked * 5) * 35 : 0;
                for (int i = 0; i < thing.legs.length; i++) {
                    thing.legs[i].rotation(i == 0 || i == 3 ? swing : -swing, 0, 0);
                }
            }
        }
        Hooks.of(getClass()).call(this, "tick");
    }

    /** Things that started touching each other since the last frame. */
    private void touches() {
        Set<Long> now = new HashSet<>();
        for (int i = 0; i < things.size(); i++) {
            Thing a = things.get(i);
            for (int j = i + 1; j < things.size(); j++) {
                Thing b = things.get(j);
                if (a.removed || b.removed || !a.placed || !b.placed || !overlaps(a.body, b.body)) {
                    continue;
                }
                long key = ((long) System.identityHashCode(a) << 32) ^ System.identityHashCode(b);
                now.add(key);
                if (!touching.contains(key)) {
                    Hooks.of(a.getClass()).call(a, "touch", b);
                    Hooks.of(b.getClass()).call(b, "touch", a);
                }
            }
        }
        touching = now;
    }

    private static boolean overlaps(Body a, Body b) {
        return Math.abs(a.x - b.x) < (a.width + b.width) / 2 && Math.abs(a.z - b.z) < (a.width + b.width) / 2
                && a.y < b.y + b.height && b.y < a.y + a.height;
    }

    /** What the crosshair points at. */
    private void aim() {
        target = null;
        if (explorer != null && explorer.placed) {
            double[] e = explorer.eye();
            target = Raycast.cast(voxels, e[0], e[1], e[2], e[3], e[4], e[5], Explorer.REACH);
        }
        outline.visible(target != null && host.locked());
        if (target != null) {
            outline.position(target.x() + 0.5f, target.y() + 0.5f, target.z() + 0.5f);
        }
    }

    /** The nearest thing under the crosshair closer than {@code limit}, or null. */
    private Thing thingAimedAt(double limit) {
        double[] e = explorer.eye();
        Thing best = null;
        for (Thing thing : things) {
            if (thing == explorer || thing.removed || !thing.placed) {
                continue;
            }
            double distance = thing.body.ray(e[0], e[1], e[2], e[3], e[4], e[5]);
            if (distance >= 0 && distance < limit) {
                limit = distance;
                best = thing;
            }
        }
        return best;
    }

    // ---- input ----

    @Override
    protected boolean clickable() {
        return true;
    }

    @Override
    protected Cursor cursor() {
        return Cursor.CROSSHAIR;
    }

    @Override
    protected void onPress(Pointer pointer) {
        press(pointer);
    }

    void press(Pointer pointer) {
        if (!host.locked()) {
            host.lock();
            return;
        }
        if (explorer == null || !explorer.placed) {
            return;
        }
        if (pointer.button() == Pointer.Button.LEFT) {
            Thing thing = thingAimedAt(target != null ? target.distance() : Explorer.REACH);
            if (thing != null) {
                Hooks.of(thing.getClass()).call(thing, "clicked");
            } else if (target != null) {
                breakBlock(target);
            }
        } else if (pointer.button() == Pointer.Button.RIGHT && target != null) {
            placeBlock(target);
        }
    }

    private void breakBlock(Raycast.Hit hit) {
        Block block = voxels.block(hit.x(), hit.y(), hit.z());
        if (block == null || block.unbreakable) {
            return;
        }
        voxels.set(hit.x(), hit.y(), hit.z(), null);
        Block above = voxels.block(hit.x(), hit.y() + 1, hit.z());
        if (above != null && above.plant) {
            voxels.set(hit.x(), hit.y() + 1, hit.z(), null); // flowers don't float
        }
        Sound.effect(block.getClass(), Assets.snake(block.getClass().getSimpleName()) + "_break", "break");
        Hooks.of(block.getClass()).call(block, "broken");
        Hooks.of(explorer.getClass()).call(explorer, "broke", block);
    }

    private void placeBlock(Raycast.Hit hit) {
        if (voxels.blocks.isEmpty()) {
            return;
        }
        Block block = voxels.blocks.get(selected);
        int x = hit.frontX(), y = hit.frontY(), z = hit.frontZ();
        Block there = voxels.block(x, y, z);
        if (there != null && !there.liquid && !there.plant) {
            return;
        }
        if (block.solid()) {
            for (Thing thing : things) {
                if (thing.placed && !thing.removed && thing.body.overlapsBlock(x, y, z)) {
                    return; // not inside someone
                }
            }
        }
        if (voxels.set(x, y, z, block)) {
            Sound.effect(block.getClass(), Assets.snake(block.getClass().getSimpleName()) + "_place", "place");
            Hooks.of(block.getClass()).call(block, "placed");
            Hooks.of(explorer.getClass()).call(explorer, "placed", block);
        }
    }

    @Override
    protected boolean onMove(Pointer pointer) {
        return move(pointer);
    }

    boolean move(Pointer pointer) {
        if (host.locked() && explorer != null) {
            explorer.yaw -= pointer.dx() * TURN;
            explorer.pitch = Math.max(-89, Math.min(89, explorer.pitch - pointer.dy() * TURN));
            return true;
        }
        return false;
    }

    @Override
    protected boolean onScroll(Pointer at, double dx, double dy) {
        return scroll(dy);
    }

    boolean scroll(double dy) {
        int count = voxels == null ? 0 : voxels.blocks.size();
        if (count == 0 || dy == 0) {
            return false;
        }
        select(Math.floorMod(selected + (dy > 0 ? -1 : 1), count));
        return true;
    }

    private void select(int index) {
        selected = index;
        selectedAt = time;
    }

    // ---- drawing on top ----

    private void drawHud(int width, int height) {
        hud.begin(width, height);
        float r = pixelRatio;
        boolean underwater = explorer != null && explorer.placed
                && voxels.liquid(Body.floor(explorer.body.x), Body.floor(explorer.body.y + Explorer.EYE), Body.floor(explorer.body.z));
        if (underwater) {
            hud.rect(0, 0, width, height, 0.1f, 0.25f, 0.6f, 0.45f);
        }
        if (host.locked()) {
            float cx = width / 2f, cy = height / 2f, len = 9 * r, thick = Math.max(1, Math.round(2 * r));
            hud.rect(cx - len - 1, cy - thick / 2 - 1, len * 2 + 2, thick + 2, 0, 0, 0, 0.35f);
            hud.rect(cx - thick / 2 - 1, cy - len - 1, thick + 2, len * 2 + 2, 0, 0, 0, 0.35f);
            hud.rect(cx - len, cy - thick / 2, len * 2, thick, 1, 1, 1, 0.9f);
            hud.rect(cx - thick / 2, cy - len, thick, len * 2, 1, 1, 1, 0.9f);
        }
        List<Block> blocks = voxels.blocks;
        if (explorer != null && !blocks.isEmpty()) {
            int shown = Math.min(10, blocks.size());
            int first = Math.max(0, Math.min(selected - shown + 1, blocks.size() - shown));
            first = Math.min(first, selected);
            float slot = 44 * r, gap = 4 * r, pad = 6 * r;
            float total = shown * slot + (shown - 1) * gap;
            float x = (width - total) / 2, y = 14 * r;
            for (int i = 0; i < shown; i++) {
                int index = first + i;
                float sx = x + i * (slot + gap);
                boolean active = index == selected;
                hud.rect(sx - 2 * r, y - 2 * r, slot + 4 * r, slot + 4 * r, active ? 1 : 0, active ? 1 : 0, active ? 1 : 0, active ? 0.9f : 0.35f);
                hud.rect(sx, y, slot, slot, 0.12f, 0.12f, 0.16f, 0.7f);
                Block block = blocks.get(index);
                hud.image(voxels.art.icons[block.id], sx + pad, y + pad, slot - pad * 2, slot - pad * 2, 1, 1, false, 1);
            }
        }
        hud.end();
    }

    private void drawLabels(Canvas canvas, float x, float y0, float width, float height) {
        float cx = x + width / 2;
        if (!host.locked()) {
            canvas.fillRect(cx - 130, y0 + height / 2 - 26, 260, 52, 12, new Color(0, 0, 0, 0.45f));
            canvas.label("Click to play", cx, y0 + height / 2, Canvas.Weight.SEMIBOLD, 18, Color.WHITE);
        } else if (time - selectedAt < 1.5 && !voxels.blocks.isEmpty()) {
            String name = voxels.blocks.get(selected).name();
            float y = y0 + height - 14 - 44 - 22;
            canvas.label(name, cx + 1, y + 1, Canvas.Weight.SEMIBOLD, 16, new Color(0, 0, 0, 0.6f));
            canvas.label(name, cx, y, Canvas.Weight.SEMIBOLD, 16, Color.WHITE);
        }
    }

    private Color skyColor() {
        Sky sky = getClass().getAnnotation(Sky.class);
        int rgb = sky != null ? sky.value() : 0x9ccfff;
        return new Color(((rgb >> 16) & 255) / 255f, ((rgb >> 8) & 255) / 255f, (rgb & 255) / 255f, 1);
    }

    /** The edges of a block, slightly bigger than it, as thin bars. */
    private static Mesh outlineMesh() {
        Mesh.Builder builder = Mesh.builder();
        float h = 0.502f, t = 0.012f;
        for (int a = -1; a <= 1; a += 2) {
            for (int b = -1; b <= 1; b += 2) {
                builder.box(0, a * h, b * h, 1.004f, t, t, null);
                builder.box(a * h, 0, b * h, t, 1.004f, t, null);
                builder.box(a * h, b * h, 0, t, t, 1.004f, null);
            }
        }
        return builder.build();
    }
}
