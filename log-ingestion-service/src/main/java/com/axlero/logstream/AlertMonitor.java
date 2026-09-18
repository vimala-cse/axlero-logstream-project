package com.axlero.logstream;

// Run this AFTER logs have been sent. It checks how many ERROR logs
// happened recently, and prints an alert if there are too many.
// This is the "Week 4: alerting" feature.
public class AlertMonitor {

    private static final int ERROR_THRESHOLD = 3;    // alert if MORE than this many ERRORs...
    private static final int WINDOW_MINUTES = 5;      // ...within this many minutes

    public static void main(String[] args) throws Exception {
        LuceneIndexer indexer = new LuceneIndexer();

        long windowMillis = WINDOW_MINUTES * 60_000L;
        int recentErrors = indexer.countRecentErrors(windowMillis);

        System.out.println("Checked last " + WINDOW_MINUTES + " minute(s): "
                + recentErrors + " ERROR log(s) found.");

        if (recentErrors > ERROR_THRESHOLD) {
            System.out.println();
            System.out.println("*** ALERT: " + recentErrors + " ERROR logs in the last "
                    + WINDOW_MINUTES + " minutes (threshold: " + ERROR_THRESHOLD + ") ***");
            System.out.println("*** Something may be wrong - check the affected service. ***");
        } else {
            System.out.println("No alert - error rate is within the normal range.");
        }
    }
}
