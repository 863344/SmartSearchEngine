package search.engine;

import search.engine.core.*;
import java.io.IOException;
import java.util.*;

public class Main {

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("FATAL: Target corpus directory required.");
            System.err.println("Usage: java -cp bin search.engine.Main <corpus_directory>");
            System.exit(1);
        }

        String corpusPath = args[0];
        SearchEngine engine = new SearchEngine();
        
        System.out.println("Initiating OS-level file traversal at: " + corpusPath);
        long startIngest = System.currentTimeMillis();
        
        try {
            int docCount = CorpusIngestor.ingestDirectory(corpusPath, engine);
            long elapsed = System.currentTimeMillis() - startIngest;
            System.out.printf("Index finalized. Ingested %d documents in %d ms.%n", docCount, elapsed);
        } catch (IOException e) {
            System.err.println("I/O Fault during corpus ingestion: " + e.getMessage());
            System.exit(1);
        }

        Scanner scanner = new Scanner(System.in);
        printBanner();

        while (true) {
            System.out.print("\nsearch-engine> ");
            String input = scanner.nextLine().trim();

            if (input.equalsIgnoreCase("exit") || input.equalsIgnoreCase("quit")) {
                break;
            }
            if (input.isEmpty()) {
                continue;
            }

            if (input.startsWith(":auto ")) {
                String prefix = input.substring(6).trim();
                System.out.println("Autocomplete for \"" + prefix + "\": " + engine.autocomplete(prefix, 5));
                continue;
            }

            if (input.startsWith(":spell ")) {
                String term = input.substring(7).trim();
                List<BKTree.Match> matches = engine.suggestCorrections(term, 2);
                System.out.println("Did you mean:");
                for (BKTree.Match m : matches) {
                    System.out.printf("  - %s (d=%d)%n", m.word(), m.distance());
                }
                continue;
            }

            // Command: Live file ingestion
            if (input.startsWith(":ingest ")) {
                String filePath = input.substring(8).trim();
                try {
                    String content = java.nio.file.Files.readString(java.nio.file.Paths.get(filePath));
                    engine.indexDocument(filePath, content);
                    engine.finalizeEngine(); // Recompute skip pointers for the new data
                    System.out.println("Live ingestion successful. Indexed: " + filePath);
                } catch (Exception e) {
                    System.out.println("Ingestion failed: " + e.getMessage());
                }
                continue;
            }

            long startTime = System.nanoTime();
            List<SearchResult> results;
            try {
                results = engine.search(input, 5);
            } catch (IllegalArgumentException ex) {
                System.out.println("Query error: " + ex.getMessage());
                continue;
            }
            double elapsedMs = (System.nanoTime() - startTime) / 1_000_000.0;

            if (results.isEmpty()) {
                System.out.println("No matches.");
                List<Tokenizer.Token> tokens = Tokenizer.tokenize(input);
                if (tokens.size() == 1) {
                    List<BKTree.Match> suggestions = engine.suggestCorrections(tokens.get(0).term(), 1);
                    if (!suggestions.isEmpty()) {
                        System.out.print("Did you mean: ");
                        for (BKTree.Match s : suggestions) System.out.print("\"" + s.word() + "\" ");
                        System.out.println();
                    }
                }
                continue;
            }

            System.out.printf("Found %d result(s) in %.3f ms:%n%n", results.size(), elapsedMs);
            int rank = 1;
            for (SearchResult res : results) {
                System.out.printf("[%d] File: %s | BM25: %.4f%n", rank++, res.document().title(), res.score());
                System.out.printf("    Snippet: %s%n%n", truncate(res.document().content().replace("\n", " ").replaceAll("\\s+", " "), 120));
            }
        }
        scanner.close();
    }

    private static String truncate(String text, int maxLen) {
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "...";
    }

    private static void printBanner() {
        System.out.println("==========================================================");
        System.out.println("  Smart Document Search System (DSA Engine CLI)");
        System.out.println("==========================================================");
    }
}
