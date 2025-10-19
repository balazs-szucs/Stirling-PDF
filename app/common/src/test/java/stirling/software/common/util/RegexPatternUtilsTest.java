package stirling.software.common.util;

import java.util.regex.Pattern;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class RegexPatternUtilsTest {

    private RegexPatternUtils utils;

    @BeforeEach
    void setUp() {
        utils = RegexPatternUtils.getInstance();
        utils.clearCache(); // Start with clean cache for each test
    }

    @Test
    void testPatternCaching() {
        String regex = "test\\d+";

        Pattern pattern1 = utils.getPattern(regex);
        Assertions.assertNotNull(pattern1);
        Assertions.assertTrue(utils.isCached(regex));
        Assertions.assertEquals(
                1, utils.getCacheSize()); // Should have at least 1 pattern (plus precompiled ones
        // are cleared)

        Pattern pattern2 = utils.getPattern(regex);
        Assertions.assertSame(pattern1, pattern2); // Should be the exact same object
    }

    @Test
    void testPatternWithFlags() {
        String regex = "test";
        int flags = Pattern.CASE_INSENSITIVE;

        Pattern pattern1 = utils.getPattern(regex, flags);
        Pattern pattern2 = utils.getPattern(regex); // No flags

        Assertions.assertNotSame(pattern1, pattern2); // Different flags = different cached patterns
        Assertions.assertTrue(utils.isCached(regex, flags));
        Assertions.assertTrue(utils.isCached(regex, 0));
    }

    @Test
    void testCacheEviction() {
        String regex = "evict\\d+";

        utils.getPattern(regex);
        Assertions.assertTrue(utils.isCached(regex));

        boolean removed = utils.removeFromCache(regex);
        Assertions.assertTrue(removed);
        Assertions.assertFalse(utils.isCached(regex));

        boolean removedAgain = utils.removeFromCache(regex);
        Assertions.assertFalse(removedAgain);
    }

    @Test
    void testNullRegexHandling() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> utils.getPattern(null));

        Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> utils.getPattern(null, Pattern.CASE_INSENSITIVE));

        Assertions.assertFalse(utils.isCached(null));
        Assertions.assertFalse(utils.removeFromCache(null));
    }

    @Test
    void testCommonPatterns() {
        Pattern whitespace = utils.getWhitespacePattern();
        Assertions.assertTrue(whitespace.matcher("  \t  ").matches());

        Pattern trailing = utils.getTrailingSlashesPattern();
        Assertions.assertTrue(trailing.matcher("/path/to/dir///").find());

        Pattern filename = utils.getSafeFilenamePattern();
        Assertions.assertTrue(filename.matcher("bad<file>name").find());
    }

    @Test
    void testCreateSearchPattern() {
        String regex = "Hello";

        Pattern caseSensitive = utils.createSearchPattern(regex, false);
        Pattern caseInsensitive = utils.createSearchPattern(regex, true);

        Assertions.assertTrue(caseSensitive.matcher("Hello").matches());
        Assertions.assertFalse(caseSensitive.matcher("hello").matches());

        Assertions.assertTrue(caseInsensitive.matcher("Hello").matches());
        Assertions.assertTrue(caseInsensitive.matcher("hello").matches());
        Assertions.assertTrue(caseInsensitive.matcher("HELLO").matches());
    }

    @Test
    void testSingletonBehavior() {
        RegexPatternUtils instance1 = RegexPatternUtils.getInstance();
        RegexPatternUtils instance2 = RegexPatternUtils.getInstance();

        Assertions.assertSame(instance1, instance2);
    }
}
