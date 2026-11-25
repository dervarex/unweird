package com.dervarex.example.android;

import com.dervarex.unweird.Pixels;
import com.dervarex.unweird.Screen;
import com.dervarex.unweird.Text;
import com.dervarex.unweird.annotation.Shortcut;
import com.dervarex.unweird.annotation.State;
import com.dervarex.unweird.annotation.Title;
import com.dervarex.unweird.scene3d.Camera;
import com.dervarex.unweird.scene3d.Material;
import com.dervarex.unweird.scene3d.Mesh;
import com.dervarex.unweird.scene3d.Node;
import com.dervarex.unweird.scene3d.Scene;
import com.dervarex.unweird.scene3d.Texture;
import com.dervarex.unweird.scene3d.View3D;
import com.dervarex.unweird.style.Color;

/** unweird-3d on a phone: drag to turn, tap a cube to paint it. */
@Title("3D")
public class CubesScreen extends Screen {

    static final Color[] PAINT = {Color.hex("#5b5bd6"), Color.hex("#0d9488"), Color.hex("#ea580c"), Color.hex("#db2777")};

    @State int taps;

    Text heading = new Text("3D");
    Text hint = new Text(() -> taps == 0 ? "Drag to turn. Tap a cube to paint it." : "Painted " + taps + (taps == 1 ? " cube." : " cubes."));

    View3D view = new View3D(scene())
            .camera(Camera.orbit(0, 0.5f, 0, 7).angles(35, 25))
            .height(360)
            .autoRotate(12)
            .onClick((hit, button) -> {
                if (hit != null && hit.node().mesh() != null && hit.node().name() != null) {
                    hit.node().material(Material.color(PAINT[++taps % PAINT.length]));
                }
            });

    private static Scene scene() {
        Scene scene = new Scene().background(Color.hex("#dfe7f2"));
        scene.add(new Node(Mesh.plane(8, 8), Material.texture(checkerboard()).doubleSided()));
        for (int i = 0; i < 4; i++) {
            float x = (i % 2) * 2.2f - 1.1f;
            float z = (i / 2) * 2.2f - 1.1f;
            scene.add(new Node(Mesh.box(1.4f, 1.4f, 1.4f), Material.color(PAINT[i]))
                    .position(x, 0.7f, z).rotation(0, i * 20, 0).name("cube " + i));
        }
        return scene;
    }

    /** A texture made from pixels in code: the same on desktop and Android. */
    private static Texture checkerboard() {
        int size = 8;
        int[] argb = new int[size * size];
        for (int i = 0; i < argb.length; i++) {
            argb[i] = ((i % size) + (i / size)) % 2 == 0 ? 0xffc8d3e3 : 0xffaebcd1;
        }
        return Texture.of(Pixels.fromArgb(size, size, argb)).nearest();
    }

    @Shortcut("Escape")
    void back() {
        go(TasksScreen.class);
    }
}
