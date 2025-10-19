package stirling.software.SPDF.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

class AttachmentServiceTest {

    private AttachmentService attachmentService;

    @BeforeEach
    void setUp() {
        attachmentService = new AttachmentService();
    }

    @Test
    void addAttachmentToPDF() throws IOException {
        try (var document = new PDDocument()) {
            document.setDocumentId(100L);
            var attachments = List.of(Mockito.mock(MultipartFile.class));

            Mockito.when(attachments.get(0).getOriginalFilename()).thenReturn("test.txt");
            Mockito.when(attachments.get(0).getInputStream())
                    .thenReturn(new ByteArrayInputStream("Test content".getBytes()));
            Mockito.when(attachments.get(0).getSize()).thenReturn(12L);
            Mockito.when(attachments.get(0).getContentType()).thenReturn("text/plain");

            PDDocument result = attachmentService.addAttachment(document, attachments);

            Assertions.assertNotNull(result);
            Assertions.assertEquals(document.getDocumentId(), result.getDocumentId());
            Assertions.assertNotNull(result.getDocumentCatalog().getNames());
        }
    }

    @Test
    void addAttachmentToPDF_MultipleAttachments() throws IOException {
        try (var document = new PDDocument()) {
            document.setDocumentId(100L);
            var attachment1 = Mockito.mock(MultipartFile.class);
            var attachment2 = Mockito.mock(MultipartFile.class);
            var attachments = List.of(attachment1, attachment2);

            Mockito.when(attachment1.getOriginalFilename()).thenReturn("document.pdf");
            Mockito.when(attachment1.getInputStream())
                    .thenReturn(new ByteArrayInputStream("PDF content".getBytes()));
            Mockito.when(attachment1.getSize()).thenReturn(15L);
            Mockito.when(attachment1.getContentType()).thenReturn(MediaType.APPLICATION_PDF_VALUE);

            Mockito.when(attachment2.getOriginalFilename()).thenReturn("image.jpg");
            Mockito.when(attachment2.getInputStream())
                    .thenReturn(new ByteArrayInputStream("Image content".getBytes()));
            Mockito.when(attachment2.getSize()).thenReturn(20L);
            Mockito.when(attachment2.getContentType()).thenReturn(MediaType.IMAGE_JPEG_VALUE);

            PDDocument result = attachmentService.addAttachment(document, attachments);

            Assertions.assertNotNull(result);
            Assertions.assertNotNull(result.getDocumentCatalog().getNames());
        }
    }

    @Test
    void addAttachmentToPDF_WithBlankContentType() throws IOException {
        try (var document = new PDDocument()) {
            document.setDocumentId(100L);
            var attachments = List.of(Mockito.mock(MultipartFile.class));

            Mockito.when(attachments.get(0).getOriginalFilename()).thenReturn("image.jpg");
            Mockito.when(attachments.get(0).getInputStream())
                    .thenReturn(new ByteArrayInputStream("Image content".getBytes()));
            Mockito.when(attachments.get(0).getSize()).thenReturn(25L);
            Mockito.when(attachments.get(0).getContentType()).thenReturn("");

            PDDocument result = attachmentService.addAttachment(document, attachments);

            Assertions.assertNotNull(result);
            Assertions.assertNotNull(result.getDocumentCatalog().getNames());
        }
    }

    @Test
    void addAttachmentToPDF_AttachmentInputStreamThrowsIOException() throws IOException {
        try (var document = new PDDocument()) {
            var attachments = List.of(Mockito.mock(MultipartFile.class));
            var ioException = new IOException("Failed to read attachment stream");

            Mockito.when(attachments.get(0).getOriginalFilename()).thenReturn("test.txt");
            Mockito.when(attachments.get(0).getInputStream()).thenThrow(ioException);
            Mockito.when(attachments.get(0).getSize()).thenReturn(10L);

            PDDocument result = attachmentService.addAttachment(document, attachments);

            Assertions.assertNotNull(result);
            Assertions.assertNotNull(result.getDocumentCatalog().getNames());
        }
    }

    @Test
    void extractAttachments_SanitizesFilenamesAndExtractsData() throws IOException {
        attachmentService = new AttachmentService(1024 * 1024, 5 * 1024 * 1024);

        try (var document = new PDDocument()) {
            var maliciousAttachment =
                    new MockMultipartFile(
                            "file",
                            "..\\evil/../../tricky.txt",
                            MediaType.TEXT_PLAIN_VALUE,
                            "danger".getBytes());

            attachmentService.addAttachment(document, List.of(maliciousAttachment));

            Optional<byte[]> extracted = attachmentService.extractAttachments(document);
            Assertions.assertTrue(extracted.isPresent());

            try (var zipInputStream =
                    new ZipInputStream(new ByteArrayInputStream(extracted.get()))) {
                ZipEntry entry = zipInputStream.getNextEntry();
                Assertions.assertNotNull(entry);
                String sanitizedName = entry.getName();

                Assertions.assertFalse(sanitizedName.contains(".."));
                Assertions.assertFalse(sanitizedName.contains("/"));
                Assertions.assertFalse(sanitizedName.contains("\\"));

                byte[] data = zipInputStream.readAllBytes();
                Assertions.assertArrayEquals("danger".getBytes(), data);
                Assertions.assertNull(zipInputStream.getNextEntry());
            }
        }
    }

    @Test
    void extractAttachments_SkipsAttachmentsExceedingSizeLimit() throws IOException {
        attachmentService = new AttachmentService(4, 10);

        try (var document = new PDDocument()) {
            var oversizedAttachment =
                    new MockMultipartFile(
                            "file",
                            "large.bin",
                            MediaType.APPLICATION_OCTET_STREAM_VALUE,
                            "too big".getBytes());

            attachmentService.addAttachment(document, List.of(oversizedAttachment));

            Optional<byte[]> extracted = attachmentService.extractAttachments(document);
            Assertions.assertTrue(extracted.isEmpty());
        }
    }

    @Test
    void extractAttachments_EnforcesTotalSizeLimit() throws IOException {
        attachmentService = new AttachmentService(10, 9);

        try (var document = new PDDocument()) {
            var first =
                    new MockMultipartFile(
                            "file", "first.txt", MediaType.TEXT_PLAIN_VALUE, "12345".getBytes());
            var second =
                    new MockMultipartFile(
                            "file", "second.txt", MediaType.TEXT_PLAIN_VALUE, "67890".getBytes());

            attachmentService.addAttachment(document, List.of(first, second));

            Optional<byte[]> extracted = attachmentService.extractAttachments(document);
            Assertions.assertTrue(extracted.isPresent());

            try (var zipInputStream =
                    new ZipInputStream(new ByteArrayInputStream(extracted.get()))) {
                ZipEntry firstEntry = zipInputStream.getNextEntry();
                Assertions.assertNotNull(firstEntry);
                Assertions.assertEquals("first.txt", firstEntry.getName());
                byte[] firstData = zipInputStream.readNBytes(5);
                Assertions.assertArrayEquals("12345".getBytes(), firstData);
                Assertions.assertNull(zipInputStream.getNextEntry());
            }
        }
    }
}
