"""Writes one page per example from examples/src. Run after adding an example: python3 docs/examples/generate.py"""
import os

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(HERE, "..", "..", "examples", "src")

# id: (title, what it shows, files in tab order)
EXAMPLES = {
    "counter":    ("Counter", "`@State`, live texts, `enabledIf`, `visibleIf`, `@Shortcut`", ["CounterScreen"]),
    "todo":       ("Todo list", "`Each` with a row component, `Field.onSubmit`, `rightOf`", ["TodoScreen", "TaskRow", "Task"]),
    "signup":     ("Form validation", "live hint texts, `enabledIf` on the submit button, masked fields", ["SignUpScreen"]),
    "login":      ("Login and header", "`LoginForm`, a `@Default` header, `@Without`, passing data with `shared(...)`", ["LoginScreen", "Header", "HomeScreen"]),
    "navigation": ("Sidebar navigation", "`@Default(Side.LEFT)`, cached and `@Fresh` screens, shortcuts on a docked component", ["Sidebar", "InboxScreen", "ArchiveScreen", "SettingsScreen", "Mail"]),
    "settings":   ("Settings and themes", "`Toggle`, `Slider`, `Dropdown`, `Checkbox`, switching the theme at runtime, sections as components", ["SettingsScreen", "AppearanceSection", "NotificationsSection", "Accent"]),
    "search":     ("Async search", "`async(...)`, dropping stale answers, `@Card` results in an `Each`", ["SearchScreen", "BookCard", "Book", "Library"]),
    "contacts":   ("Master / detail", "a filtered `ListView` and a detail card that follows the selection", ["ContactsScreen", "ContactDetails", "Contact"]),
    "dialogs":    ("Dialogs and toasts", "`Dialog.confirm`, a dialog with a form and `stay()`, every kind of toast", ["DialogsScreen", "RenameDialog"]),
    "stopwatch":  ("Timers and shortcuts", "`Unweird.after(...)` as a timer, letter shortcuts, cleaning up in `onHide`", ["StopwatchScreen"]),
    "files":      ("Dropping files", "`onDrop`, building a component with `add(...)`, `Image.file`, `Unweird.open`", ["FilesScreen", "FileRow"]),
    "rating":     ("Custom widget", "a focusable widget: `measure`, `draw`, mouse and keyboard hooks", ["RatingScreen", "Stars"]),
    "chart":      ("Custom drawing", "drawing with `Canvas`, animating with `requestFrame()`", ["ChartScreen", "BarChart"]),
    "cube":       ("3D view", "`View3D` in a page, a scene, orbit camera, picking, per-frame animation", ["CubeScreen"]),
}

def page(ex, title, shows, files):
    task = "run" + ex[0].upper() + ex[1:]
    tabs = "\n".join(
        f"<<< @/../examples/src/{ex}/java/com/dervarex/examples/{ex}/{name}.java [{name}.java]"
        for name in files + ["Main"])
    return f"""---
title: {title}
---

# {title}

{shows}

<img class="shot" src="/shots/{ex}.png" alt="{title} example">

::: code-group
{tabs}
:::

<p class="run">Run it from a checkout: <code>./gradlew :examples:{task}</code></p>
"""

for ex, (title, shows, files) in EXAMPLES.items():
    on_disk = sorted(f[:-5] for f in os.listdir(os.path.join(SRC, ex, "java/com/dervarex/examples", ex)))
    missing = set(on_disk) - set(files) - {"Main"}
    assert not missing, f"{ex}: add {missing} to the tab list"
    with open(os.path.join(HERE, ex + ".md"), "w") as out:
        out.write(page(ex, title, shows, files))

cards = "\n".join(
    f'<a href="./{ex}"><img src="/shots/{ex}.png" alt="" loading="lazy"><span>{title}</span><small>{shows.replace("`", "")}</small></a>'
    for ex, (title, shows, _) in EXAMPLES.items())
with open(os.path.join(HERE, "index.md"), "w") as out:
    out.write(f"""---
title: Examples
aside: false
---

# Examples

Small, complete apps. Every file compiles as shown: they live in [`examples/`](https://github.com/dervarex/unweird/tree/main/examples) and are built with the library.

<div class="gallery">
{cards}
</div>
""")
