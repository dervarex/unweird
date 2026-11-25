package com.dervarex.unweird.scene3d;

import com.dervarex.unweird.Gl;
import com.dervarex.unweird.style.Color;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;


/** Draws a {@link Scene} with OpenGL 3.3 or OpenGL ES 3 (through {@link Gl}). One per {@link View3D}; UI thread only. */
final class Renderer {

    private static final String VERTEX = """
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
            void main() {
                vNormal = normalize(uNormal * aNormal);
                vUv = aUv;
                vColor = aColor;
                gl_Position = uViewProj * uModel * vec4(aPos, 1.0);
            }
            """;

    private static final String FRAGMENT = """
            in vec3 vNormal;
            centroid in vec2 vUv;
            in vec4 vColor;
            uniform sampler2D uTexture;
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
                if (uOpaque == 1) c.a = 1.0;
                fragColor = vec4(c.rgb * c.a, c.a); // premultiplied, like the surface is composited
            }
            """;

    private static final int EVICT_AFTER_FRAMES = 300;

    private int program;
    private int uModel, uViewProj, uNormal, uTexture, uTint, uLit, uOpaque, uSun, uAmbient, uCutoff;
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

    private Gl gl;

    void render(Gl gl, Scene scene, Camera camera, int width, int height) {
        if (this.gl != gl) {
            // a new GL context (Android recreates it after the app was in the background): upload everything again
            program = 0;
            meshes.clear();
            textures.clear();
            this.gl = gl;
        }
        frame++;
        if (program == 0) {
            setUp();
        }
        if (scene.background != null) {
            Color bg = scene.background;
            gl.clearColor(bg.r() * bg.a(), bg.g() * bg.a(), bg.b() * bg.a(), bg.a());
            gl.clear(Gl.COLOR_BUFFER_BIT);
        }
        Matrix4f view = camera.view();
        Matrix4f viewProj = camera.projection((float) width / height).mul(view);

        List<Item> opaque = new ArrayList<>();
        List<Item> transparent = new ArrayList<>();
        scene.root().walk(new Matrix4f(), (node, world) -> {
            if (node.mesh() == null || node.mesh().triangleCount() == 0) {
                return;
            }
            Vector3f center = node.mesh().min().add(node.mesh().max()).mul(0.5f);
            world.transformPosition(center);
            view.transformPosition(center);
            (node.material().transparent ? transparent : opaque).add(new Item(node, world, center.z));
        });
        transparent.sort(Comparator.comparingDouble(Item::depth)); // most negative z = farthest first

        gl.useProgram(program);
        gl.uniformMatrix4fv(uViewProj, viewProj.get(new float[16]));
        gl.uniform3f(uSun, scene.sunDirection.x, scene.sunDirection.y, scene.sunDirection.z);
        gl.uniform1f(uAmbient, scene.ambient);
        gl.uniform1i(uTexture, 0);
        gl.activeTexture(Gl.TEXTURE0);
        gl.enable(Gl.DEPTH_TEST);
        gl.depthFunc(Gl.LEQUAL);

        gl.disable(Gl.BLEND);
        gl.depthMask(true);
        for (Item item : opaque) {
            draw(item);
        }
        gl.enable(Gl.BLEND);
        gl.blendFunc(Gl.ONE, Gl.ONE_MINUS_SRC_ALPHA);
        gl.depthMask(false);
        for (Item item : transparent) {
            draw(item);
        }
        gl.depthMask(true);
        gl.disable(Gl.BLEND);
        evict();
    }

    private void draw(Item item) {
        Node node = item.node();
        Material material = node.material();
        GlMesh mesh = upload(node.mesh());
        gl.bindTexture(Gl.TEXTURE_2D, material.texture == null ? whiteTexture : upload(material.texture));
        gl.uniformMatrix4fv(uModel, item.world().get(new float[16]));
        Matrix3f normal = new Matrix3f(item.world()).invert().transpose();
        gl.uniformMatrix3fv(uNormal, normal.get(new float[9]));
        Color tint = material.color;
        gl.uniform4f(uTint, tint.r(), tint.g(), tint.b(), tint.a());
        gl.uniform1i(uLit, material.lit ? 1 : 0);
        gl.uniform1i(uOpaque, material.transparent ? 0 : 1);
        gl.uniform1f(uCutoff, material.cutoff);
        if (material.doubleSided) {
            gl.disable(Gl.CULL_FACE);
        } else {
            gl.enable(Gl.CULL_FACE);
            gl.cullFace(Gl.BACK);
        }
        gl.bindVertexArray(mesh.vao);
        gl.drawElements(Gl.TRIANGLES, mesh.count, Gl.UNSIGNED_INT, 0);
    }

    private GlMesh upload(Mesh mesh) {
        GlMesh cached = meshes.get(mesh);
        if (cached == null) {
            cached = new GlMesh();
            cached.vao = gl.genVertexArray();
            gl.bindVertexArray(cached.vao);
            cached.vbo = gl.genBuffer();
            gl.bindBuffer(Gl.ARRAY_BUFFER, cached.vbo);
            gl.bufferData(Gl.ARRAY_BUFFER, mesh.vertices, Gl.STATIC_DRAW);
            cached.ebo = gl.genBuffer();
            gl.bindBuffer(Gl.ELEMENT_ARRAY_BUFFER, cached.ebo);
            gl.bufferData(Gl.ELEMENT_ARRAY_BUFFER, mesh.indices, Gl.STATIC_DRAW);
            int stride = Mesh.STRIDE * Float.BYTES;
            gl.enableVertexAttribArray(0);
            gl.vertexAttribPointer(0, 3, Gl.FLOAT, false, stride, 0);
            gl.enableVertexAttribArray(1);
            gl.vertexAttribPointer(1, 3, Gl.FLOAT, false, stride, 3 * Float.BYTES);
            gl.enableVertexAttribArray(2);
            gl.vertexAttribPointer(2, 2, Gl.FLOAT, false, stride, 6 * Float.BYTES);
            gl.enableVertexAttribArray(3);
            gl.vertexAttribPointer(3, 4, Gl.FLOAT, false, stride, 8 * Float.BYTES);
            gl.bindVertexArray(0);
            cached.count = mesh.indices.length;
            meshes.put(mesh, cached);
        }
        cached.lastFrame = frame;
        return cached;
    }

