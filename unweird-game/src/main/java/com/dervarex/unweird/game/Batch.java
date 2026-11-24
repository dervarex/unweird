package com.dervarex.unweird.game;

import org.lwjgl.system.MemoryUtil;

import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.IdentityHashMap;
import java.util.Map;

import static org.lwjgl.opengl.GL33C.*;

/**
 * Draws textured rectangles fast: collects quads per picture and draws them in one call. Coordinates are physical
 * pixels with y up (0, 0 = bottom-left), like the world. Pictures are uploaded once, with sharp (nearest) pixels and
 * repeat wrapping so a long wall is one quad. UI thread, inside {@code Canvas.gl} only.
 */
final class Batch {

    private static final String VERTEX = """
            #version 330 core
            layout(location = 0) in vec2 aPos;
            layout(location = 1) in vec2 aUv;
            layout(location = 2) in vec4 aColor;
            uniform vec2 uSize;
            out vec2 vUv;
            out vec4 vColor;
            void main() {
                vUv = aUv;
                vColor = aColor;
                gl_Position = vec4(aPos / uSize * 2.0 - 1.0, 0.0, 1.0);
            }
            """;

    private static final String FRAGMENT = """
            #version 330 core
            in vec2 vUv;
            in vec4 vColor;
            uniform sampler2D uTexture;
            out vec4 fragColor;
            void main() {
                vec4 c = texture(uTexture, vUv) * vColor;
                fragColor = vec4(c.rgb * c.a, c.a);
            }
            """;

    private static final int FLOATS_PER_VERTEX = 8;
    private static final int MAX_QUADS = 4096;

    private int program;
    private int uSize;
    private int vao;
    private int vbo;
    private final float[] data = new float[MAX_QUADS * 6 * FLOATS_PER_VERTEX];
    private FloatBuffer buffer;
    private int floats;
    private int texture = -1;
    private final Map<BufferedImage, Integer> textures = new IdentityHashMap<>();
    private BufferedImage white;

    void begin(int width, int height) {
        if (program == 0) {
            setUp();
        }
        glViewport(0, 0, width, height);
        glDisable(GL_DEPTH_TEST);
        glDisable(GL_CULL_FACE);
        glEnable(GL_BLEND);
        glBlendFunc(GL_ONE, GL_ONE_MINUS_SRC_ALPHA);
        glUseProgram(program);
        glUniform2f(uSize, width, height);
        glActiveTexture(GL_TEXTURE0);
        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        floats = 0;
        texture = -1;
    }

    /** Fills the whole target with a color (premultiplied by alpha 1). */
    void clear(float r, float g, float b) {
        glClearColor(r, g, b, 1);
        glClear(GL_COLOR_BUFFER_BIT);
    }

    /**
     * A picture at (x, y) with size (w, h); {@code u1}/{@code v1} above 1 repeat it. {@code flip} mirrors it
     * horizontally. {@code alpha} fades it.
     */
    void image(BufferedImage image, float x, float y, float w, float h, float u1, float v1, boolean flip, float alpha) {
        use(texture(image));
        float u0 = 0;
        if (flip) {
            u0 = u1;
            u1 = 0;
        }
        // image row 0 is the top: v = 0 at the top edge
        vertex(x, y, u0, v1, alpha);
        vertex(x + w, y, u1, v1, alpha);
        vertex(x + w, y + h, u1, 0, alpha);
        vertex(x, y, u0, v1, alpha);
        vertex(x + w, y + h, u1, 0, alpha);
        vertex(x, y + h, u0, 0, alpha);
    }

    /** A plain rectangle. */
    void rect(float x, float y, float w, float h, float r, float g, float b, float a) {
        if (white == null) {
            white = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
            white.setRGB(0, 0, 0xffffffff);
        }
        use(texture(white));
        int start = floats;
        image(white, x, y, w, h, 1, 1, false, 1);
        for (int i = start; i < floats; i += FLOATS_PER_VERTEX) {
            data[i + 4] = r;
            data[i + 5] = g;
            data[i + 6] = b;
            data[i + 7] = a;
        }
    }

    void end() {
        flush();
        glBindVertexArray(0);
    }

    private void use(int id) {
        if (id != texture) {
            flush();
            texture = id;
        }
        if (floats + 6 * FLOATS_PER_VERTEX > data.length) {
            flush();
        }
    }

    private void vertex(float x, float y, float u, float v, float alpha) {
        float[] d = data;
        int i = floats;
        d[i] = x;
        d[i + 1] = y;
        d[i + 2] = u;
        d[i + 3] = v;
        d[i + 4] = 1;
        d[i + 5] = 1;
        d[i + 6] = 1;
        d[i + 7] = alpha;
        floats += FLOATS_PER_VERTEX;
    }

    private void flush() {
        if (floats == 0 || texture < 0) {
            floats = 0;
            return;
        }
        glBindTexture(GL_TEXTURE_2D, texture);
        buffer.clear();
        buffer.put(data, 0, floats).flip();
        glBufferData(GL_ARRAY_BUFFER, buffer, GL_STREAM_DRAW);
        glDrawArrays(GL_TRIANGLES, 0, floats / FLOATS_PER_VERTEX);
        floats = 0;
    }

    private int texture(BufferedImage image) {
        Integer id = textures.get(image);
        if (id != null) {
            return id;
        }
        int w = image.getWidth();
        int h = image.getHeight();
        int[] argb = image.getRGB(0, 0, w, h, null, 0, w);
        ByteBuffer pixels = MemoryUtil.memAlloc(w * h * 4);
        for (int p : argb) {
            pixels.put((byte) (p >> 16)).put((byte) (p >> 8)).put((byte) p).put((byte) (p >>> 24));
        }
        pixels.flip();
        int created = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, created);
        glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, w, h, 0, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
        MemoryUtil.memFree(pixels);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT);
        textures.put(image, created);
        texture = -1; // binding changed
        return created;
    }

    private void setUp() {
        program = Shaders.program(VERTEX, FRAGMENT);
        uSize = glGetUniformLocation(program, "uSize");
        glUseProgram(program);
        glUniform1i(glGetUniformLocation(program, "uTexture"), 0);
        vao = glGenVertexArrays();
        vbo = glGenBuffers();
        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        int stride = FLOATS_PER_VERTEX * Float.BYTES;
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(0, 2, GL_FLOAT, false, stride, 0);
        glEnableVertexAttribArray(1);
        glVertexAttribPointer(1, 2, GL_FLOAT, false, stride, 2L * Float.BYTES);
        glEnableVertexAttribArray(2);
        glVertexAttribPointer(2, 4, GL_FLOAT, false, stride, 4L * Float.BYTES);
        buffer = MemoryUtil.memAllocFloat(data.length);
    }

    /** Frees the GPU side (the game left the screen). */
    void free() {
        if (program == 0) {
            return;
        }
        textures.values().forEach(id -> glDeleteTextures(id));
        textures.clear();
        glDeleteBuffers(vbo);
        glDeleteVertexArrays(vao);
        glDeleteProgram(program);
        MemoryUtil.memFree(buffer);
        program = 0;
    }
}
