package search.engine.test;

import search.engine.core.BKTree;
import search.engine.core.Trie;
import java.util.List;

public class Phase5VerificationTest {

    public static void main(String[] args) {
        System.out.println("Running Phase 5 Trie Autocomplete & BK-Tree Suite...\n");

        testTrieAutocomplete();
        testLevenshteinDistanceMath();
        testBKTreeMetricPruning();

        System.out.println("\nAll Phase 5 Verification Tests Passed Successfully.");
    }

    private static void testTrieAutocomplete() {
        System.out.print("[TEST] Trie Insertion & Prefix Autocomplete... ");
        Trie trie = new Trie();
        trie.insert("algorithm");
        trie.insert("algorithms");
        trie.insert("algorithmic");
        trie.insert("algebra");
        trie.insert("allocate");

        List<String> algMatches = trie.autocomplete("alg", 5);
        assert algMatches.contains("algebra");
        assert algMatches.contains("algorithm");
        assert algMatches.contains("algorithmic");
        assert !algMatches.contains("allocate");

        List<String> limitTest = trie.autocomplete("alg", 2);
        assert limitTest.size() == 2 : "Limit constraint violated. Expected 2, got " + limitTest.size();

        System.out.println("PASSED");
    }

    private static void testLevenshteinDistanceMath() {
        System.out.print("[TEST] Levenshtein DP Matrix Correctness... ");
        assert BKTree.levenshteinDistance("kitten", "sitting") == 3 : "kitten -> sitting should be 3";
        assert BKTree.levenshteinDistance("systems", "systems") == 0 : "Identity distance should be 0";
        assert BKTree.levenshteinDistance("systms", "systems") == 1 : "Single deletion should be 1";
        assert BKTree.levenshteinDistance("docment", "document") == 1 : "Single insertion should be 1";
        assert BKTree.levenshteinDistance("", "abc") == 3 : "Empty boundary case failed";
        System.out.println("PASSED");
    }

    private static void testBKTreeMetricPruning() {
        System.out.print("[TEST] BK-Tree Fuzzy Pruning Search (Distance <= 1)... ");
        BKTree bkTree = new BKTree();
        bkTree.insert("document");
        bkTree.insert("documents");
        bkTree.insert("dock");
        bkTree.insert("systems");
        bkTree.insert("system");
        bkTree.insert("systematic");

        // Query typo: "docment" (distance = 1 to "document")
        List<BKTree.Match> results = bkTree.search("docment", 1);
        assert results.size() == 1 : "Expected exactly 1 match, got " + results.size();
        assert results.get(0).word().equals("document") : "Expected 'document', got " + results.get(0).word();
        assert results.get(0).distance() == 1 : "Distance should be 1";

        // Query typo: "systms" (distance = 1 to "systems")
        List<BKTree.Match> sysResults = bkTree.search("systms", 1);
        assert sysResults.size() == 1 && sysResults.get(0).word().equals("systems");

        // Distance <= 2 should return both "system" (dist 1) and "systems" (dist 2) for "systm"
        List<BKTree.Match> multiMatches = bkTree.search("systm", 2);
        List<String> matchedWords = multiMatches.stream().map(BKTree.Match::word).toList();
        assert matchedWords.contains("system");
        assert matchedWords.contains("systems");
        assert !matchedWords.contains("systematic"); // Distance is 4, must be pruned

        System.out.println("PASSED");
    }
}