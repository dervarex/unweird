package com.dervarex.example.screens;

import com.dervarex.example.world.VoxelWorld;
import com.dervarex.example.world.VoxelWorld.Block;
import com.dervarex.unweird.Button;
import com.dervarex.unweird.Dropdown;
import com.dervarex.unweird.Pointer;
import com.dervarex.unweird.Screen;
import com.dervarex.unweird.Text;
import com.dervarex.unweird.annotation.Shortcut;
import com.dervarex.unweird.annotation.Title;
import com.dervarex.unweird.scene3d.Camera;
import com.dervarex.unweird.scene3d.Hit;
import com.dervarex.unweird.scene3d.Material;
import com.dervarex.unweird.scene3d.Node;
import com.dervarex.unweird.scene3d.Scene;
import com.dervarex.unweird.scene3d.View3D;

import java.util.Arrays;

/** A tiny block world editor: picking does the hard part (which block, which face). */
@Title("World editor")
public class WorldScreen extends Screen {

    VoxelWorld world = VoxelWorld.sample();
    final Node terrain = new Node(world.mesh(), new Material());

    Text heading  = new Text("World editor");
    Text subtitle = new Text("Click to place, right-click to remove, drag to turn, scroll to zoom. Ctrl+R resets.");

    Dropdown<Block> block = new Dropdown<>("Block", Arrays.stream(Block.values()).filter(b -> b != Block.AIR).toList())
            .labels(Block::label)
            .select(Block.STONE);
    Text count = new Text(() -> world.count() + " blocks").muted().rightOf(block);
    Button reset = new Button("Reset").secondary().rightOf(count).onClick(this::reset);

    View3D view = new View3D(new Scene().add(terrain).sun(-0.5f, -1, -0.3f))
            .camera(Camera.orbit(8, 2, 8, 28).angles(35, 35).zoomLimits(6, 80))
            .height(440)
            .onClick(this::edit);

    private void edit(Hit hit, Pointer.Button button) {
        if (hit == null) {
            return;
        }
        boolean changed;
        if (button == Pointer.Button.RIGHT) {
            int[] b = hit.block();
            changed = world.set(b[0], b[1], b[2], Block.AIR);
        } else {
            int[] b = hit.blockInFront();
            changed = world.set(b[0], b[1], b[2], block.selected());
        }
        if (changed) {
            terrain.mesh(world.mesh());
        }
    }

    @Shortcut("Ctrl+R")
    void reset() {
        world = VoxelWorld.sample();
        terrain.mesh(world.mesh());
    }
}
