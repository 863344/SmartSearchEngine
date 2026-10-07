package search.engine.core;

/**
 * Immutable metadata and payload container for indexed documents.
 */
public record Document(int docId, String title, String content) {}