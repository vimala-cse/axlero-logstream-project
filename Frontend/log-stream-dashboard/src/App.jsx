import { useEffect, useMemo, useRef, useState } from 'react';
import {
  Activity,
  ArrowDownRight,
  ArrowUpRight,
  Bell,
  ChevronDown,
  ChevronLeft,
  ChevronRight,
  Clock3,
  Database,
  Download,
  Radio,
  RefreshCw,
  Search,
  Server,
  ShieldAlert,
  Timer,
  X,
  Zap,
} from 'lucide-react';
import * as echarts from 'echarts/core';
import { LineChart } from 'echarts/charts';
import { GridComponent, LegendComponent, TooltipComponent } from 'echarts/components';
import { CanvasRenderer } from 'echarts/renderers';
import ReactEChartsCore from 'echarts-for-react/lib/core';
import { fetchLogs, searchLogs } from './api/logs.js';

const RANGE_MINUTES = { '15m': 15, '1h': 60, '6h': 360 };
const PAGE_SIZE = 12;

echarts.use([LineChart, GridComponent, LegendComponent, TooltipComponent, CanvasRenderer]);

function formatNumber(value) {
  return new Intl.NumberFormat('en-US').format(value);
}

function formatTimestamp(timestamp, includeSeconds = false) {
  return new Intl.DateTimeFormat('en-US', {
    hour: '2-digit',
    minute: '2-digit',
    ...(includeSeconds ? { second: '2-digit' } : {}),
    hour12: false,
    timeZone: 'UTC',
  }).format(new Date(timestamp));
}

function formatRelativeTime(timestamp, now) {
  const seconds = Math.max(0, Math.floor((now - Date.parse(timestamp)) / 1000));
  return seconds < 60 ? `${seconds}s ago` : `${Math.floor(seconds / 60)}m ago`;
}

function percentile95(logs) {
  if (!logs.length) return 0;
  const sorted = logs.map((log) => log.latencyMs).sort((a, b) => a - b);
  return sorted[Math.ceil(sorted.length * 0.95) - 1];
}

function getRangeBounds(range, logs, now) {
  if (range === 'all') {
    const timestamps = logs.map((log) => Date.parse(log.timestamp)).filter(Number.isFinite);
    return timestamps.length
      ? { start: Math.min(...timestamps), end: Math.max(...timestamps) }
      : { start: now - 60_000, end: now };
  }

  const end = now;
  return { start: end - RANGE_MINUTES[range] * 60_000, end };
}

function formatChartLabel(timestamp, includeDate) {
  if (!includeDate) return formatTimestamp(timestamp);
  return new Intl.DateTimeFormat('en-US', {
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
    timeZone: 'UTC',
  }).format(new Date(timestamp));
}

function percentChange(current, previous) {
  if (!previous) return current ? null : 0;
  return ((current - previous) / previous) * 100;
}

function formatChange(change) {
  if (change === null) return 'New';
  const sign = change > 0 ? '+' : change < 0 ? '-' : '';
  return `${sign}${Math.abs(change).toFixed(1)}%`;
}

