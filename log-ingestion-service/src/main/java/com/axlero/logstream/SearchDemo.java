package com.axlero.logstream;

import java.util.List;
import java.util.Scanner;

// Run this AFTER you've sent some logs through TestClient (or several
// times with different messages). This lets you type a search query
// and instantly see matching logs - this is the core "Week 2" proof
// that search works.
//
// Example queries to try:
//   message:timeout
//   level:ERROR
//   service:payment-service
public class SearchDemo {
    public static void main(String[] args) throws Exception {
        LuceneIndexer indexer = new LuceneIndexer();
        Scanner scanner = new Scanner(System.in);

        System.out.println("LogStream Search - type a query (or 'exit' to quit)");
        System.out.println("Examples: message:timeout | level:ERROR | service:payment-service");

        while (true) {
            System.out.print("\nsearch> ");
            String query = scanner.nextLine();
            if (query.equalsIgnoreCase("exit")) break;

            List<String> results = indexer.search(query, 20);
            if (results.isEmpty()) {
                System.out.println("No matching logs found.");
            } else {
                System.out.println("Found " + results.size() + " result(s):");
                for (String r : results) {
                    System.out.println("  " + r);
                }
            }
        }
        scanner.close();
    }
}
