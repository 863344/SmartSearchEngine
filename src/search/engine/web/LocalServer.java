package search.engine.web;

import com.sun.net.httpserver.*;
import search.engine.core.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Loopback HTTP adapter. Requests run serially because the in-memory engine is shared. */
public final class LocalServer implements AutoCloseable {
    private final HttpServer server;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Path project;
    private SearchEngine engine;
    private String collection;
    private long revision;

    public LocalServer(Path project, int port) throws IOException {
        this.project = project.toAbsolutePath().normalize();
        load(this.project.resolve("demo_dataset"), "Demo collection");
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        server.setExecutor(executor);
        server.createContext("/", this::handle);
        server.start();
    }

    public int port() { return server.getAddress().getPort(); }
    @Override public void close() { server.stop(0); executor.shutdownNow(); }

    public static void main(String[] args) throws IOException {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 8765;
        LocalServer app = new LocalServer(Path.of("."), port);
        Runtime.getRuntime().addShutdownHook(new Thread(app::close));
        System.out.println("Smart Document Search: http://127.0.0.1:" + app.port());
        System.out.println("Keep this process running. Press Ctrl+C to stop.");
    }

    private void handle(HttpExchange exchange) throws IOException {
        try {
            exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
            exchange.getResponseHeaders().set("Cache-Control", "no-store");
            exchange.getResponseHeaders().set("Content-Security-Policy", "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; connect-src 'self'; frame-ancestors 'none'; base-uri 'none'");
            String path = exchange.getRequestURI().getPath();
            if (!path.startsWith("/api/")) { serveStatic(exchange, path); return; }
            boolean get = Set.of("/api/status", "/api/autocomplete", "/api/spell").contains(path)
                || (path.equals("/api/document") && exchange.getRequestMethod().equals("GET"));
            String expectedMethod = get ? "GET" : "POST";
            if (!exchange.getRequestMethod().equals(expectedMethod)) {
                reply(exchange, 405, map("error", "Use " + expectedMethod + " for this request.")); return;
            }
            String origin = exchange.getRequestHeaders().getFirst("Origin");
            if (origin != null && !origin.equals("http://127.0.0.1:" + port()) && !origin.equals("http://localhost:" + port())) {
                reply(exchange, 403, map("error", "Use the interface served by this local application.")); return;
            }
            Map<String,String> params = get ? form(exchange.getRequestURI().getRawQuery()) : readForm(exchange);
            switch (path) {
                case "/api/status" -> reply(exchange, 200, status());
                case "/api/autocomplete" -> reply(exchange, 200, map("suggestions", engine.autocomplete(params.getOrDefault("prefix", ""), 7)));
                case "/api/spell" -> {
                    String term = params.getOrDefault("term", "").trim();
                    if (term.length() > 80) throw new IllegalArgumentException("Use a single word of at most 80 characters.");
                    List<Object> suggestions = new ArrayList<>();
                    for (BKTree.Match match : engine.suggestCorrections(term, 2)) {
                        if (suggestions.size() == 12) break;
                        suggestions.add(map("word", match.word(), "distance", match.distance()));
                    }
                    reply(exchange, 200, map("suggestions", suggestions));
                }
                case "/api/search" -> reply(exchange, 200, search(params));
                case "/api/document" -> {
                    if (get) {
                        Document doc = engine.getIndex().getDocument(Integer.parseInt(params.getOrDefault("id", "0")));
                        if (doc == null) { reply(exchange, 404, map("error", "Document not found.")); return; }
                        reply(exchange, 200, map("id",doc.docId(),"title",doc.title(),"content",doc.content()));
                    } else {
                        String title = params.getOrDefault("title", "").trim();
                        String content = params.getOrDefault("content", "");
                        if (title.isEmpty() || title.length() > 255 || !(title.toLowerCase(Locale.ROOT).endsWith(".txt") || title.toLowerCase(Locale.ROOT).endsWith(".md"))) {
                            throw new IllegalArgumentException("Select a .txt or .md document with a title under 256 characters.");
                        }
                        if (content.length() > 1_000_000) throw new IllegalArgumentException("Use a document under 1 MB for this demonstration.");
                        int id = engine.indexDocument(title, content);
                        engine.finalizeEngine(); revision++;
                        if (collection.equals("Demo collection") || collection.equals("RFC collection")) collection += " + added documents";
                        reply(exchange, 201, map("id", id, "status", status()));
                    }
                }
                case "/api/load" -> {
                    String dataset = params.getOrDefault("dataset", "");
                    Path directory;
                    String label;
                    if (dataset.equals("demo")) { directory = project.resolve("demo_dataset"); label = "Demo collection"; }
                    else if (dataset.equals("rfc")) { directory = project.resolve("corpus"); label = "RFC collection"; }
                    else {
                        String pathText = params.getOrDefault("directory", "").trim();
                        if (pathText.isEmpty()) throw new IllegalArgumentException("Enter a local folder path.");
                        directory = Path.of(pathText).toAbsolutePath().normalize(); label = directory.getFileName() == null ? directory.toString() : directory.getFileName().toString();
                    }
                    load(directory, label);
                    reply(exchange, 200, status());
                }
                default -> reply(exchange, 404, map("error", "Unknown API route."));
            }
        } catch (IllegalArgumentException ex) {
            reply(exchange, 400, map("error", ex.getMessage() == null ? "Invalid input." : ex.getMessage()));
        } catch (IOException ex) {
            reply(exchange, 400, map("error", "Could not read the folder or document. Check its path and access."));
        } catch (Exception ex) {
            System.err.println("Local API: " + ex);
            reply(exchange, 500, map("error", "The request could not be completed. Check the server terminal."));
        } finally { exchange.close(); }
    }