function buildChartOption(logs, range, now) {
  const binCount = 12;
  const { start, end } = getRangeBounds(range, logs, now);
  const binMs = Math.max(1, (end - start + 1) / binCount);
  const bins = Array.from({ length: binCount }, () => ({ ERROR: 0, WARN: 0, INFO: 0 }));
  const labels = bins.map((_, index) => formatChartLabel(start + (index + 1) * binMs, range === 'all'));

  logs.forEach((log) => {
    const index = Math.min(binCount - 1, Math.max(0, Math.floor((Date.parse(log.timestamp) - start) / binMs)));
    if (log.level === 'ERROR') bins[index].ERROR += 1;
    else if (log.level === 'WARN') bins[index].WARN += 1;
    else bins[index].INFO += 1;
  });

  return {
    color: ['#d7654d', '#dda842', '#5b9b7b'],
    animationDuration: 450,
    grid: { left: 8, right: 12, top: 34, bottom: 8, containLabel: true },
    legend: {
      top: 0,
      right: 4,
      itemWidth: 8,
      itemHeight: 8,
      icon: 'circle',
      textStyle: { color: '#68736d', fontFamily: 'DM Sans, sans-serif', fontSize: 11 },
    },
    tooltip: {
      trigger: 'axis',
      backgroundColor: '#17251f',
      borderWidth: 0,
      textStyle: { color: '#f7f8f5', fontFamily: 'DM Sans, sans-serif', fontSize: 12 },
      axisPointer: { type: 'line', lineStyle: { color: '#aeb8b1', type: 'dashed' } },
    },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: labels,
      axisTick: { show: false },
      axisLine: { lineStyle: { color: '#e4e8e4' } },
      axisLabel: { color: '#87918b', fontSize: 10, hideOverlap: true, margin: 12 },
    },
    yAxis: {
      type: 'value',
      minInterval: 1,
      axisLabel: { color: '#87918b', fontSize: 10, margin: 10 },
      axisLine: { show: false },
      axisTick: { show: false },
      splitLine: { lineStyle: { color: '#edf0ed', type: 'dashed' } },
    },
    series: [
      { name: 'Errors', type: 'line', smooth: 0.35, stack: 'volume', showSymbol: false, lineStyle: { width: 2 }, areaStyle: { opacity: 0.48 }, data: bins.map((bin) => bin.ERROR) },
      { name: 'Warnings', type: 'line', smooth: 0.35, stack: 'volume', showSymbol: false, lineStyle: { width: 2 }, areaStyle: { opacity: 0.42 }, data: bins.map((bin) => bin.WARN) },
      { name: 'Info + debug', type: 'line', smooth: 0.35, stack: 'volume', showSymbol: false, lineStyle: { width: 2 }, areaStyle: { opacity: 0.3 }, data: bins.map((bin) => bin.INFO) },
    ],
  };
}

function downloadCsv(logs) {
  const columns = ['timestamp', 'level', 'service', 'message', 'traceId', 'latencyMs'];
  const escapeCell = (value) => `"${String(value ?? '').replaceAll('"', '""')}"`;
  const content = [columns.join(','), ...logs.map((log) => columns.map((column) => escapeCell(log[column])).join(','))].join('\r\n');
  const url = URL.createObjectURL(new Blob([content], { type: 'text/csv;charset=utf-8' }));
  const link = document.createElement('a');
  link.href = url;
  link.download = 'logstream-events.csv';
  link.click();
  URL.revokeObjectURL(url);
}

function MetricCard({ label, value, detail, icon: Icon, tone, trend, trendDirection = 'up', trendTone = 'positive' }) {
  const TrendIcon = trendDirection === 'down' ? ArrowDownRight : ArrowUpRight;
  return (
    <article className={`metric-card metric-card--${tone}`}>
      <div className="metric-topline">
        <span className="metric-label">{label}</span>
        <span className="metric-icon"><Icon size={16} strokeWidth={1.8} /></span>
      </div>
      <div className="metric-value">{value}</div>
      <div className="metric-foot">
        <span className={`metric-trend metric-trend--${trendTone}`}><TrendIcon size={13} />{trend}</span>
        <span>{detail}</span>
      </div>
    </article>
  );
}

