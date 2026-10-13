# LogStream — Distributed Log Analytics & Alerting Platform

A self-hosted log analytics tool that ingests logs from multiple
services in real time, indexes them for instant search, visualizes
trends on a live dashboard, and flags unusual error activity — built
as part of the Axlero Solutions internship (Project 2: Observability
& Big Data).

---

## 1. The Problem

Modern systems are made up of many small services, and each one
produces its own stream of logs. Storing and searching through
terabytes of these logs using a standard relational database (like
PostgreSQL) doesn't scale — a single keyword search can mean scanning
every row. Organizations need something that can take in millions of
log lines per minute, index them instantly, and surface problems the
moment they happen — the same idea behind tools like the ELK stack
(Elasticsearch, Logstash, Kibana), built here from scratch.

## 2. Our Solution — How It Works End to End

1. **A service produces a log** (e.g. `payment-service` logs an
   `ERROR: Payment gateway timeout`).
2. That log is sent to our **gRPC ingestion server**
   (`LogIngestionServer`), which is built for fast, high-volume,
   binary log ingestion.
3. The log is immediately:
   - **Indexed into Apache Lucene** (the same search engine that
     powers Elasticsearch) so it becomes instantly searchable, and
   - **Pushed live to any open dashboard** over a WebSocket, for the
     Live Tail feature — this path skips the index entirely, so it's
     never delayed by search traffic.
4. An engineer opens the **React dashboard**, which talks to the
   backend through a REST bridge (`QueryApiServer`). From here they
   can:
   - Search logs with a simple query language, e.g.
     `level:ERROR AND service:payment-service`
   - View stat cards and charts — total logs, breakdown by service,
     breakdown by level, volume over time
   - Watch a background process check the recent error rate; if it
     crosses a fixed threshold, an alert appears on the dashboard and
     is saved to a history log
   - **Define their own alert rules** — any service (or all of them),
     any log level, any threshold and time window — instead of relying
     on just the one fixed rule
   - Get **notified outside the dashboard**: the moment a custom rule
     fires, a message is posted to a Slack or Discord webhook, so the
     alert isn't missed just because nobody is looking at the screen
   - Watch new logs stream onto the **Live Tail** page the instant
     they arrive, with no refresh needed

That's the whole loop: a log is born in a microservice, and within
moments it's searchable, visualized, checked against every alert rule
— and, if it crosses one, someone is notified.

## 3. Key Features

| Feature | Description |
|---|---|
| High-throughput ingestion | gRPC server built for fast, binary log ingestion from multiple services |
| Full-text & field search | Apache Lucene index; query by level, service, or free-text message |
| Live analytics | Total logs, per-service and per-level breakdowns, volume-over-time chart |
| Built-in alert | Background check flags when the global error rate crosses a fixed threshold |
| **User-defined alert rules** | Create any number of custom rules — per service, per log level, with your own threshold and time window |
| **Real webhook alerts** | The moment a custom rule fires, a message is sent to a Slack/Discord webhook — not just shown in the UI |
| Alert history | Past alerts (both built-in and rule-based) are saved and viewable, not just the current moment |
| Live Tail | Logs stream to the dashboard in real time via WebSocket, bypassing the search index |
| REST API + Postman collection | Every piece of data the dashboard shows is also available as a documented REST endpoint |

## 4. Architecture

```
 Microservices                      LogStream Backend
 (log producers)
      |
      | gRPC :9090           ┌─────────────────────────┐
      ├──SendLog() ─────────▶│   LogIngestionServer      │
      |                      │                           │
      |                      │  1. writes to Lucene ─────┼──────▶ ┌────────────────┐
      |                      │  2. broadcasts to  ───────┼───┐    │ Lucene index    │
      |                      │     Live Tail              │   │    │ (on disk)       │
      |                      └─────────────────────────┘   │    └────────┬────────┘
      |                                                     │             │ reads
      |                                                     ▼             ▼
      |                                        ┌──────────────────┐ ┌───────────────────┐
      |                                        │ LiveTailWebSocket │ │   QueryApiServer    │
      |                                        │ Server (:8081)    │ │   REST (:8080)      │
      |                                        └────────┬─────────┘ └──────────┬──────────┘
      |                                                  │ push logs           │ JSON
      |                                                  ▼                     ▼
      |                                        ┌───────────────────────────────────────┐
      |                                        │       React Dashboard (Vite, :5173)      │
      |                                        │  Overview · Logs · Analytics · Alerts    │
      |                                        │       · Services · Live Tail             │
      |                                        └───────────────────────────────────────┘
      |                                                                      ▲
      |                                                                      │ reads alert rules
      |                                        ┌───────────────────┐         │ via REST
      |                                        │ AlertRuleStore /   │◀───────┘
      |                                        │ RuleAlertHistory   │
      |                                        │ (checked every 15s │
      |                                        │  against Lucene)   │
      |                                        └─────────┬─────────┘
      |                                                   │ on "fine → alert"
      |                                                   ▼
      |                                        ┌───────────────────┐
      |                                        │  WebhookNotifier    │────────▶  Slack / Discord
      |                                        └───────────────────┘
```

