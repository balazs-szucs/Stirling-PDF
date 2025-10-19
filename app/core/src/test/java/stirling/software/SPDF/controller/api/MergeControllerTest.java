package stirling.software.SPDF.controller.api;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentCatalog;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageTree;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import stirling.software.common.service.CustomPDFDocumentFactory;

@ExtendWith(MockitoExtension.class)
class MergeControllerTest {

    @Mock private CustomPDFDocumentFactory pdfDocumentFactory;

    @InjectMocks private MergeController mergeController;

    private MockMultipartFile mockFile1;
    private MockMultipartFile mockFile2;
    private MockMultipartFile mockFile3;
    private PDDocument mockMergedDocument;
    private PDDocumentCatalog mockCatalog;
    private PDPage mockPage1;
    private PDPage mockPage2;

    @BeforeEach
    void setUp() {
        mockFile1 =
                new MockMultipartFile(
                        "file1",
                        "document1.pdf",
                        MediaType.APPLICATION_PDF_VALUE,
                        "PDF content 1".getBytes());
        mockFile2 =
                new MockMultipartFile(
                        "file2",
                        "document2.pdf",
                        MediaType.APPLICATION_PDF_VALUE,
                        "PDF content 2".getBytes());
        mockFile3 =
                new MockMultipartFile(
                        "file3",
                        "chapter3.pdf",
                        MediaType.APPLICATION_PDF_VALUE,
                        "PDF content 3".getBytes());

        PDDocument mockDocument = Mockito.mock(PDDocument.class);
        mockMergedDocument = Mockito.mock(PDDocument.class);
        mockCatalog = Mockito.mock(PDDocumentCatalog.class);
        PDPageTree mockPages = Mockito.mock(PDPageTree.class);
        mockPage1 = Mockito.mock(PDPage.class);
        mockPage2 = Mockito.mock(PDPage.class);
    }

    @Test
    void testAddTableOfContents_WithMultipleFiles_Success() throws Exception {
        // Given
        MultipartFile[] files = {mockFile1, mockFile2, mockFile3};

        // Mock the merged document setup
        Mockito.when(mockMergedDocument.getDocumentCatalog()).thenReturn(mockCatalog);
        Mockito.when(mockMergedDocument.getNumberOfPages()).thenReturn(6);
        Mockito.when(mockMergedDocument.getPage(0)).thenReturn(mockPage1);
        Mockito.when(mockMergedDocument.getPage(2)).thenReturn(mockPage2);
        Mockito.when(mockMergedDocument.getPage(4)).thenReturn(mockPage1);

        // Mock individual document loading for page count
        PDDocument doc1 = Mockito.mock(PDDocument.class);
        PDDocument doc2 = Mockito.mock(PDDocument.class);
        PDDocument doc3 = Mockito.mock(PDDocument.class);

        Mockito.when(pdfDocumentFactory.load(mockFile1)).thenReturn(doc1);
        Mockito.when(pdfDocumentFactory.load(mockFile2)).thenReturn(doc2);
        Mockito.when(pdfDocumentFactory.load(mockFile3)).thenReturn(doc3);

        Mockito.when(doc1.getNumberOfPages()).thenReturn(2);
        Mockito.when(doc2.getNumberOfPages()).thenReturn(2);
        Mockito.when(doc3.getNumberOfPages()).thenReturn(2);

        // When
        Method addTableOfContentsMethod =
                MergeController.class.getDeclaredMethod(
                        "addTableOfContents", PDDocument.class, MultipartFile[].class);
        addTableOfContentsMethod.setAccessible(true);
        addTableOfContentsMethod.invoke(mergeController, mockMergedDocument, files);

        // Then
        ArgumentCaptor<PDDocumentOutline> outlineCaptor =
                ArgumentCaptor.forClass(PDDocumentOutline.class);
        Mockito.verify(mockCatalog).setDocumentOutline(outlineCaptor.capture());

        PDDocumentOutline capturedOutline = outlineCaptor.getValue();
        Assertions.assertNotNull(capturedOutline);

        // Verify that documents were loaded for page count
        Mockito.verify(pdfDocumentFactory).load(mockFile1);
        Mockito.verify(pdfDocumentFactory).load(mockFile2);
        Mockito.verify(pdfDocumentFactory).load(mockFile3);

        // Verify document closing
        Mockito.verify(doc1).close();
        Mockito.verify(doc2).close();
        Mockito.verify(doc3).close();
    }

