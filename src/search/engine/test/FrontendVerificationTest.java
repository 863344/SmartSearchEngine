package search.engine.test;

import search.engine.core.*;
import search.engine.web.LocalServer;
import java.net.*;
import java.net.http.*;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Exercises browser-facing behavior through a real local HTTP server. */
public class FrontendVerificationTest {
    private static final HttpClient CLIENT = HttpClient.newHttpClient();
    private static String base;

    public static void main(String[] args) throws Exception {
        boolean assertions = false; assert assertions = true;
        if (!assertions) throw new IllegalStateException("Run this suite with java -ea.");
        testParser(); testHeapTrace();
        try (LocalServer server = new LocalServer(Path.of("."), 0)) {
            base = "http://127.0.0.1:" + server.port();
            testStaticAndStatus(); testSearchAndSuggestions(); testImportsAndSwitching(); testApiErrors();
        }
        System.out.println("All 6 frontend verification groups passed.");
    }

    private static void testParser() {
        InvertedIndex index = new InvertedIndex();
        index.addDocument(new Document(1,"one","alpha beta"));
        index.addDocument(new Document(10,"two","beta gamma"));
        index.addDocument(new Document(1000,"live","gamma"));
        assert QueryParser.parse("alpha beta",index).evaluate(index).equals(List.of(1));
        assert QueryParser.parse("NOT beta",index).evaluate(index).equals(List.of(1000));
        assert QueryParser.parse("alpha OR NOT beta",index).evaluate(index).equals(List.of(1,1000));
        assert QueryParser.parse("NOT NOT beta",index).evaluate(index).equals(List.of(1,10));
        assert QueryParser.parse("(alpha OR gamma) NOT beta",index).evaluate(index).equals(List.of(1000));
        assert QueryParser.parse("beta AND NOT alpha",index).evaluate(index).equals(List.of(10));
        QueryNode ast = QueryParser.parse("alpha OR beta AND gamma",index);
        assert ast.label().equals("OR") && ast.children().get(1).label().equals("AND");
        for (String invalid : List.of("alpha AND","(alpha","alpha)","()","AND alpha","\"unclosed","\"\"","alpha OR OR beta")) {
            boolean failed=false; try {QueryParser.parse(invalid,index);} catch(IllegalArgumentException ex){failed=true;}
            assert failed : "Accepted invalid input: " + invalid;
        }
    }

    private static void testHeapTrace() {
        InvertedIndex index=new InvertedIndex();
        index.addDocument(new Document(1,"one","cache filler filler filler"));
        index.addDocument(new Document(2,"two","cache cache cache"));
        index.addDocument(new Document(3,"three","unrelated"));
        var trace=new RankingEngine().rankTopKWithTrace(index,List.of(1,2,3),List.of("cache"),1);
        assert trace.offered()==3 && trace.replaced()==1 && trace.discarded()==1;
        assert trace.results().get(0).docId()==2 && trace.retained().get(0).docId()==2;
        assert trace.events().get(1).action().equals("replace root");
    }

    private static void testStaticAndStatus() throws Exception {
        var page=get("/");assert page.statusCode()==200 && page.body().contains("Search your collection");
        assert page.headers().firstValue("Content-Security-Policy").isPresent();
        assert get("/app.js").body().contains("renderInspector");
        assert get("/api/status").body().contains("\"documents\":4");
        assert get("/../README.md").statusCode()==404;
    }

    private static void testSearchAndSuggestions() throws Exception {
        var response=post("/api/search",Map.of("query","data structures","k","3"));
        assert response.statusCode()==200;
        assert response.body().contains("\"label\":\"AND\"") && response.body().contains("doc1.txt");
        assert response.body().contains("\"postings\"") && response.body().contains("\"retained\"");
        assert get("/api/autocomplete?prefix=mem").body().contains("memory");
        assert get("/api/spell?term=systms").body().contains("systems");
        assert post("/api/search",Map.of("query","zzzznonexistent")).body().contains("\"matched\":0");
    }

    private static void testImportsAndSwitching() throws Exception {
        String title="quotes\" & <tag>.txt";
        var added=post("/api/document",Map.of("title",title,"content","frontendproof \"quoted\"\nsecond line"));
        assert added.statusCode()==201 && added.body().contains("\"id\":5");
        assert get("/api/document?id=5").body().contains("\\\"quoted\\\"\\nsecond line");
        assert post("/api/search",Map.of("query","frontendproof")).body().contains("\"matched\":1");
        var bad=post("/api/load",Map.of("directory","C:\\missing-folder-dsa-test"));
        assert bad.statusCode()==400 && get("/api/status").body().contains("\"documents\":5");
        var switched=post("/api/load",Map.of("dataset","rfc"));
        assert switched.statusCode()==200 && switched.body().contains("RFC collection");
        assert post("/api/search",Map.of("query","frontendproof")).body().contains("\"matched\":0");
        assert post("/api/load",Map.of("dataset","demo")).body().contains("\"documents\":4");
    }

    private static void testApiErrors() throws Exception {
        assert post("/api/search",Map.of("query","data AND")).statusCode()==400;
        assert post("/api/search",Map.of("query","data","k","0")).statusCode()==400;
        assert post("/api/document",Map.of("title","bad.exe","content","bad")).statusCode()==400;
        assert post("/api/status",Map.of()).statusCode()==405;
        var request=HttpRequest.newBuilder(URI.create(base+"/api/search")).header("Origin","https://example.com")
            .header("Content-Type","application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString("query=data")).build();
        assert CLIENT.send(request,HttpResponse.BodyHandlers.ofString()).statusCode()==403;
    }

    private static HttpResponse<String> get(String route) throws Exception {
        return CLIENT.send(HttpRequest.newBuilder(URI.create(base+route)).GET().build(),HttpResponse.BodyHandlers.ofString());
    }
    private static HttpResponse<String> post(String route,Map<String,String> params) throws Exception {
        List<String> parts=new ArrayList<>();
        params.forEach((key,value)->parts.add(URLEncoder.encode(key,StandardCharsets.UTF_8)+"="+URLEncoder.encode(value,StandardCharsets.UTF_8)));
        return CLIENT.send(HttpRequest.newBuilder(URI.create(base+route)).header("Content-Type","application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(String.join("&",parts))).build(),HttpResponse.BodyHandlers.ofString());
    }
}
