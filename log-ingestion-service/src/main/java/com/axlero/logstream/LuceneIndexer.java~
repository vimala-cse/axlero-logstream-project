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
public class LuceneIndexer {

    private static final String INDEX_DIR = "lucene-index";

    private final Directory directory;
    private final Analyzer analyzer;

    public LuceneIndexer() throws Exception {
        this.directory = FSDirectory.open(Path.of(INDEX_DIR));

        Map<String, Analyzer> perField = new HashMap<>();
        perField.put("service", new KeywordAnalyzer());
        perField.put("level", new KeywordAnalyzer());
        this.analyzer = new PerFieldAnalyzerWrapper(new StandardAnalyzer(), perField);
    }

    public void addLog(String serviceName, String level, String message, long timestamp) throws Exception {
        IndexWriterConfig config = new IndexWriterConfig(analyzer);
        try (IndexWriter writer = new IndexWriter(directory, config)) {
            Document doc = new Document();
            doc.add(new StringField("service", serviceName, Field.Store.YES));
            doc.add(new StringField("level", level, Field.Store.YES));
            doc.add(new TextField("message", message, Field.Store.YES));
            doc.add(new LongPoint("timestamp", timestamp));
            doc.add(new StoredField("timestamp_stored", timestamp));
            writer.addDocument(doc);
        }
    }

    public List<String> search(String queryText, int maxResults) throws Exception {
        List<String> results = new ArrayList<>();
        try (DirectoryReader reader = DirectoryReader.open(directory)) {
            IndexSearcher searcher = new IndexSearcher(reader);
            QueryParser parser = new QueryParser("message", analyzer);
            Query query = parser.parse(queryText);
            TopDocs topDocs = searcher.search(query, maxResults);
            for (ScoreDoc sd : topDocs.scoreDocs) {
                Document doc = searcher.doc(sd.doc);
                results.add(formatLine(doc));
            }
        }
        return results;
    }

    public int getTotalLogs() throws Exception {
        try (DirectoryReader reader = DirectoryReader.open(directory)) {
            return reader.numDocs();
        }
    }

    public Map<String, Integer> countByField(String fieldName) throws Exception {
        Map<String, Integer> counts = new LinkedHashMap<>();
        try (DirectoryReader reader = DirectoryReader.open(directory)) {
            IndexSearcher searcher = new IndexSearcher(reader);
            for (int i = 0; i < reader.maxDoc(); i++) {
                Document doc = searcher.doc(i);
                String value = doc.get(fieldName);
                if (value != null) counts.merge(value, 1, Integer::sum);
            }
        }
        List<Map.Entry<String, Integer>> entries = new ArrayList<>(counts.entrySet());
        entries.sort((a, b) -> b.getValue() - a.getValue());
        Map<String, Integer> sorted = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> e : entries) sorted.put(e.getKey(), e.getValue());
        return sorted;
    }

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

    public int countRecentErrors(long withinMillis) throws Exception {
        long cutoff = System.currentTimeMillis() - withinMillis;
        int count = 0;
        try (DirectoryReader reader = DirectoryReader.open(directory)) {
            IndexSearcher searcher = new IndexSearcher(reader);
            for (int i = 0; i < reader.maxDoc(); i++) {
                Document doc = searcher.doc(i);
                String level = doc.get("level");
                String tsStr = doc.get("timestamp_stored");
                if ("ERROR".equals(level) && tsStr != null) {
                    long ts = Long.parseLong(tsStr);
                    if (ts >= cutoff) count++;
                }
            }
        }
        return count;
    }

    // ===================== NEW: for Live Tail =====================

    // Returns every log with a timestamp strictly after `sinceMillis`,
    // oldest first. QueryApiServer calls this every couple of seconds
    // on the /api/stream endpoint, so the dashboard's Live Tail page
    // can show new logs as they arrive, without the person refreshing.
    //
    // We fetch matching docs with a range query, then sort them by
    // timestamp in plain Java - this avoids needing extra index fields
    // just for sorting, which would require re-indexing old data.
    public List<String> getLogsSince(long sinceMillis) throws Exception {
        List<Document> matched = new ArrayList<>();
        try (DirectoryReader reader = DirectoryReader.open(directory)) {
            IndexSearcher searcher = new IndexSearcher(reader);
            Query query = LongPoint.newRangeQuery("timestamp", sinceMillis + 1, Long.MAX_VALUE);
            TopDocs topDocs = searcher.search(query, 200);
            for (ScoreDoc sd : topDocs.scoreDocs) {
                matched.add(searcher.doc(sd.doc));
            }
        }
        matched.sort(Comparator.comparingLong(d -> Long.parseLong(d.get("timestamp_stored"))));

        List<String> results = new ArrayList<>();
        for (Document doc : matched) {
            results.add(formatLine(doc));
        }
        return results;
    }

    private String formatLine(Document doc) {
        return "[" + doc.get("level") + "] "
                + doc.get("service") + ": "
                + doc.get("message")
                + " (time: " + doc.get("timestamp_stored") + ")";
    }
}
