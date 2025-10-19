package stirling.software.SPDF.controller.api;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentCatalog;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageTree;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import stirling.software.SPDF.controller.api.EditTableOfContentsController.BookmarkItem;
import stirling.software.SPDF.model.api.EditTableOfContentsRequest;
import stirling.software.common.service.CustomPDFDocumentFactory;

@ExtendWith(MockitoExtension.class)
class EditTableOfContentsControllerTest {

    @Mock private CustomPDFDocumentFactory pdfDocumentFactory;

    @Mock private ObjectMapper objectMapper;

    @InjectMocks private EditTableOfContentsController editTableOfContentsController;

    private MockMultipartFile mockFile;
    private PDDocument mockDocument;
    private PDDocumentCatalog mockCatalog;
    private PDPageTree mockPages;
    private PDPage mockPage1;
    private PDPage mockPage2;
    private PDDocumentOutline mockOutline;
    private PDOutlineItem mockOutlineItem;

    @BeforeEach
    void setUp() {
        mockFile =
                new MockMultipartFile(
                        "file",
                        "test.pdf",
                        MediaType.APPLICATION_PDF_VALUE,
                        "PDF content".getBytes());
        mockDocument = Mockito.mock(PDDocument.class);
        mockCatalog = Mockito.mock(PDDocumentCatalog.class);
        mockPages = Mockito.mock(PDPageTree.class);
        mockPage1 = Mockito.mock(PDPage.class);
        mockPage2 = Mockito.mock(PDPage.class);
        mockOutline = Mockito.mock(PDDocumentOutline.class);
        mockOutlineItem = Mockito.mock(PDOutlineItem.class);
    }

    @Test
    void testExtractBookmarks_WithExistingBookmarks_Success() throws Exception {
        // Given
        Mockito.when(pdfDocumentFactory.load(mockFile)).thenReturn(mockDocument);
        Mockito.when(mockDocument.getDocumentCatalog()).thenReturn(mockCatalog);
        Mockito.when(mockCatalog.getDocumentOutline()).thenReturn(mockOutline);
        Mockito.when(mockOutline.getFirstChild()).thenReturn(mockOutlineItem);

        Mockito.when(mockOutlineItem.getTitle()).thenReturn("Chapter 1");
        Mockito.when(mockOutlineItem.findDestinationPage(mockDocument)).thenReturn(mockPage1);
        Mockito.when(mockDocument.getPages()).thenReturn(mockPages);
        Mockito.when(mockPages.indexOf(mockPage1)).thenReturn(0);
        Mockito.when(mockOutlineItem.getFirstChild()).thenReturn(null);
        Mockito.when(mockOutlineItem.getNextSibling()).thenReturn(null);

        // When
        List<Map<String, Object>> result = editTableOfContentsController.extractBookmarks(mockFile);

        // Then
        Assertions.assertNotNull(result);
        Assertions.assertEquals(1, result.size());

        Map<String, Object> bookmark = result.get(0);
        Assertions.assertEquals("Chapter 1", bookmark.get("title"));
        Assertions.assertEquals(1, bookmark.get("pageNumber")); // 1-based
        Assertions.assertInstanceOf(List.class, bookmark.get("children"));

        Mockito.verify(mockDocument).close();
    }

    @Test
    void testExtractBookmarks_NoOutline_ReturnsEmptyList() throws Exception {
        // Given
        Mockito.when(pdfDocumentFactory.load(mockFile)).thenReturn(mockDocument);
        Mockito.when(mockDocument.getDocumentCatalog()).thenReturn(mockCatalog);
        Mockito.when(mockCatalog.getDocumentOutline()).thenReturn(null);

        // When
        List<Map<String, Object>> result = editTableOfContentsController.extractBookmarks(mockFile);

        // Then
        Assertions.assertNotNull(result);
        Assertions.assertTrue(result.isEmpty());
        Mockito.verify(mockDocument).close();
    }

