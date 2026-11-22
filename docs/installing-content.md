# Installing content (`unweird-install`)

For apps that let people browse a catalog and install things into a folder: mods, resource packs, plugins, assets.
It doesn't know any catalog. You plug one in.

```kotlin
implementation("com.github.dervarex.unweird:unweird-install:v0.1.0")
```

## Plug in a source

Three methods:

```java
public class MySource implements Source {

    @Override
    public List<Kind> kinds() {
        return List.of(new Kind("mods", "Mods"), new Kind("packs", "Resource packs"));
    }

    @Override
    public Results search(String query, Kind kind, int offset) {
        // call your API (this runs in the background, blocking is fine)
        List<Item> items = ...;   // new Item(id, kind, title, description, author, iconUrl, "1.2M downloads")
        return new Results(items, total);
    }

    @Override
    public Release release(Item item) {
        // what installing it means right now
        return Release.version("2.1.0")
                .download(url, "mods/" + fileName, sha1, size)
                .requires(Item.of("fabric-api", MODS))
                .build();
    }
}
```

A `Release` can also unpack archives (`.unpack(zipPath, "overrides/", "")`), keep extra info
(`.info("minecraft", "1.21.1")`) and clean up temp files (`.temporary(path)`). `Downloads.temporary(url, sha1, agent)`
helps when you have to download something first (like a modpack index).

Throw an exception with a readable message when something's wrong ("No version for 1.21.1"); the user sees it.

## Install things

```java
Installer installer = new Installer(new MySource(), gameDirectory);

installer.install(item);      // background, returns a CompletableFuture
installer.uninstall(item);
installer.status(item);       // NOT_INSTALLED, PREPARING, INSTALLING (with progress), REMOVING, INSTALLED, FAILED
installer.installed();        // what's there now
```

The important part: **status is never stored in the UI.** It's worked out every time from the running job, the last
error, and a manifest (`.unweird-installed.json`) checked against the files on disk. Delete a mod by hand and it
says "Install" again. Restart the app and it still knows.

Also:

- Downloads are checked against their SHA-1 and written via `.part` files. If anything fails, nothing stays behind.
- Dependencies get installed too. Removing something also removes dependencies nobody else needs.
- Updating removes the old version's files.
- Paths outside the folder (`../`, zip tricks) are refused.

## The ready-made UI

```java
public class ModsScreen extends Screen {
    Text heading = new Text("Mods");
    ContentBrowser browser = new ContentBrowser(installer);
}
```

Search field, kind dropdown, result cards with icons, Install/Remove buttons with live progress, toasts when done.
`ItemCard` is the single card if you want to build your own list.

## Example

`example/src/main/java/com/dervarex/example/modrinth/` is a complete Modrinth source in about 200 lines:
mods, resource packs, shaders, data packs and `.mrpack` modpacks.

Next: [3D](3d.md)
