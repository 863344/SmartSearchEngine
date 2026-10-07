package search.engine.core;

import java.util.*;

/**
 * Unified facade coordinating indexing, query parsing, fuzzy correction,
 * prefix autocomplete, and BM25 ranked retrieval.
 */
public class SearchEngine {
    public record SearchDetails(QueryNode ast, List<Integer> candidates, List<String> terms,
        RankingEngine.RankingTrace ranking) {}
    private final InvertedIndex index;
    private final RankingEngine rankingEngine;
    private final Trie autocompleteTrie;
    private final BKTree spellChecker;

    public SearchEngine() {
        this.index = new InvertedIndex();
        this.rankingEngine = new RankingEngine();
        this.autocompleteTrie = new Trie();
        this.spellChecker = new BKTree();
    }

    /** Allocates and inserts a document as one operation for ingestion callers. */
    public synchronized int indexDocument(String title, String content) {
        int docId = index.getNextDocId();
        indexDocument(docId, title, content);
        return docId;
    }

    public synchronized void indexDocument(int docId, String title, String content) {
        Document doc = new Document(docId, title, content);
        index.addDocument(doc);

        // Populate Trie and BKTree from document content tokens
        List<Tokenizer.Token> tokens = Tokenizer.tokenize(content + " " + title);
        for (Tokenizer.Token token : tokens) {
            autocompleteTrie.insert(token.term());
            spellChecker.insert(token.term());
        }
    }

    public void finalizeEngine() {
        index.finalizeIndex();
    }

    public List<String> autocomplete(String prefix, int maxResults) {
        return autocompleteTrie.autocomplete(prefix, maxResults);
    }

    public List<BKTree.Match> suggestCorrections(String word, int maxDistance) {
        return spellChecker.search(word, maxDistance);
    }

    public List<SearchResult> search(String queryStr, int topK) {
        if (queryStr == null || queryStr.isBlank()) return List.of();
        return searchDetailed(queryStr, topK).ranking().results();
    }

    public SearchDetails searchDetailed(String queryStr, int topK) {
        if (queryStr == null || queryStr.trim().isEmpty()) {
            throw new IllegalArgumentException("Enter a query to search.");
        }

        // 1. Evaluate query via AST parser to get matching candidate doc IDs
        QueryNode ast = QueryParser.parse(queryStr, index);
        List<Integer> candidateDocs = ast.evaluate(index);


        // 2. Extract content terms for BM25 ranking
        List<Tokenizer.Token> rawTokens = Tokenizer.tokenize(queryStr);
        List<String> scoringTerms = new ArrayList<>();
        for (Tokenizer.Token t : rawTokens) {
            String term = t.term().toUpperCase();
            if (!term.equals("AND") && !term.equals("OR") && !term.equals("NOT")) {
                scoringTerms.add(t.term());
            }
        }

        // 3. Score and rank matching documents using Min-Heap
        return new SearchDetails(ast, candidateDocs, scoringTerms,
            rankingEngine.rankTopKWithTrace(index, candidateDocs, scoringTerms, topK));
    }

    public InvertedIndex getIndex() {
        return index;
    }
}
