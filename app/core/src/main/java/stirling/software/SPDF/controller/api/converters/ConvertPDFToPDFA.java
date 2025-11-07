package stirling.software.SPDF.controller.api.converters;

import java.awt.color.ColorSpace;
import java.awt.color.ICC_Profile;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.pdfbox.io.RandomAccessRead;
import org.apache.pdfbox.io.RandomAccessReadBufferedFile;
import org.apache.pdfbox.preflight.Format;
import org.apache.pdfbox.preflight.PreflightConfiguration;
import org.apache.pdfbox.preflight.PreflightDocument;
import org.apache.pdfbox.preflight.ValidationResult;
import org.apache.pdfbox.preflight.ValidationResult.ValidationError;
import org.apache.pdfbox.preflight.exception.SyntaxValidationException;
import org.apache.pdfbox.preflight.exception.ValidationException;
import org.apache.pdfbox.preflight.parser.PreflightParser;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import io.github.pixee.security.Filenames;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import lombok.extern.slf4j.Slf4j;

import stirling.software.SPDF.model.api.converters.PdfToPdfARequest;
import stirling.software.common.util.ExceptionUtils;
import stirling.software.common.util.ProcessExecutor;
import stirling.software.common.util.ProcessExecutor.ProcessExecutorResult;
import stirling.software.common.util.WebResponseUtils;

@RestController
@RequestMapping("/api/v1/convert")
@Slf4j
@Tag(name = "Convert", description = "Convert APIs")
public class ConvertPDFToPDFA {

    private static final String ICC_RESOURCE_PATH = "/icc/sRGB2014.icc";
    private static final int PDFA_COMPATIBILITY_POLICY = 1;

    private static List<String> buildGhostscriptCommand(
            Path inputPdf,
            Path outputPdf,
            ColorProfiles colorProfiles,
            Path workingDir,
            PdfaProfile profile) {

        List<String> command = new ArrayList<>();
        command.add("gs");
        command.add("--permit-file-read=" + workingDir.toAbsolutePath());
        command.add("--permit-file-read=" + colorProfiles.rgb().toAbsolutePath());
        command.add("--permit-file-read=" + colorProfiles.gray().toAbsolutePath());
        command.add("--permit-file-read=" + inputPdf.toAbsolutePath());
        command.add("--permit-file-write=" + workingDir.toAbsolutePath());
        command.add("-sDEVICE=pdfwrite");
        command.add("-dPDFA=" + profile.part());
        command.add("-dPDFACompatibilityPolicy=" + PDFA_COMPATIBILITY_POLICY);
        command.add("-dCompatibilityLevel=" + profile.compatibilityLevel());
        command.add("-sColorConversionStrategy=RGB");
        command.add("-sProcessColorModel=DeviceRGB");
        command.add("-sOutputICCProfile=" + colorProfiles.rgb().toAbsolutePath());
        command.add("-sDefaultRGBProfile=" + colorProfiles.rgb().toAbsolutePath());
        command.add("-sDefaultGrayProfile=" + colorProfiles.gray().toAbsolutePath());
        command.add("-dEmbedAllFonts=true");
        command.add("-dSubsetFonts=true");
        command.add("-dCompressFonts=true");
        command.add("-dNOPAUSE");
        command.add("-dBATCH");
        command.add("-sOutputFile=" + outputPdf.toAbsolutePath());
        command.add(inputPdf.toAbsolutePath().toString());

        return command;
    }

    private static void validatePdfaOutput(Path pdfPath, PdfaProfile profile) throws IOException {
        Optional<Format> format = profile.preflightFormat();
        if (format.isEmpty()) {
            log.debug("Skipping PDFBox preflight validation for {}", profile.displayName());
            return;
        }

        try (RandomAccessRead rar = new RandomAccessReadBufferedFile(pdfPath.toFile())) {
            PreflightParser parser = new PreflightParser(rar);
            PreflightDocument document;
            try {
                document =
                        (PreflightDocument)
                                parser.parse(
                                        format.get(),
                                        PreflightConfiguration.createPdfA1BConfiguration());
            } catch (SyntaxValidationException e) {
                throw new IOException(buildPreflightErrorMessage(e.getResult(), profile), e);
            } catch (ClassCastException e) {
                throw new IOException(
                        "PDF/A preflight did not produce a PreflightDocument for "
                                + profile.displayName(),
                        e);
            }

            if (document == null) {
                throw new IOException(
                        "PDF/A preflight returned no document for " + profile.displayName());
            }

            try (PreflightDocument closeableDocument = document) {
                ValidationResult result = closeableDocument.validate();
                if (result == null || !result.isValid()) {
                    throw new IOException(buildPreflightErrorMessage(result, profile));
                }
            }
        } catch (SyntaxValidationException e) {
            throw new IOException(buildPreflightErrorMessage(e.getResult(), profile), e);
        } catch (ValidationException e) {
            throw new IOException(
                    "PDF/A preflight validation failed for " + profile.displayName(), e);
        }
    }

