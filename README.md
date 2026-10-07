# Smart Document Search System

A Java DSA project with a local browser frontend. The browser uses the real Java search engine.

## Team

Nishok (2510030201), Sharan (2510039435), Vasthav (2510030297), Aman Pasha (2501130428). Guide: Dr. Anitha P. Computer Science and Engineering, KLH Aziz Nagar.

## Run the frontend

Open PowerShell in this folder and run `.\start-web.ps1`. Open http://127.0.0.1:8765 in a browser. Keep the terminal open. Stop with Ctrl+C. Use `.\start-web.ps1 -Port 8766` for a different port.

The project uses features from JDK 16 onward. The full build passed on Microsoft OpenJDK 21. HTML, CSS and JavaScript form the interface. No external packages are required.

## Features

- Search .txt/.md files with words, quoted phrases, AND, OR, NOT, and parentheses. Adjacent words use AND. Precedence: NOT, AND, OR.
- Choose Top K, view BM25 scores, and read complete documents.
- Inspect real linked postings, word positions, skip pointers, the query AST, and min-heap decisions.
- Use Trie prefix completion and BK-Tree spelling suggestions within two edits.
- Add local files or folders, or open a built-in collection. Opening a collection replaces the index. Added documents stay only until restart or collection replacement.

## DSA topics

| Topic | Use |
| --- | --- |
| HashMap | Map terms to posting lists |
| Linked lists | Store document IDs and word positions |
| Skip pointers | Bypass some nonmatching postings during AND |
| Stacks and Shunting-Yard | Parse queries into an AST |
| Positional matching | Check adjacent words for phrases |
| Trie | Complete vocabulary prefixes |
| BK-Tree | Narrow spelling candidates by distance |
| Dynamic programming | Compute Levenshtein edit distance |
| Bounded min-heap | Select the highest scoring K documents |

Posting intersection is O(L1 + L2) in the worst case. Trie prefix traversal is O(P), plus completion enumeration. BK-Tree search may visit the whole vocabulary. Edit distance takes O(a*b) time. Heap selection takes O(M log K), plus BM25 scoring and final sorting of K results. BM25 currently scans postings to find term frequencies.

## CLI and tests

After compilation, run `java -cp bin search.engine.Main demo_dataset`. Commands: `:auto <prefix>`, `:spell <word>`, `:ingest <file path>`.

Run each suite using `java -ea -cp bin search.engine.test.<SuiteName>`:
Phase1VerificationTest, Phase2VerificationTest, Phase3VerificationTest, Phase4VerificationTest, Phase5VerificationTest, LiveIngestionVerificationTest, FrontendVerificationTest.

All 17 original test methods, 5 live-ingestion regression methods, and 6 frontend verification groups passed. Browser checks covered search, structure tabs, spelling, collection controls, and desktop/mobile layout.

## Current limits

The server runs locally and keeps the index and complete documents in RAM. No persistent database, PDF/OCR support, or validated minimum RAM requirement exists. Requests run sequentially. Browser uploads allow 100 files per selection, 1 MB each. The included datasets demonstrate functionality, not production scalability.

## Files

- src/search/engine/core: DSA and retrieval
- src/search/engine/web: HTTP server and JSON encoding
- src/search/engine/test: verification
- frontend: browser interface
- demo_dataset and corpus: included documents
- SmartSearchEngine_Presentation.pptx: academic presentation
