package stirling.software.SPDF.controller.api;

import java.io.IOException;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageTree;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;

import stirling.software.SPDF.model.api.general.RotatePDFRequest;
import stirling.software.common.service.CustomPDFDocumentFactory;

@ExtendWith(MockitoExtension.class)
public class RotationControllerTest {

    @Mock private CustomPDFDocumentFactory pdfDocumentFactory;

    @InjectMocks private RotationController rotationController;

    @Test
    public void testRotatePDF() throws IOException {
        // Create a mock file
        MockMultipartFile mockFile =
                new MockMultipartFile(
                        "file", "test.pdf", MediaType.APPLICATION_PDF_VALUE, new byte[] {1, 2, 3});
        RotatePDFRequest request = new RotatePDFRequest();
        request.setFileInput(mockFile);
        request.setAngle(90);

        PDDocument mockDocument = Mockito.mock(PDDocument.class);
        PDPageTree mockPages = Mockito.mock(PDPageTree.class);
        PDPage mockPage = Mockito.mock(PDPage.class);

        Mockito.when(pdfDocumentFactory.load(request)).thenReturn(mockDocument);
        Mockito.when(mockDocument.getPages()).thenReturn(mockPages);
        Mockito.when(mockPages.iterator())
                .thenReturn(java.util.Collections.singletonList(mockPage).iterator());
        Mockito.when(mockPage.getRotation()).thenReturn(0);

        // Act
        ResponseEntity<byte[]> response = rotationController.rotatePDF(request);

        // Assert
        Mockito.verify(mockPage).setRotation(90);
        Assertions.assertNotNull(response);
        Assertions.assertEquals(200, response.getStatusCode().value());
    }

    @Test
    public void testRotatePDFInvalidAngle() {
        // Create a mock file
        MockMultipartFile mockFile =
                new MockMultipartFile(
                        "file", "test.pdf", MediaType.APPLICATION_PDF_VALUE, new byte[] {1, 2, 3});
        RotatePDFRequest request = new RotatePDFRequest();
        request.setFileInput(mockFile);
        request.setAngle(45); // Invalid angle

        // Act & Assert: Controller direkt aufrufen und Exception erwarten
        IllegalArgumentException exception =
                Assertions.assertThrows(
                        IllegalArgumentException.class,
                        () -> rotationController.rotatePDF(request));
        Assertions.assertEquals("Angle must be a multiple of 90", exception.getMessage());
    }
}
