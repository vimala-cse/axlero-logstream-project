package com.axlero.logstream;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.store.FSDirectory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

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
    private final StandardAnalyzer analyzer;

    public LuceneIndexer() throws Exception {
        this.directory = FSDirectory.open(Path.of(INDEX_DIR));
        this.analyzer = new StandardAnalyzer();
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
}
