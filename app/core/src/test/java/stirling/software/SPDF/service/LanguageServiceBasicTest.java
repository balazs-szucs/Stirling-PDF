package stirling.software.SPDF.service;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.core.io.Resource;

import stirling.software.common.model.ApplicationProperties;
import stirling.software.common.model.ApplicationProperties.Ui;

class LanguageServiceBasicTest {

    private LanguageService languageService;
    private ApplicationProperties applicationProperties;

    // Helper methods
    private static Resource createMockResource(String filename) {
        Resource mockResource = Mockito.mock(Resource.class);
        Mockito.when(mockResource.getFilename()).thenReturn(filename);
        return mockResource;
    }

    @BeforeEach
    void setUp() {
        // Mock application properties
        applicationProperties = Mockito.mock(ApplicationProperties.class);
        Ui ui = Mockito.mock(Ui.class);
        Mockito.when(applicationProperties.getUi()).thenReturn(ui);

        // Create language service with test implementation
        languageService = new LanguageServiceForTest(applicationProperties);
    }

    @Test
    void testGetSupportedLanguages_BasicFunctionality() {
        // Set up mocked resources
        Resource enResource = createMockResource("messages_en_US.properties");
        Resource frResource = createMockResource("messages_fr_FR.properties");
        Resource[] mockResources = {enResource, frResource};

        // Configure the test service
        ((LanguageServiceForTest) languageService).setMockResources(mockResources);
        Mockito.when(applicationProperties.getUi().getLanguages())
                .thenReturn(Collections.emptyList());

        // Execute the method
        Set<String> supportedLanguages = languageService.getSupportedLanguages();

        // Basic assertions
        Assertions.assertTrue(supportedLanguages.contains("en_US"), "en_US should be included");
        Assertions.assertTrue(supportedLanguages.contains("fr_FR"), "fr_FR should be included");
    }

    @Test
    void testGetSupportedLanguages_FilteringInvalidFiles() {
        // Set up mocked resources with invalid files
        Resource[] mockResources = {
            createMockResource("messages_en_US.properties"), // Valid
            createMockResource("invalid_file.properties"), // Invalid
            createMockResource(null) // Null filename
        };

        // Configure the test service
        ((LanguageServiceForTest) languageService).setMockResources(mockResources);
        Mockito.when(applicationProperties.getUi().getLanguages())
                .thenReturn(Collections.emptyList());

        // Execute the method
        Set<String> supportedLanguages = languageService.getSupportedLanguages();

        // Verify filtering
        Assertions.assertTrue(
                supportedLanguages.contains("en_US"), "Valid language should be included");
        Assertions.assertFalse(
                supportedLanguages.contains("invalid_file"),
                "Invalid filename should be filtered out");
    }

    @Test
    void testGetSupportedLanguages_WithRestrictions() {
        // Set up test resources
        Resource[] mockResources = {
            createMockResource("messages_en_US.properties"),
            createMockResource("messages_fr_FR.properties"),
            createMockResource("messages_de_DE.properties"),
            createMockResource("messages_en_GB.properties")
        };

        // Configure the test service
        ((LanguageServiceForTest) languageService).setMockResources(mockResources);

        // Allow only specific languages (en_GB is always included)
        Mockito.when(applicationProperties.getUi().getLanguages())
                .thenReturn(Arrays.asList("en_US", "fr_FR"));

        // Execute the method
        Set<String> supportedLanguages = languageService.getSupportedLanguages();

        // Verify filtering by restrictions
        Assertions.assertTrue(
                supportedLanguages.contains("en_US"), "Allowed language should be included");
        Assertions.assertTrue(
                supportedLanguages.contains("fr_FR"), "Allowed language should be included");
        Assertions.assertTrue(
                supportedLanguages.contains("en_GB"), "en_GB should always be included");
        Assertions.assertFalse(
                supportedLanguages.contains("de_DE"), "Restricted language should be excluded");
    }

    // Test subclass
    private static class LanguageServiceForTest extends LanguageService {
        private Resource[] mockResources;

        public LanguageServiceForTest(ApplicationProperties applicationProperties) {
            super(applicationProperties);
        }

        public void setMockResources(Resource[] mockResources) {
            this.mockResources = mockResources;
        }

        @Override
        protected Resource[] getResourcesFromPattern(String pattern) {
            return mockResources;
        }
    }
}
