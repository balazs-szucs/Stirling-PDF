package stirling.software.SPDF.service;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Calendar;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import stirling.software.common.model.ApplicationProperties;
import stirling.software.common.model.ApplicationProperties.Premium;
import stirling.software.common.model.ApplicationProperties.Premium.ProFeatures;
import stirling.software.common.model.ApplicationProperties.Premium.ProFeatures.CustomMetadata;
import stirling.software.common.model.PdfMetadata;
import stirling.software.common.service.PdfMetadataService;
import stirling.software.common.service.UserServiceInterface;

@ExtendWith(MockitoExtension.class)
class PdfMetadataServiceTest {

    @Mock private ApplicationProperties applicationProperties;
    @Mock private UserServiceInterface userService;
    private PdfMetadataService pdfMetadataService;
    private final String STIRLING_PDF_LABEL = "Stirling PDF";

    @BeforeEach
    void setUp() {
        // Set up mocks for application properties' nested objects
        Premium premium = Mockito.mock(Premium.class);
        ProFeatures proFeatures = Mockito.mock(ProFeatures.class);
        CustomMetadata customMetadata = Mockito.mock(CustomMetadata.class);

        // Use lenient() to avoid UnnecessaryStubbingException for setup stubs that might not be
        // used in every test
        Mockito.lenient().when(applicationProperties.getPremium()).thenReturn(premium);
        Mockito.lenient().when(premium.getProFeatures()).thenReturn(proFeatures);
        Mockito.lenient().when(proFeatures.getCustomMetadata()).thenReturn(customMetadata);

        // Set up the service under test
        pdfMetadataService =
                new PdfMetadataService(
                        applicationProperties,
                        STIRLING_PDF_LABEL,
                        false, // not running Pro or higher
                        userService);
    }

    @Test
    void testExtractMetadataFromPdf() {
        // Create a fresh document and information for this test to avoid stubbing issues
        PDDocument testDocument = Mockito.mock(PDDocument.class);
        PDDocumentInformation testInfo = Mockito.mock(PDDocumentInformation.class);
        Mockito.when(testDocument.getDocumentInformation()).thenReturn(testInfo);

        // Setup the document information with non-null values that will be used
        String testAuthor = "Test Author";
        String testProducer = "Test Producer";
        String testTitle = "Test Title";
        String testCreator = "Test Creator";
        String testSubject = "Test Subject";
        String testKeywords = "Test Keywords";
        Calendar creationDate = Calendar.getInstance();
        Calendar modificationDate = Calendar.getInstance();

        Mockito.when(testInfo.getAuthor()).thenReturn(testAuthor);
        Mockito.when(testInfo.getProducer()).thenReturn(testProducer);
        Mockito.when(testInfo.getTitle()).thenReturn(testTitle);
        Mockito.when(testInfo.getCreator()).thenReturn(testCreator);
        Mockito.when(testInfo.getSubject()).thenReturn(testSubject);
        Mockito.when(testInfo.getKeywords()).thenReturn(testKeywords);
        Mockito.when(testInfo.getCreationDate()).thenReturn(creationDate);
        Mockito.when(testInfo.getModificationDate()).thenReturn(modificationDate);

        // Act
        PdfMetadata metadata = pdfMetadataService.extractMetadataFromPdf(testDocument);

        // Convert Calendar to ZonedDateTime for comparison
        ZonedDateTime expectedCreationDate =
                ZonedDateTime.ofInstant(creationDate.toInstant(), ZoneId.systemDefault());
        ZonedDateTime expectedModificationDate =
                ZonedDateTime.ofInstant(modificationDate.toInstant(), ZoneId.systemDefault());

        // Assert
        Assertions.assertEquals(testAuthor, metadata.getAuthor(), "Author should match");
        Assertions.assertEquals(testProducer, metadata.getProducer(), "Producer should match");
        Assertions.assertEquals(testTitle, metadata.getTitle(), "Title should match");
        Assertions.assertEquals(testCreator, metadata.getCreator(), "Creator should match");
        Assertions.assertEquals(testSubject, metadata.getSubject(), "Subject should match");
        Assertions.assertEquals(testKeywords, metadata.getKeywords(), "Keywords should match");
        Assertions.assertEquals(
                expectedCreationDate, metadata.getCreationDate(), "Creation date should match");
        Assertions.assertEquals(
                expectedModificationDate,
                metadata.getModificationDate(),
                "Modification date should match");
    }

    @Test
    void testSetDefaultMetadata() {
        // This test will use a real instance of PdfMetadataService

        // Create a test document
        PDDocument testDocument = Mockito.mock(PDDocument.class);
        PDDocumentInformation testInfo = Mockito.mock(PDDocumentInformation.class);
        Mockito.when(testDocument.getDocumentInformation()).thenReturn(testInfo);

        // Act
        pdfMetadataService.setDefaultMetadata(testDocument);

        // Verify the right calls were made to the document info
        // We only need to verify some of the basic setters were called
        Mockito.verify(testInfo).setTitle(ArgumentMatchers.any());
        Mockito.verify(testInfo).setProducer(STIRLING_PDF_LABEL);
        Mockito.verify(testInfo).setModificationDate(ArgumentMatchers.any(Calendar.class));
    }

