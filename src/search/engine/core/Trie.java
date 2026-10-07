package search.engine.core;

import java.util.*;

public class Trie {

    private static class TrieNode {
        private final Map<Character, TrieNode> children = new HashMap<>();
        private boolean isEndOfWord = false;
        private String word = null;
    }

    private final TrieNode root = new TrieNode();

    public void insert(String word) {
        if (word == null || word.isEmpty()) return;
        TrieNode curr = root;
        for (char c : word.toLowerCase().toCharArray()) {
            curr = curr.children.computeIfAbsent(c, k -> new TrieNode());
        }
        curr.isEndOfWord = true;
        curr.word = word.toLowerCase();
    }

    /**
     * Traverses to prefix node and collects up to maxResults matching terms via DFS.
     * Complexity: O(P + K) where P is prefix length and K is subtree size scanned.
     */
    public List<String> autocomplete(String prefix, int maxResults) {
        List<String> results = new ArrayList<>();
        if (prefix == null || prefix.isEmpty() || maxResults <= 0) return results;

        TrieNode curr = root;
        for (char c : prefix.toLowerCase().toCharArray()) {
            curr = curr.children.get(c);
            if (curr == null) {
                return results; // Prefix not present in lexicon
            }
        }

        dfsCollect(curr, results, maxResults);
        return results;
    }

    private void dfsCollect(TrieNode node, List<String> results, int maxResults) {
        if (node == null || results.size() >= maxResults) return;

        if (node.isEndOfWord) {
            results.add(node.word);
        }

        // Traverse children in deterministic sorted order
        List<Character> sortedKeys = new ArrayList<>(node.children.keySet());
        Collections.sort(sortedKeys);

        for (char c : sortedKeys) {
            if (results.size() >= maxResults) break;
            dfsCollect(node.children.get(c), results, maxResults);
        }
    }
}