    @Test
    void testAddTableOfContents_WithSingleFile_Success() throws Exception {
        // Given
        MultipartFile[] files = {mockFile1};

        Mockito.when(mockMergedDocument.getDocumentCatalog()).thenReturn(mockCatalog);
        Mockito.when(mockMergedDocument.getNumberOfPages()).thenReturn(3);
        Mockito.when(mockMergedDocument.getPage(0)).thenReturn(mockPage1);

        PDDocument doc1 = Mockito.mock(PDDocument.class);
        Mockito.when(pdfDocumentFactory.load(mockFile1)).thenReturn(doc1);
        Mockito.when(doc1.getNumberOfPages()).thenReturn(3);

        // When
        Method addTableOfContentsMethod =
                MergeController.class.getDeclaredMethod(
                        "addTableOfContents", PDDocument.class, MultipartFile[].class);
        addTableOfContentsMethod.setAccessible(true);
        addTableOfContentsMethod.invoke(mergeController, mockMergedDocument, files);

        // Then
        Mockito.verify(mockCatalog)
                .setDocumentOutline(ArgumentMatchers.any(PDDocumentOutline.class));
        Mockito.verify(pdfDocumentFactory).load(mockFile1);
        Mockito.verify(doc1).close();
    }

    @Test
    void testAddTableOfContents_WithEmptyArray_Success() throws Exception {
        // Given
        MultipartFile[] files = {};
        Mockito.when(mockMergedDocument.getDocumentCatalog()).thenReturn(mockCatalog);

        // When
        Method addTableOfContentsMethod =
                MergeController.class.getDeclaredMethod(
                        "addTableOfContents", PDDocument.class, MultipartFile[].class);
        addTableOfContentsMethod.setAccessible(true);
        addTableOfContentsMethod.invoke(mergeController, mockMergedDocument, files);

        // Then
        Mockito.verify(mockMergedDocument).getDocumentCatalog();
        Mockito.verify(mockCatalog)
                .setDocumentOutline(ArgumentMatchers.any(PDDocumentOutline.class));
        Mockito.verifyNoInteractions(pdfDocumentFactory);
    }

    @Test
    void testAddTableOfContents_WithIOException_HandlesGracefully() throws Exception {
        // Given
        MultipartFile[] files = {mockFile1, mockFile2};

        Mockito.when(mockMergedDocument.getDocumentCatalog()).thenReturn(mockCatalog);
        Mockito.when(mockMergedDocument.getNumberOfPages()).thenReturn(4);
        Mockito.when(mockMergedDocument.getPage(ArgumentMatchers.anyInt()))
                .thenReturn(mockPage1); // Use anyInt() to avoid stubbing conflicts

        // First document loads successfully
        PDDocument doc1 = Mockito.mock(PDDocument.class);
        Mockito.when(pdfDocumentFactory.load(mockFile1)).thenReturn(doc1);
        Mockito.when(doc1.getNumberOfPages()).thenReturn(2);

        // Second document throws IOException
        Mockito.when(pdfDocumentFactory.load(mockFile2))
                .thenThrow(new IOException("Failed to load document"));

        // When
        Method addTableOfContentsMethod =
                MergeController.class.getDeclaredMethod(
                        "addTableOfContents", PDDocument.class, MultipartFile[].class);
        addTableOfContentsMethod.setAccessible(true);

        // Should not throw exception
        Assertions.assertDoesNotThrow(
                () -> addTableOfContentsMethod.invoke(mergeController, mockMergedDocument, files));

        // Then
        Mockito.verify(mockCatalog)
                .setDocumentOutline(ArgumentMatchers.any(PDDocumentOutline.class));
        Mockito.verify(pdfDocumentFactory).load(mockFile1);
        Mockito.verify(pdfDocumentFactory).load(mockFile2);
        Mockito.verify(doc1).close();
    }

