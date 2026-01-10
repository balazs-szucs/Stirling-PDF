package stirling.software.common.util.misc;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import javax.imageio.ImageIO;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.core.io.InputStreamResource;
import org.springframework.web.multipart.MultipartFile;

import lombok.extern.slf4j.Slf4j;

import stirling.software.common.model.ApplicationProperties;
import stirling.software.common.model.api.misc.HighContrastColorCombination;
import stirling.software.common.model.api.misc.ReplaceAndInvert;
import stirling.software.common.util.ApplicationContextProvider;
import stirling.software.common.util.ExceptionUtils;

@Slf4j
public class CustomColorReplaceStrategy extends ReplaceAndInvertColorStrategy {

    private String textColor;
    private String backgroundColor;
    private HighContrastColorCombination highContrastColorCombination;

    public CustomColorReplaceStrategy(
            MultipartFile file,
            ReplaceAndInvert replaceAndInvert,
            String textColor,
            String backgroundColor,
            HighContrastColorCombination highContrastColorCombination) {
        super(file, replaceAndInvert);
        this.textColor = textColor;
        this.backgroundColor = backgroundColor;
        this.highContrastColorCombination = highContrastColorCombination;
    }

    @Override
    public InputStreamResource replace() throws IOException {

        // If ReplaceAndInvert is HighContrastColor option, then get the colors of text and
        // background from static
        if (replaceAndInvert == ReplaceAndInvert.HIGH_CONTRAST_COLOR) {
            String[] colors =
                    HighContrastColorReplaceDecider.getColors(
                            replaceAndInvert, highContrastColorCombination);
            this.textColor = colors[0];
            this.backgroundColor = colors[1];
        }

        // Parse the target colors
        Color targetTextColor = Color.decode(this.textColor);
        Color targetBackgroundColor = Color.decode(this.backgroundColor);

        // Create a temporary file, with the original filename from the multipart file
        File file = Files.createTempFile("temp", getFileInput().getOriginalFilename()).toFile();

        try {
            // Transfer the content of the multipart file to the file
            getFileInput().transferTo(file);

            try (PDDocument document = Loader.loadPDF(file)) {
                // Render each page and replace colors
                PDFRenderer pdfRenderer = new PDFRenderer(document);

                for (int pageIndex = 0; pageIndex < document.getNumberOfPages(); pageIndex++) {
                    BufferedImage image;

                    // Use global maximum DPI setting, fallback to 300 if not set
                    int renderDpi = 300; // Default fallback
                    ApplicationProperties properties =
                            ApplicationContextProvider.getBean(ApplicationProperties.class);
                    if (properties != null && properties.getSystem() != null) {
                        renderDpi = properties.getSystem().getMaxDPI();
                    }
                    final int dpi = renderDpi;
                    final int pageNum = pageIndex;

                    image =
                            ExceptionUtils.handleOomRendering(
                                    pageNum + 1,
                                    dpi,
                                    () -> pdfRenderer.renderImageWithDPI(pageNum, dpi));

                    // Replace colors in the image
                    replaceColors(image, targetTextColor, targetBackgroundColor);

                    // Create a new PDPage from the modified image
                    PDPage pdPage = document.getPage(pageIndex);
                    File tempImageFile = null;
                    try {
                        tempImageFile = convertToBufferedImageToFile(image);
                        PDImageXObject pdImage =
                                PDImageXObject.createFromFileByContent(tempImageFile, document);

                        try (PDPageContentStream contentStream =
                                new PDPageContentStream(
                                        document,
                                        pdPage,
                                        PDPageContentStream.AppendMode.OVERWRITE,
                                        true)) {
                            contentStream.drawImage(
                                    pdImage,
                                    0,
                                    0,
                                    pdPage.getMediaBox().getWidth(),
                                    pdPage.getMediaBox().getHeight());
                        }
                    } finally {
                        if (tempImageFile != null && tempImageFile.exists()) {
                            Files.delete(tempImageFile.toPath());
                        }
                    }
                }

                // Save the modified PDF to a ByteArrayOutputStream
                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                document.save(byteArrayOutputStream);

                // Prepare the modified PDF for download
                ByteArrayInputStream inputStream =
                        new ByteArrayInputStream(byteArrayOutputStream.toByteArray());
                return new InputStreamResource(inputStream);
            }
        } finally {
            try {
                Files.deleteIfExists(file.toPath());
            } catch (IOException e) {
                log.warn("Failed to delete temporary file: {}", file.getAbsolutePath(), e);
            }
        }
    }

    /**
     * Replace colors in the image by detecting dark/light pixels and replacing them with the target
     * text/background colors.
     */
    private void replaceColors(BufferedImage image, Color textColor, Color backgroundColor) {
        int width = image.getWidth();
        int height = image.getHeight();

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int pixel = image.getRGB(x, y);

                // Extract alpha and RGB components
                int alpha = (pixel >> 24) & 0xff;
                int red = (pixel >> 16) & 0xff;
                int green = (pixel >> 8) & 0xff;
                int blue = pixel & 0xff;

                // Calculate brightness (perceived luminance)
                double brightness = (0.299 * red + 0.587 * green + 0.114 * blue);

                // Determine if this pixel is "dark" (text) or "light" (background)
                // Using a threshold of 128 (middle gray)
                Color replacementColor;
                if (brightness < 128) {
                    // Dark pixel -> replace with text color
                    replacementColor = textColor;
                } else {
                    // Light pixel -> replace with background color
                    replacementColor = backgroundColor;
                }

                // Preserve alpha channel
                int newPixel =
                        (alpha << 24)
                                | (replacementColor.getRed() << 16)
                                | (replacementColor.getGreen() << 8)
                                | replacementColor.getBlue();

                image.setRGB(x, y, newPixel);
            }
        }
    }

    // Helper method to convert BufferedImage to File
    private File convertToBufferedImageToFile(BufferedImage image) throws IOException {
        File file = File.createTempFile("image", ".png");
        ImageIO.write(image, "png", file);
        return file;
    }
}
