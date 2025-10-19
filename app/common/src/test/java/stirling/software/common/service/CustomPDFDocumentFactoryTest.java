package stirling.software.common.service;

import java.io.*;
import java.nio.file.*;
import java.util.Arrays;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDStream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import stirling.software.common.model.api.PDFFile;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Execution(value = ExecutionMode.SAME_THREAD)
class CustomPDFDocumentFactoryTest {

    private SpyPDFDocumentFactory factory;
    private byte[] basePdfBytes;

    private static byte[] inflatePdf(byte[] input, int sizeInMB) throws IOException {
        try (PDDocument doc = Loader.loadPDF(input)) {
            byte[] largeData = new byte[sizeInMB * 1024 * 1024];
            Arrays.fill(largeData, (byte) 'A');

            PDStream stream = new PDStream(doc, new ByteArrayInputStream(largeData));
            stream.getCOSObject().setItem(COSName.TYPE, COSName.XOBJECT);
            stream.getCOSObject().setItem(COSName.SUBTYPE, COSName.IMAGE);

            doc.getDocumentCatalog()
                    .getCOSObject()
                    .setItem(COSName.getPDFName("DummyBigStream"), stream.getCOSObject());

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        }
    }

    private static File writeTempFile(byte[] content) throws IOException {
        File file = Files.createTempFile("pdf-test-", ".pdf").toFile();
        Files.write(file.toPath(), content);
        return file;
    }

    @BeforeEach
    void setup() throws IOException {
        PdfMetadataService mockService = Mockito.mock(PdfMetadataService.class);
        factory = new SpyPDFDocumentFactory(mockService);

        try (InputStream is = getClass().getResourceAsStream("/example.pdf")) {
            Assertions.assertNotNull(is, "example.pdf must be present in src/test/resources");
            basePdfBytes = is.readAllBytes();
        }
    }

    @ParameterizedTest
    @CsvSource({"5,MEMORY_ONLY", "20,MIXED", "60,TEMP_FILE"})
    void testStrategy_FileInput(int sizeMB, SpyPDFDocumentFactory.StrategyType expected)
            throws IOException {
        File file = writeTempFile(inflatePdf(basePdfBytes, sizeMB));
        try (PDDocument doc = factory.load(file)) {
            Assertions.assertEquals(expected, factory.lastStrategyUsed);
        }
    }

    @ParameterizedTest
    @CsvSource({"5,MEMORY_ONLY", "20,MIXED", "60,TEMP_FILE"})
    void testStrategy_ByteArray(int sizeMB, SpyPDFDocumentFactory.StrategyType expected)
            throws IOException {
        byte[] inflated = inflatePdf(basePdfBytes, sizeMB);
        try (PDDocument doc = factory.load(inflated)) {
            Assertions.assertEquals(expected, factory.lastStrategyUsed);
        }
    }

    @ParameterizedTest
    @CsvSource({"5,MEMORY_ONLY", "20,MIXED", "60,TEMP_FILE"})
    void testStrategy_InputStream(int sizeMB, SpyPDFDocumentFactory.StrategyType expected)
            throws IOException {
        byte[] inflated = inflatePdf(basePdfBytes, sizeMB);
        try (PDDocument doc = factory.load(new ByteArrayInputStream(inflated))) {
            Assertions.assertEquals(expected, factory.lastStrategyUsed);
        }
    }

    @ParameterizedTest
    @CsvSource({"5,MEMORY_ONLY", "20,MIXED", "60,TEMP_FILE"})
    void testStrategy_MultipartFile(int sizeMB, SpyPDFDocumentFactory.StrategyType expected)
            throws IOException {
        byte[] inflated = inflatePdf(basePdfBytes, sizeMB);
        MockMultipartFile multipart =
                new MockMultipartFile("file", "doc.pdf", MediaType.APPLICATION_PDF_VALUE, inflated);
        try (PDDocument doc = factory.load(multipart)) {
            Assertions.assertEquals(expected, factory.lastStrategyUsed);
        }
    }

    @ParameterizedTest
    @CsvSource({"5,MEMORY_ONLY", "20,MIXED", "60,TEMP_FILE"})
    void testStrategy_PDFFile(int sizeMB, SpyPDFDocumentFactory.StrategyType expected)
            throws IOException {
        byte[] inflated = inflatePdf(basePdfBytes, sizeMB);
        MockMultipartFile multipart =
                new MockMultipartFile("file", "doc.pdf", MediaType.APPLICATION_PDF_VALUE, inflated);
        PDFFile pdfFile = new PDFFile();
        pdfFile.setFileInput(multipart);
        try (PDDocument doc = factory.load(pdfFile)) {
            Assertions.assertEquals(expected, factory.lastStrategyUsed);
        }
    }

