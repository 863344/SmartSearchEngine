package search.engine.core;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.concurrent.atomic.AtomicInteger;

public class CorpusIngestor {
    
    /**
     * Traverses the target directory, reading all text/markdown files into the index.
     * Returns the total number of documents ingested.
     */
    public static int ingestDirectory(String directoryPath, SearchEngine engine) throws IOException {
        Path startPath = Paths.get(directoryPath);
        AtomicInteger docCounter = new AtomicInteger(0);

        Files.walkFileTree(startPath, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                String fileName = file.getFileName().toString();
                
                if (fileName.endsWith(".txt") || fileName.endsWith(".md")) {
                    // Read the file content into memory before indexing.
                    String content = Files.readString(file);
                    engine.indexDocument(fileName, content);
                    docCounter.incrementAndGet();
                }
                return FileVisitResult.CONTINUE;
            }
        });

        engine.finalizeEngine();
        return docCounter.get();
    }
}