    @Test
    void testAddTableOfContents_FilenameWithoutExtension_UsesFullName() throws Exception {
        // Given
        MockMultipartFile fileWithoutExtension =
                new MockMultipartFile(
                        "file",
                        "document_no_ext",
                        MediaType.APPLICATION_PDF_VALUE,
                        "PDF content".getBytes());
        MultipartFile[] files = {fileWithoutExtension};

        Mockito.when(mockMergedDocument.getDocumentCatalog()).thenReturn(mockCatalog);
        Mockito.when(mockMergedDocument.getNumberOfPages()).thenReturn(1);
        Mockito.when(mockMergedDocument.getPage(0)).thenReturn(mockPage1);

        PDDocument doc = Mockito.mock(PDDocument.class);
        Mockito.when(pdfDocumentFactory.load(fileWithoutExtension)).thenReturn(doc);
        Mockito.when(doc.getNumberOfPages()).thenReturn(1);

        // When
        Method addTableOfContentsMethod =
                MergeController.class.getDeclaredMethod(
                        "addTableOfContents", PDDocument.class, MultipartFile[].class);
        addTableOfContentsMethod.setAccessible(true);
        addTableOfContentsMethod.invoke(mergeController, mockMergedDocument, files);

        // Then
        Mockito.verify(mockCatalog)
                .setDocumentOutline(ArgumentMatchers.any(PDDocumentOutline.class));
        Mockito.verify(doc).close();
    }

    @Test
    void testAddTableOfContents_PageIndexExceedsDocumentPages_HandlesGracefully() throws Exception {
        // Given
        MultipartFile[] files = {mockFile1};

        Mockito.when(mockMergedDocument.getDocumentCatalog()).thenReturn(mockCatalog);
        Mockito.when(mockMergedDocument.getNumberOfPages())
                .thenReturn(0); // No pages in merged document

        PDDocument doc1 = Mockito.mock(PDDocument.class);
        Mockito.when(pdfDocumentFactory.load(mockFile1)).thenReturn(doc1);
        Mockito.when(doc1.getNumberOfPages()).thenReturn(3);

        // When
        Method addTableOfContentsMethod =
                MergeController.class.getDeclaredMethod(
                        "addTableOfContents", PDDocument.class, MultipartFile[].class);
        addTableOfContentsMethod.setAccessible(true);

        // Should not throw exception
        Assertions.assertDoesNotThrow(
                () -> addTableOfContentsMethod.invoke(mergeController, mockMergedDocument, files));

        // Then
        Mockito.verify(mockCatalog)
                .setDocumentOutline(ArgumentMatchers.any(PDDocumentOutline.class));
        Mockito.verify(mockMergedDocument, Mockito.never()).getPage(ArgumentMatchers.anyInt());
        Mockito.verify(doc1).close();
    }

    @Test
    void testMergeDocuments_Success() throws IOException {
        // Given
        PDDocument doc1 = Mockito.mock(PDDocument.class);
        PDDocument doc2 = Mockito.mock(PDDocument.class);
        List<PDDocument> documents = Arrays.asList(doc1, doc2);

        PDPageTree pages1 = Mockito.mock(PDPageTree.class);
        PDPageTree pages2 = Mockito.mock(PDPageTree.class);
        PDPage page1 = Mockito.mock(PDPage.class);
        PDPage page2 = Mockito.mock(PDPage.class);

        Mockito.when(pdfDocumentFactory.createNewDocument()).thenReturn(mockMergedDocument);
        Mockito.when(doc1.getPages()).thenReturn(pages1);
        Mockito.when(doc2.getPages()).thenReturn(pages2);
        Mockito.when(pages1.iterator()).thenReturn(Collections.singletonList(page1).iterator());
        Mockito.when(pages2.iterator()).thenReturn(Collections.singletonList(page2).iterator());

        // When
        PDDocument result = mergeController.mergeDocuments(documents);

        // Then
        Assertions.assertNotNull(result);
        Assertions.assertEquals(mockMergedDocument, result);
        Mockito.verify(mockMergedDocument).addPage(page1);
        Mockito.verify(mockMergedDocument).addPage(page2);
    }

    @Test
    void testMergeDocuments_EmptyList_ReturnsEmptyDocument() throws IOException {
        // Given
        List<PDDocument> documents = List.of();

        Mockito.when(pdfDocumentFactory.createNewDocument()).thenReturn(mockMergedDocument);

        // When
        PDDocument result = mergeController.mergeDocuments(documents);

        // Then
        Assertions.assertNotNull(result);
        Assertions.assertEquals(mockMergedDocument, result);
        Mockito.verify(mockMergedDocument, Mockito.never())
                .addPage(ArgumentMatchers.any(PDPage.class));
    }
}
