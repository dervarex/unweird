package com.dervarex.examples.cube;

import com.dervarex.unweird.Screen;
import com.dervarex.unweird.Text;
import com.dervarex.unweird.annotation.State;
import com.dervarex.unweird.annotation.Title;
import com.dervarex.unweird.scene3d.Camera;
import com.dervarex.unweird.scene3d.Material;
import com.dervarex.unweird.scene3d.Mesh;
import com.dervarex.unweird.scene3d.Node;
import com.dervarex.unweird.scene3d.Scene;
import com.dervarex.unweird.scene3d.View3D;
import com.dervarex.unweird.style.Color;

@Title("3D")
public class CubeScreen extends Screen {

    private static final Color[] COLORS = {
            Color.hex("#7c6cff"), Color.hex("#14b8a6"), Color.hex("#f59e0b"), Color.hex("#f43f5e")};

    @State int clicks;

    private final Node cube = new Node(Mesh.box(1.4f, 1.4f, 1.4f), Material.color(COLORS[0]));
    private final Node floor = new Node(Mesh.plane(5, 5), Material.color(Color.hex("#2a2a33")))
            .position(0, -1.3f, 0);

    Text heading  = new Text("A cube");
    Text subtitle = new Text(() -> "Drag to turn, right-drag to move, scroll to zoom. Click the cube: " + clicks);

    View3D view = new View3D(new Scene().add(cube, floor).sun(-0.5f, -1, -0.3f))
            .camera(Camera.orbit(0, 0, 0, 6).angles(35, 25))
            .height(400)
            .autoRotate(15)
            .onFrame(seconds -> cube.position(0, (float) Math.sin(seconds * 2) * 0.15f, 0))
            .onClick((hit, button) -> {
                if (hit != null && hit.node() == cube) {
                    clicks++;
                    cube.material(Material.color(COLORS[clicks % COLORS.length]));
                }
            });
}
