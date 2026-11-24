package com.dervarex.unweird.game;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HooksTest {

    static class Animal {}
    static class Dog extends Animal {}

    static class Listener {
        final List<String> calls = new ArrayList<>();

        private void touch(Animal animal) {
            calls.add("animal");
        }

        void touch(Dog dog) {
            calls.add("dog");
        }

        void tick() {
            calls.add("tick");
        }

        void broken() {
            throw new IllegalStateException("on purpose");
        }
    }

    @Test
    void callsTheMostSpecificMethodWhateverItsVisibility() {
        Listener listener = new Listener();
        Hooks hooks = Hooks.of(Listener.class);
        assertTrue(hooks.call(listener, "touch", new Dog()));
        assertTrue(hooks.call(listener, "touch", new Animal()));
        assertFalse(hooks.call(listener, "touch", "a string"));
        assertTrue(hooks.call(listener, "tick"));
        assertFalse(hooks.call(listener, "missing"));
        assertEquals(List.of("dog", "animal", "tick"), listener.calls);
    }

    @Test
    void anExceptionInAHookDoesntStopTheGame() {
        assertTrue(Hooks.of(Listener.class).call(new Listener(), "broken"));
    }

    @Test
    void namesAreFoundInEverySpelling() {
        assertEquals("coin_box", Assets.snake("CoinBox"));
        assertEquals("level2", Assets.snake("Level2"));
        assertEquals("Coin Jump", Assets.words("CoinJump"));
        assertEquals(List.of("coin_box", "coinbox", "CoinBox"), Assets.spellings("CoinBox"));
    }

    @Test
    void soundsAreNamedAfterTheirFields() {
        class Holder {
            Sound explosion = new Sound();
            Sound named = new Sound("other", Holder.class);
        }
        Holder holder = new Holder();
        Members.nameSounds(holder);
        assertEquals("explosion", holder.explosion.name);
        assertEquals("other", holder.named.name);
    }

    @Test
    void theTestsAreUserCodeAndTheLibraryIsNot() {
        assertTrue(Members.isLibrary(Sprite.class));
        assertTrue(Members.isLibrary(com.dervarex.unweird.Screen.class));
        assertFalse(Members.isLibrary(HooksTest.class));
    }
}
