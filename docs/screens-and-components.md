# Screens and components

## Screens

A screen is one window's worth of content. Subclass `Screen` and declare widgets as fields:

```java
@Title("Sign in")
public class LoginScreen extends Screen {
    Text heading  = new Text("Welcome back");
    Field user    = new Field("Username");
    Field pass    = new Field("Password").masked();
    Button signIn = new Button("Sign in").onClick(this::signIn);

    private void signIn() {
        go(HomeScreen.class);
    }
}
```

- Every widget field is shown, in declaration order. Superclass fields come first.
- Non-widget fields (`String`, `List`, services) are ignored.
- `@Skip` on a widget field leaves it out.
- Screens need a constructor without parameters. Nested screen classes must be `static`.
- `@Title` sets the window title.

### Field order and lambdas

A field's initializer can only refer to fields declared **above** it (a Java rule). For anything else use a
method reference or a method call:

```java
Text status = new Text(this::status);            // fine, whatever order
Button save = new Button("Save").onClick(this::save);

private String status() { return name.value().isBlank() ? "Unnamed" : name.value(); }
```

### Choosing the children yourself

`add(...)` in a constructor or initializer block replaces the field list. Use it when the widgets depend on
constructor arguments or runtime data:

```java
public class FileRow extends Component {
    FileRow(Path file) {
        Text name = new Text(file.getFileName().toString());
        Button open = new Button("Open").onClick(() -> Unweird.open(file));
        if (isImage(file)) add(Image.file(file).size(64, 64), name, open);
        else add(name, open);
    }
}
```

## Navigation

```java
go(HomeScreen.class);                 // in screens and components
Unweird.go(HomeScreen.class);         // anywhere, any thread
```

Each screen class is created once and kept. Come back later and everything you typed is still there.

| | |
|---|---|
| default | One instance per class, reused |
| `@Fresh` | A new instance on every visit |

### Lifecycle

```java
@Override protected void onShow() { pass.value(""); }   // every time it's shown, the first time too
@Override protected void onHide() { timer.run(); }      // replaced by another screen, or the window closes
```

For things to start and stop with the screen, `whileShown` keeps the pair together:

```java
{
    whileShown(() -> bus.subscribe(listener), () -> bus.unsubscribe(listener));
}
```

Works on components too: `onShown` runs when the component appears (its screen shows, or `Each` adds it),
`onHidden` when it goes away. Registered while already showing, `onShown` runs right away.

### Passing data between screens

Screens have no constructor arguments, so data goes through something both can reach: your own state in a
static field, or a `@Default` component (below, via `shared(...)`).

```java
public final class Session {
    public static String user = "";
}

// LoginScreen
Session.user = credentials.username();
go(HomeScreen.class);

// HomeScreen: a live text reads it whenever the screen is drawn
Text hello = new Text(() -> "Hi " + Session.user);
```

## Components

A reusable group of widgets. Same rules as screens: fields are added in order.

```java
public class SearchBar extends Component {
    Field query   = new Field("Search").onSubmit(this::run);
    Button search = new Button("Go").rightOf(query).onClick(this::run);

    private void run() { ... }
}

public class LibraryScreen extends Screen {
    Text heading  = new Text("Library");
    SearchBar bar = new SearchBar();
}
```

- Components can take constructor arguments, unlike screens.
- The first text inside a component becomes a section title. Two components in a row make two sections
  (see the [settings example](/examples/settings)).
- `@Card` draws it on its own padded surface, like a search result.
- Components own their behavior. Expose parts with getters so screens can tweak them:

```java
public class SearchBar extends Component {
    @Getter private final Field query = new Field("Search");
}

bar.getQuery().focus();
```

## On every screen: `@Default`

```java
@Default(Side.LEFT)
public class Sidebar extends Component {
    Text brand    = new Text("My App");
    Button home   = new Button("Home").onClick(() -> go(HomeScreen.class));
    Button worlds = new Button("Worlds").onClick(() -> go(WorldsScreen.class));
}
```

No registration. It shows up docked on every screen.

| Side | |
|---|---|
| `LEFT`, `RIGHT` | Full window height. Buttons look like navigation items |
| `TOP`, `BOTTOM` | Span what's left. Texts go left, buttons right |

- `@Without(Sidebar.class)` on a screen hides it there (several allowed: `@Without({Sidebar.class, Header.class})`).
- There's one instance per class. Get it anywhere with `shared(Sidebar.class)`.
- Shortcuts on its methods work on every screen it's on.

A shared header that also carries the signed-in user:

```java
@Default(Side.TOP)
public class Header extends Component {
    @State @Getter @Setter private String user = "";

    Text greeting = new Text(() -> "Hi, " + user);
    Button logout = new Button("Log out").onClick(() -> go(LoginScreen.class));
}

// LoginScreen
shared(Header.class).setUser(name);
```

Full app: [Login and header](/examples/login), [Sidebar navigation](/examples/navigation).

## Ready-made: `LoginForm`

From `unweird-components`. Heading, username, password, "Remember me", optional "Forgot password?", the button
and an error line:

```java
public class LoginScreen extends Screen {
    LoginForm form = new LoginForm()
            .onSignIn(this::signIn)
            .onForgotPassword(() -> go(ResetScreen.class));   // the link only shows if you set this

    @Override protected void onShow() { form.reset(); }   // clears password and error

    private void signIn(LoginForm.Credentials c) {
        if (!accounts.check(c.username(), c.password())) form.fail("Wrong username or password.");
        else go(HomeScreen.class);
    }
}
```

- Empty fields are caught before your handler runs.
- Use a method reference for the handler: a lambda in `form`'s own initializer can't refer to `form`.
- Every part has a getter: `form.getHeading().text("Welcome to Foo")`, `form.getRemember().checked()`.