**Why does Live Tail bypass the search index?** Browsers can't speak
gRPC, so everything the dashboard needs normally goes through
`QueryApiServer`, which reads from Lucene. But Live Tail needs to feel
instant and must never be slowed down by search traffic — so instead
of polling the index, `LogIngestionServer` pushes each log straight to
connected browsers the moment it's received, over a separate
WebSocket connection. Search and Live Tail never compete with each
other.

**How alert rules differ from the built-in alert:** the built-in alert
(`AlertHistory`) is one fixed, global rule and can't be changed without
editing code. User-defined rules (`AlertRuleStore`) are created from
the dashboard itself, each scoped to a service and level with its own
threshold and window, and are checked by a separate background thread
every 15 seconds. The moment a rule crosses from "fine" to "alert", it's
recorded in `RuleAlertHistory` **and** `WebhookNotifier` fires a real
notification — independent of whether the dashboard is even open.

## 5. Tech Stack

| Layer | Technology |
|---|---|
| Log ingestion | Java, gRPC, Protocol Buffers |
| Search / indexing | Apache Lucene 9.10 |
| REST bridge | Java (`com.sun.net.httpserver`) — no extra framework |
| Live Tail | Java-WebSocket |
| Webhook notifications | `java.net.http.HttpClient` (built into Java 11+) — no extra dependency |
| Frontend | React 18, Vite |
| Build tools | Maven (backend), npm (frontend) |
| API testing | Postman |
| Version control | Git / GitHub |

## 6. Project Structure

```
axlero-logstream-project/
├── log-ingestion-service/            Backend (Java)
│   ├── src/main/java/com/axlero/logstream/
│   │   ├── LogIngestionServer.java      gRPC server; also starts Live Tail
│   │   ├── LiveTailWebSocketServer.java WebSocket push server (:8081)
│   │   ├── LuceneIndexer.java           Indexing, search, aggregations, rule checks
│   │   ├── QueryApiServer.java          REST bridge (:8080) + alert-rules endpoints
│   │   ├── AlertHistory.java            Tracks and saves past built-in alerts
│   │   ├── AlertRule.java               One user-defined alert rule (service/level/threshold/window)
│   │   ├── AlertRuleStore.java          Saves, loads and manages all user-defined rules
│   │   ├── RuleAlertHistory.java        Tracks and saves past rule-based alerts
│   │   ├── WebhookNotifier.java         Sends a Slack/Discord webhook when a rule fires
│   │   ├── AlertMonitor.java            Console alert check
│   │   ├── AggregationDemo.java         Console aggregation demo
│   │   ├── BulkTestClient.java          Sends sample logs for testing
│   │   └── SearchDemo.java              Console search demo
│   ├── src/main/proto/log_service.proto
│   └── pom.xml
│
├── logstream-dashboard/              Frontend (React)
│   ├── src/
│   │   ├── App.jsx                      All pages and data-fetching logic, incl. alert rules UI
│   │   ├── App.css                      Styling, responsive layout
│   │   └── main.jsx
│   ├── index.html
│   └── package.json
│
└── postman/                          Postman collection for the REST API
    └── LogStream.postman_collection.json
```

## 7. Running It — Start to Finish

### Prerequisites

- Java 17+ (21 also works)
- Maven (handled automatically by IntelliJ)
- Node.js and npm

### Step 1 — Start the backend

Open `log-ingestion-service` as a Maven project and run these three
files **in this order**, leaving each one running:

