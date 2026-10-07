package search.engine.test;

import search.engine.core.*;
import java.util.List;

public class Phase4VerificationTest {

    public static void main(String[] args) {
        System.out.println("Running Phase 4 BM25 Ranking & Top-K Suite...\n");

        InvertedIndex index = setupTestIndex();
        RankingEngine engine = new RankingEngine();

        testTFSaturationAndLengthNormalization(index, engine);
        testTopKHeapBoundary(index, engine);

        System.out.println("\nAll Phase 4 Verification Tests Passed Successfully.");
    }

    private static InvertedIndex setupTestIndex() {
        InvertedIndex index = new InvertedIndex();
        // Doc 1: Concise document with high frequency of "cache"
        index.addDocument(new Document(1, "Doc1", "cache memory cache coherence cache"));
        
        // Doc 2: Long document with only single mention of "cache"
        index.addDocument(new Document(2, "Doc2", "cache architectures and operating systems distributed storage protocols virtualization"));
        
        // Doc 3: Highly relevant to "memory" and "cache"
        index.addDocument(new Document(3, "Doc3", "cache memory hierarchy and low latency memory systems"));
        
        // Doc 4: Unrelated
        index.addDocument(new Document(4, "Doc4", "unrelated data network socket programming"));
        index.finalizeIndex();
        return index;
    }

    private static void testTFSaturationAndLengthNormalization(InvertedIndex index, RankingEngine engine) {
        System.out.print("[TEST] BM25 Score Ordering (Concise high-TF vs verbose low-TF)... ");
        List<String> query = List.of("cache");

        double scoreDoc1 = engine.scoreDocument(index, 1, query);
        double scoreDoc2 = engine.scoreDocument(index, 2, query);

        // Doc 1 has TF=3 in length 5. Doc 2 has TF=1 in length 9.
        // Doc 1 must score significantly higher than Doc 2.
        assert scoreDoc1 > scoreDoc2 : "BM25 failed: Doc 1 (" + scoreDoc1 + ") should outrank Doc 2 (" + scoreDoc2 + ")";
        System.out.println("PASSED (Doc 1: " + String.format("%.4f", scoreDoc1) + " > Doc 2: " + String.format("%.4f", scoreDoc2) + ")");
    }

    private static void testTopKHeapBoundary(InvertedIndex index, RankingEngine engine) {
        System.out.print("[TEST] Min-Heap Top-K Selection (K=2)... ");
        List<Integer> candidates = List.of(1, 2, 3, 4);
        List<String> query = List.of("cache", "memory");

        int k = 2;
        List<SearchResult> results = engine.rankTopK(index, candidates, query, k);

        assert results.size() == 2 : "Expected exactly " + k + " results, got: " + results.size();
        
        // Doc 1 and Doc 3 both feature high relevance to cache and memory
        assert results.get(0).score() >= results.get(1).score() : "Results must be sorted descending by score";
        
        // Doc 4 has 0 score, should be completely excluded from top-2
        for (SearchResult res : results) {
            assert res.docId() != 4 : "Doc 4 with 0 score should not appear in Top-2";
        }

        System.out.println("PASSED (Top 1 DocID: " + results.get(0).docId() + ", Top 2 DocID: " + results.get(1).docId() + ")");
    }
}