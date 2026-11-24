package com.dervarex.unweird.scene3d;

import com.dervarex.unweird.style.Color;
import org.joml.Matrix3f;
import org.joml.FrustumIntersection;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import static org.lwjgl.opengl.GL33C.*;

/**
 * Draws a {@link Scene} with OpenGL 3.3. {@link View3D} uses one; for your own widgets, call {@link #render} inside
 * {@link com.dervarex.unweird.Canvas#gl Canvas.gl}. One per view (it keeps GPU copies of meshes and textures);
 * UI thread only.
 */
public final class Renderer {

    private static final String VERTEX = """
            #version 330 core
            layout(location = 0) in vec3 aPos;
            layout(location = 1) in vec3 aNormal;
            layout(location = 2) in vec2 aUv;
            layout(location = 3) in vec4 aColor;
            uniform mat4 uModel;
            uniform mat4 uViewProj;
            uniform mat3 uNormal;
            out vec3 vNormal;
            centroid out vec2 vUv; // centroid: antialiased edges must not sample outside their face
            out vec4 vColor;
            out vec3 vWorld;
            void main() {
                vNormal = normalize(uNormal * aNormal);
                vUv = aUv;
                vColor = aColor;
                vec4 world = uModel * vec4(aPos, 1.0);
                vWorld = world.xyz;
                gl_Position = uViewProj * world;
            }
            """;

    private static final String FRAGMENT = """
            #version 330 core
            in vec3 vNormal;
            centroid in vec2 vUv;
            in vec4 vColor;
            in vec3 vWorld;
            uniform sampler2D uTexture;
            uniform vec3 uEye;
            uniform vec4 uFogColor;
            uniform vec2 uFogRange;
            uniform vec4 uTint;
            uniform int uLit;
            uniform int uOpaque;
            uniform vec3 uSun;
            uniform float uAmbient;
            uniform float uCutoff;
            out vec4 fragColor;
            void main() {
                vec4 c = texture(uTexture, vUv) * vColor * uTint;
                if (c.a < uCutoff) discard;
                if (uLit == 1) {
                    vec3 n = normalize(gl_FrontFacing ? vNormal : -vNormal);
                    float direct = max(dot(n, -uSun), 0.0);
                    c.rgb *= uAmbient + (1.0 - uAmbient) * direct;
                }
                if (uFogColor.a > 0.0) {
                    float f = clamp((length(vWorld - uEye) - uFogRange.x) / (uFogRange.y - uFogRange.x), 0.0, 1.0);
                    c = mix(c, uFogColor, f);
                }
                if (uOpaque == 1) c.a = 1.0;
                fragColor = vec4(c.rgb * c.a, c.a); // premultiplied, like the surface is composited
            }
            """;

    private static final int EVICT_AFTER_FRAMES = 300;

    private int program;
    private int uModel, uViewProj, uNormal, uTexture, uTint, uLit, uOpaque, uSun, uAmbient, uCutoff, uEye, uFogColor, uFogRange;
    private int whiteTexture;
    private final Map<Mesh, GlMesh> meshes = new IdentityHashMap<>();
    private final Map<Texture, GlTexture> textures = new IdentityHashMap<>();
    private long frame;

    private static final class GlMesh {
        int vao, vbo, ebo, count;
        long lastFrame;
    }

    private static final class GlTexture {
        int id, version = -1;
        long lastFrame;
    }

    private record Item(Node node, Matrix4f world, float depth) {
    }