    private static String buildPreflightErrorMessage(ValidationResult result, PdfaProfile profile) {
        String baseMessage = "PDF/A preflight validation failed for " + profile.displayName();
        if (result == null) {
            return baseMessage + ": no detailed validation result available";
        }

        List<ValidationError> errors = result.getErrorsList();
        if (errors == null || errors.isEmpty()) {
            return baseMessage + ": unknown validation error";
        }

        String summarizedErrors =
                errors.stream()
                        .limit(5)
                        .map(
                                error -> {
                                    StringBuilder sb =
                                            new StringBuilder(
                                                    Optional.ofNullable(error.getErrorCode())
                                                            .orElse("UNKNOWN"));
                                    String details = error.getDetails();
                                    if (details != null && !details.isBlank()) {
                                        sb.append(": ").append(details.trim());
                                    }
                                    if (error.isWarning()) {
                                        sb.append(" (warning)");
                                    }
                                    return sb.toString();
                                })
                        .collect(Collectors.joining("; "));

        if (errors.size() > 5) {
            summarizedErrors += " (" + (errors.size() - 5) + " more)";
        }

        return baseMessage + ": " + summarizedErrors;
    }

    private static void deleteQuietly(Path directory) {
        if (directory == null) {
            return;
        }
        try (Stream<Path> stream = Files.walk(directory)) {
            stream.sorted(Comparator.reverseOrder())
                    .forEach(
                            path -> {
                                try {
                                    Files.deleteIfExists(path);
                                } catch (IOException e) {
                                    log.warn("Failed to delete temporary file: {}", path, e);
                                }
                            });
        } catch (IOException e) {
            log.warn("Failed to clean temporary directory: {}", directory, e);
        }
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE, value = "/pdf/pdfa")
    @Operation(
            summary = "Convert a PDF to a PDF/A",
            description =
                    "This endpoint converts a PDF file to a PDF/A file using Ghostscript. PDF/A is a format designed for long-term archiving of digital documents. Input:PDF Output:PDF Type:SISO")
    public ResponseEntity<byte[]> pdfToPdfA(@ModelAttribute PdfToPdfARequest request)
            throws Exception {
        MultipartFile inputFile = request.getFileInput();
        String outputFormat = request.getOutputFormat();

        if (!MediaType.APPLICATION_PDF_VALUE.equals(inputFile.getContentType())) {
            log.error("Invalid input file type: {}", inputFile.getContentType());
            throw ExceptionUtils.createPdfFileRequiredException();
        }

        PdfaProfile profile = PdfaProfile.fromRequest(outputFormat);

        String originalFileName = Filenames.toSimpleFileName(inputFile.getOriginalFilename());
        if (originalFileName == null || originalFileName.trim().isEmpty()) {
            originalFileName = "output.pdf";
        }

        String baseFileName =
                originalFileName.contains(".")
                        ? originalFileName.substring(0, originalFileName.lastIndexOf('.'))
                        : originalFileName;

        Path workingDir = Files.createTempDirectory("pdfa_gs_");
        Path inputPath = workingDir.resolve("input.pdf");
        inputFile.transferTo(inputPath);

        try {
            byte[] converted = convertWithGhostscript(inputPath, workingDir, profile);
            String outputFilename = baseFileName + profile.outputSuffix();
            return WebResponseUtils.bytesToWebResponse(
                    converted, outputFilename, MediaType.APPLICATION_PDF);
        } catch (IOException | InterruptedException e) {
            log.error("Ghostscript PDF/A conversion failed", e);
            throw ExceptionUtils.createPdfaConversionFailedException(e);
        } finally {
            deleteQuietly(workingDir);
        }
    }

    private byte[] convertWithGhostscript(Path inputPdf, Path workingDir, PdfaProfile profile)
            throws IOException, InterruptedException {
    Path outputPdf = workingDir.resolve("output.pdf");
    ColorProfiles colorProfiles = prepareColorProfiles(workingDir);
    List<String> command =
        buildGhostscriptCommand(inputPdf, outputPdf, colorProfiles, workingDir, profile);

        ProcessExecutorResult result =
                ProcessExecutor.getInstance(ProcessExecutor.Processes.GHOSTSCRIPT)
                        .runCommandWithOutputHandling(command);

        if (result.getRc() != 0) {
            throw new IOException("Ghostscript exited with code " + result.getRc());
        }

        if (!Files.exists(outputPdf)) {
            throw new IOException("Ghostscript did not produce an output file");
        }

        validatePdfaOutput(outputPdf, profile);

        return Files.readAllBytes(outputPdf);
    }

