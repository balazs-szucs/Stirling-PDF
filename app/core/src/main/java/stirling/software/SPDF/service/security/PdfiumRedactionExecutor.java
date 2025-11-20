package stirling.software.SPDF.service.security;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

import stirling.software.common.util.ProcessExecutor;

@Slf4j
@Component
@RequiredArgsConstructor
public class PdfiumRedactionExecutor {

    private static final String NODE_BINARY = "node";

    private final PdfiumRuntimeManager runtimeManager;
    private final ObjectMapper objectMapper;

    public byte[] redactWithPdfium(
            Path inputPdf,
            String originalFilename,
            Map<Integer, List<PdfRedactionRect>> rectsByPage)
            throws IOException {

        if (rectsByPage == null || rectsByPage.isEmpty()) {
            return Files.readAllBytes(inputPdf);
        }

        Path runtimeDir = runtimeManager.ensureRuntime();
        Path scriptPath = runtimeDir.resolve("pdfium-redactor.mjs");
        if (!Files.exists(scriptPath)) {
            throw new IOException("PDFium redaction script not found at " + scriptPath);
        }

        Path outputFile = Files.createTempFile("pdfium-redacted-", ".pdf");
        Path configFile = Files.createTempFile("pdfium-redact-config-", ".json");

        try {
            PdfiumJobConfig jobConfig =
                    new PdfiumJobConfig(
                            inputPdf.toAbsolutePath().toString(),
                            outputFile.toAbsolutePath().toString(),
                            originalFilename,
                            rectsByPage,
                            new PdfiumOptions(true, false));
            objectMapper.writeValue(configFile.toFile(), jobConfig);

            List<String> command =
                    List.of(
                            NODE_BINARY,
                            scriptPath.toAbsolutePath().toString(),
                            configFile.toAbsolutePath().toString());

            ProcessExecutor executor =
                    ProcessExecutor.getInstance(
                            ProcessExecutor.Processes.PDFIUM_REDACTOR, /* liveUpdates */ false);
            try {
                executor.runCommandWithOutputHandling(command, runtimeDir.toFile());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while executing PDFium redactor", e);
            }
            return Files.readAllBytes(outputFile);
        } finally {
            safelyDelete(configFile);
            safelyDelete(outputFile);
        }
    }

    private void safelyDelete(Path file) {
        if (file == null) {
            return;
        }
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            log.debug("Failed to delete temp file {}: {}", file, e.getMessage());
        }
    }

    public record PdfRedactionRect(double x1, double y1, double x2, double y2) {}

    @Value
    private static class PdfiumJobConfig {
        String inputPath;
        String outputPath;
        String fileName;
        Map<Integer, List<PdfRedactionRect>> rectsByPage;
        PdfiumOptions options;
    }

    @Value
    private static class PdfiumOptions {
        boolean recurseForms;
        boolean drawBlackBoxes;
    }
}
