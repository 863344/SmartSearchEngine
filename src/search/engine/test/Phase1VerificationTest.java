package search.engine.test;

import search.engine.core.*;
import java.util.List;

public class Phase1VerificationTest {

    public static void main(String[] args) {
        System.out.println("Running Phase 1 Verification Suite...\n");

        testTokenizerPrecision();
        testIndexConstructionAndPositions();
        testSkipPointerTopologyAndTraversal();

        System.out.println("\nAll Phase 1 Verification Tests Passed Successfully.");
    }

    private static void testTokenizerPrecision() {
        System.out.print("[TEST] Tokenizer Case-Folding & Position Offset... ");
        String text = "Smart, smart documents! Searching... 100% systems.";
        List<Tokenizer.Token> tokens = Tokenizer.tokenize(text);

        assert tokens.size() == 6 : "Token count mismatch. Expected 6, got " + tokens.size();
        assert tokens.get(0).term().equals("smart") && tokens.get(0).position() == 0;
        assert tokens.get(1).term().equals("smart") && tokens.get(1).position() == 1;
        assert tokens.get(2).term().equals("documents") && tokens.get(2).position() == 2;
        assert tokens.get(3).term().equals("searching") && tokens.get(3).position() == 3;
        assert tokens.get(4).term().equals("100") && tokens.get(4).position() == 4;
        assert tokens.get(5).term().equals("systems") && tokens.get(5).position() == 5;
        System.out.println("PASSED");
    }

    private static void testIndexConstructionAndPositions() {
        System.out.print("[TEST] Inverted Index Construction & Positions... ");
        InvertedIndex index = new InvertedIndex();
        
        index.addDocument(new Document(1, "Doc1", "Database systems and file systems"));
        index.addDocument(new Document(2, "Doc2", "Distributed systems architecture"));
        index.finalizeIndex();

        PostingsList systemsList = index.getPostings("systems");
        assert systemsList != null : "Postings list for 'systems' must not be null";
        assert systemsList.size() == 2 : "Expected 2 documents in 'systems' postings list";

        PostingNode node1 = systemsList.getHead();
        assert node1.getDocId() == 1;
        assert node1.getTermFrequency() == 2;
        assert node1.getPositions().equals(List.of(1, 4)) : "Doc 1 positions incorrect: " + node1.getPositions();

        PostingNode node2 = node1.getNext();
        assert node2.getDocId() == 2;
        assert node2.getTermFrequency() == 1;
        assert node2.getPositions().equals(List.of(1));

        assert index.getAverageDocumentLength() == 4.0 : "Average document length calculation error";
        System.out.println("PASSED");
    }

    private static void testSkipPointerTopologyAndTraversal() {
        System.out.print("[TEST] Skip Pointer Interval Math and Traversal... ");
        InvertedIndex index = new InvertedIndex();

        for (int i = 1; i <= 16; i++) {
            index.addDocument(new Document(i, "Doc" + i, "target token payload"));
        }
        index.finalizeIndex();

        PostingsList list = index.getPostings("target");
        assert list.size() == 16 : "Expected size 16, got " + list.size();

        PostingNode curr = list.getHead();
        int indexCount = 0;
        while (curr != null) {
            if (indexCount % 4 == 0 && indexCount + 4 < 16) {
                assert curr.getSkip() != null : "Missing skip pointer at index: " + indexCount;
                assert curr.getSkip().getDocId() == curr.getDocId() + 4 : 
                    "Invalid skip destination. Expected DocID " + (curr.getDocId() + 4) + " but got " + curr.getSkip().getDocId();
            } else {
                assert curr.getSkip() == null : "Unexpected skip pointer present at index: " + indexCount;
            }
            curr = curr.getNext();
            indexCount++;
        }

        int targetDocId = 14;
        PostingNode searchNode = list.getHead();
        int stepCount = 0;

        while (searchNode != null && searchNode.getDocId() != targetDocId) {
            stepCount++;
            if (searchNode.getSkip() != null && searchNode.getSkip().getDocId() <= targetDocId) {
                searchNode = searchNode.getSkip();
            } else {
                searchNode = searchNode.getNext();
            }
        }

        assert searchNode != null && searchNode.getDocId() == 14 : "Failed to locate DocID 14 via skip pointers";
        assert stepCount == 4 : "Skip traversal efficiency failure. Expected 4 steps, took " + stepCount;
        System.out.println("PASSED (Traversed in " + stepCount + " jumps vs 13 linear steps)");
    }
}