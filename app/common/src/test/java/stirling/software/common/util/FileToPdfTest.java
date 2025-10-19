package stirling.software.common.util;

import java.io.IOException;
import java.nio.file.Files;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

import stirling.software.common.model.api.converters.HTMLToPdfRequest;
import stirling.software.common.service.SsrfProtectionService;

public class FileToPdfTest {

    private CustomHtmlSanitizer customHtmlSanitizer;

    @BeforeEach
    void setUp() {
        SsrfProtectionService mockSsrfProtectionService = Mockito.mock(SsrfProtectionService.class);
        stirling.software.common.model.ApplicationProperties mockApplicationProperties =
                Mockito.mock(stirling.software.common.model.ApplicationProperties.class);
        stirling.software.common.model.ApplicationProperties.System mockSystem =
                Mockito.mock(stirling.software.common.model.ApplicationProperties.System.class);

        Mockito.when(
                        mockSsrfProtectionService.isUrlAllowed(
                                org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(true);
        Mockito.when(mockApplicationProperties.getSystem()).thenReturn(mockSystem);
        Mockito.when(mockSystem.getDisableSanitize()).thenReturn(false);

        customHtmlSanitizer =
                new CustomHtmlSanitizer(mockSsrfProtectionService, mockApplicationProperties);
    }

    /**
     * Test the HTML to PDF conversion. This test expects an IOException when an empty HTML input is
     * provided.
     */
    @Test
    public void testConvertHtmlToPdf() {
        HTMLToPdfRequest request = new HTMLToPdfRequest();
        byte[] fileBytes = new byte[0]; // Sample file bytes (empty input)
        String fileName = "test.html"; // Sample file name indicating an HTML file
        TempFileManager tempFileManager =
                Mockito.mock(TempFileManager.class); // Mock TempFileManager

        // Mock the temp file creation to return real temp files
        try {
            Mockito.when(tempFileManager.createTempFile(ArgumentMatchers.anyString()))
                    .thenReturn(Files.createTempFile("test", ".pdf").toFile())
                    .thenReturn(Files.createTempFile("test", ".html").toFile());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        // Expect an IOException to be thrown due to empty input or invalid weasyprint path
        Throwable thrown =
                Assertions.assertThrows(
                        Exception.class,
                        () ->
                                FileToPdf.convertHtmlToPdf(
                                        "/path/",
                                        request,
                                        fileBytes,
                                        fileName,
                                        tempFileManager,
                                        customHtmlSanitizer));
        Assertions.assertNotNull(thrown);
    }

    /**
     * Test sanitizeZipFilename with null or empty input. It should return an empty string in these
     * cases.
     */
    @Test
    public void testSanitizeZipFilename_NullOrEmpty() {
        Assertions.assertEquals("", FileToPdf.sanitizeZipFilename(null));
        Assertions.assertEquals("", FileToPdf.sanitizeZipFilename("   "));
    }

    /**
     * Test sanitizeZipFilename to ensure it removes path traversal sequences. This includes
     * removing both forward and backward slash sequences.
     */
    @Test
    public void testSanitizeZipFilename_RemovesTraversalSequences() {
        String input = "../some/../path/..\\to\\file.txt";
        String expected = "some/path/to/file.txt";

        // Expect that the method replaces backslashes with forward slashes
        // and removes path traversal sequences
        Assertions.assertEquals(expected, FileToPdf.sanitizeZipFilename(input));
    }

    /** Test sanitizeZipFilename to ensure that it removes leading drive letters and slashes. */
    @Test
    public void testSanitizeZipFilename_RemovesLeadingDriveAndSlashes() {
        String input = "C:\\folder\\file.txt";
        String expected = "folder/file.txt";
        Assertions.assertEquals(expected, FileToPdf.sanitizeZipFilename(input));

        input = "/folder/file.txt";
        expected = "folder/file.txt";
        Assertions.assertEquals(expected, FileToPdf.sanitizeZipFilename(input));
    }

    /** Test sanitizeZipFilename to verify that safe filenames remain unchanged. */
    @Test
    public void testSanitizeZipFilename_NoChangeForSafeNames() {
        String input = "folder/subfolder/file.txt";
        Assertions.assertEquals(input, FileToPdf.sanitizeZipFilename(input));
    }
}
