package search.engine.core;

import java.util.ArrayList;
import java.util.List;

class TermNode implements QueryNode {
    private final String term;

    public TermNode(String term) {
        this.term = term.toLowerCase();
    }

    public String getTerm() { return term; }
    @Override public String label() { return term; }

    @Override
    public List<Integer> evaluate(InvertedIndex index) {
        List<Integer> docIds = new ArrayList<>();
        PostingsList postings = index.getPostings(term);
        if (postings == null) {
            return docIds;
        }

        PostingNode curr = postings.getHead();
        while (curr != null) {
            docIds.add(curr.getDocId());
            curr = curr.getNext();
        }
        return docIds;
    }
}

class AndNode implements QueryNode {
    private final QueryNode left;
    private final QueryNode right;

    public AndNode(QueryNode left, QueryNode right) {
        this.left = left;
        this.right = right;
    }
    @Override public String label() { return "AND"; }
    @Override public List<QueryNode> children() { return List.of(left, right); }

    @Override
    public List<Integer> evaluate(InvertedIndex index) {
        // Fast-path: If both operands are simple terms, intersect directly using Skip Pointers
        if (left instanceof TermNode t1 && right instanceof TermNode t2) {
            return intersectWithSkips(index.getPostings(t1.getTerm()), index.getPostings(t2.getTerm()));
        }

        // General two-pointer merge for arbitrary subtrees
        List<Integer> lDocs = left.evaluate(index);
        List<Integer> rDocs = right.evaluate(index);
        return intersectSortedLists(lDocs, rDocs);
    }

    /**
     * Intersects two postings lists utilizing Skip Pointers to skip non-matching segments.
     * Skip pointers avoid some comparisons; worst-case traversal is O(L1 + L2).
     */
    public static List<Integer> intersectWithSkips(PostingsList list1, PostingsList list2) {
        List<Integer> result = new ArrayList<>();
        if (list1 == null || list2 == null) return result;

        PostingNode p1 = list1.getHead();
        PostingNode p2 = list2.getHead();

        while (p1 != null && p2 != null) {
            if (p1.getDocId() == p2.getDocId()) {
                result.add(p1.getDocId());
                p1 = p1.getNext();
                p2 = p2.getNext();
            } else if (p1.getDocId() < p2.getDocId()) {
                // If skip pointer exists and stays <= target, jump forward
                if (p1.getSkip() != null && p1.getSkip().getDocId() <= p2.getDocId()) {
                    while (p1.getSkip() != null && p1.getSkip().getDocId() <= p2.getDocId()) {
                        p1 = p1.getSkip();
                    }
                } else {
                    p1 = p1.getNext();
                }
            } else {
                if (p2.getSkip() != null && p2.getSkip().getDocId() <= p1.getDocId()) {
                    while (p2.getSkip() != null && p2.getSkip().getDocId() <= p1.getDocId()) {
                        p2 = p2.getSkip();
                    }
                } else {
                    p2 = p2.getNext();
                }
            }
        }
        return result;
    }

    private List<Integer> intersectSortedLists(List<Integer> l1, List<Integer> l2) {
        List<Integer> result = new ArrayList<>();
        int i = 0, j = 0;
        while (i < l1.size() && j < l2.size()) {
            int id1 = l1.get(i);
            int id2 = l2.get(j);
            if (id1 == id2) {
                result.add(id1);
                i++;
                j++;
            } else if (id1 < id2) {
                i++;
            } else {
                j++;
            }
        }
        return result;
    }
}

class OrNode implements QueryNode {
    private final QueryNode left;
    private final QueryNode right;

    public OrNode(QueryNode left, QueryNode right) {
        this.left = left;
        this.right = right;
    }
    @Override public String label() { return "OR"; }
    @Override public List<QueryNode> children() { return List.of(left, right); }

    @Override
    public List<Integer> evaluate(InvertedIndex index) {
        List<Integer> l1 = left.evaluate(index);
        List<Integer> l2 = right.evaluate(index);
        List<Integer> result = new ArrayList<>();

        int i = 0, j = 0;
        while (i < l1.size() && j < l2.size()) {
            int id1 = l1.get(i);
            int id2 = l2.get(j);
            if (id1 == id2) {
                result.add(id1);
                i++;
                j++;
            } else if (id1 < id2) {
                result.add(id1);
                i++;
            } else {
                result.add(id2);
                j++;
            }
        }
        while (i < l1.size()) result.add(l1.get(i++));
        while (j < l2.size()) result.add(l2.get(j++));

        return result;
    }
}

class NotNode implements QueryNode {
    private final QueryNode left;   // Base set
    private final QueryNode right;  // Negated set to subtract

    public NotNode(QueryNode left, QueryNode right) {
        this.left = left;
        this.right = right;
    }
    @Override public String label() { return "NOT"; }
    @Override public List<QueryNode> children() { return List.of(right); }

    @Override
    public List<Integer> evaluate(InvertedIndex index) {
        List<Integer> universe = left.evaluate(index);
        List<Integer> excluded = right.evaluate(index);
        List<Integer> result = new ArrayList<>();

        int i = 0, j = 0;
        while (i < universe.size() && j < excluded.size()) {
            int baseId = universe.get(i);
            int exclId = excluded.get(j);

            if (baseId == exclId) {
                i++;
                j++;
            } else if (baseId < exclId) {
                result.add(baseId);
                i++;
            } else {
                j++;
            }
        }
        while (i < universe.size()) {
            result.add(universe.get(i++));
        }
        return result;
    }
}
