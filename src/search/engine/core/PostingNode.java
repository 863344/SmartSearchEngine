package search.engine.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Node within an inverted index postings list.
 * Maintains term occurrence statistics and spatial positions for phrase retrieval.
 */
public class PostingNode {
    private final int docId;
    private int termFrequency;
    private final List<Integer> positions;
    
    // Pointer to immediately adjacent document in sorted order
    private PostingNode next;
    
    // Skip pointer enabling O(sqrt(L)) traversal bypass
    private PostingNode skip;

    public PostingNode(int docId, int initialPosition) {
        this.docId = docId;
        this.termFrequency = 1;
        this.positions = new ArrayList<>();
        this.positions.add(initialPosition);
        this.next = null;
        this.skip = null;
    }

    public void addPosition(int position) {
        this.positions.add(position);
        this.termFrequency++;
    }

    public int getDocId() { return docId; }
    public int getTermFrequency() { return termFrequency; }
    public List<Integer> getPositions() { return Collections.unmodifiableList(positions); }
    public PostingNode getNext() { return next; }
    public void setNext(PostingNode next) { this.next = next; }
    public PostingNode getSkip() { return skip; }
    public void setSkip(PostingNode skip) { this.skip = skip; }

    @Override
    public String toString() {
        return String.format("[DocID: %d, TF: %d, Positions: %s, HasSkip: %b]", 
            docId, termFrequency, positions, (skip != null));
    }
}