package com.axlero.logstream;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.net.InetSocketAddress;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

// This is the WebSocket side of Live Tail.
//
// Everything else in this project (search, aggregations, timeline,
// alerts) works by the browser ASKING for data and the server
// ANSWERING once. A WebSocket is different: the browser connects
// ONCE, and the connection stays open. After that, the SERVER can
// push data to the browser whenever it wants, with no new request
// needed each time.
//
// We use this so that the moment a new log arrives (in sendLog,
// below in LogIngestionServer), we can push it straight to every
// open Live Tail tab - bypassing the Lucene search index entirely,
// exactly as the project guide asks for.
public class LiveTailWebSocketServer extends WebSocketServer {

    // Every browser tab currently connected to Live Tail.
    // ConcurrentHashMap.newKeySet() is thread-safe - important because
    // gRPC calls (sendLog) arrive on different threads than this
    // WebSocket server's own threads.
    private final Set<WebSocket> clients = ConcurrentHashMap.newKeySet();

    public LiveTailWebSocketServer(int port) {
        super(new InetSocketAddress(port));
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        clients.add(conn);
        System.out.println("Live Tail client connected: " + conn.getRemoteSocketAddress());
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        clients.remove(conn);
        System.out.println("Live Tail client disconnected");
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        // The browser never needs to send us anything on this
        // connection - it only listens for new logs. Nothing to do.
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        System.out.println("Live Tail WebSocket error: " + ex.getMessage());
    }

    @Override
    public void onStart() {
        System.out.println("Live Tail WebSocket server started on port " + getPort());
    }

    // Called every time a new log arrives (from LogServiceImpl.sendLog).
    // Pushes it to every currently-connected browser tab, instantly.
    public void broadcast(String logLine) {
        for (WebSocket client : clients) {
            if (client.isOpen()) {
                client.send(logLine);
            }
        }
    }
}
