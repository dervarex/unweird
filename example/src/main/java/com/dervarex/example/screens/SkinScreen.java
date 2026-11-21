package com.dervarex.example.screens;

import com.dervarex.example.skin.SkinModel;
import com.dervarex.unweird.Button;
import com.dervarex.unweird.Field;
import com.dervarex.unweird.Screen;
import com.dervarex.unweird.Text;
import com.dervarex.unweird.Toggle;
import com.dervarex.unweird.annotation.State;
import com.dervarex.unweird.annotation.Title;
import com.dervarex.unweird.scene3d.Camera;
import com.dervarex.unweird.scene3d.Scene;
import com.dervarex.unweird.scene3d.Texture;
import com.dervarex.unweird.scene3d.View3D;

/** A 3D skin viewer: the model is ~80 lines (SkinModel), everything else is unweird-3d. */
@Title("Skin")
public class SkinScreen extends Screen {

    final Texture skin = Texture.resource("/skins/demo.png").nearest();
    final SkinModel model = new SkinModel(skin);
    @State boolean walking;

    Text heading  = new Text("Skin viewer");
    Text subtitle = new Text("Drag to turn, right-drag to move, scroll to zoom.");

    View3D view = new View3D(new Scene().add(model.root()))
            .camera(Camera.orbit(0, 16, 0, 52).angles(-25, 10).zoomLimits(20, 120))
            .height(380)
            .autoRotate(18)
            .onFrame(t -> model.animate(t, walking));

    Toggle walk    = new Toggle("Walk").onChange(on -> walking = on);
    Toggle slim    = new Toggle("Slim arms").rightOf(walk).gap(32).onChange(model::slim);
    Toggle overlay = new Toggle("Outer layer").checked(true).rightOf(slim).gap(32).onChange(model::overlay);

    Field url   = new Field("Skin image URL (64×64 PNG)").onSubmit(this::load);
    Button load = new Button("Load").rightOf(url).onClick(this::load);
    Text problem = new Text(() -> skin.problem() == null ? "" : skin.problem()).muted();

    private void load() {
        if (!url.value().isBlank()) {
            skin.load(url.value().strip());
        }
    }
}
