package search.engine.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Evaluates positional phrase queries (e.g., "distributed consensus algorithms").
 * Guarantees that matching documents contain all terms strictly in sequence.
 */
public class PhraseNode implements QueryNode {
    private final List<String> phraseTerms;

    public PhraseNode(List<String> terms) {
        this.phraseTerms = new ArrayList<>();
        for (String t : terms) {
            String clean = t.trim().toLowerCase();
            if (!clean.isEmpty()) {
                this.phraseTerms.add(clean);
            }
        }
    }

    public List<String> getTerms() {
        return Collections.unmodifiableList(phraseTerms);
    }
    @Override public String label() { return "\"" + String.join(" ", phraseTerms) + "\""; }

    @Override
    public List<Integer> evaluate(InvertedIndex index) {
        if (phraseTerms.isEmpty()) {
            return Collections.emptyList();
        }

        if (phraseTerms.size() == 1) {
            PostingsList pl = index.getPostings(phraseTerms.get(0));
            if (pl == null) return Collections.emptyList();
            List<Integer> res = new ArrayList<>();
            PostingNode curr = pl.getHead();
            while (curr != null) {
                res.add(curr.getDocId());
                curr = curr.getNext();
            }
            return res;
        }

        // Positional intersection accumulator: maps docId -> matching ending positions of phrase prefix
        List<DocPositions> matches = null;

        for (int i = 0; i < phraseTerms.size(); i++) {
            PostingsList postings = index.getPostings(phraseTerms.get(i));
            if (postings == null) {
                return Collections.emptyList(); // Any missing term invalidates the whole phrase
            }

            if (i == 0) {
                matches = new ArrayList<>();
                PostingNode curr = postings.getHead();
                while (curr != null) {
                    matches.add(new DocPositions(curr.getDocId(), new ArrayList<>(curr.getPositions())));
                    curr = curr.getNext();
                }
            } else {
                matches = intersectPositions(matches, postings);
                if (matches.isEmpty()) {
                    return Collections.emptyList();
                }
            }
        }

        List<Integer> result = new ArrayList<>(matches.size());
        for (DocPositions dp : matches) {
            result.add(dp.docId);
        }
        return result;
    }

    private record DocPositions(int docId, List<Integer> positions) {}

    /**
     * Intersects existing positional prefix matches with the next term's postings list.
     * Keeps doc IDs where pos(nextTerm) == pos(prevTerm) + 1.
     */
    private List<DocPositions> intersectPositions(List<DocPositions> prevMatches, PostingsList nextPostings) {
        List<DocPositions> newMatches = new ArrayList<>();
        int i = 0;
        PostingNode p2 = nextPostings.getHead();

        while (i < prevMatches.size() && p2 != null) {
            DocPositions dp1 = prevMatches.get(i);
            int doc1 = dp1.docId;
            int doc2 = p2.getDocId();

            if (doc1 == doc2) {
                List<Integer> matchedEndingPositions = new ArrayList<>();
                List<Integer> pos1 = dp1.positions;
                List<Integer> pos2 = p2.getPositions();

                int k = 0, l = 0;
                while (k < pos1.size() && l < pos2.size()) {
                    int p1Val = pos1.get(k);
                    int p2Val = pos2.get(l);

                    if (p2Val == p1Val + 1) {
                        matchedEndingPositions.add(p2Val);
                        k++;
                        l++;
                    } else if (p2Val < p1Val + 1) {
                        l++;
                    } else {
                        k++;
                    }
                }

                if (!matchedEndingPositions.isEmpty()) {
                    newMatches.add(new DocPositions(doc1, matchedEndingPositions));
                }
                i++;
                p2 = p2.getNext();
            } else if (doc1 < doc2) {
                i++;
            } else {
                if (p2.getSkip() != null && p2.getSkip().getDocId() <= doc1) {
                    while (p2.getSkip() != null && p2.getSkip().getDocId() <= doc1) {
                        p2 = p2.getSkip();
                    }
                } else {
                    p2 = p2.getNext();
                }
            }
        }
        return newMatches;
    }
}