    @Test
    void testExtractBookmarks_WithNestedBookmarks_Success() throws Exception {
        // Given
        PDOutlineItem childItem = Mockito.mock(PDOutlineItem.class);

        Mockito.when(pdfDocumentFactory.load(mockFile)).thenReturn(mockDocument);
        Mockito.when(mockDocument.getDocumentCatalog()).thenReturn(mockCatalog);
        Mockito.when(mockCatalog.getDocumentOutline()).thenReturn(mockOutline);
        Mockito.when(mockOutline.getFirstChild()).thenReturn(mockOutlineItem);

        // Parent bookmark
        Mockito.when(mockOutlineItem.getTitle()).thenReturn("Chapter 1");
        Mockito.when(mockOutlineItem.findDestinationPage(mockDocument)).thenReturn(mockPage1);
        Mockito.when(mockDocument.getPages()).thenReturn(mockPages);
        Mockito.when(mockPages.indexOf(mockPage1)).thenReturn(0);
        Mockito.when(mockOutlineItem.getFirstChild()).thenReturn(childItem);
        Mockito.when(mockOutlineItem.getNextSibling()).thenReturn(null);

        // Child bookmark
        Mockito.when(childItem.getTitle()).thenReturn("Section 1.1");
        Mockito.when(childItem.findDestinationPage(mockDocument)).thenReturn(mockPage2);
        Mockito.when(mockPages.indexOf(mockPage2)).thenReturn(1);
        Mockito.when(childItem.getFirstChild()).thenReturn(null);
        Mockito.when(childItem.getNextSibling()).thenReturn(null);

        // When
        List<Map<String, Object>> result = editTableOfContentsController.extractBookmarks(mockFile);

        // Then
        Assertions.assertNotNull(result);
        Assertions.assertEquals(1, result.size());

        Map<String, Object> parentBookmark = result.get(0);
        Assertions.assertEquals("Chapter 1", parentBookmark.get("title"));
        Assertions.assertEquals(1, parentBookmark.get("pageNumber"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> children =
                (List<Map<String, Object>>) parentBookmark.get("children");
        Assertions.assertEquals(1, children.size());

        Map<String, Object> childBookmark = children.get(0);
        Assertions.assertEquals("Section 1.1", childBookmark.get("title"));
        Assertions.assertEquals(2, childBookmark.get("pageNumber"));

        Mockito.verify(mockDocument).close();
    }

    @Test
    void testExtractBookmarks_PageNotFound_UsesPageOne() throws Exception {
        // Given
        Mockito.when(pdfDocumentFactory.load(mockFile)).thenReturn(mockDocument);
        Mockito.when(mockDocument.getDocumentCatalog()).thenReturn(mockCatalog);
        Mockito.when(mockCatalog.getDocumentOutline()).thenReturn(mockOutline);
        Mockito.when(mockOutline.getFirstChild()).thenReturn(mockOutlineItem);

        Mockito.when(mockOutlineItem.getTitle()).thenReturn("Chapter 1");
        Mockito.when(mockOutlineItem.findDestinationPage(mockDocument))
                .thenReturn(null); // Page not found
        Mockito.when(mockOutlineItem.getFirstChild()).thenReturn(null);
        Mockito.when(mockOutlineItem.getNextSibling()).thenReturn(null);

        // When
        List<Map<String, Object>> result = editTableOfContentsController.extractBookmarks(mockFile);

        // Then
        Assertions.assertNotNull(result);
        Assertions.assertEquals(1, result.size());

        Map<String, Object> bookmark = result.get(0);
        Assertions.assertEquals("Chapter 1", bookmark.get("title"));
        Assertions.assertEquals(1, bookmark.get("pageNumber")); // Default to page 1

        Mockito.verify(mockDocument).close();
    }

    @Test
    void testEditTableOfContents_Success() throws Exception {
        // Given
        EditTableOfContentsRequest request = new EditTableOfContentsRequest();
        request.setFileInput(mockFile);
        request.setBookmarkData("[{\"title\":\"Chapter 1\",\"pageNumber\":1,\"children\":[]}]");
        request.setReplaceExisting(true);

        List<BookmarkItem> bookmarks = new ArrayList<>();
        BookmarkItem bookmark = new BookmarkItem();
        bookmark.setTitle("Chapter 1");
        bookmark.setPageNumber(1);
        bookmark.setChildren(new ArrayList<>());
        bookmarks.add(bookmark);

        Mockito.when(pdfDocumentFactory.load(mockFile)).thenReturn(mockDocument);
        Mockito.when(
                        objectMapper.readValue(
                                ArgumentMatchers.eq(request.getBookmarkData()),
                                ArgumentMatchers.<TypeReference<List<BookmarkItem>>>any()))
                .thenReturn(bookmarks);
        Mockito.when(mockDocument.getDocumentCatalog()).thenReturn(mockCatalog);
        Mockito.when(mockDocument.getNumberOfPages()).thenReturn(5);
        Mockito.when(mockDocument.getPage(0)).thenReturn(mockPage1);

        // Mock saving behavior
        Mockito.doAnswer(
                        invocation -> {
                            ByteArrayOutputStream baos = invocation.getArgument(0);
                            baos.write("mocked pdf content".getBytes());
                            return null;
                        })
                .when(mockDocument)
                .save(ArgumentMatchers.any(ByteArrayOutputStream.class));

        // When
        ResponseEntity<byte[]> result = editTableOfContentsController.editTableOfContents(request);

        // Then
        Assertions.assertNotNull(result);
        Assertions.assertNotNull(result.getBody());

        ArgumentCaptor<PDDocumentOutline> outlineCaptor =
                ArgumentCaptor.forClass(PDDocumentOutline.class);
        Mockito.verify(mockCatalog).setDocumentOutline(outlineCaptor.capture());

        PDDocumentOutline capturedOutline = outlineCaptor.getValue();
        Assertions.assertNotNull(capturedOutline);

        Mockito.verify(mockDocument).close();
    }

    @Test
    void testEditTableOfContents_WithNestedBookmarks_Success() throws Exception {
        // Given
        EditTableOfContentsRequest request = new EditTableOfContentsRequest();
        request.setFileInput(mockFile);

        String bookmarkJson =
                "[{\"title\":\"Chapter 1\",\"pageNumber\":1,\"children\":[{\"title\":\"Section"
                        + " 1.1\",\"pageNumber\":2,\"children\":[]}]}]";
        request.setBookmarkData(bookmarkJson);

        List<BookmarkItem> bookmarks = new ArrayList<>();
        BookmarkItem parentBookmark = new BookmarkItem();
        parentBookmark.setTitle("Chapter 1");
        parentBookmark.setPageNumber(1);

        BookmarkItem childBookmark = new BookmarkItem();
        childBookmark.setTitle("Section 1.1");
        childBookmark.setPageNumber(2);
        childBookmark.setChildren(new ArrayList<>());

        List<BookmarkItem> children = new ArrayList<>();
        children.add(childBookmark);
        parentBookmark.setChildren(children);
        bookmarks.add(parentBookmark);

        Mockito.when(pdfDocumentFactory.load(mockFile)).thenReturn(mockDocument);
        Mockito.when(
                        objectMapper.readValue(
                                ArgumentMatchers.eq(bookmarkJson),
                                ArgumentMatchers.<TypeReference<List<BookmarkItem>>>any()))
                .thenReturn(bookmarks);
        Mockito.when(mockDocument.getDocumentCatalog()).thenReturn(mockCatalog);
        Mockito.when(mockDocument.getNumberOfPages()).thenReturn(5);
        Mockito.when(mockDocument.getPage(0)).thenReturn(mockPage1);
        Mockito.when(mockDocument.getPage(1)).thenReturn(mockPage2);

        Mockito.doAnswer(
                        invocation -> {
                            ByteArrayOutputStream baos = invocation.getArgument(0);
                            baos.write("mocked pdf content".getBytes());
                            return null;
                        })
                .when(mockDocument)
                .save(ArgumentMatchers.any(ByteArrayOutputStream.class));

        // When
        ResponseEntity<byte[]> result = editTableOfContentsController.editTableOfContents(request);

        // Then
        Assertions.assertNotNull(result);
        Mockito.verify(mockCatalog)
                .setDocumentOutline(ArgumentMatchers.any(PDDocumentOutline.class));
        Mockito.verify(mockDocument).close();
    }

    @Test
    void testEditTableOfContents_PageNumberBounds_ClampsValues() throws Exception {
        // Given
        EditTableOfContentsRequest request = new EditTableOfContentsRequest();
        request.setFileInput(mockFile);
        request.setBookmarkData(
                "[{\"title\":\"Chapter 1\",\"pageNumber\":-5,\"children\":[]},{\"title\":\"Chapter"
                        + " 2\",\"pageNumber\":100,\"children\":[]}]");

        List<BookmarkItem> bookmarks = new ArrayList<>();

        BookmarkItem bookmark1 = new BookmarkItem();
        bookmark1.setTitle("Chapter 1");
        bookmark1.setPageNumber(-5); // Negative page number
        bookmark1.setChildren(new ArrayList<>());

        BookmarkItem bookmark2 = new BookmarkItem();
        bookmark2.setTitle("Chapter 2");
        bookmark2.setPageNumber(100); // Page number exceeds document pages
        bookmark2.setChildren(new ArrayList<>());

        bookmarks.add(bookmark1);
        bookmarks.add(bookmark2);

        Mockito.when(pdfDocumentFactory.load(mockFile)).thenReturn(mockDocument);
        Mockito.when(
                        objectMapper.readValue(
                                ArgumentMatchers.eq(request.getBookmarkData()),
                                ArgumentMatchers.<TypeReference<List<BookmarkItem>>>any()))
                .thenReturn(bookmarks);
        Mockito.when(mockDocument.getDocumentCatalog()).thenReturn(mockCatalog);
        Mockito.when(mockDocument.getNumberOfPages()).thenReturn(5);
        Mockito.when(mockDocument.getPage(0)).thenReturn(mockPage1); // For negative page number
        Mockito.when(mockDocument.getPage(4))
                .thenReturn(mockPage2); // For page number exceeding bounds

        Mockito.doAnswer(
                        invocation -> {
                            ByteArrayOutputStream baos = invocation.getArgument(0);
                            baos.write("mocked pdf content".getBytes());
                            return null;
                        })
                .when(mockDocument)
                .save(ArgumentMatchers.any(ByteArrayOutputStream.class));

        // When
        ResponseEntity<byte[]> result = editTableOfContentsController.editTableOfContents(request);

        // Then
        Assertions.assertNotNull(result);
        Mockito.verify(mockDocument).getPage(0); // Clamped to first page
        Mockito.verify(mockDocument).getPage(4); // Clamped to last page
        Mockito.verify(mockDocument).close();
    }

    @Test
    void testCreateOutlineItem_ValidPageNumber_Success() throws Exception {
        // Given
        BookmarkItem bookmark = new BookmarkItem();
        bookmark.setTitle("Test Chapter");
        bookmark.setPageNumber(3);

        Mockito.when(mockDocument.getNumberOfPages()).thenReturn(5);
        Mockito.when(mockDocument.getPage(2)).thenReturn(mockPage1); // 0-indexed

        // When
        Method createOutlineItemMethod =
                EditTableOfContentsController.class.getDeclaredMethod(
                        "createOutlineItem", PDDocument.class, BookmarkItem.class);
        createOutlineItemMethod.setAccessible(true);
        PDOutlineItem result =
                (PDOutlineItem)
                        createOutlineItemMethod.invoke(
                                editTableOfContentsController, mockDocument, bookmark);

        // Then
        Assertions.assertNotNull(result);
        Mockito.verify(mockDocument).getPage(2);
    }

    @Test
    void testBookmarkItem_GettersAndSetters() {
        // Given
        BookmarkItem bookmark = new BookmarkItem();
        List<BookmarkItem> children = new ArrayList<>();

        // When
        bookmark.setTitle("Test Title");
        bookmark.setPageNumber(5);
        bookmark.setChildren(children);

        // Then
        Assertions.assertEquals("Test Title", bookmark.getTitle());
        Assertions.assertEquals(5, bookmark.getPageNumber());
        Assertions.assertEquals(children, bookmark.getChildren());
    }

    @Test
    void testEditTableOfContents_IOExceptionDuringLoad_ThrowsException() throws Exception {
        // Given
        EditTableOfContentsRequest request = new EditTableOfContentsRequest();
        request.setFileInput(mockFile);

        Mockito.when(pdfDocumentFactory.load(mockFile))
                .thenThrow(new RuntimeException("Failed to load PDF"));

        // When & Then
        Assertions.assertThrows(
                RuntimeException.class,
                () -> editTableOfContentsController.editTableOfContents(request));
    }

    @Test
    void testExtractBookmarks_IOExceptionDuringLoad_ThrowsException() throws Exception {
        // Given
        Mockito.when(pdfDocumentFactory.load(mockFile))
                .thenThrow(new RuntimeException("Failed to load PDF"));

        // When & Then
        Assertions.assertThrows(
                RuntimeException.class,
                () -> editTableOfContentsController.extractBookmarks(mockFile));
    }
}
