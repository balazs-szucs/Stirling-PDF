package stirling.software.common.util.misc;

import java.awt.Color;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.apache.pdfbox.contentstream.PDFGraphicsStreamEngine;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.PDImage;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.rendering.PageDrawer;
import org.apache.pdfbox.rendering.PageDrawerParameters;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.apache.pdfbox.util.Matrix;
import org.springframework.core.io.InputStreamResource;
import org.springframework.web.multipart.MultipartFile;

import lombok.Data;
import lombok.EqualsAndHashCode;

import stirling.software.common.model.api.PDFFile;
import stirling.software.common.model.api.misc.ReplaceAndInvert;

@Data
@EqualsAndHashCode(callSuper = true)
public abstract class ReplaceAndInvertColorStrategy extends PDFFile {

    protected ReplaceAndInvert replaceAndInvert;

    public ReplaceAndInvertColorStrategy(MultipartFile file, ReplaceAndInvert replaceAndInvert) {
        setFileInput(file);
        setReplaceAndInvert(replaceAndInvert);
    }

    public abstract InputStreamResource replace() throws IOException;

    protected PDFont checkSupportedFontForCharacter(String unicodeText) {
        Set<String> fonts = Standard14Fonts.getNames();
        for (String font : fonts) {
            Standard14Fonts.FontName fontName = Standard14Fonts.getMappedFontName(font);
            PDFont currentFont = new PDType1Font(fontName);
            try {
                currentFont.encode(unicodeText);
                return currentFont;
            } catch (Exception e) {
                // Ignore
            }
        }
        return null;
    }

    protected void invertImageColors(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        int[] pixels = new int[width * height];
        image.getRGB(0, 0, width, height, pixels, 0, width);
        for (int i = 0; i < pixels.length; i++) {
            int pixel = pixels[i];
            int a = (pixel >> 24) & 0xff;
            int r = (pixel >> 16) & 0xff;
            int g = (pixel >> 8) & 0xff;
            int b = pixel & 0xff;
            pixels[i] = (a << 24) | ((255 - r) << 16) | ((255 - g) << 8) | (255 - b);
        }
        image.setRGB(0, 0, width, height, pixels, 0, width);
    }

    @FunctionalInterface
    protected interface ColorProcessor {
        Color process(Color color);
    }

    @FunctionalInterface
    protected interface ImageProcessor {
        void process(BufferedImage image);
    }

    protected static class ColoredTextStripper extends PDFTextStripper {
        private final List<ColoredText> coloredTexts = new ArrayList<>();

        public ColoredTextStripper() throws IOException {}

        @Override
        protected void processTextPosition(TextPosition text) {
            ColoredText ct = new ColoredText();
            ct.textPosition = text;
            try {
                float[] rgb =
                        getGraphicsState()
                                .getNonStrokingColorSpace()
                                .toRGB(getGraphicsState().getNonStrokingColor().getComponents());
                ct.color = new Color(rgb[0], rgb[1], rgb[2]);
            } catch (Exception e) {
                ct.color = Color.BLACK;
            }
            coloredTexts.add(ct);
        }

        public List<ColoredText> getColoredTexts() {
            return coloredTexts;
        }

        public static class ColoredText {
            public TextPosition textPosition;
            public Color color;
        }
    }

    protected static class LayeredRenderer extends PDFRenderer {
        private final boolean skipText;
        private final boolean skipImages;

        public LayeredRenderer(PDDocument document, boolean skipText, boolean skipImages) {
            super(document);
            this.skipText = skipText;
            this.skipImages = skipImages;
        }

        @Override
        protected PageDrawer createPageDrawer(PageDrawerParameters parameters) throws IOException {
            return new PageDrawer(parameters) {
                @Override
                public void showTextString(byte[] string) throws IOException {
                    if (!skipText) {
                        super.showTextString(string);
                    }
                }

                @Override
                public void drawImage(PDImage pdImage) throws IOException {
                    if (!skipImages) {
                        super.drawImage(pdImage);
                    }
                }
            };
        }
    }

    protected static class ImageCollector extends PDFGraphicsStreamEngine {
        private final List<ImageInstance> images = new ArrayList<>();

        public ImageCollector(PDPage page) {
            super(page);
        }

        public List<ImageInstance> getImages() {
            return images;
        }

        @Override
        public void drawImage(PDImage pdImage) throws IOException {
            ImageInstance instance = new ImageInstance();
            instance.image = pdImage;
            instance.matrix = getGraphicsState().getCurrentTransformationMatrix().clone();
            images.add(instance);
        }

        @Override
        public void appendRectangle(Point2D p0, Point2D p1, Point2D p2, Point2D p3)
                throws IOException {}

        @Override
        public void clip(int windingRule) throws IOException {}

        @Override
        public void moveTo(float x, float y) throws IOException {}

        @Override
        public void lineTo(float x, float y) throws IOException {}

        @Override
        public void curveTo(float x1, float y1, float x2, float y2, float x3, float y3)
                throws IOException {}

        @Override
        public Point2D getCurrentPoint() throws IOException {
            return new Point2D.Float();
        }

        @Override
        public void closePath() throws IOException {}

        @Override
        public void endPath() throws IOException {}

        @Override
        public void shadingFill(COSName shadingName) throws IOException {}

        @Override
        public void fillAndStrokePath(int windingRule) throws IOException {}

        @Override
        public void fillPath(int windingRule) throws IOException {}

        @Override
        public void strokePath() throws IOException {}

        public static class ImageInstance {
            public PDImage image;
            public Matrix matrix;
        }
    }
}
