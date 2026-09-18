package com.axlero.logstream;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.core.KeywordAnalyzer;
import org.apache.lucene.analysis.miscellaneous.PerFieldAnalyzerWrapper;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import java.nio.file.Path;
import java.util.*;

// This class is our "filing system". Every log that arrives gets
// saved here in a special format that can be searched instantly,
// even if there are millions of logs.
//
// Think of it like a library's card catalog: instead of reading every
// book to find one word, the catalog tells you exactly which shelf to
// look at.
public class LuceneIndexer {

    // This is the folder on disk where the search index is stored.
    private static final String INDEX_DIR = "lucene-index";

    private final Directory directory;
    private final Analyzer analyzer;

    public LuceneIndexer() throws Exception {
        this.directory = FSDirectory.open(Path.of(INDEX_DIR));

        // IMPORTANT (fixes level:ERROR and service:payment-service returning
        // nothing): "service" and "level" must be matched EXACTLY as typed
        // in when indexed - no splitting words apart, no lowercasing. Only
        // "message" should be broken into searchable words. So we use a
        // different analyzer per field instead of one analyzer for everything.
        Map<String, Analyzer> perField = new HashMap<>();
        perField.put("service", new KeywordAnalyzer());
        perField.put("level", new KeywordAnalyzer());
        this.analyzer = new PerFieldAnalyzerWrapper(new StandardAnalyzer(), perField);
    }

    // Called every time a new log comes in. This "files" it away so
    // it can be found later by a search.
    public void addLog(String serviceName, String level, String message, long timestamp) throws Exception {
        IndexWriterConfig config = new IndexWriterConfig(analyzer);
        try (IndexWriter writer = new IndexWriter(directory, config)) {
            Document doc = new Document();

            // "StringField" = stored exactly as-is, good for exact matches (like level:ERROR)
            doc.add(new StringField("service", serviceName, Field.Store.YES));
            doc.add(new StringField("level", level, Field.Store.YES));

            // "TextField" = broken into searchable words, good for free-text search
            doc.add(new TextField("message", message, Field.Store.YES));

            doc.add(new LongPoint("timestamp", timestamp));
            doc.add(new StoredField("timestamp_stored", timestamp));

            writer.addDocument(doc);
        }
    }

    // Runs a search query and returns matching logs as readable strings.
    // Example queries: "level:ERROR", "message:timeout", "service:payment-service"
    // NOTE: level and service are case-sensitive exact match (type ERROR,
    // not error - same case you sent it in).
    public List<String> search(String queryText, int maxResults) throws Exception {
        List<String> results = new ArrayList<>();

        try (DirectoryReader reader = DirectoryReader.open(directory)) {
            IndexSearcher searcher = new IndexSearcher(reader);
            QueryParser parser = new QueryParser("message", analyzer);
            Query query = parser.parse(queryText);

            TopDocs topDocs = searcher.search(query, maxResults);
            for (ScoreDoc sd : topDocs.scoreDocs) {
                Document doc = searcher.doc(sd.doc);
                String line = "[" + doc.get("level") + "] "
                        + doc.get("service") + ": "
                        + doc.get("message")
                        + " (time: " + doc.get("timestamp_stored") + ")";
                results.add(line);
            }
        }
        return results;
    }

    // ===================== Week 3: aggregations =====================

    // How many logs are indexed right now, in total.
    public int getTotalLogs() throws Exception {
        try (DirectoryReader reader = DirectoryReader.open(directory)) {
            return reader.numDocs();
        }
    }

    // Counts how many logs have each value of a given field.
    // countByField("service") -> {payment-service=5, auth-service=3, ...}
    // countByField("level")   -> {ERROR=6, INFO=5, WARN=2}
    // Sorted with the highest count first - exactly what a bar chart wants.
    public Map<String, Integer> countByField(String fieldName) throws Exception {
        Map<String, Integer> counts = new LinkedHashMap<>();

        try (DirectoryReader reader = DirectoryReader.open(directory)) {
            IndexSearcher searcher = new IndexSearcher(reader);
            for (int i = 0; i < reader.maxDoc(); i++) {
                Document doc = searcher.doc(i);
                String value = doc.get(fieldName);
                if (value != null) {
                    counts.merge(value, 1, Integer::sum);
                }
            }
        }

        List<Map.Entry<String, Integer>> entries = new ArrayList<>(counts.entrySet());
        entries.sort((a, b) -> b.getValue() - a.getValue());

        Map<String, Integer> sorted = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> e : entries) sorted.put(e.getKey(), e.getValue());
        return sorted;
    }

    // Groups logs into 1-minute buckets and counts how many fall into
    // each bucket. This is what powers a "logs over time" line chart.
    // Key = start of that minute (epoch millis), Value = count in that minute.
    public Map<Long, Integer> countByMinute() throws Exception {
        Map<Long, Integer> counts = new TreeMap<>();

        try (DirectoryReader reader = DirectoryReader.open(directory)) {
            IndexSearcher searcher = new IndexSearcher(reader);
            for (int i = 0; i < reader.maxDoc(); i++) {
                Document doc = searcher.doc(i);
                String tsStr = doc.get("timestamp_stored");
                if (tsStr != null) {
                    long ts = Long.parseLong(tsStr);
                    long minuteBucket = (ts / 60000L) * 60000L;
                    counts.merge(minuteBucket, 1, Integer::sum);
                }
            }
        }
        return counts;
    }
}
