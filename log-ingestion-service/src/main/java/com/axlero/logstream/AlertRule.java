package com.axlero.logstream;

// One user-defined alert rule: "if more than `threshold` logs at
// `level` happen for `service` within `windowMinutes`, fire an alert."
//
// `service` is "*" to mean "any service" - we use that instead of
// null/empty so the saved file format stays simple (one line,
// comma-separated, no blank fields to worry about).
public class AlertRule {

    public final String id;
    public final String service;      // "*" = any service
    public final String level;        // e.g. "ERROR", "WARN"
    public final int threshold;
    public final int windowMinutes;

    // In-memory only: was this rule firing the last time we checked it?
    // Used so we only record ONE history entry per "fine -> alert"
    // transition, not on every 15-second check while it stays active.
    public transient boolean wasActive = false;

    public AlertRule(String id, String service, String level, int threshold, int windowMinutes) {
        this.id = id;
        this.service = (service == null || service.isBlank()) ? "*" : service;
        this.level = (level == null || level.isBlank()) ? "ERROR" : level;
        this.threshold = threshold;
        this.windowMinutes = windowMinutes;
    }

    public String describe() {
        String who = "*".equals(service) ? "any service" : service;
        return level + " > " + threshold + " in " + windowMinutes + " min (" + who + ")";
    }

    public String toCsvLine() {
        return id + "," + service + "," + level + "," + threshold + "," + windowMinutes;
    }

    public static AlertRule fromCsvLine(String line) {
        String[] p = line.split(",");
        if (p.length != 5) return null;
        return new AlertRule(p[0], p[1], p[2], Integer.parseInt(p[3]), Integer.parseInt(p[4]));
    }

    public String toJson() {
        return "{\"id\": \"" + id + "\""
                + ", \"service\": \"" + service + "\""
                + ", \"level\": \"" + level + "\""
                + ", \"threshold\": " + threshold
                + ", \"windowMinutes\": " + windowMinutes
                + ", \"description\": \"" + describe() + "\"}";
    }
}