    private int upload(Texture texture) {
        GlTexture cached = textures.computeIfAbsent(texture, t -> {
            GlTexture created = new GlTexture();
            created.id = gl.genTexture();
            return created;
        });
        cached.lastFrame = frame;
        if (cached.version != texture.version()) {
            Texture.Snapshot snapshot = texture.snapshot();
            gl.bindTexture(Gl.TEXTURE_2D, cached.id);
            gl.pixelStorei(Gl.UNPACK_ALIGNMENT, 1);
            gl.texImage2D(Gl.TEXTURE_2D, 0, Gl.RGBA8, snapshot.width(), snapshot.height(), Gl.RGBA, Gl.UNSIGNED_BYTE, snapshot.pixels());
            if (texture.isNearest()) {
                gl.texParameteri(Gl.TEXTURE_2D, Gl.TEXTURE_MIN_FILTER, Gl.NEAREST);
                gl.texParameteri(Gl.TEXTURE_2D, Gl.TEXTURE_MAG_FILTER, Gl.NEAREST);
            } else {
                gl.generateMipmap(Gl.TEXTURE_2D);
                gl.texParameteri(Gl.TEXTURE_2D, Gl.TEXTURE_MIN_FILTER, Gl.LINEAR_MIPMAP_LINEAR);
                gl.texParameteri(Gl.TEXTURE_2D, Gl.TEXTURE_MAG_FILTER, Gl.LINEAR);
            }
            gl.texParameteri(Gl.TEXTURE_2D, Gl.TEXTURE_WRAP_S, Gl.CLAMP_TO_EDGE);
            gl.texParameteri(Gl.TEXTURE_2D, Gl.TEXTURE_WRAP_T, Gl.CLAMP_TO_EDGE);
            cached.version = snapshot.version();
        }
        return cached.id;
    }

    /** Frees GPU copies of meshes and textures nobody drew for a while (e.g. a rebuilt voxel mesh). */
    private void evict() {
        meshes.values().removeIf(cached -> {
            if (frame - cached.lastFrame > EVICT_AFTER_FRAMES) {
                gl.deleteVertexArray(cached.vao);
                gl.deleteBuffer(cached.vbo);
                gl.deleteBuffer(cached.ebo);
                return true;
            }
            return false;
        });
        textures.values().removeIf(cached -> {
            if (frame - cached.lastFrame > EVICT_AFTER_FRAMES) {
                gl.deleteTexture(cached.id);
                return true;
            }
            return false;
        });
    }

    private void setUp() {
        int vertex = compile(Gl.VERTEX_SHADER, VERTEX);
        int fragment = compile(Gl.FRAGMENT_SHADER, FRAGMENT);
        program = gl.createProgram();
        gl.attachShader(program, vertex);
        gl.attachShader(program, fragment);
        gl.linkProgram(program);
        if (gl.getProgrami(program, Gl.LINK_STATUS) != Gl.TRUE) {
            throw new IllegalStateException("unweird-3d: shader link failed: " + gl.getProgramInfoLog(program));
        }
        gl.deleteShader(vertex);
        gl.deleteShader(fragment);
        uModel = gl.getUniformLocation(program, "uModel");
        uViewProj = gl.getUniformLocation(program, "uViewProj");
        uNormal = gl.getUniformLocation(program, "uNormal");
        uTexture = gl.getUniformLocation(program, "uTexture");
        uTint = gl.getUniformLocation(program, "uTint");
        uLit = gl.getUniformLocation(program, "uLit");
        uOpaque = gl.getUniformLocation(program, "uOpaque");
        uSun = gl.getUniformLocation(program, "uSun");
        uAmbient = gl.getUniformLocation(program, "uAmbient");
        uCutoff = gl.getUniformLocation(program, "uCutoff");

        whiteTexture = gl.genTexture();
        gl.bindTexture(Gl.TEXTURE_2D, whiteTexture);
        gl.texImage2D(Gl.TEXTURE_2D, 0, Gl.RGBA8, 1, 1, Gl.RGBA, Gl.UNSIGNED_BYTE, new byte[]{-1, -1, -1, -1});
        gl.texParameteri(Gl.TEXTURE_2D, Gl.TEXTURE_MIN_FILTER, Gl.NEAREST);
        gl.texParameteri(Gl.TEXTURE_2D, Gl.TEXTURE_MAG_FILTER, Gl.NEAREST);
    }

    private int compile(int type, String source) {
        int shader = gl.createShader(type);
        gl.shaderSource(shader, gl.shaderVersion() + source);
        gl.compileShader(shader);
        if (gl.getShaderi(shader, Gl.COMPILE_STATUS) != Gl.TRUE) {
            throw new IllegalStateException("unweird-3d: shader compile failed: " + gl.getShaderInfoLog(shader));
        }
        return shader;
    }
}
