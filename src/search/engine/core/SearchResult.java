package search.engine.core;

/**
 * Encapsulates a document matched by a query alongside its computed BM25 score.
 * Implements Comparable to enable Min-Heap ordering.
 */
public record SearchResult(int docId, double score, Document document) implements Comparable<SearchResult> {

    @Override
    public int compareTo(SearchResult other) {
        // Natural ordering based on score ascending (for Min-Heap root eviction)
        return Double.compare(this.score, other.score);
    }
}