    /** Draws {@code scene} as {@code camera} sees it into the current target of {@code width × height} pixels. */
    public void render(Scene scene, Camera camera, int width, int height) {
        frame++;
        if (program == 0) {
            setUp();
        }
        if (scene.background != null) {
            Color bg = scene.background;
            glClearColor(bg.r() * bg.a(), bg.g() * bg.a(), bg.b() * bg.a(), bg.a());
            glClear(GL_COLOR_BUFFER_BIT);
        }
        Matrix4f view = camera.view();
        Matrix4f viewProj = camera.projection((float) width / height).mul(view);
        FrustumIntersection frustum = new FrustumIntersection(viewProj);
        Vector3f boxMin = new Vector3f();
        Vector3f boxMax = new Vector3f();

        List<Item> opaque = new ArrayList<>();
        List<Item> transparent = new ArrayList<>();
        scene.root().walk(new Matrix4f(), (node, world) -> {
            if (node.mesh() == null || node.mesh().triangleCount() == 0) {
                return;
            }
            Vector3f min = node.mesh().min();
            Vector3f max = node.mesh().max();
            world.transformAab(min, max, boxMin, boxMax);
            if (!frustum.testAab(boxMin, boxMax)) {
                return; // off screen
            }
            Vector3f center = min.add(max).mul(0.5f);
            world.transformPosition(center);
            view.transformPosition(center);
            (node.material().transparent ? transparent : opaque).add(new Item(node, world, center.z));
        });
        transparent.sort(Comparator.comparingDouble(Item::depth)); // most negative z = farthest first

        glUseProgram(program);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            glUniformMatrix4fv(uViewProj, false, viewProj.get(stack.mallocFloat(16)));
        }
        glUniform3f(uSun, scene.sunDirection.x, scene.sunDirection.y, scene.sunDirection.z);
        glUniform1f(uAmbient, scene.ambient);
        Vector3f eye = camera.eye();
        glUniform3f(uEye, eye.x, eye.y, eye.z);
        Color fog = scene.fog;
        if (fog != null) {
            glUniform4f(uFogColor, fog.r(), fog.g(), fog.b(), 1);
            glUniform2f(uFogRange, scene.fogStart, scene.fogEnd);
        } else {
            glUniform4f(uFogColor, 0, 0, 0, 0);
        }
        glUniform1i(uTexture, 0);
        glActiveTexture(GL_TEXTURE0);
        glEnable(GL_DEPTH_TEST);
        glDepthFunc(GL_LEQUAL);

