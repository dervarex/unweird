package com.dervarex.example.screens;

import com.dervarex.example.modrinth.Modrinth;
import com.dervarex.example.modrinth.ModrinthSource;
import com.dervarex.example.modrinth.Target;
import com.dervarex.unweird.Screen;
import com.dervarex.unweird.Text;
import com.dervarex.unweird.annotation.Title;
import com.dervarex.unweird.install.ContentBrowser;
import com.dervarex.unweird.install.Installer;

import java.nio.file.Path;

/**
 * Browse Modrinth and install mods, resource packs, shaders, data packs and modpacks.
 * The whole UI is unweird-install's ContentBrowser; the only Modrinth-specific code is ModrinthSource.
 */
@Title("Mods")
public class ModsScreen extends Screen {

    static final Target TARGET = new Target(Path.of("run", "minecraft"), "1.21.1", "fabric");

    final Installer installer = new Installer(new ModrinthSource(Modrinth.connect(), TARGET), TARGET.gameDirectory());

    Text heading  = new Text("Mods & more");
    Text subtitle = new Text("Minecraft " + TARGET.gameVersion() + " with Fabric, installs into example/run/minecraft");
    ContentBrowser browser = new ContentBrowser(installer);
}