1. `LogIngestionServer.java` — starts the gRPC server (port 9090) and
   the Live Tail WebSocket server (port 8081)
2. `BulkTestClient.java` — sends 15 sample logs across 6 services and
   3 levels (run once; run again any time for more sample data)
3. `QueryApiServer.java` — starts the REST API (port 8080) —
   **keep this running**

Confirm it's working: open `http://localhost:8080/api/aggregations`
in a browser — you should see JSON data.

**Optional — enable real webhook alerts:** before running
`QueryApiServer`, set an environment variable
`ALERT_WEBHOOK_URL` to a Slack or Discord incoming-webhook URL (Run →
Edit Configurations → Environment Variables, in IntelliJ). If it's not
set, everything still works — rule alerts just won't be posted
anywhere outside the dashboard.

### Step 2 — Start the frontend

```
cd logstream-dashboard
npm install
npm run dev
```

Open the link shown (usually `http://localhost:5173`). If the backend
isn't running, you'll see a clear "could not reach the backend"
message instead of a blank page.

### Step 3 — Explore

- **Overview** — stat cards, volume chart, recent logs
- **Logs** — full search
- **Analytics** — bigger charts, including the level breakdown
- **Alerts** — built-in alert status + history, plus your own alert
  rules (create, remove) and their own history
- **Services** — log counts per service
- **Live Tail** — watch logs arrive in real time (run `BulkTestClient`
  again while this tab is open to see it in action)

## 8. API Reference

| Method | Path | Purpose |
|---|---|---|
| GET | `/api/aggregations` | Total logs, counts by service, counts by level |
| GET | `/api/search?q=<query>` | Search logs — try `level:ERROR`, `message:timeout`, `service:payment-service` |
| GET | `/api/timeline` | Logs per minute, for the volume chart |
| GET | `/api/alerts` | Whether the built-in global alert is active right now |
| GET | `/api/alerts/history` | Past built-in alerts that have fired |
| GET | `/api/alert-rules` | List every user-defined alert rule |
| POST | `/api/alert-rules` | Create a rule — body: `{"service", "level", "threshold", "windowMinutes"}` (`service` optional, defaults to any) |
| DELETE | `/api/alert-rules?id=<id>` | Delete a rule |
| GET | `/api/alert-rules/history` | Past alerts fired by user-defined rules |
| WS | `ws://localhost:8081` | Live Tail — pushes each new log the instant it's received |

A ready-made **Postman collection** for the REST endpoints is at
`postman/LogStream.postman_collection.json`. Import it into Postman
and every request is pre-filled — no typing URLs by hand. It uses a
`baseUrl` variable (default `http://localhost:8080`) so it's easy to
re-point at a deployed backend later.

## 9. Week-by-Week Development Plan

### Backend

| Week | Focus |
|---|---|
| 1 | gRPC server to receive logs; protobuf schema for a log message |
| 2 | Apache Lucene integration — parsing, analyzing, and indexing logs |
| 3 | Aggregations — counts by service/level, and volume-per-minute for the charts |
| 4 | Alerting, REST bridge for the frontend, Live Tail (WebSocket), alert history |
| 5 | User-defined alert rules + real webhook notifications (Slack/Discord) |

### Frontend

| Week | Focus |
|---|---|
| 1 | Project setup; basic search bar and log table (sample data) |
| 2 | Search bar connected to the real backend |
| 3 | Stat cards and charts connected to real data |
| 4 | Alerts page, Services page, Live Tail page, mobile-responsive layout, visual polish |
| 5 | Alert rules UI — create/list/delete rules, rule alert history |

## 10. Team

| Name | Role |
|---|---|
| **Vimala** (Team Lead) | Backend (gRPC, Lucene, aggregations, alerting, alert rules, webhook notifications, Live Tail, REST bridge), Frontend dashboard, Testing |
| Doni Navaneeth | Backend |
| Shoyab Ahamed | Frontend |

## 11. Deployment

Planned on [Render](https://render.com) — backend as a Docker web
service, frontend as a static site. Docker setup is ready; this
section will be updated with live URLs once deployed.

## 12. Future Improvements

- Persisting logs somewhere durable across restarts (currently the
  Lucene index and alert files live on local disk)
- Role-based access, if this were ever used by more than one team
