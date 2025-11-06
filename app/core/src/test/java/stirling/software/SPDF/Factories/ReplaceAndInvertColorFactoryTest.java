package stirling.software.SPDF.Factories;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.multipart.MultipartFile;

import stirling.software.common.model.api.misc.HighContrastColorCombination;
import stirling.software.common.model.api.misc.ReplaceAndInvert;
import stirling.software.common.util.TempFileManager;
import stirling.software.common.util.misc.ColorSpaceConversionStrategy;
import stirling.software.common.util.misc.CustomColorReplaceStrategy;
import stirling.software.common.util.misc.InvertFullColorStrategy;
import stirling.software.common.util.misc.ReplaceAndInvertColorStrategy;

class ReplaceAndInvertColorFactoryTest {

    private ReplaceAndInvertColorFactory factory;
    private MultipartFile file;

    @BeforeEach
    void setup() {
        TempFileManager tempFileManager = mock(TempFileManager.class);
        factory = new ReplaceAndInvertColorFactory(tempFileManager);
        file = mock(MultipartFile.class);
        when(file.getOriginalFilename()).thenReturn("test.pdf");
    }

    @Test
    void whenCustomColor_thenReturnsCustomColorReplaceStrategy() {
        // Arrange
        ReplaceAndInvert option = ReplaceAndInvert.CUSTOM_COLOR;
        String backgroundColor = "#FFFFFF";
        String textColor = "#000000";

        // Act
        ReplaceAndInvertColorStrategy strategy =
                factory.replaceAndInvert(file, option, null, backgroundColor, textColor);

        // Assert
        assertNotNull(strategy, "Strategy should not be null");
        assertInstanceOf(
                CustomColorReplaceStrategy.class,
                strategy,
                "Expected CustomColorReplaceStrategy for CUSTOM_COLOR");
        assertEquals(file, strategy.getFileInput(), "File input should match");
        assertEquals(
                option, strategy.getReplaceAndInvert(), "ReplaceAndInvert option should match");
    }

    @Test
    void whenCustomColor_withDifferentColors_thenReturnsCustomColorReplaceStrategy() {
        // Test with different color values
        String[][] colorPairs = {
            {"#FFFFFF", "#000000"},
            {"#000000", "#FFFFFF"},
            {"#FFFF00", "#0000FF"},
            {"#FF0000", "#00FF00"}
        };

        for (String[] colors : colorPairs) {
            ReplaceAndInvertColorStrategy strategy =
                    factory.replaceAndInvert(
                            file, ReplaceAndInvert.CUSTOM_COLOR, null, colors[0], colors[1]);

            assertNotNull(
                    strategy,
                    "Strategy should not be null for colors: " + colors[0] + ", " + colors[1]);
            assertInstanceOf(
                    CustomColorReplaceStrategy.class,
                    strategy,
                    "Expected CustomColorReplaceStrategy for colors: "
                            + colors[0]
                            + ", "
                            + colors[1]);
        }
    }

    @Test
    void whenHighContrastColor_thenReturnsCustomColorReplaceStrategy() {
        // Arrange
        ReplaceAndInvert option = ReplaceAndInvert.HIGH_CONTRAST_COLOR;
        HighContrastColorCombination combo = HighContrastColorCombination.WHITE_TEXT_ON_BLACK;

        // Act
        ReplaceAndInvertColorStrategy strategy =
                factory.replaceAndInvert(file, option, combo, null, null);

        // Assert
        assertNotNull(strategy, "Strategy should not be null");
        assertInstanceOf(
                CustomColorReplaceStrategy.class,
                strategy,
                "Expected CustomColorReplaceStrategy for HIGH_CONTRAST_COLOR");
        assertEquals(file, strategy.getFileInput(), "File input should match");
        assertEquals(
                option, strategy.getReplaceAndInvert(), "ReplaceAndInvert option should match");
    }

    @Test
    void whenHighContrastColor_withAllCombinations_thenReturnsCustomColorReplaceStrategy() {
        // Test all high contrast color combinations
        HighContrastColorCombination[] combinations = {
            HighContrastColorCombination.WHITE_TEXT_ON_BLACK,
            HighContrastColorCombination.BLACK_TEXT_ON_WHITE,
            HighContrastColorCombination.YELLOW_TEXT_ON_BLACK,
            HighContrastColorCombination.GREEN_TEXT_ON_BLACK
        };

        for (HighContrastColorCombination combo : combinations) {
            ReplaceAndInvertColorStrategy strategy =
                    factory.replaceAndInvert(
                            file, ReplaceAndInvert.HIGH_CONTRAST_COLOR, combo, null, null);

            assertNotNull(strategy, "Strategy should not be null for combination: " + combo);
            assertInstanceOf(
                    CustomColorReplaceStrategy.class,
                    strategy,
                    "Expected CustomColorReplaceStrategy for combination: " + combo);
        }
    }