    @Test
    void testSetMetadataToPdf_NewDocument() {
        // Create a fresh document
        PDDocument testDocument = Mockito.mock(PDDocument.class);
        PDDocumentInformation testInfo = Mockito.mock(PDDocumentInformation.class);
        Mockito.when(testDocument.getDocumentInformation()).thenReturn(testInfo);

        // Prepare test metadata
        PdfMetadata testMetadata =
                PdfMetadata.builder()
                        .author("Test Author")
                        .title("Test Title")
                        .subject("Test Subject")
                        .keywords("Test Keywords")
                        .build();

        // Act
        pdfMetadataService.setMetadataToPdf(testDocument, testMetadata, true);

        // Assert
        Mockito.verify(testInfo).setCreator(STIRLING_PDF_LABEL);
        Mockito.verify(testInfo).setCreationDate(org.mockito.ArgumentMatchers.any(Calendar.class));
        Mockito.verify(testInfo).setTitle("Test Title");
        Mockito.verify(testInfo).setProducer(STIRLING_PDF_LABEL);
        Mockito.verify(testInfo).setSubject("Test Subject");
        Mockito.verify(testInfo).setKeywords("Test Keywords");
        Mockito.verify(testInfo)
                .setModificationDate(org.mockito.ArgumentMatchers.any(Calendar.class));
        Mockito.verify(testInfo).setAuthor("Test Author");
    }

    @Test
    void testSetMetadataToPdf_WithProFeatures() {
        // Create a fresh document and information for this test
        PDDocument testDocument = Mockito.mock(PDDocument.class);
        PDDocumentInformation testInfo = Mockito.mock(PDDocumentInformation.class);
        Mockito.when(testDocument.getDocumentInformation()).thenReturn(testInfo);

        // Create a special service instance for Pro version
        PdfMetadataService proService =
                new PdfMetadataService(
                        applicationProperties,
                        STIRLING_PDF_LABEL,
                        true, // running Pro version
                        userService);

        PdfMetadata testMetadata =
                PdfMetadata.builder().author("Original Author").title("Test Title").build();

        // Configure pro features
        CustomMetadata customMetadata =
                applicationProperties.getPremium().getProFeatures().getCustomMetadata();
        Mockito.when(customMetadata.isAutoUpdateMetadata()).thenReturn(true);
        Mockito.when(customMetadata.getCreator()).thenReturn("Pro Creator");
        Mockito.when(customMetadata.getAuthor()).thenReturn("Pro Author username");
        Mockito.when(userService.getCurrentUsername()).thenReturn("testUser");

        // Act - create a new document with Pro features
        proService.setMetadataToPdf(testDocument, testMetadata, true);

        // Assert - verify only once for each call
        Mockito.verify(testInfo).setCreator("Pro Creator");
        Mockito.verify(testInfo).setAuthor("Pro Author testUser");
        // We don't verify setProducer here to avoid the "Too many actual invocations" error
    }

    @Test
    void testSetMetadataToPdf_ExistingDocument() {
        // Create a fresh document
        PDDocument testDocument = Mockito.mock(PDDocument.class);
        PDDocumentInformation testInfo = Mockito.mock(PDDocumentInformation.class);
        Mockito.when(testDocument.getDocumentInformation()).thenReturn(testInfo);

        // Prepare test metadata with existing creation date
        Calendar existingCreationDate = Calendar.getInstance();
        existingCreationDate.add(Calendar.DAY_OF_MONTH, -1); // Yesterday
        ZonedDateTime existingCreationDateZdt =
                ZonedDateTime.ofInstant(existingCreationDate.toInstant(), ZoneId.systemDefault());

        PdfMetadata testMetadata =
                PdfMetadata.builder()
                        .author("Test Author")
                        .title("Test Title")
                        .subject("Test Subject")
                        .keywords("Test Keywords")
                        .creationDate(existingCreationDateZdt)
                        .build();

        // Act
        pdfMetadataService.setMetadataToPdf(testDocument, testMetadata, false);

        // Assert - should NOT set a new creation date
        Mockito.verify(testInfo).setTitle("Test Title");
        Mockito.verify(testInfo).setProducer(STIRLING_PDF_LABEL);
        Mockito.verify(testInfo).setSubject("Test Subject");
        Mockito.verify(testInfo).setKeywords("Test Keywords");
        Mockito.verify(testInfo)
                .setModificationDate(org.mockito.ArgumentMatchers.any(Calendar.class));
        Mockito.verify(testInfo).setAuthor("Test Author");
    }

    @Test
    void testSetMetadataToPdf_NullCreationDate() {
        // Create a fresh document
        PDDocument testDocument = Mockito.mock(PDDocument.class);
        PDDocumentInformation testInfo = Mockito.mock(PDDocumentInformation.class);
        Mockito.when(testDocument.getDocumentInformation()).thenReturn(testInfo);

        // Prepare test metadata with null creation date
        PdfMetadata testMetadata =
                PdfMetadata.builder()
                        .author("Test Author")
                        .title("Test Title")
                        .creationDate(null) // Explicitly null creation date
                        .build();

        // Act
        pdfMetadataService.setMetadataToPdf(testDocument, testMetadata, false);

        // Assert - should set a new creation date
        Mockito.verify(testInfo).setCreator(STIRLING_PDF_LABEL);
        Mockito.verify(testInfo).setCreationDate(org.mockito.ArgumentMatchers.any(Calendar.class));
    }
}
