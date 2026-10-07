package search.engine.test;

import search.engine.core.*;
import java.io.IOException;
import java.util.List;

/** Regression checks for shared document IDs and repeated index updates. */
public class LiveIngestionVerificationTest {
    public static void main(String[] args) throws IOException {
        testCorpusAndRepeatedUpdates();
        testRejectedInsertionPreservesIndex();
        testExplicitIdsAdvanceAllocation();
        testIdExhaustion();
        testSkipPointerRebuild();
        System.out.println("All 5 live-ingestion regression checks passed.");
    }

    private static void testCorpusAndRepeatedUpdates() throws IOException {
        SearchEngine engine = new SearchEngine();
        int count = CorpusIngestor.ingestDirectory("demo_dataset", engine);
        assert count == 4;
        assert engine.getIndex().getNextDocId() == 5;
        for (int id = 5; id <= 20; id++) {
            assert engine.indexDocument("live" + id, "shared update") == id;
            engine.finalizeEngine();
        }
        assert QueryParser.parse("shared AND update", engine.getIndex())
            .evaluate(engine.getIndex()).size() == 16;
        assert engine.search("shared", 5).size() == 5;
        assert CorpusIngestor.ingestDirectory("demo_dataset", engine) == 4;
        assert engine.getIndex().getTotalDocuments() == 24;
        assert engine.getIndex().getNextDocId() == 25;
        PostingNode node = engine.getIndex().getPostings("shared").getHead();
        int previous = 0;
        while (node != null) {
            assert node.getDocId() > previous;
            previous = node.getDocId();
            node = node.getNext();
        }
    }

    private static void testRejectedInsertionPreservesIndex() {
        InvertedIndex index = new InvertedIndex();
        index.addDocument(new Document(10, "existing", "common"));
        for (int id : List.of(9, 10, 0, -1)) {
            boolean rejected = false;
            try { index.addDocument(new Document(id, "invalid", "newterm common")); }
            catch (IllegalArgumentException ex) { rejected = true; }
            assert rejected;
            assert index.getTotalDocuments() == 1;
            assert index.getNextDocId() == 11;
            assert index.getAverageDocumentLength() == 1.0;
            assert index.getPostings("newterm") == null;
            assert index.getPostings("common").size() == 1;
        }
    }

    private static void testExplicitIdsAdvanceAllocation() {
        SearchEngine engine = new SearchEngine();
        engine.indexDocument(1000, "explicit", "common");
        assert engine.indexDocument("automatic", "common") == 1001;
        assert engine.getIndex().getNextDocId() == 1002;
    }

    private static void testIdExhaustion() {
        SearchEngine engine = new SearchEngine();
        engine.indexDocument(Integer.MAX_VALUE, "last", "common");
        boolean rejected = false;
        try { engine.indexDocument("overflow", "common"); }
        catch (IllegalStateException ex) { rejected = true; }
        assert rejected;
        assert engine.getIndex().getTotalDocuments() == 1;
    }

    private static void testSkipPointerRebuild() {
        PostingsList list = new PostingsList();
        for (int id = 1; id <= 9; id++) list.add(id, 0);
        list.buildSkipPointers();
        for (int id = 10; id <= 16; id++) list.add(id, 0);
        list.buildSkipPointers();
        PostingNode node = list.getHead();
        for (int offset = 0; node != null; offset++, node = node.getNext()) {
            if (offset % 4 == 0 && offset + 4 < 16) {
                assert node.getSkip() != null;
                assert node.getSkip().getDocId() == node.getDocId() + 4;
            } else {
                assert node.getSkip() == null;
            }
        }
    }
}
