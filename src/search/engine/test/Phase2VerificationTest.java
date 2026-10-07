package search.engine.test;

import search.engine.core.*;
import java.util.List;

public class Phase2VerificationTest {

    public static void main(String[] args) {
        System.out.println("Running Phase 2 Verification Suite...\n");

        InvertedIndex index = setupTestIndex();

        testSimpleTermQuery(index);
        testAndIntersection(index);
        testOrUnion(index);
        testNotDifference(index);
        testComplexNestedExpression(index);

        System.out.println("\nAll Phase 2 Verification Tests Passed Successfully.");
    }

    private static InvertedIndex setupTestIndex() {
        InvertedIndex index = new InvertedIndex();
        index.addDocument(new Document(1, "Doc1", "linux kernel memory management"));
        index.addDocument(new Document(2, "Doc2", "linux kernel device drivers"));
        index.addDocument(new Document(3, "Doc3", "operating systems memory virtualization"));
        index.addDocument(new Document(4, "Doc4", "distributed systems consensus algorithms"));
        index.finalizeIndex();
        return index;
    }

    private static void testSimpleTermQuery(InvertedIndex index) {
        System.out.print("[TEST] Simple Term Query... ");
        QueryNode q = QueryParser.parse("linux", index);
        List<Integer> results = q.evaluate(index);
        assert results.equals(List.of(1, 2)) : "Expected [1, 2], got: " + results;
        System.out.println("PASSED");
    }

    private static void testAndIntersection(InvertedIndex index) {
        System.out.print("[TEST] AND Intersection (Skip-Accelerated)... ");
        QueryNode q = QueryParser.parse("linux AND memory", index);
        List<Integer> results = q.evaluate(index);
        assert results.equals(List.of(1)) : "Expected [1], got: " + results;
        System.out.println("PASSED");
    }

    private static void testOrUnion(InvertedIndex index) {
        System.out.print("[TEST] OR Union... ");
        QueryNode q = QueryParser.parse("drivers OR consensus", index);
        List<Integer> results = q.evaluate(index);
        assert results.equals(List.of(2, 4)) : "Expected [2, 4], got: " + results;
        System.out.println("PASSED");
    }

    private static void testNotDifference(InvertedIndex index) {
        System.out.print("[TEST] NOT Set Difference... ");
        QueryNode q = QueryParser.parse("memory NOT linux", index);
        List<Integer> results = q.evaluate(index);
        assert results.equals(List.of(3)) : "Expected [3], got: " + results;
        System.out.println("PASSED");
    }

    private static void testComplexNestedExpression(InvertedIndex index) {
        System.out.print("[TEST] Complex Expression with Parentheses: (linux OR systems) AND memory... ");
        // linux docs: [1, 2]
        // systems docs: [3, 4]
        // (linux OR systems) = [1, 2, 3, 4]
        // memory docs: [1, 3]
        // [1, 2, 3, 4] AND [1, 3] = [1, 3]
        QueryNode q = QueryParser.parse("(linux OR systems) AND memory", index);
        List<Integer> results = q.evaluate(index);
        assert results.equals(List.of(1, 3)) : "Expected [1, 3], got: " + results;
        System.out.println("PASSED");
    }
}