# LogStream Postman Collection

Ready-made requests for all 5 REST endpoints, so anyone on the team
(or a reviewer) can test the backend without typing URLs by hand.

## What's included

- Get aggregations (Overview page)
- Search logs
- Get timeline (log volume)
- Get current alert status
- Get alert history

Live Tail is NOT included - it's a WebSocket connection
(`ws://localhost:8081`), not a regular REST request, so it works
differently in Postman. The other 5 endpoints are plain HTTP GET
requests, which is what this collection is for.

## How to use it

1. Open Postman (the app, or the web version)
2. Click **Import** (top left)
3. Select `LogStream.postman_collection.json` from this folder
4. You'll see a new collection called "LogStream API" with all 5
   requests ready to go
5. Make sure the backend is running first (`LogIngestionServer` +
   `QueryApiServer`), then click any request and hit **Send**

## Changing the backend address

The collection uses a variable called `baseUrl`, set to
`http://localhost:8080` by default. If your backend runs somewhere
else (like after deploying to Render), click the collection name ->
**Variables** tab -> change `baseUrl` there, instead of editing every
request one by one.
