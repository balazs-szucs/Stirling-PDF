package stirling.software.common.util.misc;

import java.io.IOException;

import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class PdfTextStripperCustomTest {

    private PdfTextStripperCustom stripper;
    private PDPage mockPage;

    @BeforeEach
    void setUp() throws IOException {
        // Create the stripper instance
        stripper = new PdfTextStripperCustom();

        // Create mock objects
        mockPage = Mockito.mock(PDPage.class);
        PDRectangle mockMediaBox = Mockito.mock(PDRectangle.class);

        // Configure mock behavior
        Mockito.when(mockPage.getMediaBox()).thenReturn(mockMediaBox);
        Mockito.when(mockMediaBox.getLowerLeftX()).thenReturn(0f);
        Mockito.when(mockMediaBox.getLowerLeftY()).thenReturn(0f);
        Mockito.when(mockMediaBox.getWidth()).thenReturn(612f);
        Mockito.when(mockMediaBox.getHeight()).thenReturn(792f);
    }

    @Test
    void testConstructor() throws IOException {
        // Verify that constructor doesn't throw an exception
        PdfTextStripperCustom newStripper = new PdfTextStripperCustom();
        Assertions.assertNotNull(newStripper, "Constructor should create a non-null instance");
    }

    @Test
    void testBasicFunctionality() {
        // Simply test that the method runs without exceptions
        try {
            stripper.addRegion("testRegion", new java.awt.geom.Rectangle2D.Float(0, 0, 100, 100));
            stripper.extractRegions(mockPage);
            Assertions.assertTrue(true, "Should execute without errors");
        } catch (Exception e) {
            Assertions.fail("Method should not throw exception: " + e.getMessage());
        }
    }
}
