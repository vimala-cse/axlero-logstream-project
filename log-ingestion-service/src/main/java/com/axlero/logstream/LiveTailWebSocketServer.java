package com.axlero.logstream;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

// A separate, dedicated WebSocket server just for Live Tail - this is
// NOT the same as QueryApiServer (REST, port 8080) or LogIngestionServer
// (gRPC, port 9090). It runs on its own port (8081) and does one thing:
// keeps a list of connected browsers, and pushes new log lines to all
// of them the instant a log arrives - this is the "bypassing the search
// index" part the project guide asks for. Logs reach the browser without
// ever going through a Lucene query.
public class LiveTailWebSocketServer extends WebSocketServer {

    // A thread-safe set: connections can open or close on any thread at
    // any time, while we're also looping over this same set to broadcast.
    // A plain HashSet would risk a ConcurrentModificationException here.
    private final Set<WebSocket> connections =
            Collections.newSetFromMap(new ConcurrentHashMap<>());

    public LiveTailWebSocketServer(int port) {
        super(new InetSocketAddress(port));
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        connections.add(conn);
        System.out.println("Live Tail client connected: " + conn.getRemoteSocketAddress());
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        connections.remove(conn);
        System.out.println("Live Tail client disconnected");
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        // The browser never sends us anything on this channel - it's
        // one-way, server to browser - so there's nothing to handle here.
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        // A broken connection counts as closed; drop it so we stop
        // trying to send to it.
        if (conn != null) {
            connections.remove(conn);
        }
    }

    @Override
    public void onStart() {
        System.out.println("Live Tail WebSocket server running on port " + getPort());
    }

    // Called by LogIngestionServer every time a new log is received.
    // Sends it to every browser currently watching Live Tail. If nobody
    // is connected, this is a harmless no-op.
    public void broadcast(String logLine) {
        for (WebSocket conn : connections) {
            if (conn.isOpen()) {
                conn.send(logLine);
            }
        }
    }
}
