package search.engine.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Common interface for all query AST expression nodes.
 */
public interface QueryNode {
    /**
     * Evaluates the node and returns a strictly sorted list of matching doc IDs.
     */
    List<Integer> evaluate(InvertedIndex index);

    default String label() { return "ALL DOCUMENTS"; }
    default List<QueryNode> children() { return List.of(); }
}
