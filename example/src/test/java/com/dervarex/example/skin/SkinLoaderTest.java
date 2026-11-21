package com.dervarex.example.skin;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkinLoaderTest {

    @TempDir
    Path dir;

    @Test
    void legacySkinsGetAMirroredLeftLegAndArm() throws IOException {
        BufferedImage legacy = new BufferedImage(64, 32, BufferedImage.TYPE_INT_ARGB);
        legacy.setRGB(4, 20, 0xffff0000);  // right leg, front face, leftmost column
        legacy.setRGB(44, 20, 0xff00ff00); // right arm, front face, leftmost column
        BufferedImage skin = SkinLoader.normalize(legacy);
        assertEquals(64, skin.getHeight());
        assertEquals(0xffff0000, skin.getRGB(23, 52)); // left leg front (20..24, 52..64), mirrored: rightmost column
        assertEquals(0xff00ff00, skin.getRGB(39, 52)); // left arm front (36..40, 52..64), mirrored
        assertEquals(0xffff0000, skin.getRGB(4, 20));  // the original stays
    }

    @Test
    void modernAndHdSkinsPassThrough() throws IOException {
        assertEquals(64, SkinLoader.normalize(new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB)).getWidth());
        assertEquals(128, SkinLoader.normalize(new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB)).getHeight());
        assertThrows(IOException.class, () -> SkinLoader.normalize(new BufferedImage(50, 50, BufferedImage.TYPE_INT_ARGB)));
    }

    @Test
    void filesLoadAndBadInputIsExplained() throws IOException {
        Path png = dir.resolve("me.png");
        ImageIO.write(new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB), "png", png.toFile());
        SkinLoader.Skin skin = SkinLoader.load(png.toString());
        assertNull(skin.slim());
        assertTrue(skin.description().contains("me.png"));

        assertTrue(assertThrows(IOException.class, () -> SkinLoader.load("  ")).getMessage().contains("username"));
        assertTrue(assertThrows(IOException.class, () -> SkinLoader.load("not a name!")).getMessage().contains("isn't"));
        assertTrue(assertThrows(IOException.class, () -> SkinLoader.load(dir.resolve("missing.png").toString())).getMessage().contains("no file"));
    }
}
