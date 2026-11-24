package com.dervarex.unweird.game;

import java.util.Random;

/**
 * Little built-in sound effects, so a game makes noise before anyone records anything: {@code jump}, {@code pickup},
 * {@code hurt}, {@code win}, {@code stomp}, {@code shoot}, {@code break}, {@code place}, {@code step}.
 * A file with the same name replaces them.
 */
final class Synth {

    private static final int RATE = 22050;

    private Synth() {
    }

    /** The built-in effect called {@code name}, or null. */
    static Audio.Pcm effect(String name) {
        return switch (name) {
            case "jump" -> sweep(0.16, 280, 620, Wave.SQUARE, 0.25);
            case "pickup", "coin" -> join(tone(0.06, 988, Wave.SQUARE, 0.22), tone(0.14, 1319, Wave.SQUARE, 0.22));
            case "hurt" -> sweep(0.32, 440, 90, Wave.SAW, 0.3);
            case "win", "goal" -> join(tone(0.1, 523, Wave.SQUARE, 0.22), tone(0.1, 659, Wave.SQUARE, 0.22),
                    tone(0.1, 784, Wave.SQUARE, 0.22), tone(0.28, 1047, Wave.SQUARE, 0.22));
            case "stomp" -> sweep(0.12, 220, 60, Wave.SQUARE, 0.3);
            case "shoot" -> sweep(0.12, 1100, 240, Wave.SQUARE, 0.18);
            case "break" -> noise(0.14, 0.35, 900);
            case "place" -> noise(0.06, 0.3, 300);
            case "step" -> noise(0.04, 0.12, 200);
            default -> null;
        };
    }

    private enum Wave { SQUARE, SAW }

    private static Audio.Pcm tone(double seconds, double hz, Wave wave, double volume) {
        return sweep(seconds, hz, hz, wave, volume);
    }

    private static Audio.Pcm sweep(double seconds, double fromHz, double toHz, Wave wave, double volume) {
        int count = (int) (seconds * RATE);
        short[] samples = new short[count];
        double phase = 0;
        for (int i = 0; i < count; i++) {
            double t = (double) i / count;
            double hz = fromHz + (toHz - fromHz) * t;
            phase += hz / RATE;
            double p = phase - Math.floor(phase);
            double value = wave == Wave.SQUARE ? (p < 0.5 ? 1 : -1) : 2 * p - 1;
            samples[i] = (short) (value * volume * envelope(i, count) * Short.MAX_VALUE);
        }
        return new Audio.Pcm(samples, 1, RATE);
    }

    /** Filtered noise; {@code cutoffHz} lower = duller. */
    private static Audio.Pcm noise(double seconds, double volume, double cutoffHz) {
        int count = (int) (seconds * RATE);
        short[] samples = new short[count];
        Random random = new Random(7);
        double alpha = Math.min(1, 2 * Math.PI * cutoffHz / RATE);
        double smooth = 0;
        for (int i = 0; i < count; i++) {
            smooth += alpha * ((random.nextDouble() * 2 - 1) - smooth);
            samples[i] = (short) (Math.max(-1, Math.min(1, smooth * 3)) * volume * envelope(i, count) * Short.MAX_VALUE);
        }
        return new Audio.Pcm(samples, 1, RATE);
    }

    /** Quick attack, linear fade out: no clicks at the ends. */
    private static double envelope(int i, int count) {
        double attack = Math.min(1, i / (RATE * 0.004));
        return attack * (1 - (double) i / count);
    }

    private static Audio.Pcm join(Audio.Pcm... parts) {
        int total = 0;
        for (Audio.Pcm part : parts) {
            total += part.samples().length;
        }
        short[] samples = new short[total];
        int at = 0;
        for (Audio.Pcm part : parts) {
            System.arraycopy(part.samples(), 0, samples, at, part.samples().length);
            at += part.samples().length;
        }
        return new Audio.Pcm(samples, 1, RATE);
    }
}
