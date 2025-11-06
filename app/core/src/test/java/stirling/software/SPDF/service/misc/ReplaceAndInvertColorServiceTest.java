package stirling.software.SPDF.service.misc;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.IOException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.core.io.InputStreamResource;
import org.springframework.web.multipart.MultipartFile;

import stirling.software.SPDF.Factories.ReplaceAndInvertColorFactory;
import stirling.software.common.model.api.misc.HighContrastColorCombination;
import stirling.software.common.model.api.misc.ReplaceAndInvert;
import stirling.software.common.util.misc.ReplaceAndInvertColorStrategy;

class ReplaceAndInvertColorServiceTest {

    @Mock private ReplaceAndInvertColorFactory replaceAndInvertColorFactory;

    @Mock private MultipartFile file;

    @Mock private ReplaceAndInvertColorStrategy replaceAndInvertColorStrategy;

    @InjectMocks private ReplaceAndInvertColorService replaceAndInvertColorService;

    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (mocks != null) {
            mocks.close();
        }
    }

    @Test
    void testReplaceAndInvertColor_withCustomColor() throws IOException {
        // Arrange
        ReplaceAndInvert replaceAndInvertOption = ReplaceAndInvert.CUSTOM_COLOR;
        String backGroundColor = "#FFFFFF";
        String textColor = "#000000";

        when(replaceAndInvertColorFactory.replaceAndInvert(
                        file, replaceAndInvertOption, null, backGroundColor, textColor))
                .thenReturn(replaceAndInvertColorStrategy);

        InputStreamResource expectedResource = mock(InputStreamResource.class);
        when(replaceAndInvertColorStrategy.replace()).thenReturn(expectedResource);

        // Act
        InputStreamResource result =
                replaceAndInvertColorService.replaceAndInvertColor(
                        file, replaceAndInvertOption, null, backGroundColor, textColor);

        // Assert
        assertNotNull(result);
        assertEquals(expectedResource, result);
        verify(replaceAndInvertColorFactory, times(1))
                .replaceAndInvert(file, replaceAndInvertOption, null, backGroundColor, textColor);
        verify(replaceAndInvertColorStrategy, times(1)).replace();
    }

    @Test
    void testReplaceAndInvertColor_withHighContrastColor() throws IOException {
        // Arrange
        ReplaceAndInvert replaceAndInvertOption = ReplaceAndInvert.HIGH_CONTRAST_COLOR;
        HighContrastColorCombination highContrastColorCombination =
                HighContrastColorCombination.WHITE_TEXT_ON_BLACK;

        when(replaceAndInvertColorFactory.replaceAndInvert(
                        file, replaceAndInvertOption, highContrastColorCombination, null, null))
                .thenReturn(replaceAndInvertColorStrategy);

        InputStreamResource expectedResource = mock(InputStreamResource.class);
        when(replaceAndInvertColorStrategy.replace()).thenReturn(expectedResource);

        // Act
        InputStreamResource result =
                replaceAndInvertColorService.replaceAndInvertColor(
                        file, replaceAndInvertOption, highContrastColorCombination, null, null);

        // Assert
        assertNotNull(result);
        assertEquals(expectedResource, result);
        verify(replaceAndInvertColorFactory, times(1))
                .replaceAndInvert(
                        file, replaceAndInvertOption, highContrastColorCombination, null, null);
        verify(replaceAndInvertColorStrategy, times(1)).replace();
    }

    @Test
    void testReplaceAndInvertColor_withFullInversion() throws IOException {
        // Arrange
        ReplaceAndInvert replaceAndInvertOption = ReplaceAndInvert.FULL_INVERSION;

        when(replaceAndInvertColorFactory.replaceAndInvert(
                        file, replaceAndInvertOption, null, null, null))
                .thenReturn(replaceAndInvertColorStrategy);

        InputStreamResource expectedResource = mock(InputStreamResource.class);
        when(replaceAndInvertColorStrategy.replace()).thenReturn(expectedResource);

        // Act
        InputStreamResource result =
                replaceAndInvertColorService.replaceAndInvertColor(
                        file, replaceAndInvertOption, null, null, null);

        // Assert
        assertNotNull(result);
        assertEquals(expectedResource, result);
        verify(replaceAndInvertColorFactory, times(1))
                .replaceAndInvert(file, replaceAndInvertOption, null, null, null);
        verify(replaceAndInvertColorStrategy, times(1)).replace();
    }

    @Test
    void testReplaceAndInvertColor_withCMYKConversion() throws IOException {
        // Arrange
        ReplaceAndInvert replaceAndInvertOption = ReplaceAndInvert.COLOR_SPACE_CONVERSION;

        when(replaceAndInvertColorFactory.replaceAndInvert(
                        file, replaceAndInvertOption, null, null, null))
                .thenReturn(replaceAndInvertColorStrategy);

        InputStreamResource expectedResource = mock(InputStreamResource.class);
        when(replaceAndInvertColorStrategy.replace()).thenReturn(expectedResource);

        // Act
        InputStreamResource result =
                replaceAndInvertColorService.replaceAndInvertColor(
                        file, replaceAndInvertOption, null, null, null);

        // Assert
        assertNotNull(result);
        assertEquals(expectedResource, result);
        verify(replaceAndInvertColorFactory, times(1))
                .replaceAndInvert(file, replaceAndInvertOption, null, null, null);
        verify(replaceAndInvertColorStrategy, times(1)).replace();
    }

    @Test
    void testReplaceAndInvertColor_withAllHighContrastCombinations() throws IOException {
        // Test all high contrast color combinations
        HighContrastColorCombination[] combinations = {
            HighContrastColorCombination.WHITE_TEXT_ON_BLACK,
            HighContrastColorCombination.BLACK_TEXT_ON_WHITE,
            HighContrastColorCombination.YELLOW_TEXT_ON_BLACK,
            HighContrastColorCombination.GREEN_TEXT_ON_BLACK
        };

        for (HighContrastColorCombination combination : combinations) {
            // Arrange
            reset(replaceAndInvertColorFactory, replaceAndInvertColorStrategy);

            when(replaceAndInvertColorFactory.replaceAndInvert(
                            file, ReplaceAndInvert.HIGH_CONTRAST_COLOR, combination, null, null))
                    .thenReturn(replaceAndInvertColorStrategy);

            InputStreamResource expectedResource = mock(InputStreamResource.class);
            when(replaceAndInvertColorStrategy.replace()).thenReturn(expectedResource);

            // Act
            InputStreamResource result =
                    replaceAndInvertColorService.replaceAndInvertColor(
                            file, ReplaceAndInvert.HIGH_CONTRAST_COLOR, combination, null, null);

            // Assert
            assertNotNull(result);
            assertEquals(expectedResource, result);
            verify(replaceAndInvertColorFactory, times(1))
                    .replaceAndInvert(
                            file, ReplaceAndInvert.HIGH_CONTRAST_COLOR, combination, null, null);
            verify(replaceAndInvertColorStrategy, times(1)).replace();
        }
    }

    @Test
    void testReplaceAndInvertColor_withDifferentCustomColors() throws IOException {
        // Test with different color combinations
        String[][] colorPairs = {
            {"#FFFFFF", "#000000"}, // White background, black text
            {"#000000", "#FFFFFF"}, // Black background, white text
            {"#FFFF00", "#000000"}, // Yellow background, black text
            {"#000000", "#00FF00"} // Black background, green text
        };

        for (String[] colors : colorPairs) {
            // Arrange
            reset(replaceAndInvertColorFactory, replaceAndInvertColorStrategy);
            String backgroundColor = colors[0];
            String textColor = colors[1];

            when(replaceAndInvertColorFactory.replaceAndInvert(
                            file, ReplaceAndInvert.CUSTOM_COLOR, null, backgroundColor, textColor))
                    .thenReturn(replaceAndInvertColorStrategy);

            InputStreamResource expectedResource = mock(InputStreamResource.class);
            when(replaceAndInvertColorStrategy.replace()).thenReturn(expectedResource);

            // Act
            InputStreamResource result =
                    replaceAndInvertColorService.replaceAndInvertColor(
                            file, ReplaceAndInvert.CUSTOM_COLOR, null, backgroundColor, textColor);

            // Assert
            assertNotNull(result);
            assertEquals(expectedResource, result);
            verify(replaceAndInvertColorFactory, times(1))
                    .replaceAndInvert(
                            file, ReplaceAndInvert.CUSTOM_COLOR, null, backgroundColor, textColor);
            verify(replaceAndInvertColorStrategy, times(1)).replace();
        }
    }

    @Test
    void testReplaceAndInvertColor_strategyThrowsException() throws IOException {
        // Arrange
        ReplaceAndInvert replaceAndInvertOption = ReplaceAndInvert.CUSTOM_COLOR;
        String backGroundColor = "#FFFFFF";
        String textColor = "#000000";

        when(replaceAndInvertColorFactory.replaceAndInvert(
                        file, replaceAndInvertOption, null, backGroundColor, textColor))
                .thenReturn(replaceAndInvertColorStrategy);

        IOException expectedException = new IOException("Test exception");
        when(replaceAndInvertColorStrategy.replace()).thenThrow(expectedException);

        // Act & Assert
        IOException thrown =
                assertThrows(
                        IOException.class,
                        () ->
                                replaceAndInvertColorService.replaceAndInvertColor(
                                        file,
                                        replaceAndInvertOption,
                                        null,
                                        backGroundColor,
                                        textColor));

        assertEquals(expectedException, thrown);
        verify(replaceAndInvertColorFactory, times(1))
                .replaceAndInvert(file, replaceAndInvertOption, null, backGroundColor, textColor);
        verify(replaceAndInvertColorStrategy, times(1)).replace();
    }
}
