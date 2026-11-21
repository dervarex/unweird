package com.dervarex.unweird.scene3d;

import com.dervarex.unweird.style.Color;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Geometry, camera and picking: everything that doesn't need a GPU. */
class Scene3DTest {

    private static final float E = 1e-4f;
    private static final float UV = 1e-4f; // faces are inset by a hair

    @Test
    void boxesHave24VerticesAnd12OutwardFacingTriangles() {
        Mesh box = Mesh.box(2, 4, 6);
        assertEquals(24, box.vertexCount());
        assertEquals(12, box.triangleCount());
        assertEquals(new Vector3f(-1, -2, -3), box.min());
        assertEquals(new Vector3f(1, 2, 3), box.max());
        for (int i = 0; i < box.indices.length; i += 3) {
            Vector3f a = box.position(box.indices[i]);
            Vector3f b = box.position(box.indices[i + 1]);
            Vector3f c = box.position(box.indices[i + 2]);
            Vector3f faceNormal = new Vector3f(b).sub(a).cross(new Vector3f(c).sub(a)).normalize();
            int v = box.indices[i] * Mesh.STRIDE;
            Vector3f stored = new Vector3f(box.vertices[v + 3], box.vertices[v + 4], box.vertices[v + 5]);
            assertTrue(faceNormal.distance(stored) < E, "winding must be counter-clockwise seen from outside");
            Vector3f center = new Vector3f(a).add(b).add(c).div(3);
            assertTrue(center.dot(stored) > 0, "normals point outwards");
        }
    }

    @Test
    void boxUvFollowsTheMinecraftLayout() {
        // the head: 8x8x8 at (0, 0) on a 64x64 skin; front at (8,8)-(16,16), top at (8,0)-(16,8), back at (24,8)
        Mesh.BoxUv head = Mesh.BoxUv.of(0, 0, 8, 8, 8, 64, 64);
        assertArrayEquals(new float[]{8 / 64f, 8 / 64f, 16 / 64f, 16 / 64f}, head.face(Mesh.Face.FRONT), UV);
        assertArrayEquals(new float[]{8 / 64f, 0, 16 / 64f, 8 / 64f}, head.face(Mesh.Face.TOP), UV);
        assertArrayEquals(new float[]{24 / 64f, 8 / 64f, 32 / 64f, 16 / 64f}, head.face(Mesh.Face.BACK), UV);
        assertArrayEquals(new float[]{0, 8 / 64f, 8 / 64f, 16 / 64f}, head.face(Mesh.Face.RIGHT), UV);
    }

    @Test
    void cameraOrbitsItsTarget() {
        Camera camera = Camera.orbit(1, 2, 3, 10).angles(0, 0);
        assertTrue(camera.eye().distance(new Vector3f(1, 2, 13)) < E);
        camera.angles(90, 0);
        assertTrue(camera.eye().distance(new Vector3f(11, 2, 3)) < E);
        assertEquals(89, camera.angles(0, 200).pitch());
    }

    @Test
    void theCenterRayGoesThroughTheTarget() {
        Camera camera = Camera.orbit(0, 1, 0, 8).angles(30, 25);
        Ray ray = camera.ray(400, 300, 800, 600);
        Vector3f toTarget = camera.target().sub(ray.origin()).normalize();
        assertTrue(toTarget.distance(ray.direction()) < 1e-3f);
    }

    @Test
    void pickingFindsTheNearestFaceAndItsNormal() {
        Node box = new Node(Mesh.box(2, 2, 2), new Material());
        Scene scene = new Scene().add(box);
        Hit hit = scene.pick(new Ray(new Vector3f(0, 0, 10), new Vector3f(0, 0, -1)));
        assertNotNull(hit);
        assertSame(box, hit.node());
        assertTrue(hit.point().distance(new Vector3f(0, 0, 1)) < E);
        assertTrue(hit.normal().distance(new Vector3f(0, 0, 1)) < E);
        assertEquals(9, hit.distance(), E);
        assertNull(scene.pick(new Ray(new Vector3f(5, 5, 10), new Vector3f(0, 0, -1))));
    }

    @Test
    void pickingRespectsTransformsAndVisibility() {
        Node arm = new Node(Mesh.box(1, 1, 1), new Material()).position(0, 0, 0);
        Node shoulder = Node.group().position(3, 0, 0).rotation(0, 90, 0).add(arm);
        Scene scene = new Scene().add(shoulder);
        Hit hit = scene.pick(new Ray(new Vector3f(3, 0, 10), new Vector3f(0, 0, -1)));
        assertNotNull(hit);
        assertTrue(hit.normal().distance(new Vector3f(0, 0, 1)) < E, hit.normal().toString());
        shoulder.visible(false);
        assertNull(scene.pick(new Ray(new Vector3f(3, 0, 10), new Vector3f(0, 0, -1))));
    }

    @Test
    void blockHelpersPickTheHitBlockAndTheOneInFront() {
        Mesh.Builder builder = Mesh.builder();
        for (Mesh.BlockFace face : Mesh.BlockFace.values()) {
            builder.blockFace(2, 0, 5, face, Color.WHITE);
        }
        Scene scene = new Scene().add(new Node(builder.build(), new Material()));
        Hit hit = scene.pick(new Ray(new Vector3f(2.5f, 10, 5.5f), new Vector3f(0, -1, 0)));
        assertNotNull(hit);
        assertArrayEquals(new int[]{2, 0, 5}, hit.block());
        assertArrayEquals(new int[]{2, 1, 5}, hit.blockInFront());
    }

    private static void assertSame(Object expected, Object actual) {
        org.junit.jupiter.api.Assertions.assertSame(expected, actual);
    }
}
