package stirling.software.common.util.misc;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

import javax.imageio.ImageIO;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDFontFactory;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.text.TextPosition;
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

        // Create a temporary file, with the original filename from the multipart file
        File tempFile = Files.createTempFile("temp", getFileInput().getOriginalFilename()).toFile();

        // Transfer the content of the multipart file to the file
        getFileInput().transferTo(tempFile);

        try (PDDocument document = Loader.loadPDF(tempFile)) {

            int renderDpi = 300;
            ApplicationProperties properties =
                    ApplicationContextProvider.getBean(ApplicationProperties.class);
            if (properties != null && properties.getSystem() != null) {
                renderDpi = properties.getSystem().getMaxDPI();
            }
            final int dpi = renderDpi;

            for (int pageNum = 0; pageNum < document.getNumberOfPages(); pageNum++) {
                PDPage pdPage = document.getPage(pageNum);
                final int currentPageNum = pageNum;
                final int currentDpi = dpi;

                // 1. Collect images and their positions
                ImageCollector imageCollector = new ImageCollector(pdPage);
                imageCollector.processPage(pdPage);
                List<ImageCollector.ImageInstance> images = imageCollector.getImages();

                // 2. Collect text positions (original colors ignored for now as we use fixed
                // textColor)
                PdfTextStripperCustom stripper = new PdfTextStripperCustom();
                List<List<TextPosition>> charactersByArticle = stripper.processPageCustom(pdPage);

                // 3. Render background (no text, no images)
                LayeredRenderer backgroundRenderer = new LayeredRenderer(document, true, true);
                BufferedImage backgroundImage =
                        ExceptionUtils.handleOomRendering(
                                pageNum + 1,
                                dpi,
                                () ->
                                        backgroundRenderer.renderImageWithDPI(
                                                currentPageNum, currentDpi));

                // Process background colors if needed, or just fill with backgroundColor
                // For now, let's keep the vector graphics but try to adjust them?
                // Actually, many users expect the background to be strictly the chosen color.
                // But we should draw the vector graphics on top of it or replace their color.

                // 4. Reconstruct page
                try (PDPageContentStream contentStream =
                        new PDPageContentStream(
                                document,
                                pdPage,
                                PDPageContentStream.AppendMode.OVERWRITE,
                                true,
                                true)) {

                    // Draw solid background color
                    contentStream.setNonStrokingColor(Color.decode(this.backgroundColor));
                    contentStream.addRect(
                            0,
                            0,
                            pdPage.getMediaBox().getWidth(),
                            pdPage.getMediaBox().getHeight());
                    contentStream.fill();

                    // Draw original vector graphics (background image) but inverted/processed if
                    // high contrast
                    // If background color is dark, maybe we should invert the vector graphics?
                    if (isDarkColor(Color.decode(this.backgroundColor))) {
                        invertImageColors(backgroundImage);
                    }
                    // Make background image transparent where it was white? Hard to do perfectly.
                    // For now, let's just draw it. If it's mostly white, it will cover our solid
                    // background.
                    // To avoid this, we can try to make white transparent.
                    BufferedImage transparentBg =
                            makeColorTransparent(backgroundImage, Color.WHITE);

                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    ImageIO.write(transparentBg, "png", baos);
                    PDImageXObject pdBackgroundImage =
                            PDImageXObject.createFromByteArray(
                                    document, baos.toByteArray(), "background-" + pageNum);
                    contentStream.drawImage(
                            pdBackgroundImage,
                            0,
                            0,
                            pdPage.getMediaBox().getWidth(),
                            pdPage.getMediaBox().getHeight());

                    // Draw original images
                    for (ImageCollector.ImageInstance instance : images) {
                        try {
                            BufferedImage img = instance.image.getImage();
                            if (img != null) {
                                // For high contrast, we might want to invert images too?
                                // User said "consistently applied to all layers".
                                if (replaceAndInvert == ReplaceAndInvert.HIGH_CONTRAST_COLOR
                                        && isDarkColor(Color.decode(this.backgroundColor))) {
                                    invertImageColors(img);
                                }
                                ByteArrayOutputStream imgBaos = new ByteArrayOutputStream();
                                ImageIO.write(img, "png", imgBaos);
                                PDImageXObject pdImg =
                                        PDImageXObject.createFromByteArray(
                                                document, imgBaos.toByteArray(), null);
                                contentStream.drawImage(pdImg, instance.matrix);
                            }
                        } catch (Exception e) {
                            log.warn("Failed to re-draw an image on page {}", pageNum + 1, e);
                        }
                    }

                    // Set the new text color
                    contentStream.setNonStrokingColor(Color.decode(this.textColor));

                    // Draw the text with the new color
                    for (List<TextPosition> textPositions : charactersByArticle) {
                        for (TextPosition text : textPositions) {
                            contentStream.beginText();
                            contentStream.newLineAtOffset(
                                    text.getX(), pdPage.getMediaBox().getHeight() - text.getY());
                            PDFont font = null;
                            String unicodeText = text.getUnicode();
                            try {
                                font = PDFontFactory.createFont(text.getFont().getCOSObject());
                            } catch (IOException io) {
                                font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
                            }
                            try {
                                font.encode(unicodeText);
                            } catch (Exception e) {
                                font = checkSupportedFontForCharacter(unicodeText);
                            } finally {
                                if (font == null) {
                                    font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
                                    unicodeText = "*";
                                }
                            }
                            contentStream.setFont(font, text.getFontSize());
                            contentStream.showText(unicodeText);
                            contentStream.endText();
                        }
                    }
                }
            }

            // Save the modified PDF to a ByteArrayOutputStream
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            document.save(byteArrayOutputStream);

            // Prepare the modified PDF for download
            ByteArrayInputStream inputStream =
                    new ByteArrayInputStream(byteArrayOutputStream.toByteArray());
            InputStreamResource resource = new InputStreamResource(inputStream);
            return resource;
        } finally {
            try {
                Files.deleteIfExists(tempFile.toPath());
            } catch (IOException e) {
                log.warn("Failed to delete temporary file: {}", tempFile.getAbsolutePath(), e);
            }
        }
    }

    private boolean isDarkColor(Color color) {
        // Simple luminance check
        double luminance =
                (0.299 * color.getRed() + 0.587 * color.getGreen() + 0.114 * color.getBlue()) / 255;
        return luminance < 0.5;
    }

    private BufferedImage makeColorTransparent(BufferedImage im, final Color color) {
        BufferedImage dest =
                new BufferedImage(im.getWidth(), im.getHeight(), BufferedImage.TYPE_INT_ARGB);
        int colorRGB = color.getRGB() & 0x00FFFFFF;
        for (int x = 0; x < im.getWidth(); x++) {
            for (int y = 0; y < im.getHeight(); y++) {
                int rgba = im.getRGB(x, y);
                if ((rgba & 0x00FFFFFF) == colorRGB) {
                    dest.setRGB(x, y, 0x00FFFFFF & rgba);
                } else {
                    dest.setRGB(x, y, rgba);
                }
            }
        }
        return dest;
    }
}