function App() {
  const searchInputRef = useRef(null);
  const [logs, setLogs] = useState([]);
  const [searchResults, setSearchResults] = useState(null);
  const [backendState, setBackendState] = useState('connecting');
  const [backendError, setBackendError] = useState('');
  const [searchLoading, setSearchLoading] = useState(false);
  const [isLoading, setIsLoading] = useState(true);
  const [refreshVersion, setRefreshVersion] = useState(0);
  const [range, setRange] = useState('all');
  const [service, setService] = useState('all');
  const [level, setLevel] = useState('all');
  const [query, setQuery] = useState('');
  const [liveTail, setLiveTail] = useState(false);
  const [selectedLog, setSelectedLog] = useState(null);
  const [oldestFirst, setOldestFirst] = useState(false);
  const [requestedPage, setRequestedPage] = useState(0);
  const [now, setNow] = useState(() => Date.now());

  useEffect(() => {
    let active = true;
    const controller = new AbortController();
    const refreshLogs = async () => {
      try {
        const latestLogs = await fetchLogs(controller.signal);
        if (!active) return;
        setLogs(latestLogs);
        setNow(Date.now());
        setBackendState('connected');
        setBackendError('');
      } catch (error) {
        if (!active || error.name === 'AbortError') return;
        setBackendState('offline');
        setBackendError(error.message);
      } finally {
        if (active) setIsLoading(false);
      }
    };

    refreshLogs();
    const interval = window.setInterval(refreshLogs, liveTail ? 3200 : 30_000);
    return () => {
      active = false;
      controller.abort();
      window.clearInterval(interval);
    };
  }, [liveTail, refreshVersion]);

  useEffect(() => {
    if (!selectedLog) return undefined;
    const closeOnEscape = (event) => {
      if (event.key === 'Escape') setSelectedLog(null);
    };
    window.addEventListener('keydown', closeOnEscape);
    return () => window.removeEventListener('keydown', closeOnEscape);
  }, [selectedLog]);

  useEffect(() => {
    const focusSearch = (event) => {
      const target = event.target;
      const isEditing = target instanceof HTMLElement
        && (target.isContentEditable || ['INPUT', 'TEXTAREA', 'SELECT'].includes(target.tagName));
      if (event.key !== '/' || isEditing) return;
      event.preventDefault();
      searchInputRef.current?.focus();
    };
    window.addEventListener('keydown', focusSearch);
    return () => window.removeEventListener('keydown', focusSearch);
  }, []);

  const apiSearchQuery = [
    level === 'all' ? '' : `level:${level}`,
    service === 'all' ? '' : `service:${service}`,
    query.trim(),
  ].filter(Boolean).join(' AND ');

  useEffect(() => {
    if (!apiSearchQuery) {
      setSearchResults(null);
      setSearchLoading(false);
      return undefined;
    }

    setSearchResults(null);
    const controller = new AbortController();
    const timeout = window.setTimeout(async () => {
      setSearchLoading(true);
      try {
        const results = await searchLogs(apiSearchQuery, controller.signal);
        setSearchResults(results);
        setBackendState('connected');
        setBackendError('');
      } catch (error) {
        if (error.name !== 'AbortError') {
          setBackendState('offline');
          setBackendError(error.message);
        }
      } finally {
        if (!controller.signal.aborted) setSearchLoading(false);
      }
    }, 250);

    return () => {
      window.clearTimeout(timeout);
      controller.abort();
    };
  }, [apiSearchQuery]);

  const rangeLogs = useMemo(() => {
    if (range === 'all') return logs;
    const cutoff = now - RANGE_MINUTES[range] * 60_000;
    return logs.filter((log) => Date.parse(log.timestamp) >= cutoff && Date.parse(log.timestamp) <= now);
  }, [logs, now, range]);

  const previousRangeLogs = useMemo(() => {
    if (range === 'all') return [];
    const cutoff = now - RANGE_MINUTES[range] * 60_000;
    const previousCutoff = cutoff - RANGE_MINUTES[range] * 60_000;
    return logs.filter((log) => Date.parse(log.timestamp) >= previousCutoff && Date.parse(log.timestamp) < cutoff);
  }, [logs, now, range]);

  const matchingLogs = useMemo(() => {
    const sourceLogs = apiSearchQuery ? searchResults ?? [] : logs;
    return sourceLogs
      .filter((log) => range === 'all' || (Date.parse(log.timestamp) >= now - RANGE_MINUTES[range] * 60_000 && Date.parse(log.timestamp) <= now))
      .filter((log) => service === 'all' || log.service === service)
      .filter((log) => level === 'all' || log.level === level)
      .sort((a, b) => oldestFirst ? Date.parse(a.timestamp) - Date.parse(b.timestamp) : Date.parse(b.timestamp) - Date.parse(a.timestamp));
  }, [apiSearchQuery, level, logs, now, oldestFirst, range, searchResults, service]);

  const errorCount = rangeLogs.filter((log) => log.level === 'ERROR').length;
  const errorRate = rangeLogs.length ? (errorCount / rangeLogs.length) * 100 : 0;
  const serviceOptions = [...new Set(logs.map((log) => log.service).filter(Boolean))].sort();
  const activeServices = new Set(rangeLogs.map((log) => log.service)).size;
  const p95 = percentile95(rangeLogs);
  const previousErrorCount = previousRangeLogs.filter((log) => log.level === 'ERROR').length;
  const previousErrorRate = previousRangeLogs.length ? (previousErrorCount / previousRangeLogs.length) * 100 : 0;
  const previousP95 = percentile95(previousRangeLogs);
  const previousServices = new Set(previousRangeLogs.map((log) => log.service)).size;
  const eventChange = percentChange(rangeLogs.length, previousRangeLogs.length);
  const errorRateChange = errorRate - previousErrorRate;
  const latencyChange = percentChange(p95, previousP95);
  const serviceChange = activeServices - previousServices;
  const rangeDescription = range === 'all' ? 'latest loaded logs' : `the last ${range}`;
  const trendDescription = range === 'all' ? 'All time' : null;
  const pageCount = Math.max(1, Math.ceil(matchingLogs.length / PAGE_SIZE));
  const currentPage = Math.min(requestedPage, pageCount - 1);
  const pageLogs = matchingLogs.slice(currentPage * PAGE_SIZE, (currentPage + 1) * PAGE_SIZE);
  const chartOption = useMemo(() => buildChartOption(rangeLogs, range, now), [now, range, rangeLogs]);
  const recentErrors = rangeLogs.filter((log) => log.level === 'ERROR').slice(0, 4);

  function clearFilters() {
    setService('all');
    setLevel('all');
    setQuery('');
    setRequestedPage(0);
  }

  function updateFilter(setter, value) {
    setter(value);
    setRequestedPage(0);
  }

  const chartBuckets = chartOption.series[0].data.map((_, index) => chartOption.series.reduce((total, series) => total + series.data[index], 0));
  const peakVolume = Math.max(0, ...chartBuckets);

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <a className="brand" href="#overview" aria-label="Logstream overview">
          <span className="brand-mark"><Activity size={19} strokeWidth={2.2} /></span>
          <span className="brand-name">logstream<span>.</span></span>
        </a>

        <div className="workspace-picker">
          <span className="workspace-avatar">N</span>
          <span className="workspace-copy"><strong>Northstar</strong><small>Engineering</small></span>
          <ChevronDown size={14} />
        </div>

        <div className="nav-caption">OBSERVABILITY</div>
        <nav className="side-nav" aria-label="Main navigation">
          <a className="nav-link nav-link--active" href="#overview"><Activity size={17} /><span>Overview</span><span className="nav-indicator" /></a>
          <a className="nav-link" href="#log-explorer"><Database size={17} /><span>Log explorer</span></a>
          <a className="nav-link" href="#alert-activity"><Bell size={17} /><span>Alert activity</span><span className="nav-count">{errorCount}</span></a>
        </nav>

        <div className="sidebar-bottom">
          <div className="sidebar-status"><span className="status-dot" /><span>Demo environment</span></div>
          <div className="sidebar-user"><span className="user-avatar">AM</span><span className="user-copy"><strong>Alex Morgan</strong><small>Platform engineer</small></span><span className="user-menu">···</span></div>
        </div>
      </aside>

      <main className="main-content" id="overview">
        <header className="topbar">
          <div className="breadcrumb"><span>Northstar</span><span className="breadcrumb-divider">/</span><strong>Observability</strong></div>
          <div className="topbar-right"><span className={`environment-pill environment-pill--${backendState}`}><span />{backendState === 'connected' ? 'BACKEND CONNECTED' : backendState === 'offline' ? 'BACKEND OFFLINE' : 'CONNECTING'}</span><span className="topbar-divider" /><span className="utc-clock"><Clock3 size={14} />UTC</span></div>
        </header>

        <div className="page-content">
          <section className="page-heading">
            <div>
              <div className="eyebrow"><span className="eyebrow-line" />SYSTEM OVERVIEW</div>
              <h1>Log analytics</h1>
              <p>Distributed events across your services, in one place.</p>
            </div>
            <div className="heading-actions">
              <span className={`stream-state ${backendState === 'connected' && liveTail ? 'stream-state--live' : ''}`}><span />{backendState === 'offline' ? 'Backend unavailable' : liveTail ? 'Polling every 3s' : 'Polling every 30s'}</span>
              <button className="button" type="button" onClick={() => setRefreshVersion((version) => version + 1)}><RefreshCw size={14} />Refresh</button>
              <button className={`button button--live ${liveTail ? 'button--live-active' : ''}`} type="button" aria-pressed={liveTail} onClick={() => setLiveTail((active) => !active)}>
                <Radio size={15} />{liveTail ? 'Stop live' : 'Live polling'}
              </button>
              <button className="button button--export" type="button" onClick={() => downloadCsv(matchingLogs)}><Download size={15} />Export CSV</button>
            </div>
          </section>

          {backendState === 'offline' && <section className="connection-banner" role="alert"><div><strong>LogStream backend is unavailable</strong><span>{backendError || 'Check that the Java service is running on port 8080.'}</span></div><button className="button" type="button" onClick={() => setRefreshVersion((version) => version + 1)}><RefreshCw size={14} />Retry</button></section>}

          <section className="metrics-grid" aria-label="Log metrics">
            <MetricCard label="Events ingested" value={formatNumber(rangeLogs.length)} detail={`from ${rangeDescription}`} icon={Database} tone="green" trend={trendDescription || formatChange(eventChange)} trendDirection={eventChange === null || eventChange >= 0 ? 'up' : 'down'} trendTone={eventChange !== null && eventChange < 0 ? 'negative' : 'positive'} />
            <MetricCard label="Error rate" value={`${errorRate.toFixed(2)}%`} detail={`${formatNumber(errorCount)} error events`} icon={ShieldAlert} tone="red" trend={trendDescription || `${errorRateChange > 0 ? '+' : errorRateChange < 0 ? '-' : ''}${Math.abs(errorRateChange).toFixed(2)} pp`} trendDirection={errorRateChange >= 0 ? 'up' : 'down'} trendTone={errorRateChange > 0 ? 'negative' : 'positive'} />
            <MetricCard label="P95 latency" value={`${p95} ms`} detail="across indexed services" icon={Timer} tone="amber" trend={trendDescription || formatChange(latencyChange)} trendDirection={latencyChange === null || latencyChange >= 0 ? 'up' : 'down'} trendTone={latencyChange !== null && latencyChange > 0 ? 'negative' : 'positive'} />
            <MetricCard label="Active services" value={`${activeServices} / ${serviceOptions.length}`} detail="observed in selected range" icon={Server} tone="blue" trend={trendDescription || (serviceChange === 0 ? 'No change' : `${serviceChange > 0 ? '+' : ''}${serviceChange} ${Math.abs(serviceChange) === 1 ? 'service' : 'services'}`)} trendDirection={serviceChange >= 0 ? 'up' : 'down'} trendTone={serviceChange < 0 ? 'negative' : 'positive'} />
          </section>

          <section className="overview-grid" aria-label="Log activity and alerts">
            <article className="panel chart-panel">
              <div className="panel-heading chart-heading">
                <div><div className="panel-title-row"><h2>Log volume</h2><span className="subtle-live"><span />BACKEND INDEX</span></div><p>Indexed events grouped by severity over time</p></div>
                <label className="select-wrap range-select"><span className="sr-only">Chart time range</span><select value={range} onChange={(event) => updateFilter(setRange, event.target.value)}><option value="all">Latest indexed</option><option value="15m">Last 15 min</option><option value="1h">Last 1 hour</option><option value="6h">Last 6 hours</option></select><ChevronDown size={14} /></label>
              </div>
              <div className="chart-summary"><span className="chart-total">{formatNumber(rangeLogs.length)} <small>events</small></span><span className="chart-period"><Clock3 size={13} />{range === 'all' ? 'Latest indexed range' : range === '15m' ? '15 minute window' : range === '1h' ? 'Hourly window' : '6 hour window'}</span></div>
              <ReactEChartsCore echarts={echarts} option={chartOption} notMerge lazyUpdate style={{ height: 224, width: '100%' }} opts={{ renderer: 'canvas' }} />
              <div className="chart-footnote"><span><Zap size={13} />Peak volume</span><strong>{formatNumber(peakVolume)} <small>events / bucket</small></strong></div>
            </article>

            <article className="panel alerts-panel" id="alert-activity">
              <div className="panel-heading">
                <div><div className="panel-title-row"><h2>Recent errors</h2><span className="alert-count">{errorCount}</span></div><p>Latest ERROR events in the selected window</p></div>
                <span className="mock-label">LUCENE</span>
              </div>
              {recentErrors.length ? <div className="alert-list">
                {recentErrors.map((log) => <button className="alert-item" key={log.id} type="button" onClick={() => setSelectedLog(log)}>
                  <span className="alert-icon"><ShieldAlert size={15} /></span>
                  <span className="alert-copy"><strong>{log.service}</strong><span>{log.message}</span><small>{formatRelativeTime(log.timestamp, now)}<i />{log.latencyMs} ms</small></span>
                  <ChevronRight className="alert-chevron" size={15} />
                </button>)}
              </div> : <div className="alert-empty"><span><ShieldAlert size={17} /></span><strong>{backendState === 'connected' ? 'No errors in this window' : 'Waiting for backend'}</strong><small>{backendState === 'connected' ? 'No indexed ERROR events in this range.' : 'Recent error events will appear here.'}</small></div>}
              <div className="alerts-footer"><span className="alert-rule-dot" />Indexed error events <strong>{formatNumber(errorCount)}</strong></div>
            </article>
          </section>

          <section className="panel logs-panel" id="log-explorer">
            <div className="logs-heading">
              <div><div className="panel-title-row"><h2>Log explorer</h2><span className="result-count">{formatNumber(matchingLogs.length)} events</span></div><p>Search and inspect events from every service</p></div>
              <div className="filter-controls">
                <label className="search-box"><Search size={15} /><span className="sr-only">Search logs</span><input ref={searchInputRef} value={query} onChange={(event) => updateFilter(setQuery, event.target.value)} placeholder="Search messages, trace IDs…" /><kbd>/</kbd>{query && <button type="button" aria-label="Clear search" onClick={() => updateFilter(setQuery, '')}><X size={14} /></button>}</label>
                <label className="select-wrap"><span className="sr-only">Filter by severity</span><select value={level} onChange={(event) => updateFilter(setLevel, event.target.value)}><option value="all">All levels</option><option value="ERROR">Error</option><option value="WARN">Warning</option><option value="INFO">Info</option><option value="DEBUG">Debug</option></select><ChevronDown size={14} /></label>
                <label className="select-wrap service-select"><span className="sr-only">Filter by service</span><select value={service} onChange={(event) => updateFilter(setService, event.target.value)}><option value="all">All services</option>{serviceOptions.map((name) => <option value={name} key={name}>{name}</option>)}</select><ChevronDown size={14} /></label>
              </div>
            </div>

            <div className="table-wrap">
              <table>
                <thead><tr><th><button className="sort-button" type="button" onClick={() => setOldestFirst((value) => !value)}>Timestamp<ChevronDown className={oldestFirst ? 'sort-oldest' : ''} size={13} /></button></th><th>Severity</th><th>Service</th><th>Message</th><th>Latency</th><th>Trace ID</th><th><span className="sr-only">Open event</span></th></tr></thead>
                <tbody>
                  {pageLogs.map((log) => <tr key={log.id} tabIndex={0} onClick={() => setSelectedLog(log)} onKeyDown={(event) => { if (event.key === 'Enter' || event.key === ' ') { event.preventDefault(); setSelectedLog(log); } }}>
                    <td className="timestamp-cell">{formatTimestamp(log.timestamp, true)}</td>
                    <td><span className={`level-badge level-badge--${log.level.toLowerCase()}`}><span />{log.level}</span></td>
                    <td className="service-cell">{log.service}</td>
                    <td className="message-cell"><span>{log.message}</span></td>
                    <td className="latency-cell">{log.latencyMs} ms</td>
                    <td className="trace-cell">{log.traceId || '—'}</td>
                    <td><ChevronRight className="row-chevron" size={15} /></td>
                  </tr>)}
                </tbody>
              </table>
              {!matchingLogs.length && <div className="table-empty"><span>{searchLoading ? <RefreshCw size={18} /> : <Search size={18} />}</span><strong>{isLoading ? 'Connecting to backend' : searchLoading ? 'Searching Lucene index' : backendState === 'connected' && logs.length === 0 ? 'No logs indexed yet' : 'No matching events'}</strong><p>{backendState === 'offline' ? 'Start the Spring Boot service to load indexed logs.' : 'Try another search, time range, or filter.'}</p>{(query || service !== 'all' || level !== 'all') && <button className="text-button" type="button" onClick={clearFilters}>Clear filters</button>}</div>}
            </div>

            <div className="table-footer"><span>Showing {matchingLogs.length ? currentPage * PAGE_SIZE + 1 : 0}–{Math.min((currentPage + 1) * PAGE_SIZE, matchingLogs.length)} of {formatNumber(matchingLogs.length)} events</span><div className="pagination"><button type="button" aria-label="Previous page" disabled={currentPage === 0} onClick={() => setRequestedPage((page) => Math.max(0, page - 1))}><ChevronLeft size={15} /></button><span>Page <strong>{currentPage + 1}</strong> of <strong>{pageCount}</strong></span><button type="button" aria-label="Next page" disabled={currentPage >= pageCount - 1} onClick={() => setRequestedPage((page) => Math.min(pageCount - 1, page + 1))}><ChevronRight size={15} /></button></div></div>
          </section>

          <footer className="page-footer"><span><span className={`footer-dot footer-dot--${backendState}`} />{backendState === 'connected' ? `${formatNumber(logs.length)} logs loaded` : backendState === 'offline' ? 'Backend disconnected' : 'Connecting to backend'}</span><span>Last checked {formatTimestamp(now, true)} UTC</span></footer>
        </div>
      </main>

      {selectedLog && <div className="drawer-backdrop" role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget) setSelectedLog(null); }}>
        <aside className="detail-drawer" role="dialog" aria-modal="true" aria-labelledby="detail-title">
          <div className="drawer-header"><div><span className="drawer-eyebrow">EVENT DETAILS</span><h2 id="detail-title">Log event</h2></div><button className="icon-button" type="button" aria-label="Close event details" onClick={() => setSelectedLog(null)}><X size={18} /></button></div>
          <div className="drawer-event"><span className={`level-badge level-badge--${selectedLog.level.toLowerCase()}`}><span />{selectedLog.level}</span><p>{selectedLog.message}</p><time>{new Date(selectedLog.timestamp).toLocaleString('en-US', { dateStyle: 'medium', timeStyle: 'medium', hour12: false })}</time></div>
          <div className="detail-section"><h3>Indexed fields</h3><dl className="detail-list"><div><dt>Service</dt><dd>{selectedLog.service}</dd></div><div><dt>Response time</dt><dd>{selectedLog.latencyMs} ms</dd></div></dl></div>
          <div className="detail-section"><h3>Correlation</h3><dl className="detail-list"><div><dt>Trace ID</dt><dd>{selectedLog.traceId || 'Unavailable'}</dd></div></dl></div>
          <div className="detail-section"><h3>Structured payload</h3><pre className="payload-json">{JSON.stringify(selectedLog.payload, null, 2)}</pre></div>
          <div className="drawer-footer"><span><span className="footer-dot" />Lucene index</span><button className="text-button" type="button" onClick={() => setSelectedLog(null)}>Close details</button></div>
        </aside>
      </div>}
    </div>
  );
}

export default App;