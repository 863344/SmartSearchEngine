package search.engine.core;

import java.util.*;

public class RankingEngine {
    public record HeapEvent(int docId, double score, String action, Double rootScore) {}
    public record RankingTrace(List<SearchResult> results, List<SearchResult> retained,
        List<HeapEvent> events, int offered, int replaced, int discarded) {}
    private final double k1;
    private final double b;

    public RankingEngine() {
        this(1.2, 0.75); // Standard industry-default tuning parameters
    }

    public RankingEngine(double k1, double b) {
        this.k1 = k1;
        this.b = b;
    }

    /**
     * Computes Robertson-Spärck Jones Inverse Document Frequency (IDF).
     */
    public double calculateIDF(int totalDocs, int docsWithTerm) {
        return Math.log(1.0 + (totalDocs - docsWithTerm + 0.5) / (docsWithTerm + 0.5));
    }

    /**
     * Scores a single document against a collection of query terms using Okapi BM25.
     */
    public double scoreDocument(InvertedIndex index, int docId, List<String> queryTerms) {
        int docLength = index.getDocLength(docId);
        double avgDocLength = index.getAverageDocumentLength();
        int totalDocs = index.getTotalDocuments();

        if (docLength == 0 || avgDocLength == 0.0) {
            return 0.0;
        }

        double totalScore = 0.0;

        for (String rawTerm : queryTerms) {
            String term = rawTerm.toLowerCase();
            PostingsList postings = index.getPostings(term);
            if (postings == null) {
                continue;
            }

            // Find TF of term in docId
            int tf = 0;
            PostingNode curr = postings.getHead();
            while (curr != null) {
                if (curr.getDocId() == docId) {
                    tf = curr.getTermFrequency();
                    break;
                } else if (curr.getDocId() > docId) {
                    break; // Sorted by docId, can break early
                }
                curr = curr.getNext();
            }

            if (tf == 0) {
                continue;
            }

            int docFreq = postings.size();
            double idf = calculateIDF(totalDocs, docFreq);

            double numerator = tf * (k1 + 1.0);
            double denominator = tf + k1 * (1.0 - b + b * ((double) docLength / avgDocLength));

            totalScore += idf * (numerator / denominator);
        }

        return totalScore;
    }

    /**
     * Top-K document selection using a bounded Min-Heap.
     * Time Complexity: O(M * log K) where M = candidateDocs.size()
     * Space Complexity: O(K)
     */
    public List<SearchResult> rankTopK(InvertedIndex index, List<Integer> candidateDocIds, List<String> queryTerms, int k) {
        return rankTopKWithTrace(index, candidateDocIds, queryTerms, k).results();
    }

    public RankingTrace rankTopKWithTrace(InvertedIndex index, List<Integer> candidateDocIds, List<String> queryTerms, int k) {
        if (candidateDocIds == null || candidateDocIds.isEmpty() || k <= 0) {
            return new RankingTrace(List.of(), List.of(), List.of(), 0, 0, 0);
        }

        // Min-Heap keeps K highest scoring items; the smallest of the top-K is always at the root
        PriorityQueue<SearchResult> minHeap = new PriorityQueue<>(k);
        List<HeapEvent> events = new ArrayList<>();
        int replaced = 0, discarded = 0;

        for (int docId : candidateDocIds) {
            double score = scoreDocument(index, docId, queryTerms);
            Document doc = index.getDocument(docId);
            SearchResult result = new SearchResult(docId, score, doc);
            String action;

            if (minHeap.size() < k) {
                minHeap.offer(result);
                action = "insert";
            } else if (score > minHeap.peek().score()) {
                minHeap.poll();
                minHeap.offer(result);
                action = "replace root";
                replaced++;
            } else {
                action = "discard";
                discarded++;
            }
            if (events.size() < 50) events.add(new HeapEvent(docId, score, action, minHeap.peek().score()));
        }

        // Extract and sort results in descending order (highest score first)
        List<SearchResult> topKList = new ArrayList<>(minHeap);
        topKList.sort(Collections.reverseOrder());
        List<SearchResult> retained = new ArrayList<>(minHeap);
        Collections.sort(retained);
        return new RankingTrace(topKList, retained, events, candidateDocIds.size(), replaced, discarded);
    }
}
