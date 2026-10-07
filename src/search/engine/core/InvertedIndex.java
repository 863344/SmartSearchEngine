package search.engine.core;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class InvertedIndex {
    private final Map<String, PostingsList> index;
    private final Map<Integer, Integer> docLengths;
    private final Map<Integer, Document> documentStore;
    private long totalTokenCount;
    private long nextDocId = 1;

    public InvertedIndex() {
        this.index = new HashMap<>();
        this.docLengths = new HashMap<>();
        this.documentStore = new HashMap<>();
        this.totalTokenCount = 0;
    }

    public synchronized void addDocument(Document doc) {
        if (documentStore.containsKey(doc.docId())) {
            throw new IllegalArgumentException("Document with ID " + doc.docId() + " already exists.");
        }

        if (doc.docId() < nextDocId) {
            throw new IllegalArgumentException(
                "Out-of-order docId: " + doc.docId() + ". Expected an ID at least " + nextDocId + "."
            );
        }

        List<Tokenizer.Token> tokens = Tokenizer.tokenize(doc.content());
        documentStore.put(doc.docId(), doc);
        
        int docLength = tokens.size();
        docLengths.put(doc.docId(), docLength);
        totalTokenCount += docLength;

        for (Tokenizer.Token token : tokens) {
            index.computeIfAbsent(token.term(), k -> new PostingsList())
                 .add(doc.docId(), token.position());
        }
        nextDocId = (long) doc.docId() + 1;
    }

    /** Returns an unused ID greater than every successfully indexed document ID. */
    public synchronized int getNextDocId() {
        if (nextDocId > Integer.MAX_VALUE) {
            throw new IllegalStateException("Document ID range exhausted.");
        }
        return (int) nextDocId;
    }

    public void finalizeIndex() {
        for (PostingsList list : index.values()) {
            list.buildSkipPointers();
        }
    }

    public PostingsList getPostings(String term) {
        return index.get(term.toLowerCase());
    }

    public Document getDocument(int docId) {
        return documentStore.get(docId);
    }

    public int getDocLength(int docId) {
        return docLengths.getOrDefault(docId, 0);
    }

    public int getTotalDocuments() {
        return documentStore.size();
    }

    public List<Integer> getDocumentIds() {
        return documentStore.keySet().stream().sorted().toList();
    }

    public double getAverageDocumentLength() {
        if (documentStore.isEmpty()) return 0.0;
        return (double) totalTokenCount / documentStore.size();
    }

    public Map<String, PostingsList> getLexicon() {
        return Collections.unmodifiableMap(index);
    }
}
