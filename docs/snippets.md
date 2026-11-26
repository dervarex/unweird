---
title: Snippets
---

# Snippets

Copy, paste, rename. Each one goes into a `Screen` or `Component` unless it says otherwise.

## Forms

### Field with a button in the same row

```java
Field query   = new Field("Search").onSubmit(this::search);
Button search = new Button("Search").rightOf(query).onClick(this::search);
```

### Submit only when valid

```java
Field name  = new Field("Name");
Field email = new Field("Email");
Button save = new Button("Save")
        .enabledIf(() -> !name.value().isBlank() && email.value().contains("@"))
        .onClick(this::save);
```

### Inline error under a field

```java
Field email    = new Field("Email");
Text emailHint = new Text(() -> email.value().isEmpty() || email.value().contains("@") ? "" : "Not an email address")
        .color(Color.hex("#f87171"))
        .gap(4);
```

### Error line after submitting

```java
@State String error = "";
Button signIn = new Button("Sign in").onClick(this::signIn);
Text problem  = new Text(() -> error).muted();

private void signIn() {
    error = accounts.check(user.value(), pass.value()) ? "" : "Wrong username or password.";
}
```

### Clear a form whenever the screen opens

```java
@Override
protected void onShow() {
    pass.value("");
    user.focus();
}
```

### Password with "show"

```java
Field pass  = new Field("Password").masked();
Field plain = new Field("Password");
Checkbox show = new Checkbox("Show password").onChange(on -> (on ? plain : pass).value((on ? pass : plain).value()));

{
    pass.visibleIf(() -> !show.checked());
    plain.visibleIf(show::checked);
}
```

### "Remember me" and "Forgot password?" in one row

```java
Button forgot     = new Button("Forgot password?").ghost().alignRight(pass).gap(4);
Checkbox remember = new Checkbox("Remember me").leftOf(forgot).alignLeft(pass);
```

## Lists

### Add and remove

```java
@State final List<String> items = new ArrayList<>();
Field input = new Field("New item").onSubmit(this::add);
ListView<String> list = new ListView<>(() -> items).empty("Nothing yet");
Button remove = new Button("Remove").enabledIf(() -> list.selected() != null)
        .onClick(() -> items.remove(list.selected()));

private void add() {
    if (!input.value().isBlank()) {
        items.add(input.value().strip());
        input.value("");
    }
}
```

### Filter as you type

```java
Field filter = new Field("Filter");
ListView<Contact> list = new ListView<>(this::visible).labels(Contact::name);

private List<Contact> visible() {
    String q = filter.value().toLowerCase(Locale.ROOT);
    return all.stream().filter(c -> c.name().toLowerCase(Locale.ROOT).contains(q)).toList();
}
```

### Rows with buttons

```java
Each<Item> rows = new Each<>(() -> items, item -> new ItemRow(item, items::remove));

public class ItemRow extends Component {
    final Text name;
    final Button delete;

    ItemRow(Item item, Consumer<Item> onDelete) {
        name = new Text(item.name());
        delete = new Button("Delete").ghost().rightOf(name).onClick(() -> onDelete.accept(item));
    }
}
```

### Cards

```java
@Card
public class ResultCard extends Component {
    final Text title;
    final Text meta;

    ResultCard(Result r) {
        title = new Text(r.title());
        meta = new Text(r.author() + " · " + r.year()).muted();
    }
}

Each<Result> results = new Each<>(() -> found, ResultCard::new);
```

### Sorted

```java
Dropdown<String> sort = new Dropdown<>("Sort by", List.of("Name", "Newest"));
ListView<Doc> list = new ListView<>(() -> docs.stream()
        .sorted("Newest".equals(sort.selected())
                ? Comparator.comparing(Doc::created).reversed()
                : Comparator.comparing(Doc::name))
        .toList());
```

### Empty state

```java
Text empty = new Text("No results. Try a shorter search.").muted().visibleIf(() -> results.isEmpty());
```

## Loading data

### Load once when the screen opens

```java
@State List<Project> projects = List.of();
@State String status = "Loading…";
Text info = new Text(() -> status).muted();
Each<Project> rows = new Each<>(() -> projects, ProjectRow::new);

{
    async(api::projects)
            .then(p -> { projects = p; status = ""; })
            .failed(e -> status = "Couldn't load: " + e.getMessage());
}
```

### Reload button with a busy state

