export const SERVICES = [
  'billing-api',
  'auth-service',
  'checkout-worker',
  'catalog-api',
  'notification-gateway',
  'edge-proxy',
];

const MESSAGES = {
  ERROR: [
    'Timeout waiting for primary datastore connection',
    'SQLSTATE 08006: connection pool exhausted',
    'Invoice reconciliation failed after 3 attempts',
    'Upstream request failed after retry limit reached',
  ],
  WARN: [
    'Retrying downstream payment provider',
    'Consumer lag is above the 30s threshold',
    'Request queue nearing configured capacity',
    'Cache miss rate elevated for this route',
  ],
  INFO: [
    'Request completed successfully',
    'Payment authorization accepted',
    'Cache refreshed from origin',
    'Worker finished processing batch',
  ],
  DEBUG: [
    'Connection returned to pool',
    'Feature flag evaluated: checkout_v2',
    'Request context propagated to worker',
    'Health check completed',
  ],
};

const ROUTES = [
  '/v1/invoices',
  '/v1/sessions/refresh',
  '/v2/checkout/confirm',
  '/v1/catalog/search',
  '/v1/notifications/send',
  '/internal/health',
];

function makeLog(index, timestamp) {
  const level = index % 29 === 0 || index % 17 === 0
    ? 'ERROR'
    : index % 5 === 0
      ? 'WARN'
      : index % 11 === 0
        ? 'DEBUG'
        : 'INFO';
  const service = SERVICES[(index * 7 + 3) % SERVICES.length];
  const route = ROUTES[(index * 5 + 2) % ROUTES.length];
  const latencyMs = 18 + ((index * 67) % 420) + (level === 'ERROR' ? 380 : 0);
  const traceId = `tr_${(0x8c51a2 + index * 7919).toString(16).padStart(8, '0')}`;
  const statusCode = level === 'ERROR' ? [500, 502, 504][index % 3] : level === 'WARN' ? 429 : 200;

  return {
    id: `evt-${timestamp}-${index}`,
    timestamp: new Date(timestamp).toISOString(),
    level,
    service,
    message: MESSAGES[level][index % MESSAGES[level].length],
    traceId,
    requestId: `req_${(0xb139 + index * 97).toString(16)}`,
    route,
    host: `${service}-${String((index % 8) + 1).padStart(2, '0')}`,
    region: ['us-east-1', 'eu-west-1', 'ap-southeast-1'][index % 3],
    latencyMs,
    statusCode,
    payload: {
      method: index % 3 === 0 ? 'POST' : 'GET',
      route,
      statusCode,
      latencyMs,
      retryCount: level === 'ERROR' ? 3 : level === 'WARN' ? 1 : 0,
    },
    stackTrace: level === 'ERROR'
      ? `DataAccessException: upstream operation timed out\n  at ${service}.RequestHandler.handle(RequestHandler.java:184)\n  at ${service}.RequestPipeline.execute(RequestPipeline.java:92)`
      : null,
  };
}

export function createInitialLogs(count = 360, now = Date.now()) {
  return Array.from({ length: count }, (_, index) => {
    const timestamp = now - (count - index - 1) * 60_000;
    return makeLog(index, timestamp);
  }).reverse();
}

export function createIncomingLog(sequence, now = Date.now()) {
  return makeLog(360 + sequence, now);
}