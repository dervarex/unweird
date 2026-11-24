package com.dervarex.unweird.game;

import org.lwjgl.openal.AL;
import org.lwjgl.openal.ALC;
import org.lwjgl.openal.ALCCapabilities;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.system.libc.LibCStdlib;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.ByteArrayInputStream;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.lwjgl.openal.AL10.*;
import static org.lwjgl.openal.ALC10.*;
import static org.lwjgl.stb.STBVorbis.stb_vorbis_decode_memory;

/**
 * OpenAL playback, started on first use. Without a sound device (servers, CI) everything stays silent and the game
 * runs on. UI thread only.
 */
final class Audio {

    private static final int SOURCES = 24;

    private static boolean started;
    private static boolean available;
    private static long device;
    private static long context;
    private static final List<Integer> sources = new ArrayList<>();
    /** OpenAL buffer per decoded sound (by key); -1 = couldn't be loaded. */
    private static final Map<String, Integer> buffers = new HashMap<>();

    private Audio() {
    }

    private static boolean start() {
        if (started) {
            return available;
        }
        started = true;
        try {
            device = alcOpenDevice((ByteBuffer) null);
            if (device == MemoryUtil.NULL) {
                System.err.println("unweird-game: no sound device, playing silently");
                return false;
            }
            ALCCapabilities capabilities = ALC.createCapabilities(device);
            context = alcCreateContext(device, (IntBuffer) null);
            if (context == MemoryUtil.NULL || !alcMakeContextCurrent(context)) {
                System.err.println("unweird-game: can't start sound, playing silently");
                return false;
            }
            AL.createCapabilities(capabilities);
            for (int i = 0; i < SOURCES; i++) {
                sources.add(alGenSources());
            }
            available = true;
            Runtime.getRuntime().addShutdownHook(new Thread(Audio::stopAll, "unweird-audio-shutdown"));
        } catch (Throwable e) { // missing natives, no OpenAL implementation...
            System.err.println("unweird-game: sound unavailable (" + e.getMessage() + "), playing silently");
            available = false;
        }
        return available;
    }

    private static void stopAll() {
        try {
            if (context != MemoryUtil.NULL) {
                alcMakeContextCurrent(MemoryUtil.NULL);
                alcDestroyContext(context);
            }
            if (device != MemoryUtil.NULL) {
                alcCloseDevice(device);
            }
        } catch (Throwable ignored) {
            // shutting down anyway
        }
    }

    /** Plays a loaded buffer; returns the source (for stopping) or -1. */
    static int play(String key, Loader loader, float volume, boolean loop) {
        if (!start()) {
            return -1;
        }
        Integer buffer = buffers.get(key);
        if (buffer == null) {
            buffer = upload(loader.load());
            buffers.put(key, buffer);
        }
        if (buffer < 0) {
            return -1;
        }
        int source = freeSource();
        alSourceStop(source);
        alSourcei(source, AL_BUFFER, buffer);
        alSourcef(source, AL_GAIN, Math.max(0, volume));
        alSourcei(source, AL_LOOPING, loop ? AL_TRUE : AL_FALSE);
        alSourcePlay(source);
        return source;
    }

    static void stop(int source) {
        if (available && source >= 0) {
            alSourceStop(source);
            alSourcei(source, AL_LOOPING, AL_FALSE);
        }
    }

    static boolean playing(int source) {
        return available && source >= 0 && alGetSourcei(source, AL_SOURCE_STATE) == AL_PLAYING;
    }

    private static int freeSource() {
        for (int source : sources) {
            int state = alGetSourcei(source, AL_SOURCE_STATE);
            if (state != AL_PLAYING && state != AL_PAUSED) {
                return source;
            }
        }
        // all busy: cut the oldest one-shot (never a looping one if we can help it)
        for (int source : sources) {
            if (alGetSourcei(source, AL_LOOPING) == AL_FALSE) {
                sources.remove((Integer) source);
                sources.add(source);
                return source;
            }
        }
        return sources.getFirst();
    }

    private static int upload(Pcm pcm) {
        if (pcm == null) {
            return -1;
        }
        int buffer = alGenBuffers();
        int format = pcm.channels == 1 ? AL_FORMAT_MONO16 : AL_FORMAT_STEREO16;
        ShortBuffer data = MemoryUtil.memAllocShort(pcm.samples.length).put(pcm.samples).flip();
        alBufferData(buffer, format, data, pcm.rate);
        MemoryUtil.memFree(data);
        return buffer;
    }

    /** 16-bit signed samples, interleaved. */
    record Pcm(short[] samples, int channels, int rate) {
    }

    @FunctionalInterface
    interface Loader {
        Pcm load();
    }

    /** Decodes an .ogg (Vorbis) or .wav file; null if it can't. */
    static Pcm decode(URL url) {
        byte[] bytes = Assets.read(url);
        String path = url.getPath().toLowerCase(java.util.Locale.ROOT);
        try {
            return path.endsWith(".ogg") ? decodeOgg(bytes) : decodeWav(bytes);
        } catch (Exception e) {
            System.err.println("unweird-game: can't play " + url + " (" + e.getMessage() + "); use .ogg or 16-bit .wav");
            return null;
        }
    }

    private static Pcm decodeOgg(byte[] bytes) {
        ByteBuffer encoded = MemoryUtil.memAlloc(bytes.length).put(bytes).flip();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer channels = stack.mallocInt(1);
            IntBuffer rate = stack.mallocInt(1);
            ShortBuffer decoded = stb_vorbis_decode_memory(encoded, channels, rate);
            if (decoded == null) {
                throw new IllegalArgumentException("not a Vorbis file");
            }
            short[] samples = new short[decoded.remaining()];
            decoded.get(samples);
            LibCStdlib.free(decoded);
            return new Pcm(samples, channels.get(0), rate.get(0));
        } finally {
            MemoryUtil.memFree(encoded);
        }
    }

    private static Pcm decodeWav(byte[] bytes) throws Exception {
        try (AudioInputStream original = AudioSystem.getAudioInputStream(new ByteArrayInputStream(bytes))) {
            AudioFormat source = original.getFormat();
            int channels = Math.min(2, source.getChannels());
            AudioFormat target = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, source.getSampleRate(), 16, channels,
                    channels * 2, source.getSampleRate(), false);
            try (AudioInputStream converted = AudioSystem.getAudioInputStream(target, original)) {
                byte[] raw = converted.readAllBytes();
                short[] samples = new short[raw.length / 2];
                ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(samples);
                return new Pcm(samples, channels, (int) source.getSampleRate());
            }
        }
    }
}
