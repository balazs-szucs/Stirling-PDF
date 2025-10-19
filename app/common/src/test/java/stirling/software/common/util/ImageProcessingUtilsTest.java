package stirling.software.common.util;

import java.awt.*;
import java.awt.image.BufferedImage;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ImageProcessingUtilsTest {

    private static void fillImageWithColor(BufferedImage image) {
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                image.setRGB(x, y, Color.RED.getRGB());
            }
        }
    }

    @Test
    void testConvertColorTypeToGreyscale() {
        BufferedImage sourceImage = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
        fillImageWithColor(sourceImage);

        BufferedImage convertedImage =
                ImageProcessingUtils.convertColorType(sourceImage, "greyscale");

        Assertions.assertNotNull(convertedImage);
        Assertions.assertEquals(BufferedImage.TYPE_BYTE_GRAY, convertedImage.getType());
        Assertions.assertEquals(sourceImage.getWidth(), convertedImage.getWidth());
        Assertions.assertEquals(sourceImage.getHeight(), convertedImage.getHeight());

        // Check if a pixel is correctly converted to greyscale
        Color grey = new Color(convertedImage.getRGB(0, 0));
        Assertions.assertEquals(grey.getRed(), grey.getGreen());
        Assertions.assertEquals(grey.getGreen(), grey.getBlue());
    }

    @Test
    void testConvertColorTypeToBlackWhite() {
        BufferedImage sourceImage = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
        fillImageWithColor(sourceImage);

        BufferedImage convertedImage =
                ImageProcessingUtils.convertColorType(sourceImage, "blackwhite");

        Assertions.assertNotNull(convertedImage);
        Assertions.assertEquals(BufferedImage.TYPE_BYTE_BINARY, convertedImage.getType());
        Assertions.assertEquals(sourceImage.getWidth(), convertedImage.getWidth());
        Assertions.assertEquals(sourceImage.getHeight(), convertedImage.getHeight());

        // Check if a pixel is converted correctly (binary image will be either black or white)
        int rgb = convertedImage.getRGB(0, 0);
        Assertions.assertTrue(rgb == Color.BLACK.getRGB() || rgb == Color.WHITE.getRGB());
    }

    @Test
    void testConvertColorTypeToFullColor() {
        BufferedImage sourceImage = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
        fillImageWithColor(sourceImage);

        BufferedImage convertedImage =
                ImageProcessingUtils.convertColorType(sourceImage, "fullcolor");

        Assertions.assertNotNull(convertedImage);
        Assertions.assertEquals(sourceImage, convertedImage);
    }

    @Test
    void testConvertColorTypeInvalid() {
        BufferedImage sourceImage = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
        fillImageWithColor(sourceImage);

        BufferedImage convertedImage =
                ImageProcessingUtils.convertColorType(sourceImage, "invalidtype");

        Assertions.assertNotNull(convertedImage);
        Assertions.assertEquals(sourceImage, convertedImage);
    }
}