    @Test
    void testLoadFromPath() throws IOException {
        File file = writeTempFile(inflatePdf(basePdfBytes, 5));
        Path path = file.toPath();
        try (PDDocument doc = factory.load(path)) {
            Assertions.assertNotNull(doc);
        }
    }

    // neeed to add password pdf
    //    @Test
    //    void testLoadPasswordProtectedPdfFromInputStream() throws IOException {
    //        try (InputStream is = getClass().getResourceAsStream("/protected.pdf")) {
    //            assertNotNull(is, "protected.pdf must be present in src/test/resources");
    //            try (PDDocument doc = factory.load(is, "test123")) {
    //                assertNotNull(doc);
    //            }
    //        }
    //    }
    //
    //    @Test
    //    void testLoadPasswordProtectedPdfFromMultipart() throws IOException {
    //        try (InputStream is = getClass().getResourceAsStream("/protected.pdf")) {
    //            assertNotNull(is, "protected.pdf must be present in src/test/resources");
    //            byte[] bytes = is.readAllBytes();
    //            MockMultipartFile file = new MockMultipartFile("file", "protected.pdf",
    // "application/pdf", bytes);
    //            try (PDDocument doc = factory.load(file, "test123")) {
    //                assertNotNull(doc);
    //            }
    //        }
    //    }

    @Test
    void testLoadFromStringPath() throws IOException {
        File file = writeTempFile(inflatePdf(basePdfBytes, 5));
        try (PDDocument doc = factory.load(file.getAbsolutePath())) {
            Assertions.assertNotNull(doc);
        }
    }

    @Test
    void testLoadReadOnlySkipsPostProcessing() throws IOException {
        PdfMetadataService mockService = Mockito.mock(PdfMetadataService.class);
        CustomPDFDocumentFactory readOnlyFactory = new CustomPDFDocumentFactory(mockService);

        byte[] bytes = inflatePdf(basePdfBytes, 5);
        try (PDDocument doc = readOnlyFactory.load(bytes, true)) {
            Assertions.assertNotNull(doc);
            Mockito.verify(mockService, Mockito.never()).setDefaultMetadata(ArgumentMatchers.any());
        }
    }

    @Test
    void testCreateNewDocument() throws IOException {
        try (PDDocument doc = factory.createNewDocument()) {
            Assertions.assertNotNull(doc);
        }
    }

    @Test
    void testCreateNewDocumentBasedOnOldDocument() throws IOException {
        byte[] inflated = inflatePdf(basePdfBytes, 5);
        try (PDDocument oldDoc = Loader.loadPDF(inflated);
                PDDocument newDoc = factory.createNewDocumentBasedOnOldDocument(oldDoc)) {
            Assertions.assertNotNull(newDoc);
        }
    }

    @Test
    void testLoadToBytesRoundTrip() throws IOException {
        byte[] inflated = inflatePdf(basePdfBytes, 5);
        File file = writeTempFile(inflated);

        byte[] resultBytes = factory.loadToBytes(file);
        try (PDDocument doc = Loader.loadPDF(resultBytes)) {
            Assertions.assertNotNull(doc);
            Assertions.assertTrue(doc.getNumberOfPages() > 0);
        }
    }

    @Test
    void testSaveToBytesAndReload() throws IOException {
        try (PDDocument doc = Loader.loadPDF(basePdfBytes)) {
            byte[] saved = factory.saveToBytes(doc);
            try (PDDocument reloaded = Loader.loadPDF(saved)) {
                Assertions.assertNotNull(reloaded);
                Assertions.assertEquals(doc.getNumberOfPages(), reloaded.getNumberOfPages());
            }
        }
    }

    @Test
    void testCreateNewBytesBasedOnOldDocument() throws IOException {
        byte[] newBytes = factory.createNewBytesBasedOnOldDocument(basePdfBytes);
        Assertions.assertNotNull(newBytes);
        Assertions.assertTrue(newBytes.length > 0);
    }

    @BeforeEach
    void cleanup() {
        System.gc();
    }
}
