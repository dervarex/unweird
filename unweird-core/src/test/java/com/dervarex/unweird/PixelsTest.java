package com.dervarex.unweird;

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PixelsTest {

    @Test
    void decodesPngToRgba() throws Exception {
        BufferedImage image = new BufferedImage(2, 1, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, 0xffff0000);
        image.setRGB(1, 0, 0x8000ff00);
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        javax.imageio.ImageIO.write(image, "png", png);

        Pixels pixels = Pixels.decode(png.toByteArray());
        assertEquals(2, pixels.width());
        assertEquals(1, pixels.height());
        assertArrayEquals(new byte[]{(byte) 255, 0, 0, (byte) 255, 0, (byte) 255, 0, (byte) 128}, pixels.rgba());
    }

    @Test
    void rejectsWhatIsntAnImage() {
        assertThrows(IllegalArgumentException.class, () -> Pixels.decode(new byte[]{1, 2, 3}));
    }

    @Test
    void checksTheSize() {
        assertThrows(IllegalArgumentException.class, () -> new Pixels(2, 2, new byte[4]));
    }
}