```java
@State boolean busy;
Button reload = new Button(() -> busy ? "Loading…" : "Reload").enabledIf(() -> !busy).onClick(this::reload);

private void reload() {
    busy = true;
    async(api::fetch).then(d -> { data = d; busy = false; }).failed(e -> busy = false);
}
```

### Ignore stale answers

```java
private int latest;

private void search() {
    int mine = ++latest;
    async(() -> api.search(query.value())).then(r -> { if (mine == latest) results = r; });
}
```

### Poll every few seconds while the screen is open

```java
private Runnable stop = () -> { };

{
    whileShown(this::poll, () -> stop.run());
}

private void poll() {
    async(api::status).then(s -> status = s);
    stop = Unweird.after(Duration.ofSeconds(5), this::poll);
}
```

### Report from a worker thread

```java
executor.submit(() -> {
    for (int i = 0; i < files.size(); i++) {
        process(files.get(i));
        progress = (i + 1) * 100 / files.size();
        Unweird.refresh();
    }
    Unweird.toast(Toast.success("Done"));
});
```

## Navigation

### Sidebar

```java
@Default(Side.LEFT)
public class Sidebar extends Component {
    Text brand      = new Text("My App");
    Button home     = new Button("Home").onClick(() -> go(HomeScreen.class));
    Button settings = new Button("Settings").onClick(() -> go(SettingsScreen.class));
}
```

### Header with the user and a logout button

```java
@Default(Side.TOP)
public class Header extends Component {
    @State @Getter @Setter private String user = "";
    Text greeting = new Text(() -> "Hi, " + user);
    Button logout = new Button("Log out").onClick(() -> go(LoginScreen.class));
}
```

### No sidebar on the login screen

```java
@Without({Sidebar.class, Header.class})
public class LoginScreen extends Screen { ... }
```

### Back button

```java
Button back = new Button("← Back").ghost().onClick(() -> go(ListScreen.class));

@Shortcut("Escape")
void back() { go(ListScreen.class); }
```

### Start fresh every visit

```java
@Fresh
public class NewDocumentScreen extends Screen { ... }
```

## Dialogs and toasts

### Confirm before deleting

```java
show(Dialog.confirm("Delete “" + doc.name() + "”?", "This can't be undone.")
        .action("Delete", () -> docs.remove(doc)).danger()
        .cancel("Keep it"));
```

### Unsaved changes

```java
show(new Dialog("Unsaved changes", "Save before leaving?")
        .action("Save", () -> { save(); go(HomeScreen.class); })
        .action("Discard", () -> go(HomeScreen.class)).danger()
        .cancel("Stay"));
```

### Ask for a name

```java
class NameDialog extends Dialog {
    Field name = new Field("Name");

    NameDialog(String title, Consumer<String> then) {
        super(title);
        action("OK", () -> {
            if (name.value().isBlank()) stay();
            else then.accept(name.value().strip());
        });
        cancel("Cancel");
    }
}

show(new NameDialog("New folder", folders::create));
```

### Toasts

```java
toast("Saved");
toast(Toast.success("Uploaded 3 files"));
toast(Toast.error("Couldn't connect"));
toast(Toast.info("Back online").lasting(Duration.ofSeconds(8)));
Unweird.toast("From any thread");
```

## Settings

### Dark mode toggle

```java
Toggle dark = new Toggle("Dark mode")
        .checked(Unweird.theme().isDark())
        .onChange(on -> Unweird.theme((on ? Theme.dark() : Theme.light()).withAccent(Unweird.theme().getAccent())));
```

### Accent color picker

```java
Dropdown<String> accent = new Dropdown<>("Accent", List.of("#7c6cff", "#14b8a6", "#f59e0b", "#f43f5e"))
        .onChange(hex -> Unweird.theme(Unweird.theme().withAccent(Color.hex(hex))));
```

### Dependent options

```java
Toggle notify  = new Toggle("Notifications");
Checkbox sound = new Checkbox("With sound").enabledIf(() -> notify.checked());
```

### Slider with units

```java
Slider size = new Slider("Font size", 10, 24).step(1).value(14).format(v -> Math.round(v) + " pt");
```

### Save settings to a file

