package com.hitorro.prbench;

import com.hitorro.prbench.service.TextNormalizer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TextNormalizerTest {

    @Test
    void normalizeStripsMarkdown() {
        String input = "Check **this** `code` [link](http://example.com)";
        String result = TextNormalizer.normalize(input);
        assertFalse(result.contains("**"));
        assertFalse(result.contains("`"));
        assertFalse(result.contains("http"));
        assertTrue(result.contains("check"));
        assertTrue(result.contains("link"));
    }

    @Test
    void normalizeHandlesNull() {
        assertEquals("", TextNormalizer.normalize(null));
    }

    @Test
    void winnowingHashProducesConsistentResult() {
        String hash1 = TextNormalizer.winnowingHash("This is a test string", 5, 4);
        String hash2 = TextNormalizer.winnowingHash("This is a test string", 5, 4);
        assertEquals(hash1, hash2);
        assertFalse(hash1.isEmpty());
    }

    @Test
    void winnowingSimilarityIdenticalStrings() {
        String text = "This is a longer test string for winnowing hash comparison";
        String hash = TextNormalizer.winnowingHash(text, 5, 4);
        double sim = TextNormalizer.winnowingSimilarity(hash, hash);
        assertEquals(1.0, sim, 0.001);
    }

    @Test
    void jaroWinklerIdenticalStrings() {
        double sim = TextNormalizer.jaroWinklerSimilarity("hello world", "hello world");
        assertEquals(1.0, sim, 0.001);
    }

    @Test
    void jaroWinklerDifferentStrings() {
        double sim = TextNormalizer.jaroWinklerSimilarity("hello", "world");
        assertTrue(sim < 0.5);
    }

    @Test
    void jaroWinklerSimilarStrings() {
        double sim = TextNormalizer.jaroWinklerSimilarity("potential null pointer", "possible null pointer");
        assertTrue(sim > 0.7);
    }
}
