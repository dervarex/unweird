# Your own widgets

Extend `Widget`, say how big it wants to be, draw it, handle input.

```java
public class ColorSwatch extends Widget {
    private Color color = Color.hex("#5b5bd6");

    @Override
    protected Size measure(Canvas canvas, float maxWidth) {
        return new Size(Math.min(maxWidth, 120), 40);
    }

    @Override
    protected void draw(Canvas canvas) {
        canvas.fillRect(getX(), getY(), getWidth(), getHeight(), canvas.getTheme().getRadius(), color);
        if (isHovered()) {
            canvas.strokeRect(getX(), getY(), getWidth(), getHeight(), canvas.getTheme().getRadius(), 2,
                    canvas.getTheme().getAccent());
        }
    }

    @Override protected boolean clickable() { return true; }

    @Override
    protected void onPress(Pointer p) {
        color = color.mix(Color.WHITE, 0.2f);
    }
}
```

You don't redraw yourself. After every event unweird calls `draw` again.

## Drawing (`Canvas`)

`fillRect`, `strokeRect`, `shadow`, `circle`, `polyline`, `text` (wrapped), `textLine`, `label` (centered),
`measureText`, `textWidth`, `clip`/`unclip`, `dimmed`. Everything in logical pixels; HiDPI is handled.
`canvas.getTheme()` has the colors.

## Input hooks

| Override | Called when |
|---|---|
| `clickable()` / `focusable()` | return true to get mouse / keyboard input |
| `cursor()` | which mouse cursor to show (`Cursor.HAND`, `TEXT`, `MOVE`, ...) |
| `onPress` / `onDrag` / `onRelease` | any mouse button; drags keep coming outside the widget, with `dx`/`dy` |
| `onMove` | mouse moves over it; return true if that changes the drawing |
| `onScroll` | wheel; return true to take it, else the page scrolls |
| `onKey(KeyPress)` | while focused; `key.is("Ctrl+S")`, `key.is("Left")` |
| `onType(String)` | typed text while focused |
| `onDrop` | files dropped on it |

`click()` runs on a left click (press and release on the widget).

`Slider` in core is written only with these hooks, read it if you want a full example.

## Animation

```java
@Override
protected void draw(Canvas canvas) {
    float t = (float) canvas.time();
    ...
    requestFrame();   // draw again next frame
}
```

As long as someone asks for frames, the window renders continuously. When nobody does, it sleeps.

## Raw OpenGL

```java
Gl gl = canvas.openGl();
canvas.gl(this, getX(), getY(), getWidth(), getHeight(), radius, (pixelWidth, pixelHeight) -> {
    // the target is a 4x multisampled framebuffer with depth, already cleared
    gl.clearColor(0.1f, 0.1f, 0.2f, 1);
    gl.clear(Gl.COLOR_BUFFER_BIT);
});
```

The result is placed like an image, so it clips and scrolls with the page. Mess up GL state as you like, it's
restored afterwards. This is what `unweird-3d` is built on.

`Gl` is the part of OpenGL that desktop (3.3) and Android (ES 3.0) share: shaders, buffers, vertex arrays,
textures, framebuffers. Code written against it runs on both. Start shader sources with `gl.shaderVersion()`, which
is `#version 330 core` on desktop and `#version 300 es` (with default precisions) on Android. On desktop you can also
call LWJGL's `GL33C` directly, but then it's desktop only.

On Android the GL context can be lost while the app is in the background. When `canvas.openGl()` returns a different
`Gl` than last frame, create your programs, buffers and textures again.

## Chaining

Override the relation methods to return your type, otherwise `new ColorSwatch().below(x)` returns a `Widget`:

```java
@Override public ColorSwatch below(Widget other) { super.below(other); return this; }
```

Only worth doing for widgets you chain a lot.

Next: [Installing content](installing-content.md)