    private void load(Path path, String label) throws IOException {
        if (!Files.isDirectory(path)) throw new IllegalArgumentException("That folder does not exist.");
        SearchEngine next = new SearchEngine();
        int count = CorpusIngestor.ingestDirectory(path.toString(), next);
        if (count == 0) throw new IllegalArgumentException("No .txt or .md documents were found in that folder.");
        engine = next; collection = label; revision++;
    }

    private Map<String,Object> status() {
        InvertedIndex index = engine.getIndex();
        List<Object> documents = new ArrayList<>();
        long tokens = 0;
        for (int id : index.getDocumentIds()) {
            tokens += index.getDocLength(id);
            if (documents.size() < 200) documents.add(map("id",id,"title",index.getDocument(id).title(),"tokens",index.getDocLength(id)));
        }
        return map("collection",collection,"documents",index.getTotalDocuments(),"terms",index.getLexicon().size(),
            "tokens",tokens,"revision",revision,"files",documents);
    }

    private Map<String,Object> search(Map<String,String> params) {
        String query = params.getOrDefault("query", "").trim();
        if (query.length() > 512) throw new IllegalArgumentException("Keep queries under 513 characters.");
        int k = Integer.parseInt(params.getOrDefault("k", "5"));
        if (k < 1 || k > 50) throw new IllegalArgumentException("Top K must be between 1 and 50.");
        long started = System.nanoTime();
        SearchEngine.SearchDetails details = engine.searchDetailed(query, k);
        double elapsed = (System.nanoTime() - started) / 1_000_000.0;
        List<Object> results = new ArrayList<>(), retained = new ArrayList<>(), events = new ArrayList<>(), postings = new ArrayList<>();
        for (SearchResult item : details.ranking().results()) results.add(result(item));
        for (SearchResult item : details.ranking().retained()) retained.add(result(item));
        for (RankingEngine.HeapEvent event : details.ranking().events()) events.add(map("id",event.docId(),"score",event.score(),"action",event.action(),"root",event.rootScore()));
        for (String term : new LinkedHashSet<>(details.terms())) {
            if (postings.size() == 10) break;
            PostingsList list = engine.getIndex().getPostings(term);
            List<Object> nodes = new ArrayList<>();
            for (PostingNode node = list == null ? null : list.getHead(); node != null && nodes.size() < 60; node = node.getNext()) {
                nodes.add(map("id",node.getDocId(),"frequency",node.getTermFrequency(),"positions",node.getPositions().stream().limit(20).toList(),"skip",node.getSkip() == null ? null : node.getSkip().getDocId()));
            }
            postings.add(map("term",term,"length",list == null ? 0 : list.size(),"nodes",nodes));
        }
        return map("query",query,"k",k,"elapsedMs",elapsed,"matched",details.candidates().size(),"results",results,
            "ast",ast(details.ast()),"postings",postings,"revision",revision,
            "heap",map("retained",retained,"events",events,"offered",details.ranking().offered(),"replaced",details.ranking().replaced(),"discarded",details.ranking().discarded()));
    }

