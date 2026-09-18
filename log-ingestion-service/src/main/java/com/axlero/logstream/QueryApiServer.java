package com.axlero.logstream;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

// This is the BRIDGE between our gRPC backend and the React frontend.
//
// Browsers cannot call a gRPC server directly - they only speak plain
// HTTP. So this is a small, separate HTTP server (built into Java, no
// extra library needed) that reads from the SAME Lucene index our gRPC
// server writes to, and exposes it as simple REST endpoints that
// fetch() in React can call directly.
//
// Run this AFTER LogIngestionServer has already sent some logs in,
// so there is something to serve.
//
// Endpoints:
//   GET http://localhost:8080/api/search?q=level:ERROR
//   GET http://localhost:8080/api/aggregations
public class QueryApiServer {

    public static void main(String[] args) throws Exception {
        LuceneIndexer indexer = new LuceneIndexer();

        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);

        server.createContext("/api/search", exchange -> {
            addCorsHeaders(exchange);
            if (exchange.getRequestMethod().equals("OPTIONS")) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            String json;
            try {
                String query = getQueryParam(exchange, "q");
                List<String> results = indexer.search(query == null ? "*:*" : query, 50);
                json = toJsonArray(results);
            } catch (Exception e) {
                json = "{\"error\": \"" + e.getMessage() + "\"}";
            }
            sendJson(exchange, json);
        });

        server.createContext("/api/aggregations", exchange -> {
            addCorsHeaders(exchange);
            if (exchange.getRequestMethod().equals("OPTIONS")) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            String json;
            try {
                int total = indexer.getTotalLogs();
                Map<String, Integer> byService = indexer.countByField("service");
                Map<String, Integer> byLevel = indexer.countByField("level");
                json = "{\"totalLogs\": " + total
                        + ", \"byService\": " + toJsonObject(byService)
                        + ", \"byLevel\": " + toJsonObject(byLevel) + "}";
            } catch (Exception e) {
                json = "{\"error\": \"" + e.getMessage() + "\"}";
            }
            sendJson(exchange, json);
        });

        server.setExecutor(null);
        server.start();
        System.out.println("Query API running on http://localhost:8080");
        System.out.println("Try in a browser: http://localhost:8080/api/search?q=level:ERROR");
        System.out.println("Try in a browser: http://localhost:8080/api/aggregations");
    }

    private static String getQueryParam(HttpExchange exchange, String name) {
        String rawQuery = exchange.getRequestURI().getRawQuery();
        if (rawQuery == null) return null;
        for (String pair : rawQuery.split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2 && kv[0].equals(name)) {
                return URLDecoder.decode(kv[1], StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    // Lets the React dev server (a different port, e.g. localhost:5173)
    // call this API without the browser blocking the request.
    private static void addCorsHeaders(HttpExchange exchange) {
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, OPTIONS");
        exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");
    }

    private static void sendJson(HttpExchange exchange, String json) throws IOException {
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static String toJsonArray(List<String> items) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append("\"").append(items.get(i).replace("\"", "\\\"")).append("\"");
        }
        sb.append("]");
        return sb.toString();
    }

    private static String toJsonObject(Map<String, Integer> map) {
        StringBuilder sb = new StringBuilder("{");
        int i = 0;
        for (Map.Entry<String, Integer> entry : map.entrySet()) {
            if (i++ > 0) sb.append(", ");
            sb.append("\"").append(entry.getKey()).append("\": ").append(entry.getValue());
        }
        sb.append("}");
        return sb.toString();
    }
}
