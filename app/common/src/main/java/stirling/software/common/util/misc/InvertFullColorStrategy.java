package stirling.software.common.util.misc;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.cos.COSFloat;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSNumber;
import org.apache.pdfbox.pdfparser.PDFStreamParser;
import org.apache.pdfbox.pdfwriter.ContentStreamWriter;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.PDStream;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.core.io.InputStreamResource;
import org.springframework.web.multipart.MultipartFile;

import stirling.software.common.model.ApplicationProperties;
import stirling.software.common.model.api.misc.ReplaceAndInvert;
import stirling.software.common.util.ApplicationContextProvider;
import stirling.software.common.util.ExceptionUtils;

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
                PDFRenderer pdfRenderer = new PDFRenderer(document);
                for (int pageIndex = 0; pageIndex < document.getNumberOfPages(); pageIndex++) {
                    PDPage page = document.getPage(pageIndex);

                    // Render the page to an image (captures everything including text)
                    int renderDpi = 150;
                    ApplicationProperties properties =
                            ApplicationContextProvider.getBean(ApplicationProperties.class);
                    if (properties != null && properties.getSystem() != null) {
                        renderDpi = properties.getSystem().getMaxDPI();
                    }

                    final int finalPageIndex = pageIndex;
                    final int finalRenderDpi = renderDpi;

                    BufferedImage image =
                            ExceptionUtils.handleOomRendering(
                                    finalPageIndex + 1,
                                    finalRenderDpi,
                                    () ->
                                            pdfRenderer.renderImageWithDPI(
                                                    finalPageIndex, finalRenderDpi));

                    // Invert the colors of the rendered image (Background)
                    invertImageColors(image);

                    File tempImageFile = null;
                    try {
                        tempImageFile = convertToBufferedImageTpFile(image);
                        PDImageXObject bgImage =
                                PDImageXObject.createFromFileByContent(tempImageFile, document);

                        // Process the page: Filter original content (remove images, invert text)
                        // and overlay on new background
                        processPage(document, page, bgImage);
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
                InputStreamResource resource = new InputStreamResource(inputStream);
                return resource;
            }
        }
    }

    private void processPage(PDDocument document, PDPage page, PDImageXObject bgImage)
            throws IOException {
        PDResources resources = page.getResources();
        processResources(document, resources);

        // Parse and filter original tokens
        PDFStreamParser parser = new PDFStreamParser(page);
        List<Object> originalTokens = parser.parse();
        List<Object> filteredTokens = filterTokens(originalTokens, resources);

        // 1. Draw Background Image on cleared page
        try (PDPageContentStream contentStream =
                new PDPageContentStream(
                        document, page, PDPageContentStream.AppendMode.OVERWRITE, true)) {
            contentStream.drawImage(
                    bgImage, 0, 0, page.getMediaBox().getWidth(), page.getMediaBox().getHeight());
        }

        // 2. Append filtered (text) tokens
        // We get the current content stream (which now has just the image) and append our tokens

        // Create a new stream that combines Background + Text Layer
        PDStream combinedStream = new PDStream(document);
        try (OutputStream out = combinedStream.createOutputStream()) {
            ContentStreamWriter writer = new ContentStreamWriter(out);

            // Write background ops
            PDFStreamParser bgParser = new PDFStreamParser(page);
            writer.writeTokens(bgParser.parse());

            // Write filtered text/vector ops
            writer.writeTokens(filteredTokens);
        }

        page.setContents(combinedStream);
    }

    private void processResources(PDDocument document, PDResources resources) throws IOException {
        if (resources == null) return;
        for (COSName name : resources.getXObjectNames()) {
            PDXObject xObject;
            try {
                xObject = resources.getXObject(name);
            } catch (IOException e) {
                continue; // Skip if issues
            }

            if (xObject instanceof PDFormXObject) {
                PDFormXObject form = (PDFormXObject) xObject;
                processForm(document, form);
            }
        }
    }

    private void processForm(PDDocument document, PDFormXObject form) throws IOException {
        processResources(document, form.getResources());

        PDFStreamParser parser = new PDFStreamParser(form);
        List<Object> tokens = parser.parse();
        List<Object> newTokens = filterTokens(tokens, form.getResources());

        try (OutputStream out = (form.getCOSObject()).createOutputStream()) {
            ContentStreamWriter writer = new ContentStreamWriter(out);
            writer.writeTokens(newTokens);
        }
    }

    private List<Object> filterTokens(List<Object> tokens, PDResources resources)
            throws IOException {
        List<Object> newTokens = new ArrayList<>();

        for (int i = 0; i < tokens.size(); i++) {
            Object token = tokens.get(i);

            if (token instanceof Operator) {
                Operator op = (Operator) token;
                String opName = op.getName();

                // Remove Image XObjects
                if ("Do".equals(opName)) {
                    if (newTokens.size() > 0
                            && newTokens.get(newTokens.size() - 1) instanceof COSName) {
                        COSName xName = (COSName) newTokens.get(newTokens.size() - 1);
                        PDXObject xObject =
                                (resources != null) ? resources.getXObject(xName) : null;

                        if (xObject instanceof PDImageXObject) {
                            // Remove the operand (xName) we just added
                            newTokens.remove(newTokens.size() - 1);
                            continue; // Skip adding the 'Do' operator
                        }
                    }
                }

                // Invert Colors (Stroking and Non-Stroking for RGB, Gray, CMYK)
                // Operators: g (Generic Gray), rg (RGB), k (CMYK) - Non-Stroking
                //            G (Generic Gray), RG (RGB), K (CMYK) - Stroking
                if ("g".equals(opName) || "G".equals(opName)) {
                    invertGray(newTokens);
                } else if ("rg".equals(opName) || "RG".equals(opName)) {
                    invertRGB(newTokens);
                } else if ("k".equals(opName) || "K".equals(opName)) {
                    invertCMYK(newTokens);
                }
            }
            newTokens.add(token);
        }
        return newTokens;
    }

    private void invertGray(List<Object> tokens) {
        if (tokens.isEmpty()) return;
        Object top = tokens.get(tokens.size() - 1);
        if (top instanceof COSNumber) {
            float val = ((COSNumber) top).floatValue();
            tokens.set(tokens.size() - 1, new COSFloat(1f - val));
        }
    }

    private void invertRGB(List<Object> tokens) {
        if (tokens.size() < 3) return;
        for (int i = 1; i <= 3; i++) {
            Object obj = tokens.get(tokens.size() - i);
            if (obj instanceof COSNumber) {
                float val = ((COSNumber) obj).floatValue();
                tokens.set(tokens.size() - i, new COSFloat(1f - val));
            }
        }
    }

    private void invertCMYK(List<Object> tokens) {
        if (tokens.size() < 4) return;
        // In CMYK, 0 is white, 1 is black? No, 0 is zero ink.
        // If we want to invert visual color:
        // C' = 1 - C, M' = 1 - M, Y' = 1 - Y, K' = 1 - K?
        for (int i = 1; i <= 4; i++) {
            Object obj = tokens.get(tokens.size() - i);
            if (obj instanceof COSNumber) {
                float val = ((COSNumber) obj).floatValue();
                tokens.set(tokens.size() - i, new COSFloat(1f - val));
            }
        }
    }

    // Method to invert image colors
    private void invertImageColors(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        int[] pixels = new int[width * height];
        image.getRGB(0, 0, width, height, pixels, 0, width);
        for (int i = 0; i < pixels.length; i++) {
            int pixel = pixels[i];

            int a = 0xff;

            int r = (pixel >> 16) & 0xff;
            int g = (pixel >> 8) & 0xff;
            int b = pixel & 0xff;

            pixels[i] = (a << 24) | ((255 - r) << 16) | ((255 - g) << 8) | (255 - b);
        }
        image.setRGB(0, 0, width, height, pixels, 0, width);
    }

    // Helper method to convert BufferedImage to InputStream
    private File convertToBufferedImageTpFile(BufferedImage image) throws IOException {
        File file = File.createTempFile("image", ".png");
        ImageIO.write(image, "png", file);
        return file;
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
