# Your own widgets

Extend `Widget`: say how big it wants to be, draw it, handle input. You never redraw yourself: after every event
unweird calls `draw` again.

```java
public class ColorSwatch extends Widget {
    private Color color = Color.hex("#7c6cff");

    @Override
    protected Size measure(Canvas canvas, float maxWidth) {
        return new Size(Math.min(maxWidth, 120), 40);
    }

    @Override
    protected void draw(Canvas canvas) {
        float r = canvas.getTheme().getRadius();
        canvas.fillRect(getX(), getY(), getWidth(), getHeight(), r, color);
        if (isHovered()) {
            canvas.strokeRect(getX(), getY(), getWidth(), getHeight(), r, 2, canvas.getTheme().getAccent());
        }
    }

    @Override protected boolean clickable() { return true; }

    @Override
    protected void onPress(Pointer p) {
        color = color.mix(Color.WHITE, 0.2f);
    }
}
```

Use it like any widget: `ColorSwatch swatch = new ColorSwatch();`.

## Size and position

| | |
|---|---|
| `measure(canvas, maxWidth)` | Return the size you want. Take `maxWidth` for "full width" |
| `getX()`, `getY()`, `getWidth()`, `getHeight()` | Where you ended up, in window coordinates |
| `isHovered()`, `isPressed()`, `isFocused()`, `isFocusVisible()` | State (focus visible = reached with the keyboard) |

## Drawing (`Canvas`)

Everything is in logical pixels; HiDPI is handled.

| | |
|---|---|
| `fillRect(x, y, w, h, radius, color)` | Filled rounded rectangle |
| `strokeRect(x, y, w, h, radius, width, color)` | Outline |
| `shadow(x, y, w, h, radius, blur, color)` | Soft shadow |
| `circle(cx, cy, r, color)` | Filled circle |
| `polyline(float[] xy, width, color)` | Connected line segments: `{x0, y0, x1, y1, ...}` |
| `text(s, x, y, width, weight, size, color, align)` | Wrapped text (optional `maxLines`) |
| `textLine(s, x, centerY, weight, size, color)` | One line, vertically centered |
| `label(s, centerX, centerY, weight, size, color)` | One line, centered both ways |
| `measureText(s, weight, size, maxWidth)` | Size of wrapped text |
| `textWidth(s, weight, size)` / `lineHeight(size)` | |
| `clip(x, y, w, h)` / `unclip()` | Restrict drawing |
| `dimmed(Runnable)` | Draw half transparent |
| `time()` | Seconds since start, for animation |
| `getTheme()` | The current [theme](styling.md) colors |

`Canvas.Weight` is `REGULAR` or `SEMIBOLD`, `Canvas.Align` is `LEFT`, `CENTER` or `RIGHT`.

### A line chart

```java
@Override
protected void draw(Canvas canvas) {
    double max = values.stream().mapToDouble(d -> d).max().orElse(1);
    float[] points = new float[values.size() * 2];
    for (int i = 0; i < values.size(); i++) {
        points[i * 2] = getX() + i * getWidth() / (values.size() - 1);
        points[i * 2 + 1] = getY() + getHeight() - (float) (values.get(i) / max) * getHeight();
    }
    canvas.polyline(points, 2, canvas.getTheme().getAccent());
}
```

## Input

| Override | Called when |
|---|---|
| `clickable()` / `focusable()` | Return true to get mouse / keyboard input |
| `cursor()` | Which cursor to show: `ARROW`, `HAND`, `TEXT`, `CROSSHAIR`, `MOVE`, `RESIZE_HORIZONTAL`, `RESIZE_VERTICAL`, `NOT_ALLOWED` |
| `click()` | A left click (press and release on the widget), or activated with the keyboard |
| `onPress` / `onDrag` / `onRelease` | Any mouse button. Drags keep coming outside the widget, with `dx`/`dy` |
| `onMove` | The mouse moves over it. Return true if that changes the drawing |
| `onScroll(at, dx, dy)` | Wheel. Return true to take it, else the page scrolls |
| `onKey(KeyPress)` | A key while focused. Return true if you used it, otherwise shortcuts get it |
| `onType(String)` | Typed text while focused |
| `onDrop(at, files)` | Files dropped on it |

`Pointer` has `x`, `y` (window coordinates), `button` (`LEFT`, `RIGHT`, `MIDDLE`), `clicks` (2 = double click),
`shift`, `ctrl`, `alt`, and `dx`, `dy` for drags. `KeyPress` has `is("Ctrl+S")`, `name()`, `shift()`, `ctrl()`,
`alt()`, `meta()`.

### Keyboard

```java
@Override protected boolean focusable() { return true; }

@Override
protected boolean onKey(KeyPress key) {
    if (key.is("Left")) value--;
    else if (key.is("Right")) value++;
    else return false;   // not ours: let shortcuts have it
    return true;
}
```

### Dragging

```java
@Override protected boolean clickable() { return true; }
@Override protected Cursor cursor() { return Cursor.MOVE; }

@Override
protected void onDrag(Pointer p) {
    offsetX += p.dx();
    offsetY += p.dy();
}
```

Full examples: [Custom widget](/examples/rating) (mouse and keyboard), [Custom drawing](/examples/chart)
(animation). `Slider` in core is written with only these hooks.

## Animation

```java
@Override
protected void draw(Canvas canvas) {
    float pulse = (float) (0.5 + 0.5 * Math.sin(canvas.time() * 4));
    canvas.circle(getX() + 10, getY() + 10, 4 + pulse * 3, canvas.getTheme().getAccent());
    requestFrame();   // draw again next frame
}
```

As long as someone asks for frames, the window renders continuously. When nobody does, it sleeps. Stop asking
when the animation is done:

```java
double t = Math.min(1, (canvas.time() - startedAt) / 0.3);
if (t < 1) requestFrame();
```

## Chaining

Layout calls on `Widget` return `Widget`. Override the ones you chain to keep your type:

```java
@Override public ColorSwatch below(Widget other) { super.below(other); return this; }
@Override public ColorSwatch rightOf(Widget other) { super.rightOf(other); return this; }
```

Only worth it for widgets you chain a lot. Your own setters (`value(...)`, `onChange(...)`) should return `this`
with your type.

## Raw OpenGL

```java
Gl gl = canvas.openGl();
canvas.gl(this, getX(), getY(), getWidth(), getHeight(), radius, (pixelWidth, pixelHeight) -> {
    // a 4x multisampled framebuffer with depth, already cleared
    gl.clearColor(0.1f, 0.1f, 0.2f, 1);
    gl.clear(Gl.COLOR_BUFFER_BIT);
});
```

The result is placed like an image: it clips, scrolls and gets rounded corners. Change any GL state you like,
it's restored afterwards. `unweird-3d` is built on this.

`Gl` is the part of OpenGL that desktop (3.3) and Android (ES 3.0) share: shaders, buffers, vertex arrays,
textures, framebuffers. Start shader sources with `gl.shaderVersion()` (`#version 330 core` on desktop,
`#version 300 es` with default precisions on Android). LWJGL's `GL33C` works too, but desktop only.

On Android the GL context can be lost while the app is in the background. When `canvas.openGl()` returns a
different `Gl` than last frame, create your programs, buffers and textures again.
