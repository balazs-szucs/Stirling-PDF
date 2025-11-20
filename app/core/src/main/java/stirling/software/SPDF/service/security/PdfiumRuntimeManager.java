package stirling.software.SPDF.service.security;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.FileSystemUtils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import stirling.software.common.configuration.InstallationPathConfig;

@Slf4j
@Component
@RequiredArgsConstructor
public class PdfiumRuntimeManager {

    private static final String RUNTIME_ARCHIVE = "static/pdfium/pdfium-redactor.zip";
    private static final String RUNTIME_FOLDER = "pdfium-redactor";
    private static final String SCRIPT_NAME = "pdfium-redactor.mjs";

    private final Object extractionLock = new Object();
    private Path resolvedRuntimeDir;

    public Path ensureRuntime() throws IOException {
        if (isRuntimeReady()) {
            return resolvedRuntimeDir;
        }
        synchronized (extractionLock) {
            if (isRuntimeReady()) {
                return resolvedRuntimeDir;
            }
            extractRuntimeArchive();
            if (!isRuntimeReady()) {
                throw new IOException("Failed to extract embedded PDFium runtime");
            }
            return resolvedRuntimeDir;
        }
    }

    private boolean isRuntimeReady() {
        return resolvedRuntimeDir != null
                && Files.exists(resolvedRuntimeDir.resolve(SCRIPT_NAME))
                && Files.isDirectory(resolvedRuntimeDir);
    }

    private void extractRuntimeArchive() throws IOException {
        ClassPathResource resource = new ClassPathResource(RUNTIME_ARCHIVE);
        if (!resource.exists()) {
            throw new IOException("Embedded PDFium runtime archive not found: " + RUNTIME_ARCHIVE);
        }

        Path baseDir = Paths.get(InstallationPathConfig.getCustomFilesPath(), "bin");
        Files.createDirectories(baseDir);

        Path existingRuntime = baseDir.resolve(RUNTIME_FOLDER);
        FileSystemUtils.deleteRecursively(existingRuntime);

        try (InputStream in = resource.getInputStream();
                ZipInputStream zipInputStream = new ZipInputStream(in)) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                Path targetPath = baseDir.resolve(entry.getName()).normalize();
                if (!targetPath.startsWith(baseDir)) {
                    throw new IOException("Zip entry outside target dir: " + entry.getName());
                }

                if (entry.isDirectory()) {
                    Files.createDirectories(targetPath);
                } else {
                    Files.createDirectories(targetPath.getParent());
                    Files.copy(zipInputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
                    if (targetPath.getFileName().toString().endsWith(".mjs")) {
                        targetPath.toFile().setExecutable(true, true);
                    }
                }
                zipInputStream.closeEntry();
            }
        }

        Path candidate = baseDir.resolve(RUNTIME_FOLDER);
        if (Files.exists(candidate) && Files.isDirectory(candidate)) {
            resolvedRuntimeDir = candidate;
        } else {
            resolvedRuntimeDir = existingRuntime;
        }
        log.info("Extracted PDFium runtime to {}", resolvedRuntimeDir);
    }
}
