package com.hitorro.prbench.service;

import java.util.*;

/**
 * Normalizes comment text for comparison: strips markdown, URLs,
 * punctuation, lowercases. Also computes Winnowing fingerprint hashes.
 */
public class TextNormalizer {

    public static String normalize(String text) {
        if (text == null) return "";
        String s = text.toLowerCase();
        // Strip markdown code blocks
        s = s.replaceAll("```[\\s\\S]*?```", " ");
        // Strip inline code
        s = s.replaceAll("`[^`]*`", " ");
        // Strip URLs
        s = s.replaceAll("https?://\\S+", " ");
        // Strip markdown links
        s = s.replaceAll("\\[([^]]*)]\\([^)]*\\)", "$1");
        // Strip markdown formatting
        s = s.replaceAll("[*_~#>]", "");
        // Strip non-alphanumeric (keep spaces)
        s = s.replaceAll("[^a-z0-9\\s]", " ");
        // Collapse whitespace
        s = s.replaceAll("\\s+", " ").trim();
        return s;
    }

    /** Compute Winnowing fingerprint hash for a text. */
    public static String winnowingHash(String text, int k, int w) {
        String normalized = normalize(text);
        if (normalized.length() < k) return String.valueOf(normalized.hashCode());

        // Generate k-grams and their hashes
        int[] hashes = new int[normalized.length() - k + 1];
        for (int i = 0; i <= normalized.length() - k; i++) {
            hashes[i] = normalized.substring(i, i + k).hashCode();
        }

        // Winnowing: select minimum hash from each window
        Set<Integer> fingerprints = new TreeSet<>();
        for (int i = 0; i <= hashes.length - w; i++) {
            int min = Integer.MAX_VALUE;
            for (int j = i; j < i + w; j++) {
                if (hashes[j] < min) min = hashes[j];
            }
            fingerprints.add(min);
        }

        // Encode as hex string
        StringBuilder sb = new StringBuilder();
        for (int fp : fingerprints) {
            if (!sb.isEmpty()) sb.append(",");
            sb.append(Integer.toHexString(fp));
        }
        return sb.toString();
    }

    /** Jaccard similarity between two winnowing fingerprint sets. */
    public static double winnowingSimilarity(String hashA, String hashB) {
        if (hashA == null || hashB == null || hashA.isEmpty() || hashB.isEmpty()) return 0;
        Set<String> setA = new HashSet<>(Arrays.asList(hashA.split(",")));
        Set<String> setB = new HashSet<>(Arrays.asList(hashB.split(",")));
        Set<String> intersection = new HashSet<>(setA);
        intersection.retainAll(setB);
        Set<String> union = new HashSet<>(setA);
        union.addAll(setB);
        return union.isEmpty() ? 0 : (double) intersection.size() / union.size();
    }

    /** Jaro-Winkler similarity between two strings. */
    public static double jaroWinklerSimilarity(String s1, String s2) {
        if (s1 == null || s2 == null) return 0;
        if (s1.equals(s2)) return 1.0;
        int len1 = s1.length(), len2 = s2.length();
        if (len1 == 0 || len2 == 0) return 0;

        int matchDistance = Math.max(len1, len2) / 2 - 1;
        if (matchDistance < 0) matchDistance = 0;

        boolean[] s1Matches = new boolean[len1];
        boolean[] s2Matches = new boolean[len2];
        int matches = 0, transpositions = 0;

        for (int i = 0; i < len1; i++) {
            int start = Math.max(0, i - matchDistance);
            int end = Math.min(i + matchDistance + 1, len2);
            for (int j = start; j < end; j++) {
                if (s2Matches[j] || s1.charAt(i) != s2.charAt(j)) continue;
                s1Matches[i] = true;
                s2Matches[j] = true;
                matches++;
                break;
            }
        }
        if (matches == 0) return 0;

        int k = 0;
        for (int i = 0; i < len1; i++) {
            if (!s1Matches[i]) continue;
            while (!s2Matches[k]) k++;
            if (s1.charAt(i) != s2.charAt(k)) transpositions++;
            k++;
        }

        double jaro = ((double) matches / len1 + (double) matches / len2 +
                (matches - transpositions / 2.0) / matches) / 3.0;

        // Winkler prefix bonus
        int prefix = 0;
        for (int i = 0; i < Math.min(4, Math.min(len1, len2)); i++) {
            if (s1.charAt(i) == s2.charAt(i)) prefix++;
            else break;
        }
        return jaro + prefix * 0.1 * (1 - jaro);
    }
}
