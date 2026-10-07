package search.engine.core;

import java.util.*;

public class BKTree {

    public record Match(String word, int distance) implements Comparable<Match> {
        @Override
        public int compareTo(Match o) {
            int cmp = Integer.compare(this.distance, o.distance);
            return (cmp != 0) ? cmp : this.word.compareTo(o.word);
        }
    }

    private static class Node {
        private final String term;
        // Edge weight is discrete Levenshtein distance -> child node
        private final Map<Integer, Node> children = new HashMap<>();

        public Node(String term) {
            this.term = term;
        }
    }

    private Node root;

    public void insert(String word) {
        if (word == null || word.isEmpty()) return;
        String clean = word.toLowerCase();

        if (root == null) {
            root = new Node(clean);
            return;
        }

        Node curr = root;
        while (true) {
            int dist = levenshteinDistance(curr.term, clean);
            if (dist == 0) return; // Word already in tree

            Node child = curr.children.get(dist);
            if (child == null) {
                curr.children.put(dist, new Node(clean));
                break;
            }
            curr = child;
        }
    }

    /**
     * Finds all terms within maxDistance using Triangle Inequality pruning.
     * Condition: D - N <= edge_weight <= D + N
     */
    public List<Match> search(String query, int maxDistance) {
        List<Match> results = new ArrayList<>();
        if (root == null || query == null || query.isEmpty()) return results;

        String clean = query.toLowerCase();
        Deque<Node> queue = new ArrayDeque<>();
        queue.push(root);

        while (!queue.isEmpty()) {
            Node curr = queue.pop();
            int d = levenshteinDistance(curr.term, clean);

            if (d <= maxDistance) {
                results.add(new Match(curr.term, d));
            }

            int minD = Math.max(1, d - maxDistance);
            int maxD = d + maxDistance;

            for (Map.Entry<Integer, Node> entry : curr.children.entrySet()) {
                int edgeDist = entry.getKey();
                if (edgeDist >= minD && edgeDist <= maxD) {
                    queue.push(entry.getValue());
                }
            }
        }

        Collections.sort(results);
        return results;
    }

    /**
     * Space-optimized Levenshtein distance using two DP rows: O(min(N, M)) auxiliary space.
     */
    public static int levenshteinDistance(String s1, String s2) {
        if (s1.equals(s2)) return 0;
        if (s1.isEmpty()) return s2.length();
        if (s2.isEmpty()) return s1.length();

        // Ensure s1 is shorter to minimize DP array memory allocation
        if (s1.length() > s2.length()) {
            String temp = s1;
            s1 = s2;
            s2 = temp;
        }

        int m = s1.length();
        int n = s2.length();
        int[] prev = new int[m + 1];
        int[] curr = new int[m + 1];

        for (int i = 0; i <= m; i++) prev[i] = i;

        for (int j = 1; j <= n; j++) {
            curr[0] = j;
            char c2 = s2.charAt(j - 1);

            for (int i = 1; i <= m; i++) {
                char c1 = s1.charAt(i - 1);
                int cost = (c1 == c2) ? 0 : 1;

                curr[i] = Math.min(
                    Math.min(curr[i - 1] + 1, prev[i] + 1), // Insertion, Deletion
                    prev[i - 1] + cost                     // Substitution
                );
            }

            System.arraycopy(curr, 0, prev, 0, m + 1);
        }

        return prev[m];
    }
}