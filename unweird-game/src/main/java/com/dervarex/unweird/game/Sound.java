package com.dervarex.unweird.game;

/**
 * A sound effect or music, found by the name of its field: {@code Sound explosion = new Sound();} plays
 * {@code explosion.ogg} (or {@code .wav}) from your resources.
 *
 * <pre>{@code
 * Sound explosion = new Sound();
 * Sound theme = new Sound().volume(0.5f);
 * ...
 * explosion.play();
 * theme.loop();
 * }</pre>
 *
 * A few names work without any file (built-in effects): {@code jump}, {@code pickup}, {@code hurt}, {@code win},
 * {@code stomp}, {@code shoot}, {@code break}, {@code place}, {@code step}. Without a sound device the game runs silently.
 */
public final class Sound {

    String name;
    Class<?> owner = Sound.class;
    private float volume = 1;
    private int source = -1;
    private boolean warned;

    /** A sound named after the field it's stored in (games, levels and things are searched). */
    public Sound() {
    }

    Sound(String name, Class<?> owner) {
        this.name = name;
        this.owner = owner;
    }

    /** Loudness, 0..1 (default 1). */
    public Sound volume(float volume) {
        this.volume = Math.max(0, volume);
        return this;
    }

    /** Plays it once. Several plays overlap. */
    public void play() {
        start(false);
    }

    /** Plays it over and over until {@link #stop()}, e.g. music. Already looping? Nothing happens. */
    public void loop() {
        if (Audio.playing(source)) {
            return;
        }
        start(true);
    }

    /** Stops it if it's playing. */
    public void stop() {
        Audio.stop(source);
        source = -1;
    }

    /** Whether it can be heard: a file was found or it's a built-in effect. */
    boolean exists() {
        return name != null && (Assets.find(owner, name, "ogg", "wav") != null || Synth.effect(name) != null);
    }

    private void start(boolean loop) {
        if (name == null) {
            throw new IllegalStateException("This Sound has no name yet: store it in a field of your game "
                    + "(Sound explosion = new Sound();) and it plays explosion.ogg");
        }
        java.net.URL url = Assets.find(owner, name, "ogg", "wav");
        if (url == null && Synth.effect(name) == null) {
            if (!warned) {
                warned = true;
                System.err.println("unweird-game: no sound file for \"" + name + "\": add " + Assets.snake(name)
                        + ".ogg or .wav to your resources (e.g. src/main/resources/sounds/)");
            }
            return;
        }
        String key = url != null ? url.toString() : "synth:" + name;
        source = Audio.play(key, () -> url != null ? Audio.decode(url) : Synth.effect(name), volume, loop);
    }

    /** The first of {@code names} that has a file or a built-in effect, played once; quiet if none (engine sounds). */
    static void effect(Class<?> owner, String... names) {
        for (String name : names) {
            Sound sound = new Sound(name, owner);
            if (sound.exists()) {
                sound.play();
                return;
            }
        }
    }
}