    private ColorProfiles prepareColorProfiles(Path workingDir) throws IOException {
        Path rgbProfile = workingDir.resolve("sRGB.icc");
        copyResourceIcc(ICC_RESOURCE_PATH, rgbProfile);

        Path grayProfile = workingDir.resolve("Gray.icc");
        try {
            writeJavaIccProfile(ICC_Profile.getInstance(ColorSpace.CS_GRAY), grayProfile);
        } catch (IllegalArgumentException e) {
            log.warn("Falling back to sRGB ICC profile for grayscale defaults", e);
            Files.copy(rgbProfile, grayProfile, StandardCopyOption.REPLACE_EXISTING);
        }
        }

        return new ColorProfiles(rgbProfile, grayProfile);
    }

    private void copyResourceIcc(String resourcePath, Path target) throws IOException {
        try (InputStream in = getClass().getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IOException("ICC profile resource not found: " + resourcePath);
            }
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void writeJavaIccProfile(ICC_Profile profile, Path target) throws IOException {
        try (OutputStream out = Files.newOutputStream(target)) {
            out.write(profile.getData());
        }
    }

    private record ColorProfiles(Path rgb, Path gray) {}

    private enum PdfaProfile {
        PDF_A_1B(1, "PDF/A-1b", "_PDFA-1b.pdf", "1.4", Format.PDF_A1B, "pdfa-1"),
        PDF_A_2B(2, "PDF/A-2b", "_PDFA-2b.pdf", "1.7", null, "pdfa", "pdfa-2", "pdfa-2b"),
        PDF_A_3B(3, "PDF/A-3b", "_PDFA-3b.pdf", "1.7", null, "pdfa-3", "pdfa-3b");

        private final int part;
        private final String displayName;
        private final String suffix;
        private final String compatibilityLevel;
        private final Format preflightFormat;
        private final List<String> requestTokens;

        PdfaProfile(
                int part,
                String displayName,
                String suffix,
                String compatibilityLevel,
                Format preflightFormat,
                String... requestTokens) {
            this.part = part;
            this.displayName = displayName;
            this.suffix = suffix;
            this.compatibilityLevel = compatibilityLevel;
            this.preflightFormat = preflightFormat;
            this.requestTokens = Arrays.asList(requestTokens);
        }

        static PdfaProfile fromRequest(String requestToken) {
            if (requestToken == null) {
                return PDF_A_2B;
            }
            String normalized = requestToken.trim().toLowerCase(Locale.ROOT);
            Optional<PdfaProfile> match =
                    Arrays.stream(values())
                            .filter(
                                    profile ->
                                            profile.requestTokens.stream()
                                                    .map(token -> token.toLowerCase(Locale.ROOT))
                                                    .anyMatch(token -> token.equals(normalized)))
                            .findFirst();

        // Set creation and modification dates using java.time and convert to GregorianCalendar
        Instant nowInstant = Instant.now();
        ZonedDateTime nowZdt = ZonedDateTime.ofInstant(nowInstant, ZoneId.of("UTC"));
        GregorianCalendar nowCal = GregorianCalendar.from(nowZdt);

        java.util.Calendar originalCreationDate = docInfo.getCreationDate();
        GregorianCalendar creationCal;
        if (originalCreationDate == null) {
            creationCal = nowCal;
        } else if (originalCreationDate instanceof GregorianCalendar) {
            creationCal = (GregorianCalendar) originalCreationDate;
        } else {
            // convert other Calendar implementations to GregorianCalendar preserving instant
            creationCal =
                    GregorianCalendar.from(
                            ZonedDateTime.ofInstant(
                                    originalCreationDate.toInstant(), ZoneId.of("UTC")));
        }
        }
        docInfo.setCreationDate(creationCal);
        xmpBasicSchema.setCreateDate(creationCal);

        docInfo.setModificationDate(nowCal);
        xmpBasicSchema.setModifyDate(nowCal);
        xmpBasicSchema.setMetadataDate(nowCal);

        // Serialize the created metadata so it can be attached to the existent metadata
        ByteArrayOutputStream xmpOut = new ByteArrayOutputStream();
        new XmpSerializer().serialize(xmp, xmpOut, true);

        int part() {
            return part;
        }

        String displayName() {
            return displayName;
        }

        String outputSuffix() {
            return suffix;
        }

        String compatibilityLevel() {
            return compatibilityLevel;
        }

        Optional<Format> preflightFormat() {
            return Optional.ofNullable(preflightFormat);
        }
    }
}