    private Map<String,Object> ast(QueryNode node) {
        List<Object> children = new ArrayList<>();
        for (QueryNode child : node.children()) children.add(ast(child));
        return map("label",node.label(),"children",children);
    }

    private Map<String,Object> result(SearchResult item) {
        String content = item.document().content();
        return map("id",item.docId(),"title",item.document().title(),"score",item.score(),"tokens",engine.getIndex().getDocLength(item.docId()),
            "snippet",content.substring(0, Math.min(content.length(), 220)).replaceAll("\\s+", " "));
    }

    private void serveStatic(HttpExchange exchange, String route) throws IOException {
        if (!exchange.getRequestMethod().equals("GET")) { reply(exchange,405,map("error","Use GET.")); return; }
        Map<String,String> assets = Map.of("/","index.html","/index.html","index.html","/app.js","app.js","/styles.css","styles.css","/favicon.svg","favicon.svg");
        String file = assets.get(route);
        if (file == null) { reply(exchange,404,map("error","Page not found.")); return; }
        Path path = project.resolve("frontend").resolve(file);
        String type = file.endsWith(".css") ? "text/css" : file.endsWith(".js") ? "text/javascript" : file.endsWith(".svg") ? "image/svg+xml" : "text/html";
        byte[] bytes = Files.readAllBytes(path);
        exchange.getResponseHeaders().set("Content-Type",type+"; charset=utf-8");
        exchange.sendResponseHeaders(200,bytes.length); exchange.getResponseBody().write(bytes);
    }

    private Map<String,String> readForm(HttpExchange exchange) throws IOException {
        String type = exchange.getRequestHeaders().getFirst("Content-Type");
        if (type == null || !type.startsWith("application/x-www-form-urlencoded")) throw new IllegalArgumentException("Use form-encoded input.");
        byte[] bytes = exchange.getRequestBody().readNBytes(4_000_001);
        if (bytes.length > 4_000_000) throw new IllegalArgumentException("The document request is too large.");
        return form(new String(bytes,StandardCharsets.UTF_8));
    }

    private static Map<String,String> form(String input) {
        Map<String,String> result = new HashMap<>();
        if (input == null || input.isEmpty()) return result;
        for (String part : input.split("&")) {
            String[] pair = part.split("=",2);
            result.put(URLDecoder.decode(pair[0],StandardCharsets.UTF_8), pair.length == 2 ? URLDecoder.decode(pair[1],StandardCharsets.UTF_8) : "");
        }
        return result;
    }

    private static Map<String,Object> map(Object... pairs) {
        Map<String,Object> result = new LinkedHashMap<>();
        for (int i=0;i<pairs.length;i+=2) result.put((String)pairs[i],pairs[i+1]);
        return result;
    }

    private static void reply(HttpExchange exchange, int code, Object data) throws IOException {
        byte[] bytes = Json.encode(data).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type","application/json; charset=utf-8");
        exchange.sendResponseHeaders(code,bytes.length); exchange.getResponseBody().write(bytes);
    }
}
