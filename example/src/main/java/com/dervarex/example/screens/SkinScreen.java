package com.dervarex.example.screens;

import com.dervarex.example.skin.SkinLoader;
import com.dervarex.example.skin.SkinModel;
import com.dervarex.unweird.Button;
import com.dervarex.unweird.Field;
import com.dervarex.unweird.Pointer;
import com.dervarex.unweird.Screen;
import com.dervarex.unweird.Slider;
import com.dervarex.unweird.Text;
import com.dervarex.unweird.Toggle;
import com.dervarex.unweird.annotation.State;
import com.dervarex.unweird.annotation.Title;
import com.dervarex.unweird.scene3d.Camera;
import com.dervarex.unweird.scene3d.Scene;
import com.dervarex.unweird.scene3d.Texture;
import com.dervarex.unweird.scene3d.View3D;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/** A 3D skin viewer: the model is ~80 lines (SkinModel), loading ~150 (SkinLoader), everything else is unweird. */
@Title("Skin")
public class SkinScreen extends Screen {

    private static final float SPIN_SPEED = 18;

    final Texture skin = Texture.resource("/skins/demo.png").nearest();
    final SkinModel model = new SkinModel(skin);
    @State boolean walking;
    @State boolean loading;
    @State String status = "";

    Text heading  = new Text("Skin viewer");
    Text subtitle = new Text("Drag to turn, right-drag to move, scroll to zoom. Drop a skin PNG anywhere on the window.");

    View3D view = new View3D(new Scene().add(model.root()))
            .camera(Camera.orbit(0, 16, 0, 52).angles(-25, 10).zoomLimits(20, 120))
            .height(360)
            .autoRotate(SPIN_SPEED)
            .onFrame(this::frame);

    Toggle walk    = new Toggle("Walk").onChange(on -> walking = on);
    Toggle slim    = new Toggle("Slim arms").rightOf(walk).gap(32).onChange(model::slim);
    Toggle overlay = new Toggle("Outer layer").checked(true).rightOf(slim).gap(32).onChange(model::overlay);
    Toggle spin    = new Toggle("Spin").checked(true).rightOf(overlay).gap(32).onChange(on -> view.autoRotate(on ? SPIN_SPEED : 0));

    Slider turn = new Slider("Turn", 0, 360).step(1).format(v -> Math.round(v) + "°").onChange(this::turnTo);

    Field input = new Field("Your Minecraft username, a skin URL or a PNG file").onSubmit(this::load);
    Button load = new Button(() -> loading ? "Loading…" : "Load").rightOf(input).enabledIf(() -> !loading).onClick(this::load);
    Text result = new Text(() -> status).muted();

    /** Runs every frame: walking, and the slider follows the camera while it spins or is dragged. */
    private void frame(double seconds) {
        model.animate(seconds, walking);
        float yaw = ((view.camera().yaw() % 360) + 360) % 360;
        turn.value(yaw);
    }

    private void turnTo(double degrees) {
        spin.checked(false);                 // turning by hand stops the spin
        view.autoRotate(0);
        view.camera().angles((float) degrees, view.camera().pitch());
    }

    private void load() {
        String what = input.value();
        loading = true;
        status = "";
        async(() -> SkinLoader.load(what))
                .then(this::show)
                .failed(error -> {
                    loading = false;
                    status = error.getMessage();
                });
    }

    @Override
    protected boolean onDrop(Pointer at, List<Path> files) {
        Path png = files.stream().filter(f -> f.toString().toLowerCase(Locale.ROOT).endsWith(".png")).findFirst().orElse(null);
        if (png == null) {
            status = "Drop a .png skin file.";
            return true;
        }
        input.value(png.toString());
        load();
        return true;
    }

    private void show(SkinLoader.Skin loaded) {
        loading = false;
        skin.set(loaded.image());
        if (loaded.slim() != null) {
            slim.checked(loaded.slim());
            model.slim(loaded.slim());
        }
        status = loaded.description();
    }
}
