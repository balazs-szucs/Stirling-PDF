package stirling.software.common.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class GeneralUtilsTest {

    @Test
    void testParsePageListWithAll() {
        List<Integer> result = GeneralUtils.parsePageList(new String[] {"all"}, 5, false);
        Assertions.assertEquals(
                List.of(0, 1, 2, 3, 4), result, "'All' keyword should return all pages.");
    }

    @Test
    void testParsePageListWithAllOneBased() {
        List<Integer> result = GeneralUtils.parsePageList(new String[] {"all"}, 5, true);
        Assertions.assertEquals(
                List.of(1, 2, 3, 4, 5), result, "'All' keyword should return all pages.");
    }

    @Test
    void nFunc() {
        List<Integer> result = GeneralUtils.parsePageList(new String[] {"n"}, 5, true);
        Assertions.assertEquals(
                List.of(1, 2, 3, 4, 5), result, "'n' keyword should return all pages.");
    }

    @Test
    void nFuncAdvanced() {
        List<Integer> result = GeneralUtils.parsePageList(new String[] {"4n"}, 9, true);
        // skip 0 as not valid
        Assertions.assertEquals(List.of(4, 8), result, "'All' keyword should return all pages.");
    }

    @Test
    void nFuncAdvancedZero() {
        List<Integer> result = GeneralUtils.parsePageList(new String[] {"4n"}, 9, false);
        // skip 0 as not valid
        Assertions.assertEquals(List.of(3, 7), result, "'All' keyword should return all pages.");
    }

    @Test
    void nFuncAdvanced2() {
        List<Integer> result = GeneralUtils.parsePageList(new String[] {"4n-1"}, 9, true);
        // skip -1 as not valid
        Assertions.assertEquals(List.of(3, 7), result, "4n-1 should do (0-1), (4-1), (8-1)");
    }

    @Test
    void nFuncAdvanced3() {
        List<Integer> result = GeneralUtils.parsePageList(new String[] {"4n+1"}, 9, true);
        Assertions.assertEquals(List.of(5, 9), result, "'All' keyword should return all pages.");
    }

    @Test
    void nFunc_spaces() {
        List<Integer> result = GeneralUtils.parsePageList(new String[] {"n + 1"}, 9, true);
        Assertions.assertEquals(List.of(2, 3, 4, 5, 6, 7, 8, 9), result);
    }

    @Test
    void nFunc_consecutive_Ns_nnn() {
        List<Integer> result = GeneralUtils.parsePageList(new String[] {"nnn"}, 9, true);
        Assertions.assertEquals(List.of(1, 8), result);
    }

    @Test
    void nFunc_consecutive_Ns_nn() {
        List<Integer> result = GeneralUtils.parsePageList(new String[] {"nn"}, 9, true);
        Assertions.assertEquals(List.of(1, 4, 9), result);
    }

    @Test
    void nFunc_opening_closing_round_brackets() {
        List<Integer> result = GeneralUtils.parsePageList(new String[] {"(n-1)(n-2)"}, 9, true);
        Assertions.assertEquals(List.of(2, 6), result);
    }

    @Test
    void nFunc_opening_round_brackets() {
        List<Integer> result = GeneralUtils.parsePageList(new String[] {"2(n-1)"}, 9, true);
        Assertions.assertEquals(List.of(2, 4, 6, 8), result);
    }

    @Test
    void nFunc_opening_round_brackets_n() {
        List<Integer> result = GeneralUtils.parsePageList(new String[] {"n(n-1)"}, 9, true);
        Assertions.assertEquals(List.of(2, 6), result);
    }

    @Test
    void nFunc_closing_round_brackets() {
        List<Integer> result = GeneralUtils.parsePageList(new String[] {"(n-1)2"}, 9, true);
        Assertions.assertEquals(List.of(2, 4, 6, 8), result);
    }

    @Test
    void nFunc_closing_round_brackets_n() {
        List<Integer> result = GeneralUtils.parsePageList(new String[] {"(n-1)n"}, 9, true);
        Assertions.assertEquals(List.of(2, 6), result);
    }

    @Test
    void nFunc_function_surrounded_with_brackets() {
        List<Integer> result = GeneralUtils.parsePageList(new String[] {"(n-1)"}, 9, true);
        Assertions.assertEquals(List.of(1, 2, 3, 4, 5, 6, 7, 8), result);
    }

    @Test
    void nFuncAdvanced4() {
        List<Integer> result = GeneralUtils.parsePageList(new String[] {"3+2n"}, 9, true);
        Assertions.assertEquals(List.of(5, 7, 9), result, "'All' keyword should return all pages.");
    }

    @Test
    void nFuncAdvancedZerobased() {
        List<Integer> result = GeneralUtils.parsePageList(new String[] {"4n"}, 9, false);
        Assertions.assertEquals(List.of(3, 7), result, "'All' keyword should return all pages.");
    }

    @Test
    void nFuncAdvanced2Zerobased() {
        List<Integer> result = GeneralUtils.parsePageList(new String[] {"4n-1"}, 9, false);
        Assertions.assertEquals(List.of(2, 6), result, "'All' keyword should return all pages.");
    }

    @Test
    void testParsePageListWithRangeOneBasedOutput() {
        List<Integer> result = GeneralUtils.parsePageList(new String[] {"1-3"}, 5, true);
        Assertions.assertEquals(List.of(1, 2, 3), result, "Range should be parsed correctly.");
    }

    @Test
    void testParsePageListWithRangeZeroBaseOutput() {
        List<Integer> result = GeneralUtils.parsePageList(new String[] {"1-3"}, 5, false);
        Assertions.assertEquals(List.of(0, 1, 2), result, "Range should be parsed correctly.");
    }

    @Test
    void testParsePageListWithRangeOneBasedOutputFull() {
        List<Integer> result = GeneralUtils.parsePageList(new String[] {"1,3,7-8"}, 8, true);
        Assertions.assertEquals(List.of(1, 3, 7, 8), result, "Range should be parsed correctly.");
    }

    @Test
    void testParsePageListWithRangeOneBasedOutputFullOutOfRange() {
        List<Integer> result = GeneralUtils.parsePageList(new String[] {"1,3,7-8"}, 5, true);
        Assertions.assertEquals(List.of(1, 3), result, "Range should be parsed correctly.");
    }

    @Test
    void testParsePageListWithRangeZeroBaseOutputFull() {
        List<Integer> result = GeneralUtils.parsePageList(new String[] {"1,3,7-8"}, 8, false);
        Assertions.assertEquals(List.of(0, 2, 6, 7), result, "Range should be parsed correctly.");
    }

    @Test
    void testRemoveExtension() {
        // Test common cases (should use fast string operations)
        Assertions.assertEquals("document", GeneralUtils.removeExtension("document.pdf"));
        Assertions.assertEquals("image", GeneralUtils.removeExtension("image.jpg"));
        Assertions.assertEquals("file.backup", GeneralUtils.removeExtension("file.backup.zip"));
        Assertions.assertEquals(
                "complex.file.name", GeneralUtils.removeExtension("complex.file.name.txt"));

        // Test edge cases (should fall back to regex)
        Assertions.assertEquals("default", GeneralUtils.removeExtension(null));
        Assertions.assertEquals("noextension", GeneralUtils.removeExtension("noextension"));
        Assertions.assertEquals(
                ".hidden", GeneralUtils.removeExtension(".hidden")); // Hidden file, no extension
        Assertions.assertEquals(
                "trailing.", GeneralUtils.removeExtension("trailing.")); // Trailing dot
        Assertions.assertEquals("", GeneralUtils.removeExtension(""));
        Assertions.assertEquals("a", GeneralUtils.removeExtension("a"));

        // Test multiple dots
        Assertions.assertEquals(
                "file.with.multiple", GeneralUtils.removeExtension("file.with.multiple.dots"));
        Assertions.assertEquals("path/to/file", GeneralUtils.removeExtension("path/to/file.ext"));
    }

    @Test
    void testAppendSuffix() {
        // Normal cases
        Assertions.assertEquals(
                "document_processed", GeneralUtils.appendSuffix("document", "_processed"));
        Assertions.assertEquals("file.txt", GeneralUtils.appendSuffix("file", ".txt"));

        // Null handling
        Assertions.assertEquals("default_suffix", GeneralUtils.appendSuffix(null, "_suffix"));
        Assertions.assertEquals("basename", GeneralUtils.appendSuffix("basename", null));
        Assertions.assertEquals("default", GeneralUtils.appendSuffix(null, null));

        // Empty strings
        Assertions.assertEquals("_suffix", GeneralUtils.appendSuffix("", "_suffix"));
        Assertions.assertEquals("basename", GeneralUtils.appendSuffix("basename", ""));
    }

    @Test
    void testProcessFilenames() {
        List<String> filenames = new ArrayList<>();
        filenames.add("document.pdf");
        filenames.add("image.jpg");
        filenames.add("spreadsheet.xlsx");
        filenames.add("presentation.pptx");
        filenames.add(null); // Should handle null gracefully
        filenames.add("noextension");

        List<String> results = new ArrayList<>();
        GeneralUtils.processFilenames(filenames, "_processed", results::add);

        List<String> expected =
                List.of(
                        "document_processed",
                        "image_processed",
                        "spreadsheet_processed",
                        "presentation_processed",
                        "default_processed",
                        "noextension_processed");

        Assertions.assertEquals(expected, results);
    }

    @Test
    void testProcessFilenamesNullHandling() {
        List<String> results = new ArrayList<>();

        // Null filenames list
        GeneralUtils.processFilenames(null, "_suffix", results::add);
        Assertions.assertTrue(results.isEmpty(), "Should handle null filenames list");

        // Null processor
        List<String> filenames = List.of("test.txt");
        GeneralUtils.processFilenames(filenames, "_suffix", null); // Should not throw
    }

    @Test
    void testRemoveExtensionThreadSafety() throws InterruptedException {
        final int threadCount = 50;
        final int operationsPerThread = 100;
        final String[] testFilenames = {
            "document.pdf", "image.jpg", "data.csv", "presentation.pptx",
            "archive.zip", "music.mp3", "video.mp4", "text.txt"
        };

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        List<Exception> exceptions = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < threadCount; i++) {
            executor.submit(
                    () -> {
                        try {
                            for (int j = 0; j < operationsPerThread; j++) {
                                String filename = testFilenames[j % testFilenames.length];
                                String result = GeneralUtils.removeExtension(filename);

                                // Verify result is correct
                                Assertions.assertFalse(
                                        result.contains("."),
                                        "Result should not contain extension: " + result);
                                Assertions.assertTrue(
                                        filename.startsWith(result),
                                        "Original should start with result: "
                                                + filename
                                                + " -> "
                                                + result);
                            }
                            successCount.incrementAndGet();
                        } catch (Exception e) {
                            exceptions.add(e);
                        } finally {
                            latch.countDown();
                        }
                    });
        }

        Assertions.assertTrue(latch.await(10, TimeUnit.SECONDS), "All threads should complete");

        if (!exceptions.isEmpty()) {
            Assertions.fail("Thread safety test failed with exceptions: " + exceptions);
        }

        Assertions.assertEquals(threadCount, successCount.get(), "All threads should succeed");

        executor.shutdown();
    }

    @Test
    void testBatchProcessingPerformance() {
        List<String> filenames = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            filenames.add("file" + i + ".pdf");
            filenames.add("document" + i + ".docx");
            filenames.add("image" + i + ".jpg");
        }

        List<String> results = new ArrayList<>();

        GeneralUtils.processFilenames(filenames, "_processed", results::add);

        Assertions.assertEquals(filenames.size(), results.size(), "Should process all filenames");

        Assertions.assertTrue(
                results.contains("file0_processed"), "Should contain processed filename");
        Assertions.assertTrue(
                results.contains("document500_processed"), "Should contain processed filename");
        Assertions.assertTrue(
                results.contains("image999_processed"), "Should contain processed filename");
    }

    @Test
    void testHybridStringRegexApproach() {

        String[] edgeCases = {
            "", // Empty string
            ".", // Just a dot
            "..", // Two dots
            "...", // Three dots
            ".hidden", // Hidden file
            "file.", // Trailing dot
            "a.b.c.d.e.f.g", // Many extensions
            "no-extension-here", // No extension
            "file..double.dot" // Double dots
        };

        for (String edgeCase : edgeCases) {
            String result = GeneralUtils.removeExtension(edgeCase);
            Assertions.assertNotNull(result, "Result should not be null for: " + edgeCase);

            // For specific edge cases, verify expected behavior
            switch (edgeCase) {
                case "" -> Assertions.assertEquals("", result, "Empty string should remain empty");
                case "." ->
                        Assertions.assertEquals(".", result, "Single dot should remain unchanged");
                case ".." ->
                        Assertions.assertEquals(
                                "..", result, "Double dots should remain unchanged");
                case "..." ->
                        Assertions.assertEquals(
                                "...", result, "Triple dots should remain unchanged");
                case ".hidden" ->
                        Assertions.assertEquals(
                                ".hidden", result, "Hidden file should remain unchanged");
                case "file." ->
                        Assertions.assertEquals(
                                "file.", result, "Trailing dot should remain unchanged");
                case "no-extension-here" ->
                        Assertions.assertEquals(
                                "no-extension-here",
                                result,
                                "No extension should remain unchanged");
                case "a.b.c.d.e.f.g" ->
                        Assertions.assertEquals(
                                "a.b.c.d.e.f",
                                result,
                                "Multiple extensions should remove last one");
                case "file..double.dot" ->
                        Assertions.assertEquals(
                                "file..double",
                                result,
                                "Double dot case should remove last extension");
            }
        }
    }

    @Test
    void testGetTitleFromFilename() {
        // Test normal cases
        Assertions.assertEquals("document", GeneralUtils.getTitleFromFilename("document.pdf"));
        Assertions.assertEquals(
                "presentation", GeneralUtils.getTitleFromFilename("presentation.pptx"));
        Assertions.assertEquals(
                "file.backup", GeneralUtils.getTitleFromFilename("file.backup.zip"));

        // Test null and empty handling
        Assertions.assertEquals("Untitled", GeneralUtils.getTitleFromFilename(null));
        Assertions.assertEquals("Untitled", GeneralUtils.getTitleFromFilename(""));

        // Test edge cases
        Assertions.assertEquals(".hidden", GeneralUtils.getTitleFromFilename(".hidden"));
        Assertions.assertEquals("file.", GeneralUtils.getTitleFromFilename("file."));
        Assertions.assertEquals("noextension", GeneralUtils.getTitleFromFilename("noextension"));

        // Test complex cases
        Assertions.assertEquals(
                "complex.file.name", GeneralUtils.getTitleFromFilename("complex.file.name.txt"));
        Assertions.assertEquals(
                "path/to/file", GeneralUtils.getTitleFromFilename("path/to/file.ext"));
    }
}
