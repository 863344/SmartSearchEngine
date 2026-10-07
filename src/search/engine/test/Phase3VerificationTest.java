package search.engine.test;

import search.engine.core.*;
import java.util.List;

public class Phase3VerificationTest {

    public static void main(String[] args) {
        System.out.println("Running Phase 3 Positional & Phrase Search Suite...\n");

        InvertedIndex index = setupTestIndex();

        testExactTwoWordPhrase(index);
        testPhraseWordOrderSensitivity(index);
        testMultiWordPhrase(index);
        testHybridPhraseAndBoolean(index);

        System.out.println("\nAll Phase 3 Verification Tests Passed Successfully.");
    }

    private static InvertedIndex setupTestIndex() {
        InvertedIndex index = new InvertedIndex();
        // Doc 1: "machine learning models"
        index.addDocument(new Document(1, "Doc1", "machine learning models for real-time inference"));
        // Doc 2: words exist but separated/unordered
        index.addDocument(new Document(2, "Doc2", "learning deep machine architectures"));
        // Doc 3: matches 3-word phrase
        index.addDocument(new Document(3, "Doc3", "fault tolerant distributed consensus algorithms in systems"));
        // Doc 4: partial match
        index.addDocument(new Document(4, "Doc4", "distributed systems without consensus"));
        index.finalizeIndex();
        return index;
    }

    private static void testExactTwoWordPhrase(InvertedIndex index) {
        System.out.print("[TEST] Exact 2-Word Positional Phrase: \"machine learning\"... ");
        QueryNode q = QueryParser.parse("\"machine learning\"", index);
        List<Integer> results = q.evaluate(index);
        // Doc 1 has "machine learning" adjacent at pos 0, 1.
        // Doc 2 has "learning" at pos 1, "machine" at pos 2 (reverse order). Doc 2 must NOT match.
        assert results.equals(List.of(1)) : "Expected [1], got: " + results;
        System.out.println("PASSED");
    }

    private static void testPhraseWordOrderSensitivity(InvertedIndex index) {
        System.out.print("[TEST] Positional Order Sensitivity: \"learning machine\"... ");
        QueryNode q = QueryParser.parse("\"learning machine\"", index);
        List<Integer> results = q.evaluate(index);
        // Doc 2 has "learning deep machine" -> not adjacent (pos 1 and pos 3)
        assert results.isEmpty() : "Expected [], got: " + results;
        System.out.println("PASSED");
    }

    private static void testMultiWordPhrase(InvertedIndex index) {
        System.out.print("[TEST] Multi-Word Phrase: \"distributed consensus algorithms\"... ");
        QueryNode q = QueryParser.parse("\"distributed consensus algorithms\"", index);
        List<Integer> results = q.evaluate(index);
        assert results.equals(List.of(3)) : "Expected [3], got: " + results;
        System.out.println("PASSED");
    }

    private static void testHybridPhraseAndBoolean(InvertedIndex index) {
        System.out.print("[TEST] Hybrid Query: \"machine learning\" OR \"distributed consensus\"... ");
        QueryNode q = QueryParser.parse("\"machine learning\" OR \"distributed consensus algorithms\"", index);
        List<Integer> results = q.evaluate(index);
        assert results.equals(List.of(1, 3)) : "Expected [1, 3], got: " + results;
        System.out.println("PASSED");
    }
}