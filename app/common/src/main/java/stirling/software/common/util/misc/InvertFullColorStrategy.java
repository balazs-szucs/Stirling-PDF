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
import org.springframework.core.io.InputStreamResource;
import org.springframework.web.multipart.MultipartFile;

import lombok.extern.slf4j.Slf4j;

import stirling.software.common.model.ApplicationProperties;
import stirling.software.common.model.api.misc.ReplaceAndInvert;
import stirling.software.common.util.ApplicationContextProvider;
import stirling.software.common.util.ExceptionUtils;

@Slf4j
public class InvertFullColorStrategy extends ReplaceAndInvertColorStrategy {

    public InvertFullColorStrategy(MultipartFile file, ReplaceAndInvert replaceAndInvert) {
        super(file, replaceAndInvert);
    }

    @Override
    public InputStreamResource replace() throws IOException {
        try (TempFile tempFile =
                new TempFile(
                        Files.createTempFile("temp", getFileInput().getOriginalFilename())
                                .toFile())) {
            // Transfer the content of the multipart file to the file
            getFileInput().transferTo(tempFile.getFile());

            // Load the uploaded PDF
            try (PDDocument document = Loader.loadPDF(tempFile.getFile())) {
                // Render each page and invert colors
                int renderDpi = 300; // Default fallback
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

                    // 2. Collect text and colors
                    ColoredTextStripper stripper = new ColoredTextStripper();
                    stripper.setStartPage(pageNum + 1);
                    stripper.setEndPage(pageNum + 1);
                    stripper.getText(document);
                    List<ColoredTextStripper.ColoredText> texts = stripper.getColoredTexts();

                    // 3. Render background (no text, no images)
                    LayeredRenderer backgroundRenderer = new LayeredRenderer(document, true, true);
                    BufferedImage backgroundImage =
                            ExceptionUtils.handleOomRendering(
                                    pageNum + 1,
                                    dpi,
                                    () ->
                                            backgroundRenderer.renderImageWithDPI(
                                                    currentPageNum, currentDpi));
                    invertImageColors(backgroundImage);

                    // 4. Reconstruct page
                    try (PDPageContentStream contentStream =
                            new PDPageContentStream(
                                    document,
                                    pdPage,
                                    PDPageContentStream.AppendMode.OVERWRITE,
                                    true,
                                    true)) { // resetContext=true ensures clean graphics state

                        // Draw inverted background
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        ImageIO.write(backgroundImage, "png", baos);
                        PDImageXObject pdBackgroundImage =
                                PDImageXObject.createFromByteArray(
                                        document, baos.toByteArray(), "background-" + pageNum);
                        contentStream.drawImage(
                                pdBackgroundImage,
                                0,
                                0,
                                pdPage.getMediaBox().getWidth(),
                                pdPage.getMediaBox().getHeight());

                        // Draw inverted images
                        for (ImageCollector.ImageInstance instance : images) {
                            try {
                                BufferedImage img = instance.image.getImage();
                                if (img != null) {
                                    invertImageColors(img);
                                    ByteArrayOutputStream imgBaos = new ByteArrayOutputStream();
                                    ImageIO.write(img, "png", imgBaos);
                                    PDImageXObject pdImg =
                                            PDImageXObject.createFromByteArray(
                                                    document, imgBaos.toByteArray(), null);
                                    contentStream.drawImage(pdImg, instance.matrix);
                                }
                            } catch (Exception e) {
                                log.warn("Failed to invert an image on page {}", pageNum + 1, e);
                            }
                        }

                        // Draw inverted text
                        for (ColoredTextStripper.ColoredText ct : texts) {
                            try {
                                contentStream.beginText();
                                contentStream.newLineAtOffset(
                                        ct.textPosition.getX(),
                                        pdPage.getMediaBox().getHeight() - ct.textPosition.getY());

                                // Invert text color
                                Color invertedColor =
                                        new Color(
                                                255 - ct.color.getRed(),
                                                255 - ct.color.getGreen(),
                                                255 - ct.color.getBlue());
                                contentStream.setNonStrokingColor(invertedColor);

                                PDFont font = null;
                                String unicodeText = ct.textPosition.getUnicode();
                                try {
                                    font =
                                            PDFontFactory.createFont(
                                                    ct.textPosition.getFont().getCOSObject());
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

                                contentStream.setFont(font, ct.textPosition.getFontSize());
                                contentStream.showText(unicodeText);
                                contentStream.endText();
                            } catch (Exception e) {
                                log.warn("Failed to re-draw text on page {}", pageNum + 1, e);
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
            }
        }
    }

    private static class TempFile implements AutoCloseable {
        private final File file;

        public TempFile(File file) {
            this.file = file;
        }

        public File getFile() {
            return file;
        }

        @Override
        public void close() throws IOException {
            if (file != null && file.exists()) {
                Files.delete(file.toPath());
            }
        }
    }
}
