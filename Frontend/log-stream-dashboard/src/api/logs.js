function normalizeLog(record, index) {
  const timestamp = new Date(record.timestamp).toISOString();
  const responseTime = Number(record.responseTime ?? 0);

  return {
    id: `${record.traceId || record.service || 'log'}-${timestamp}-${index}`,
    timestamp,
    level: String(record.level || 'INFO').toUpperCase(),
    service: record.service || 'unknown',
    message: record.message || '',
    traceId: record.traceId || '',
    latencyMs: Number.isFinite(responseTime) ? responseTime : 0,
    requestId: '',
    route: '',
    host: '',
    region: '',
    statusCode: null,
    payload: {
      timestamp,
      level: record.level,
      service: record.service,
      message: record.message,
      traceId: record.traceId,
      responseTime,
    },
    stackTrace: null,
  };
}

async function requestLogs(path, signal) {
  const response = await fetch(path, {
    headers: { Accept: 'application/json' },
    signal,
  });

  if (!response.ok) {
    throw new Error(`Log API returned HTTP ${response.status}`);
  }

  const records = await response.json();
  if (!Array.isArray(records)) {
    throw new Error('Log API returned an unexpected response');
  }

  return records.map(normalizeLog);
}

export function fetchLogs(signal) {
  return requestLogs('/api/logs?limit=500', signal);
}

export function searchLogs(query, signal) {
  const params = new URLSearchParams({ query, limit: '500' });
  return requestLogs(`/api/logs/search?${params}`, signal);
}