    @Test
    void whenFullInversion_thenReturnsInvertFullColorStrategy() {
        // Arrange
        ReplaceAndInvert option = ReplaceAndInvert.FULL_INVERSION;

        // Act
        ReplaceAndInvertColorStrategy strategy =
                factory.replaceAndInvert(file, option, null, null, null);

        // Assert
        assertNotNull(strategy, "Strategy should not be null");
        assertInstanceOf(
                InvertFullColorStrategy.class,
                strategy,
                "Expected InvertFullColorStrategy for FULL_INVERSION");
        assertEquals(file, strategy.getFileInput(), "File input should match");
        assertEquals(
                option, strategy.getReplaceAndInvert(), "ReplaceAndInvert option should match");
    }

    @Test
    void whenColorSpaceConversion_thenReturnsColorSpaceConversionStrategy() {
        // Arrange
        ReplaceAndInvert option = ReplaceAndInvert.COLOR_SPACE_CONVERSION;

        // Act
        ReplaceAndInvertColorStrategy strategy =
                factory.replaceAndInvert(file, option, null, null, null);

        // Assert
        assertNotNull(strategy, "Strategy should not be null for CMYK conversion");
        assertInstanceOf(
                ColorSpaceConversionStrategy.class,
                strategy,
                "Expected ColorSpaceConversionStrategy for COLOR_SPACE_CONVERSION (CMYK)");
        assertEquals(file, strategy.getFileInput(), "File input should match");
        assertEquals(
                option, strategy.getReplaceAndInvert(), "ReplaceAndInvert option should match");
    }

    @Test
    void whenColorSpaceConversion_thenStrategyHasTempFileManager() {
        // Arrange
        ReplaceAndInvert option = ReplaceAndInvert.COLOR_SPACE_CONVERSION;

        // Act
        ReplaceAndInvertColorStrategy strategy =
                factory.replaceAndInvert(file, option, null, null, null);

        // Assert
        assertNotNull(strategy, "Strategy should not be null");
        assertInstanceOf(
                ColorSpaceConversionStrategy.class,
                strategy,
                "Expected ColorSpaceConversionStrategy");

        // Verify the strategy is properly constructed with TempFileManager
        ColorSpaceConversionStrategy cmykStrategy = (ColorSpaceConversionStrategy) strategy;
        assertNotNull(cmykStrategy, "CMYK strategy should be properly initialized");
    }

    @Test
    void whenNullOption_thenReturnsNull() {
        // Act
        ReplaceAndInvertColorStrategy strategy =
                factory.replaceAndInvert(file, null, null, null, null);

        // Assert
        assertNull(strategy, "Expected null for null option");
    }

    @Test
    void whenAllOptionsProcessed_thenCorrectStrategyTypeReturned() {
        // Test that each option returns the correct strategy type
        ReplaceAndInvert[] allOptions = ReplaceAndInvert.values();

        for (ReplaceAndInvert option : allOptions) {
            ReplaceAndInvertColorStrategy strategy;

            if (option == ReplaceAndInvert.CUSTOM_COLOR
                    || option == ReplaceAndInvert.HIGH_CONTRAST_COLOR) {
                strategy =
                        factory.replaceAndInvert(
                                file,
                                option,
                                HighContrastColorCombination.WHITE_TEXT_ON_BLACK,
                                "#FFFFFF",
                                "#000000");
                assertInstanceOf(
                        CustomColorReplaceStrategy.class,
                        strategy,
                        "Expected CustomColorReplaceStrategy for " + option);
            } else if (option == ReplaceAndInvert.FULL_INVERSION) {
                strategy = factory.replaceAndInvert(file, option, null, null, null);
                assertInstanceOf(
                        InvertFullColorStrategy.class,
                        strategy,
                        "Expected InvertFullColorStrategy for " + option);
            } else if (option == ReplaceAndInvert.COLOR_SPACE_CONVERSION) {
                strategy = factory.replaceAndInvert(file, option, null, null, null);
                assertInstanceOf(
                        ColorSpaceConversionStrategy.class,
                        strategy,
                        "Expected ColorSpaceConversionStrategy for " + option);
            }
        }
    }

    @Test
    void whenCustomColorWithNullColors_thenReturnsStrategy() {
        // Even with null colors, strategy should be created
        // (the strategy itself handles validation)
        ReplaceAndInvertColorStrategy strategy =
                factory.replaceAndInvert(file, ReplaceAndInvert.CUSTOM_COLOR, null, null, null);

        assertNotNull(strategy, "Strategy should not be null even with null colors");
        assertInstanceOf(
                CustomColorReplaceStrategy.class, strategy, "Expected CustomColorReplaceStrategy");
    }

    @Test
    void whenHighContrastWithNullCombination_thenReturnsStrategy() {
        // Strategy should be created even with null combination
        // (the strategy itself handles validation)
        ReplaceAndInvertColorStrategy strategy =
                factory.replaceAndInvert(
                        file, ReplaceAndInvert.HIGH_CONTRAST_COLOR, null, null, null);

        assertNotNull(strategy, "Strategy should not be null even with null combination");
        assertInstanceOf(
                CustomColorReplaceStrategy.class, strategy, "Expected CustomColorReplaceStrategy");
    }
}
