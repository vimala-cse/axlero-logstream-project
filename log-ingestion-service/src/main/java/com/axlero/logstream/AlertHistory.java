package com.axlero.logstream;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

// Keeps track of when alerts have fired in the past, not just whether
// one is active right now. Every time the error rate crosses the
// threshold (goes from "fine" to "alert"), we record that moment.
// The list is also saved to a small file, so the history survives a
// restart of the backend.
public class AlertHistory {

    private static final String FILE_NAME = "alert-history.log";
    private static final int MAX_ENTRIES = 50;

    // Each entry is [timestamp, errorCount]. Newest first.
    private final List<long[]> entries = new ArrayList<>();
    private boolean alertWasActive = false;

    public AlertHistory() {
        load();
    }

    // Call this regularly (QueryApiServer does this every 15 seconds).
    // Only records a NEW entry the moment an alert starts firing - not
    // on every single check - so the history doesn't fill up with
    // duplicates while the same alert stays active.
    public synchronized void checkAndRecord(int errorCount, int threshold) {
        boolean activeNow = errorCount > threshold;
        if (activeNow && !alertWasActive) {
            entries.add(0, new long[]{System.currentTimeMillis(), errorCount});
            if (entries.size() > MAX_ENTRIES) {
                entries.remove(entries.size() - 1);
            }
            save();
        }
        alertWasActive = activeNow;
    }

    public synchronized String toJsonArray() {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < entries.size(); i++) {
            if (i > 0) sb.append(", ");
            long[] e = entries.get(i);
            sb.append("{\"time\": ").append(e[0])
              .append(", \"errorCount\": ").append(e[1]).append("}");
        }
        sb.append("]");
        return sb.toString();
    }

    private void load() {
        Path path = Path.of(FILE_NAME);
        if (!Files.exists(path)) return;
        try {
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            for (String line : lines) {
                String[] parts = line.split(",", -1);
                if (parts.length != 2) continue;
                try {
                    long time = Long.parseLong(parts[0]);
                    long errorCount = Long.parseLong(parts[1]);
                    if (time >= 0 && errorCount >= 0) entries.add(new long[]{time, errorCount});
                } catch (NumberFormatException ignored) {
                    // Skip malformed rows and keep loading valid history entries.
                }
            }            // File is saved oldest-first; we keep newest-first in memory.
            Collections.reverse(entries);
        } catch (IOException ignored) {
            // If the file is missing or unreadable, we just start fresh.
        }
    }

    private void save() {
        Path target = Path.of(FILE_NAME).toAbsolutePath();
        Path temporary = null;
        try {
            temporary = Files.createTempFile(target.getParent(), "alert-history-", ".tmp");
            try (BufferedWriter writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                List<long[]> oldestFirst = new ArrayList<>(entries);
                Collections.reverse(oldestFirst);
                for (long[] e : oldestFirst) {
                    writer.write(e[0] + "," + e[1]);
                    writer.newLine();
                }
            }
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            System.out.println("Warning: failed to save alert history - " + e.getMessage());
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException ignored) {
                    // A later save can replace a leftover temporary file.
                }
            }
        }
    }
}