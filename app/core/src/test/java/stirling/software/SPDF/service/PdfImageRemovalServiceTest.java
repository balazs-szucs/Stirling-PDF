package stirling.software.SPDF.service;

import java.io.IOException;
import java.util.*;

import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageTree;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

class PdfImageRemovalServiceTest {

    private PdfImageRemovalService service;

    @BeforeEach
    void setUp() {
        service = new PdfImageRemovalService();
    }

    // Helper method for matching COSName in verification
    private static COSName eq(final COSName value) {
        return Mockito.argThat(
                new org.mockito.ArgumentMatcher<>() {
                    @Override
                    public boolean matches(COSName argument) {
                        if (argument == null && value == null) return true;
                        if (argument == null || value == null) return false;
                        return argument.getName().equals(value.getName());
                    }

                    @Override
                    public String toString() {
                        return "eq(" + (value != null ? value.getName() : "null") + ")";
                    }
                });
    }

    @Test
    void testRemoveImagesFromPdf_WithImages() throws IOException {
        // Mock PDF document and its components
        PDDocument document = Mockito.mock(PDDocument.class);
        PDPage page = Mockito.mock(PDPage.class);
        PDResources resources = Mockito.mock(PDResources.class);
        PDPageTree pageTree = Mockito.mock(PDPageTree.class);

        // Configure page tree to iterate over our single page
        Mockito.when(document.getPages()).thenReturn(pageTree);
        Iterator<PDPage> pageIterator = Collections.singletonList(page).iterator();
        Mockito.when(pageTree.iterator()).thenReturn(pageIterator);

        // Set up page resources
        Mockito.when(page.getResources()).thenReturn(resources);

        // Set up image XObjects
        COSName img1 = COSName.getPDFName("Im1");
        COSName img2 = COSName.getPDFName("Im2");
        COSName nonImg = COSName.getPDFName("NonImg");

        List<COSName> xObjectNames = Arrays.asList(img1, img2, nonImg);
        Mockito.when(resources.getXObjectNames()).thenReturn(xObjectNames);

        // Configure which are image XObjects
        Mockito.when(resources.isImageXObject(img1)).thenReturn(true);
        Mockito.when(resources.isImageXObject(img2)).thenReturn(true);
        Mockito.when(resources.isImageXObject(nonImg)).thenReturn(false);

        // Execute the method
        PDDocument result = service.removeImagesFromPdf(document);

        // Verify that images were removed
        Mockito.verify(resources, Mockito.times(1)).put(eq(img1), Mockito.<PDXObject>isNull());
        Mockito.verify(resources, Mockito.times(1)).put(eq(img2), Mockito.<PDXObject>isNull());
        Mockito.verify(resources, Mockito.never()).put(eq(nonImg), Mockito.<PDXObject>isNull());
    }

    @Test
    void testRemoveImagesFromPdf_NoImages() throws IOException {
        // Mock PDF document and its components
        PDDocument document = Mockito.mock(PDDocument.class);
        PDPage page = Mockito.mock(PDPage.class);
        PDResources resources = Mockito.mock(PDResources.class);
        PDPageTree pageTree = Mockito.mock(PDPageTree.class);

        // Configure page tree to iterate over our single page
        Mockito.when(document.getPages()).thenReturn(pageTree);
        Iterator<PDPage> pageIterator = Collections.singletonList(page).iterator();
        Mockito.when(pageTree.iterator()).thenReturn(pageIterator);

        // Set up page resources
        Mockito.when(page.getResources()).thenReturn(resources);

        // Create empty list of XObject names
        List<COSName> emptyList = new ArrayList<>();
        Mockito.when(resources.getXObjectNames()).thenReturn(emptyList);

        // Execute the method
        PDDocument result = service.removeImagesFromPdf(document);

        // Verify that no modifications were made
        Mockito.verify(resources, Mockito.never())
                .put(ArgumentMatchers.any(COSName.class), ArgumentMatchers.any(PDXObject.class));
    }

    @Test
    void testRemoveImagesFromPdf_MultiplePages() throws IOException {
        // Mock PDF document and its components
        PDDocument document = Mockito.mock(PDDocument.class);
        PDPage page1 = Mockito.mock(PDPage.class);
        PDPage page2 = Mockito.mock(PDPage.class);
        PDResources resources1 = Mockito.mock(PDResources.class);
        PDResources resources2 = Mockito.mock(PDResources.class);
        PDPageTree pageTree = Mockito.mock(PDPageTree.class);

        // Configure page tree to iterate over our two pages
        Mockito.when(document.getPages()).thenReturn(pageTree);
        Iterator<PDPage> pageIterator = Arrays.asList(page1, page2).iterator();
        Mockito.when(pageTree.iterator()).thenReturn(pageIterator);

        // Set up page resources
        Mockito.when(page1.getResources()).thenReturn(resources1);
        Mockito.when(page2.getResources()).thenReturn(resources2);

        // Set up image XObjects for page 1
        COSName img1 = COSName.getPDFName("Im1");
        Mockito.when(resources1.getXObjectNames()).thenReturn(Collections.singletonList(img1));
        Mockito.when(resources1.isImageXObject(img1)).thenReturn(true);

        // Set up image XObjects for page 2
        COSName img2 = COSName.getPDFName("Im2");
        Mockito.when(resources2.getXObjectNames()).thenReturn(Collections.singletonList(img2));
        Mockito.when(resources2.isImageXObject(img2)).thenReturn(true);

        // Execute the method
        PDDocument result = service.removeImagesFromPdf(document);

        // Verify that images were removed from both pages
        Mockito.verify(resources1, Mockito.times(1)).put(eq(img1), Mockito.<PDXObject>isNull());
        Mockito.verify(resources2, Mockito.times(1)).put(eq(img2), Mockito.<PDXObject>isNull());
    }
}
