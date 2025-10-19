package stirling.software.common.util.misc;

import java.io.IOException;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import stirling.software.common.model.api.misc.ReplaceAndInvert;

class ReplaceAndInvertColorStrategyTest {

    // A concrete implementation of the abstract class for testing
    private static class ConcreteReplaceAndInvertColorStrategy
            extends ReplaceAndInvertColorStrategy {

        public ConcreteReplaceAndInvertColorStrategy(
                MultipartFile file, ReplaceAndInvert replaceAndInvert) {
            super(file, replaceAndInvert);
        }

        @Override
        public InputStreamResource replace() throws IOException {
            // Simple implementation for testing purposes
            return new InputStreamResource(getFileInput().getInputStream());
        }
    }

    @Test
    void testConstructor() {
        // Arrange
        MultipartFile mockFile =
                new MockMultipartFile(
                        "file",
                        "test.pdf",
                        MediaType.APPLICATION_PDF_VALUE,
                        "test content".getBytes());
        ReplaceAndInvert replaceAndInvert = ReplaceAndInvert.CUSTOM_COLOR;

        // Act
        ReplaceAndInvertColorStrategy strategy =
                new ConcreteReplaceAndInvertColorStrategy(mockFile, replaceAndInvert);

        // Assert
        Assertions.assertNotNull(strategy, "Strategy should be initialized");
        Assertions.assertEquals(
                mockFile, strategy.getFileInput(), "File input should be set correctly");
        Assertions.assertEquals(
                replaceAndInvert,
                strategy.getReplaceAndInvert(),
                "ReplaceAndInvert option should be set correctly");
    }

    @Test
    void testReplace() throws IOException {
        // Arrange
        byte[] content = "test pdf content".getBytes();
        MultipartFile mockFile =
                new MockMultipartFile("file", "test.pdf", MediaType.APPLICATION_PDF_VALUE, content);
        ReplaceAndInvert replaceAndInvert = ReplaceAndInvert.CUSTOM_COLOR;

        ReplaceAndInvertColorStrategy strategy =
                new ConcreteReplaceAndInvertColorStrategy(mockFile, replaceAndInvert);

        // Act
        InputStreamResource result = strategy.replace();

        // Assert
        Assertions.assertNotNull(result, "Result should not be null");
    }

    @Test
    void testGettersAndSetters() {
        // Arrange
        MultipartFile mockFile1 =
                new MockMultipartFile(
                        "file1",
                        "test1.pdf",
                        MediaType.APPLICATION_PDF_VALUE,
                        "content1".getBytes());
        MultipartFile mockFile2 =
                new MockMultipartFile(
                        "file2",
                        "test2.pdf",
                        MediaType.APPLICATION_PDF_VALUE,
                        "content2".getBytes());

        // Act
        ReplaceAndInvertColorStrategy strategy =
                new ConcreteReplaceAndInvertColorStrategy(mockFile1, ReplaceAndInvert.CUSTOM_COLOR);

        // Test initial values
        Assertions.assertEquals(mockFile1, strategy.getFileInput());
        Assertions.assertEquals(ReplaceAndInvert.CUSTOM_COLOR, strategy.getReplaceAndInvert());

        // Test setters
        strategy.setFileInput(mockFile2);
        strategy.setReplaceAndInvert(ReplaceAndInvert.FULL_INVERSION);

        // Assert new values
        Assertions.assertEquals(mockFile2, strategy.getFileInput());
        Assertions.assertEquals(ReplaceAndInvert.FULL_INVERSION, strategy.getReplaceAndInvert());
    }
}
