# Troubleshooting

### `no field order recorded for ...`

The annotation processor isn't running for that module. Use the [Gradle plugin](index.md#add-it-to-your-build),
or add `annotationProcessor("com.github.dervarex.unweird:unweird-processor:v0.2.0")`. In IntelliJ, build with
Gradle (the default), not the IDE's own compiler. Or list the widgets yourself with `add(...)`.

### "couldn't load OpenGL" on NixOS

The JDK comes from the Nix store and doesn't see the system's `libGL`. Any of:

- Use a regular JDK (one Gradle or IntelliJ downloads; it runs through `nix-ld`).
- Start with `LD_LIBRARY_PATH=/run/current-system/sw/share/nix-ld/lib` (needs `programs.nix-ld.enable`).
- A dev shell that provides libGL.

`/run/opengl-driver/lib` alone isn't enough: it lacks `libGL.so.1`.

### A text doesn't update

```java
Text hello = new Text("Hi " + name.value());        // evaluated once
Text hello = new Text(() -> "Hi " + name.value());  // evaluated after every event
```

Changed the field from another thread? Call `Unweird.refresh()`, or use `async(...)`.

### `illegal forward reference`

A field initializer used a field declared below it. Use a method reference (`new Text(this::status)`) or move
the field up.

### A screen can't be created

Screens need a constructor without parameters, and nested screen classes must be `static`. Pass data through a
shared object instead, see [Passing data](screens-and-components.md#passing-data-between-screens).

### A shortcut doesn't fire

- The focused widget uses the key itself (Space on a button, Ctrl+A in a field).
- A typo: `"Ctl+S"` is read as plain S. See [key names](state-and-events.md#key-names).
- The method takes parameters or is static.

### `2 files found with path 'META-INF/unweird/fields.idx'` (Android)

Each module records its own field order. Use the Gradle plugin, or add
`packaging { resources { merges += "META-INF/unweird/**" } }`.

### The Android app restarts when the phone rotates

Keep the `configChanges` line in the manifest, see [Android](android.md#setting-up-an-app).

### `java.awt.Desktop` doesn't work

unweird runs AWT headless. Use `Unweird.open(...)`.

### Still stuck

[Open an issue](https://github.com/dervarex/unweird/issues). unweird's error messages are meant to say what to
do; one that doesn't is a bug too.