```java
private static final Path FILE = Path.of(System.getProperty("user.home"), ".my-app.properties");
private final Properties props = new Properties();

Toggle dark = new Toggle("Dark mode").onChange(on -> { props.setProperty("dark", "" + on); store(); });

{
    try (var in = Files.newInputStream(FILE)) { props.load(in); } catch (IOException ignored) { }
    dark.checked(Boolean.parseBoolean(props.getProperty("dark", "true")));
}

private void store() {
    try (var out = Files.newOutputStream(FILE)) { props.store(out, null); } catch (IOException e) { toast(Toast.error(e.getMessage())); }
}
```

## Keyboard

```java
@Shortcut("Ctrl+S")        void save()   { ... }
@Shortcut("Ctrl+Shift+S")  void saveAs() { ... }
@Shortcut({"F5", "Ctrl+R"}) void reload() { ... }
@Shortcut("Escape")        void close()  { go(HomeScreen.class); }
@Shortcut("Ctrl+F")        void find()   { search.focus(); }
@Shortcut("Delete")        void remove() { items.remove(list.selected()); }   // not while a field has focus

Unweird.shortcut("Ctrl+Q", Unweird::quit);    // app-wide, in main()
```

## Files and links

```java
Button docs   = new Button("Documentation").ghost().onClick(() -> Unweird.open("https://example.com/docs"));
Button folder = new Button("Open folder").onClick(() -> Unweird.open(Path.of("saves")));
Button mail   = new Button("Contact").onClick(() -> Unweird.open("mailto:hi@example.com"));
```

### Accept dropped files

```java
@Override
protected boolean onDrop(Pointer at, List<Path> files) {
    files.stream().filter(f -> f.toString().endsWith(".png")).forEach(images::add);
    return true;
}
```

## Images

```java
Image logo   = new Image("/logo.png").size(64, 64);
Image avatar = Image.url("https://example.com/me.png").size(40, 40).radius(20);
Image cover  = Image.file(Path.of("cover.jpg")).radius(8);
```

## Custom widgets

### Minimal

```java
public class Dot extends Widget {
    @Override protected Size measure(Canvas c, float maxWidth) { return new Size(12, 12); }
    @Override protected void draw(Canvas c) { c.circle(getX() + 6, getY() + 6, 6, c.getTheme().getAccent()); }
}
```

### Progress bar

```java
public class ProgressBar extends Widget {
    private final DoubleSupplier progress;   // 0..1

    public ProgressBar(DoubleSupplier progress) { this.progress = progress; }

    @Override protected Size measure(Canvas c, float maxWidth) { return new Size(maxWidth, 8); }

    @Override
    protected void draw(Canvas c) {
        float p = (float) Math.clamp(progress.getAsDouble(), 0, 1);
        c.fillRect(getX(), getY(), getWidth(), getHeight(), 4, c.getTheme().getBorder());
        c.fillRect(getX(), getY(), getWidth() * p, getHeight(), 4, c.getTheme().getAccent());
    }
}

ProgressBar bar = new ProgressBar(() -> done / (double) total);
```

### Spinner

```java
public class Spinner extends Widget {
    @Override protected Size measure(Canvas c, float maxWidth) { return new Size(24, 24); }

    @Override
    protected void draw(Canvas c) {
        double t = c.time();
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4 + t * 4;
            float alpha = (float) ((i + t * 8) % 8) / 8;
            c.circle(getX() + 12 + (float) Math.cos(a) * 8, getY() + 12 + (float) Math.sin(a) * 8, 2.5f,
                    c.getTheme().getText().withAlpha(alpha));
        }
        requestFrame();
    }
}
```

### Clickable

```java
@Override protected boolean clickable() { return true; }
@Override protected void click() { onClick.run(); }
```

### Draggable value

```java
@Override protected boolean clickable() { return true; }
@Override protected Cursor cursor() { return Cursor.RESIZE_HORIZONTAL; }
@Override protected void onDrag(Pointer p) { value = Math.clamp(value + p.dx() / getWidth(), 0, 1); }
```

## 3D

### Spinning box

```java
View3D view = new View3D(new Scene().add(new Node(Mesh.box(1, 1, 1), Material.color(Color.hex("#7c6cff")))))
        .camera(Camera.orbit(0, 0, 0, 4))
        .autoRotate(30)
        .height(300);
```

### Textured, pixel art

```java
Material skin = Material.texture(Texture.resource("/skin.png").nearest()).cutout();
```

### Click to select

```java
view.onClick((hit, button) -> selected = hit == null ? null : hit.node());
```
