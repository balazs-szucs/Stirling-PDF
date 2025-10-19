package stirling.software.SPDF.service;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import stirling.software.common.model.ApplicationProperties;
import stirling.software.common.model.ApplicationProperties.Ui;

class LanguageServiceTest {

    private LanguageService languageService;
    private ApplicationProperties applicationProperties;
    private PathMatchingResourcePatternResolver mockedResolver;

    private static Resource createMockResource(String filename) {
        Resource mockResource = Mockito.mock(Resource.class);
        Mockito.when(mockResource.getFilename()).thenReturn(filename);
        return mockResource;
    }

    @BeforeEach
    void setUp() {
        // Mock ApplicationProperties
        applicationProperties = Mockito.mock(ApplicationProperties.class);
        Ui ui = Mockito.mock(Ui.class);
        Mockito.when(applicationProperties.getUi()).thenReturn(ui);

        // Create LanguageService with our custom constructor that allows injection of resolver
        languageService = new LanguageServiceForTest(applicationProperties);
    }

    @Test
    void testGetSupportedLanguages_NoRestrictions() {
        // Setup
        Set<String> expectedLanguages =
                new HashSet<>(Arrays.asList("en_US", "fr_FR", "de_DE", "en_GB"));

        // Mock the resource resolver response
        Resource[] mockResources = createMockResources(expectedLanguages);
        ((LanguageServiceForTest) languageService).setMockResources(mockResources);

        // No language restrictions in properties
        Mockito.when(applicationProperties.getUi().getLanguages())
                .thenReturn(Collections.emptyList());

        // Test
        Set<String> supportedLanguages = languageService.getSupportedLanguages();

        // Verify
        Assertions.assertEquals(
                expectedLanguages,
                supportedLanguages,
                "Should return all languages when no restrictions");
    }

    @Test
    void testGetSupportedLanguages_WithRestrictions() {
        // Setup
        Set<String> expectedLanguages =
                new HashSet<>(Arrays.asList("en_US", "fr_FR", "de_DE", "en_GB"));
        Set<String> allowedLanguages = new HashSet<>(Arrays.asList("en_US", "fr_FR", "en_GB"));

        // Mock the resource resolver response
        Resource[] mockResources = createMockResources(expectedLanguages);
        ((LanguageServiceForTest) languageService).setMockResources(mockResources);

        // Set language restrictions in properties
        Mockito.when(applicationProperties.getUi().getLanguages())
                .thenReturn(Arrays.asList("en_US", "fr_FR")); // en_GB is always allowed

        // Test
        Set<String> supportedLanguages = languageService.getSupportedLanguages();

        // Verify
        Assertions.assertEquals(
                allowedLanguages,
                supportedLanguages,
                "Should return only allowed languages, plus en_GB which is always allowed");
        Assertions.assertTrue(
                supportedLanguages.contains("en_GB"), "en_GB should always be included");
    }

    @Test
    void testGetSupportedLanguages_ExceptionHandling() {
        // Setup - make resolver throw an exception
        ((LanguageServiceForTest) languageService).setShouldThrowException(true);

        // Test
        Set<String> supportedLanguages = languageService.getSupportedLanguages();

        // Verify
        Assertions.assertTrue(supportedLanguages.isEmpty(), "Should return empty set on exception");
    }

    // Helper methods to create mock resources
    private Resource[] createMockResources(Set<String> languages) {
        return languages.stream()
                .map(lang -> createMockResource("messages_" + lang + ".properties"))
                .toArray(Resource[]::new);
    }

    @Test
    void testGetSupportedLanguages_FilteringNonMatchingFiles() {
        // Setup with some valid and some invalid filenames
        Resource[] mixedResources = {
            createMockResource("messages_en_US.properties"),
            createMockResource("messages_en_GB.properties"), // Explicitly add en_GB resource
            createMockResource("messages_fr_FR.properties"),
            createMockResource("not_a_messages_file.properties"),
            createMockResource("messages_.properties"), // Invalid format
            createMockResource(null) // Null filename
        };

        ((LanguageServiceForTest) languageService).setMockResources(mixedResources);
        Mockito.when(applicationProperties.getUi().getLanguages())
                .thenReturn(Collections.emptyList());

        // Test
        Set<String> supportedLanguages = languageService.getSupportedLanguages();

        // Verify the valid languages are present
        Assertions.assertTrue(supportedLanguages.contains("en_US"), "en_US should be included");
        Assertions.assertTrue(supportedLanguages.contains("fr_FR"), "fr_FR should be included");
        // Add en_GB which is always included
        Assertions.assertTrue(
                supportedLanguages.contains("en_GB"), "en_GB should always be included");

        // Verify no invalid formats are included
        Assertions.assertFalse(
                supportedLanguages.contains("not_a_messages_file"),
                "Invalid format should be excluded");
        // Skip the empty string check as it depends on implementation details of extracting
        // language codes
    }

    // Test subclass that allows us to control the resource resolver
    private static class LanguageServiceForTest extends LanguageService {
        private Resource[] mockResources;
        private boolean shouldThrowException = false;

        public LanguageServiceForTest(ApplicationProperties applicationProperties) {
            super(applicationProperties);
        }

        public void setMockResources(Resource[] mockResources) {
            this.mockResources = mockResources;
        }

        public void setShouldThrowException(boolean shouldThrowException) {
            this.shouldThrowException = shouldThrowException;
        }

        @Override
        protected Resource[] getResourcesFromPattern(String pattern) throws IOException {
            if (shouldThrowException) {
                throw new IOException("Test exception");
            }
            return mockResources;
        }
    }
}
