# Screens and components

## Screens

A screen is one window's worth of content. Subclass `Screen`, declare widgets as fields, done:

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

- Every widget field is shown, in the order you declared it. Fields from a superclass come first.
- `@Skip` on a field leaves it out.
- If you call `add(a, b, c)` yourself (in a constructor or `{ }` block), only those are shown, in that order.
- Screens need a constructor without parameters.

One Java thing to know: a field's lambda can only use fields declared **above** it. Use a method reference
(`new Text(this::greeting)`) or `this.field` to get around that.

## Navigation

```java
go(HomeScreen.class);
```

Works in screens and components. From somewhere else use `Unweird.go(HomeScreen.class)`, also from other threads.

Each screen class is created once and kept. Go back to it later and everything you typed is still there.
Want a clean screen every time? Put `@Fresh` on it.

Hooks:

```java
@Override protected void onShow() { pass.value(""); }   // every time it's shown, also the first
@Override protected void onHide() { ... }               // when another screen replaces it, or the window closes
```

## Components

A component is a reusable group of widgets. Same rules as screens: fields get added in order.

```java
public class SearchBar extends Component {
    Field query   = new Field("Search");
    Button search = new Button("Go").rightOf(query).onClick(this::run);

    private void run() { ... }
}
```

Use it like any widget: `SearchBar bar = new SearchBar();`.

Components own their behavior. Give them getters (Lombok `@Getter` is fine) so screens can read or tweak parts:

```java
@Getter private final Button search = ...;
```

`@Card` on a component draws it on its own padded surface, like a result in a list.

## Things on every screen: `@Default`

```java
@Default(Side.LEFT)
public class Sidebar extends Component {
    Text brand    = new Text("My App");
    Button home   = new Button("Home").onClick(() -> go(HomeScreen.class));
    Button worlds = new Button("Worlds").onClick(() -> go(WorldsScreen.class));
}
```

That's it, no registering. It shows up docked on every screen. Sides: `LEFT`, `RIGHT`, `TOP`, `BOTTOM`.

- Left/right take the full height, top/bottom span the rest.
- In a sidebar, buttons look like navigation items. In a header, texts go left and buttons right.
- Hide them on a screen with `@Without(Sidebar.class)` (several allowed), or all of them with just `@Without`.
- There's only one instance of each. Get it anywhere with `shared(Sidebar.class)`.

That last one is also how screens share data:

```java
@Default(Side.TOP)
public class Header extends Component {
    @Getter @Setter private String user = "";
    Text greeting = new Text(() -> "Hi, " + user);
}

// in the login screen
shared(Header.class).setUser(name);
```

## Ready-made: `LoginForm`

From `unweird-components`:

```java
LoginForm form = new LoginForm()
        .onSignIn(this::signIn)
        .onForgotPassword(() -> ...);   // the link only shows if you set this

@Override protected void onShow() { form.reset(); }   // clears password and error

private void signIn(LoginForm.Credentials c) {
    if (!check(c)) form.fail("Wrong username or password.");
    else go(HomeScreen.class);
}
```

(Use a method, not a lambda: a lambda in `form`'s own initializer can't refer to `form`.)

Empty fields are caught before your handler runs.

Next: [Layout](layout.md)
