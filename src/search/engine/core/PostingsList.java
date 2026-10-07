
package search.engine.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Singly linked postings list augmented with dynamic skip pointers.
 * Postings are strictly ordered by ascending docId.
 */
public class PostingsList {
    private PostingNode head;
    private PostingNode tail;
    private int size;

    public PostingsList() {
        this.head = null;
        this.tail = null;
        this.size = 0;
    }

    /**
     * Appends an occurrence of a term in a document.
     * Documents must be ingested monotonically by docId to preserve sorting.
     */
    public void add(int docId, int position) {
        if (tail != null && tail.getDocId() == docId) {
            tail.addPosition(position);
            return;
        }

        PostingNode newNode = new PostingNode(docId, position);
        if (head == null) {
            head = newNode;
            tail = newNode;
        } else {
            if (docId < tail.getDocId()) {
                throw new IllegalArgumentException(
                    "Out-of-order docId: " + docId + ". Postings must be monotonic."
                );
            }
            tail.setNext(newNode);
            tail = newNode;
        }
        size++;
    }

    /**
     * Constructs skip pointers across the list.
     * Optimal skip frequency interval is floor(sqrt(L)), where L = list size.
     */
    public void buildSkipPointers() {
        if (size < 4) {
            return;
        }

        int skipInterval = (int) Math.floor(Math.sqrt(size));
        
        List<PostingNode> nodes = new ArrayList<>(size);
        PostingNode curr = head;
        while (curr != null) {
            curr.setSkip(null);
            nodes.add(curr);
            curr = curr.getNext();
        }

        for (int i = 0; i < nodes.size(); i += skipInterval) {
            int targetIdx = i + skipInterval;
            if (targetIdx < nodes.size()) {
                nodes.get(i).setSkip(nodes.get(targetIdx));
            }
        }
    }

    public PostingNode getHead() { return head; }
    public int size() { return size; }
}
