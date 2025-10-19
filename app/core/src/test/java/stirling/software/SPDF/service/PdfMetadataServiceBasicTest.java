package stirling.software.SPDF.service;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Calendar;
import java.util.GregorianCalendar;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

import stirling.software.common.model.ApplicationProperties;
import stirling.software.common.model.ApplicationProperties.Premium;
import stirling.software.common.model.ApplicationProperties.Premium.ProFeatures;
import stirling.software.common.model.ApplicationProperties.Premium.ProFeatures.CustomMetadata;
import stirling.software.common.model.PdfMetadata;
import stirling.software.common.service.PdfMetadataService;
import stirling.software.common.service.UserServiceInterface;

class PdfMetadataServiceBasicTest {

    private PdfMetadataService pdfMetadataService;
    private static final String STIRLING_PDF_LABEL = "Stirling PDF";

    @BeforeEach
    void setUp() {
        // Set up mocks for application properties' nested objects
        ApplicationProperties applicationProperties = Mockito.mock(ApplicationProperties.class);
        Premium premium = Mockito.mock(Premium.class);
        ProFeatures proFeatures = Mockito.mock(ProFeatures.class);
        CustomMetadata customMetadata = Mockito.mock(CustomMetadata.class);
        UserServiceInterface userService = Mockito.mock(UserServiceInterface.class);

        Mockito.when(applicationProperties.getPremium()).thenReturn(premium);
        Mockito.when(premium.getProFeatures()).thenReturn(proFeatures);
        Mockito.when(proFeatures.getCustomMetadata()).thenReturn(customMetadata);

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
        // Create test document
        PDDocument testDocument = Mockito.mock(PDDocument.class);
        PDDocumentInformation testInfo = Mockito.mock(PDDocumentInformation.class);
        Mockito.when(testDocument.getDocumentInformation()).thenReturn(testInfo);

        // Set up expected metadata values
        String testAuthor = "Test Author";
        String testProducer = "Test Producer";
        String testTitle = "Test Title";
        String testCreator = "Test Creator";
        String testSubject = "Test Subject";
        String testKeywords = "Test Keywords";
        // Use deterministic ZonedDateTime values and convert to GregorianCalendar
        ZonedDateTime fixedCreation = ZonedDateTime.of(2020, 1, 2, 3, 4, 5, 0, ZoneId.of("UTC"));
        ZonedDateTime fixedModification =
                ZonedDateTime.of(2021, 6, 7, 8, 9, 10, 0, ZoneId.of("UTC"));
        Calendar creationDate = GregorianCalendar.from(fixedCreation);
        Calendar modificationDate = GregorianCalendar.from(fixedModification);

        // Configure mock returns
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
        // Create test document
        PDDocument testDocument = Mockito.mock(PDDocument.class);
        PDDocumentInformation testInfo = Mockito.mock(PDDocumentInformation.class);
        Mockito.when(testDocument.getDocumentInformation()).thenReturn(testInfo);

        // Act
        pdfMetadataService.setDefaultMetadata(testDocument);

        // Verify basic calls
        Mockito.verify(testInfo, Mockito.times(1))
                .setModificationDate(ArgumentMatchers.any(Calendar.class));
        Mockito.verify(testInfo, Mockito.times(1)).setProducer(STIRLING_PDF_LABEL);
    }
}
