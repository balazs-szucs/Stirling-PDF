package stirling.software.common.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.*;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

class FileStorageTest {

    @TempDir Path tempDir;

    @Mock private FileOrUploadService fileOrUploadService;

    @InjectMocks private FileStorage fileStorage;

    private MultipartFile mockFile;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        ReflectionTestUtils.setField(fileStorage, "tempDirPath", tempDir.toString());

        // Create a mock MultipartFile
        mockFile = Mockito.mock(MultipartFile.class);
        Mockito.when(mockFile.getOriginalFilename()).thenReturn("test.pdf");
        Mockito.when(mockFile.getContentType()).thenReturn(MediaType.APPLICATION_PDF_VALUE);
    }

    @Test
    void testStoreFile() throws IOException {
        // Arrange
        byte[] fileContent = "Test PDF content".getBytes();
        Mockito.when(mockFile.getBytes()).thenReturn(fileContent);

        // Set up mock to handle transferTo by writing the file
        Mockito.doAnswer(
                        invocation -> {
                            java.io.File file = invocation.getArgument(0);
                            Files.write(file.toPath(), fileContent);
                            return null;
                        })
                .when(mockFile)
                .transferTo(ArgumentMatchers.any(java.io.File.class));

        // Act
        String fileId = fileStorage.storeFile(mockFile);

        // Assert
        Assertions.assertNotNull(fileId);
        Assertions.assertTrue(Files.exists(tempDir.resolve(fileId)));
        Mockito.verify(mockFile).transferTo(ArgumentMatchers.any(java.io.File.class));
    }

    @Test
    void testStoreBytes() throws IOException {
        // Arrange
        byte[] fileContent = "Test PDF content".getBytes();
        String originalName = "test.pdf";

        // Act
        String fileId = fileStorage.storeBytes(fileContent, originalName);

        // Assert
        Assertions.assertNotNull(fileId);
        Assertions.assertTrue(Files.exists(tempDir.resolve(fileId)));
        Assertions.assertArrayEquals(fileContent, Files.readAllBytes(tempDir.resolve(fileId)));
    }

    @Test
    void testRetrieveFile() throws IOException {
        // Arrange
        byte[] fileContent = "Test PDF content".getBytes();
        String fileId = UUID.randomUUID().toString();
        Path filePath = tempDir.resolve(fileId);
        Files.write(filePath, fileContent);

        MultipartFile expectedFile = Mockito.mock(MultipartFile.class);
        Mockito.when(
                        fileOrUploadService.toMockMultipartFile(
                                ArgumentMatchers.eq(fileId), ArgumentMatchers.eq(fileContent)))
                .thenReturn(expectedFile);

        // Act
        MultipartFile result = fileStorage.retrieveFile(fileId);

        // Assert
        Assertions.assertSame(expectedFile, result);
        Mockito.verify(fileOrUploadService)
                .toMockMultipartFile(ArgumentMatchers.eq(fileId), ArgumentMatchers.eq(fileContent));
    }

    @Test
    void testRetrieveBytes() throws IOException {
        // Arrange
        byte[] fileContent = "Test PDF content".getBytes();
        String fileId = UUID.randomUUID().toString();
        Path filePath = tempDir.resolve(fileId);
        Files.write(filePath, fileContent);

        // Act
        byte[] result = fileStorage.retrieveBytes(fileId);

        // Assert
        Assertions.assertArrayEquals(fileContent, result);
    }

    @Test
    void testRetrieveFile_FileNotFound() {
        // Arrange
        String nonExistentFileId = "non-existent-file";

        // Act & Assert
        Assertions.assertThrows(
                IOException.class, () -> fileStorage.retrieveFile(nonExistentFileId));
    }

    @Test
    void testRetrieveBytes_FileNotFound() {
        // Arrange
        String nonExistentFileId = "non-existent-file";

        // Act & Assert
        Assertions.assertThrows(
                IOException.class, () -> fileStorage.retrieveBytes(nonExistentFileId));
    }

    @Test
    void testDeleteFile() throws IOException {
        // Arrange
        byte[] fileContent = "Test PDF content".getBytes();
        String fileId = UUID.randomUUID().toString();
        Path filePath = tempDir.resolve(fileId);
        Files.write(filePath, fileContent);

        // Act
        boolean result = fileStorage.deleteFile(fileId);

        // Assert
        Assertions.assertTrue(result);
        Assertions.assertFalse(Files.exists(filePath));
    }

    @Test
    void testDeleteFile_FileNotFound() {
        // Arrange
        String nonExistentFileId = "non-existent-file";

        // Act
        boolean result = fileStorage.deleteFile(nonExistentFileId);

        // Assert
        Assertions.assertFalse(result);
    }

    @Test
    void testFileExists() throws IOException {
        // Arrange
        byte[] fileContent = "Test PDF content".getBytes();
        String fileId = UUID.randomUUID().toString();
        Path filePath = tempDir.resolve(fileId);
        Files.write(filePath, fileContent);

        // Act
        boolean result = fileStorage.fileExists(fileId);

        // Assert
        Assertions.assertTrue(result);
    }

    @Test
    void testFileExists_FileNotFound() {
        // Arrange
        String nonExistentFileId = "non-existent-file";

        // Act
        boolean result = fileStorage.fileExists(nonExistentFileId);

        // Assert
        Assertions.assertFalse(result);
    }
}
