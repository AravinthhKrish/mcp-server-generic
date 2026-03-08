import http from 'k6/http';
import { check, group, sleep } from 'k6';

export const options = {
  vus: Number(__ENV.VUS || 1),
  iterations: Number(__ENV.ITERATIONS || 1),
  thresholds: {
    checks: ['rate==1.0'],
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<1500'],
  },
};

const baseUrl = __ENV.BASE_URL || 'http://localhost:8080';
const summaryFile = __ENV.SUMMARY_FILE || 'perf/results/mcp-regression-summary.json';
const htmlReportFile = __ENV.HTML_REPORT_FILE || 'perf/results/mcp-regression-report.html';
const jsonHeaders = { headers: { 'Content-Type': 'application/json' } };

function post(path, payload) {
  return http.post(`${baseUrl}${path}`, JSON.stringify(payload), jsonHeaders);
}

function get(path) {
  return http.get(`${baseUrl}${path}`);
}

function esc(value) {
  return String(value)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

function num(value, digits = 2) {
  return typeof value === 'number' ? value.toFixed(digits) : '-';
}

function metricRows(metrics) {
  return Object.keys(metrics)
    .sort()
    .map((name) => {
      const metric = metrics[name] || {};
      const values = metric.values || {};
      return `
      <tr>
        <td>${esc(name)}</td>
        <td>${esc(metric.type || '')}</td>
        <td>${esc(metric.contains || '')}</td>
        <td>${num(values.count, 0)}</td>
        <td>${num(values.rate, 4)}</td>
        <td>${num(values.avg)}</td>
        <td>${num(values.min)}</td>
        <td>${num(values.med)}</td>
        <td>${num(values['p(90)'])}</td>
        <td>${num(values['p(95)'])}</td>
        <td>${num(values.max)}</td>
      </tr>`;
    })
    .join('');
}

function checkRows(checks) {
  return checks
    .map((item) => {
      const fails = item.fails || 0;
      const status = fails === 0 ? 'PASS' : 'FAIL';
      const klass = fails === 0 ? 'ok' : 'bad';
      return `
      <tr>
        <td>${esc(item.name || '')}</td>
        <td class="${klass}">${status}</td>
        <td>${item.passes || 0}</td>
        <td>${fails}</td>
      </tr>`;
    })
    .join('');
}

function buildHtmlReport(data) {
  const checks = (data.root_group && data.root_group.checks) || [];
  const metrics = data.metrics || {};

  const passed = checks.filter((c) => (c.fails || 0) === 0).length;
  const failed = checks.filter((c) => (c.fails || 0) > 0).length;
  const checksTable = checks.length
    ? checkRows(checks)
    : '<tr><td colspan="4" class="muted">No checks found.</td></tr>';
  const metricsTable = Object.keys(metrics).length
    ? metricRows(metrics)
    : '<tr><td colspan="11" class="muted">No metrics found.</td></tr>';

  return `<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1" />
  <title>k6 Regression Report</title>
  <style>
    body { font-family: Arial, sans-serif; margin: 24px; color: #1f2937; }
    .meta { color: #6b7280; margin: 8px 0 20px; }
    .cards { display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); gap: 12px; margin-bottom: 16px; }
    .card { border: 1px solid #e5e7eb; border-radius: 8px; padding: 12px; background: #fafafa; }
    .label { font-size: 12px; color: #6b7280; text-transform: uppercase; }
    .value { font-size: 24px; font-weight: 700; }
    .ok { color: #15803d; font-weight: 700; }
    .bad { color: #b91c1c; font-weight: 700; }
    .table-wrap { max-height: 55vh; overflow: auto; border: 1px solid #e5e7eb; border-radius: 8px; }
    table { width: 100%; border-collapse: collapse; font-size: 14px; }
    th, td { border: 1px solid #e5e7eb; padding: 8px; text-align: left; }
    th { background: #f3f4f6; position: sticky; top: 0; }
    input { width: 100%; max-width: 420px; padding: 8px; border: 1px solid #d1d5db; border-radius: 6px; margin-bottom: 10px; }
    .muted { color: #6b7280; }
  </style>
</head>
<body>
  <h1>k6 Regression Report</h1>
  <div class="meta">Generated ${new Date().toISOString()} · Source ${esc(summaryFile)}</div>

  <div class="cards">
    <div class="card"><div class="label">Total Checks</div><div class="value">${checks.length}</div></div>
    <div class="card"><div class="label">Passed</div><div class="value ok">${passed}</div></div>
    <div class="card"><div class="label">Failed</div><div class="value ${failed ? 'bad' : 'ok'}">${failed}</div></div>
    <div class="card"><div class="label">Metrics</div><div class="value">${Object.keys(metrics).length}</div></div>
  </div>

  <h2>Checks</h2>
  <div class="table-wrap">
    <table>
      <thead><tr><th>Name</th><th>Status</th><th>Passes</th><th>Fails</th></tr></thead>
      <tbody>${checksTable}</tbody>
    </table>
  </div>

  <h2>Metrics</h2>
  <input id="filter" type="search" placeholder="Filter metrics by name/type..." />
  <div class="table-wrap">
    <table id="metrics">
      <thead><tr><th>Metric</th><th>Type</th><th>Contains</th><th>Count</th><th>Rate</th><th>Avg</th><th>Min</th><th>Median</th><th>P90</th><th>P95</th><th>Max</th></tr></thead>
      <tbody>${metricsTable}</tbody>
    </table>
  </div>

  <script>
    const input = document.getElementById('filter');
    const rows = Array.from(document.querySelectorAll('#metrics tbody tr'));
    input.addEventListener('input', () => {
      const q = input.value.toLowerCase().trim();
      rows.forEach((row) => {
        row.style.display = row.innerText.toLowerCase().includes(q) ? '' : 'none';
      });
    });
  </script>
</body>
</html>`;
}

export function handleSummary(data) {
  return {
    stdout: 'k6 run finished. Writing JSON + HTML artifacts.\n',
    [summaryFile]: JSON.stringify(data, null, 2),
    [htmlReportFile]: buildHtmlReport(data),
  };
}

export default function () {
  group('tools endpoints', () => {
    const driveRes = post('/mcp/tools/drive.search_files', {
      query: 'roadmap',
      pageSize: 5,
    });

    check(driveRes, {
      'drive.search_files status is 200': (r) => r.status === 200,
      'drive.search_files has files': (r) => Array.isArray(r.json('files')),
      'drive.search_files source exists': (r) => r.json('source') === 'google-drive',
    });

    const gmailRes = post('/mcp/tools/gmail.search_messages', {
      query: 'from:alerts@example.com',
      maxResults: 5,
    });

    check(gmailRes, {
      'gmail.search_messages status is 200': (r) => r.status === 200,
      'gmail.search_messages has messages': (r) => Array.isArray(r.json('messages')),
    });

    const newsRes = post('/mcp/tools/news.search_articles', {
      query: 'spring boot',
      limit: 5,
    });

    check(newsRes, {
      'news.search_articles status is 200': (r) => r.status === 200,
      'news.search_articles has articles': (r) => Array.isArray(r.json('articles')),
      'news.search_articles has freshness': (r) => typeof r.json('freshness') === 'string',
    });

    const marketRes = post('/mcp/tools/market.quote', {
      symbol: 'AAPL',
    });

    check(marketRes, {
      'market.quote status is 200': (r) => r.status === 200,
      'market.quote has quote.symbol': (r) => r.json('quote.symbol') === 'AAPL',
      'market.quote has provider': (r) => typeof r.json('quote.provider') === 'string',
      'market.quote has asOf': (r) => typeof r.json('asOf') === 'string',
    });
  });

  group('resource endpoints', () => {
    const newsSourcesRes = get('/mcp/resources/news/sources');

    check(newsSourcesRes, {
      'news/sources status is 200': (r) => r.status === 200,
      'news/sources uri matches': (r) => r.json('uri') === 'resource://news/sources',
      'news/sources data is array': (r) => Array.isArray(r.json('data')),
    });

    const providerHealthRes = get('/mcp/resources/system/provider-health');

    check(providerHealthRes, {
      'provider-health status is 200': (r) => r.status === 200,
      'provider-health uri matches': (r) => r.json('uri') === 'resource://system/provider-health',
      'provider-health includes market': (r) => typeof r.json('data.market') === 'string',
    });
  });

  sleep(0.2);
}