        glDisable(GL_BLEND);
        glDepthMask(true);
        for (Item item : opaque) {
            draw(item);
        }
        glEnable(GL_BLEND);
        glBlendFunc(GL_ONE, GL_ONE_MINUS_SRC_ALPHA);
        glDepthMask(false);
        for (Item item : transparent) {
            draw(item);
        }
        glDepthMask(true);
        glDisable(GL_BLEND);
        evict();
    }

    private void draw(Item item) {
        Node node = item.node();
        Material material = node.material();
        GlMesh mesh = upload(node.mesh());
        glBindTexture(GL_TEXTURE_2D, material.texture == null ? whiteTexture : upload(material.texture));
        try (MemoryStack stack = MemoryStack.stackPush()) {
            glUniformMatrix4fv(uModel, false, item.world().get(stack.mallocFloat(16)));
            Matrix3f normal = new Matrix3f(item.world()).invert().transpose();
            glUniformMatrix3fv(uNormal, false, normal.get(stack.mallocFloat(9)));
        }
        Color tint = material.color;
        glUniform4f(uTint, tint.r(), tint.g(), tint.b(), tint.a());
        glUniform1i(uLit, material.lit ? 1 : 0);
        glUniform1i(uOpaque, material.transparent ? 0 : 1);
        glUniform1f(uCutoff, material.cutoff);
        if (material.doubleSided) {
            glDisable(GL_CULL_FACE);
        } else {
            glEnable(GL_CULL_FACE);
            glCullFace(GL_BACK);
        }
        glBindVertexArray(mesh.vao);
        glDrawElements(GL_TRIANGLES, mesh.count, GL_UNSIGNED_INT, 0);
    }

    private GlMesh upload(Mesh mesh) {
        GlMesh gl = meshes.get(mesh);
        if (gl == null) {
            gl = new GlMesh();
            gl.vao = glGenVertexArrays();
            glBindVertexArray(gl.vao);
            gl.vbo = glGenBuffers();
            glBindBuffer(GL_ARRAY_BUFFER, gl.vbo);
            FloatBuffer vertices = MemoryUtil.memAllocFloat(mesh.vertices.length).put(mesh.vertices).flip();
            glBufferData(GL_ARRAY_BUFFER, vertices, GL_STATIC_DRAW);
            MemoryUtil.memFree(vertices);
            gl.ebo = glGenBuffers();
            glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, gl.ebo);
            IntBuffer indices = MemoryUtil.memAllocInt(mesh.indices.length).put(mesh.indices).flip();
            glBufferData(GL_ELEMENT_ARRAY_BUFFER, indices, GL_STATIC_DRAW);
            MemoryUtil.memFree(indices);
            int stride = Mesh.STRIDE * Float.BYTES;
            glEnableVertexAttribArray(0);
            glVertexAttribPointer(0, 3, GL_FLOAT, false, stride, 0);
            glEnableVertexAttribArray(1);
            glVertexAttribPointer(1, 3, GL_FLOAT, false, stride, 3 * Float.BYTES);
            glEnableVertexAttribArray(2);
            glVertexAttribPointer(2, 2, GL_FLOAT, false, stride, 6 * Float.BYTES);
            glEnableVertexAttribArray(3);
            glVertexAttribPointer(3, 4, GL_FLOAT, false, stride, 8 * Float.BYTES);
            glBindVertexArray(0);
            gl.count = mesh.indices.length;
            meshes.put(mesh, gl);
        }
        gl.lastFrame = frame;
        return gl;
    }

    private int upload(Texture texture) {
        GlTexture gl = textures.computeIfAbsent(texture, t -> {
            GlTexture created = new GlTexture();
            created.id = glGenTextures();
            return created;
        });
        gl.lastFrame = frame;
        if (gl.version != texture.version()) {
            Texture.Snapshot snapshot = texture.snapshot();
            ByteBuffer pixels = MemoryUtil.memAlloc(snapshot.pixels().length).put(snapshot.pixels()).flip();
            glBindTexture(GL_TEXTURE_2D, gl.id);
            glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, snapshot.width(), snapshot.height(), 0, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
            MemoryUtil.memFree(pixels);
            if (texture.isNearest()) {
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
            } else {
                glGenerateMipmap(GL_TEXTURE_2D);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
            }
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
            gl.version = snapshot.version();
        }
        return gl.id;
    }

    /** Frees GPU copies of meshes and textures nobody drew for a while (e.g. a rebuilt voxel mesh). */
    private void evict() {
        meshes.values().removeIf(gl -> {
            if (frame - gl.lastFrame > EVICT_AFTER_FRAMES) {
                glDeleteVertexArrays(gl.vao);
                glDeleteBuffers(gl.vbo);
                glDeleteBuffers(gl.ebo);
                return true;
            }
            return false;
        });
        textures.values().removeIf(gl -> {
            if (frame - gl.lastFrame > EVICT_AFTER_FRAMES) {
                glDeleteTextures(gl.id);
                return true;
            }
            return false;
        });
    }

    private void setUp() {
        int vertex = compile(GL_VERTEX_SHADER, VERTEX);
        int fragment = compile(GL_FRAGMENT_SHADER, FRAGMENT);
        program = glCreateProgram();
        glAttachShader(program, vertex);
        glAttachShader(program, fragment);
        glLinkProgram(program);
        if (glGetProgrami(program, GL_LINK_STATUS) != GL_TRUE) {
            throw new IllegalStateException("unweird-3d: shader link failed: " + glGetProgramInfoLog(program));
        }
        glDeleteShader(vertex);
        glDeleteShader(fragment);
        uModel = glGetUniformLocation(program, "uModel");
        uViewProj = glGetUniformLocation(program, "uViewProj");
        uNormal = glGetUniformLocation(program, "uNormal");
        uTexture = glGetUniformLocation(program, "uTexture");
        uTint = glGetUniformLocation(program, "uTint");
        uLit = glGetUniformLocation(program, "uLit");
        uOpaque = glGetUniformLocation(program, "uOpaque");
        uSun = glGetUniformLocation(program, "uSun");
        uAmbient = glGetUniformLocation(program, "uAmbient");
        uCutoff = glGetUniformLocation(program, "uCutoff");
        uEye = glGetUniformLocation(program, "uEye");
        uFogColor = glGetUniformLocation(program, "uFogColor");
        uFogRange = glGetUniformLocation(program, "uFogRange");

        whiteTexture = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, whiteTexture);
        ByteBuffer white = MemoryUtil.memAlloc(4).put(new byte[]{-1, -1, -1, -1}).flip();
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, 1, 1, 0, GL_RGBA, GL_UNSIGNED_BYTE, white);
        MemoryUtil.memFree(white);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
    }

    private static int compile(int type, String source) {
        int shader = glCreateShader(type);
        glShaderSource(shader, source);
        glCompileShader(shader);
        if (glGetShaderi(shader, GL_COMPILE_STATUS) != GL_TRUE) {
            throw new IllegalStateException("unweird-3d: shader compile failed: " + glGetShaderInfoLog(shader));
        }
        return shader;
    }
}
