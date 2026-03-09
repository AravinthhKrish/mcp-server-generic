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
const apiToken = __ENV.MCP_API_TOKEN || 'dev-token';
const summaryFile = __ENV.SUMMARY_FILE || 'perf/results/mcp-regression-summary.json';
const htmlReportFile = __ENV.HTML_REPORT_FILE || 'perf/results/mcp-regression-report.html';
const jsonHeaders = { headers: { 'Content-Type': 'application/json' } };
const authedJsonHeaders = {
  headers: {
    'Content-Type': 'application/json',
    Authorization: `Bearer ${apiToken}`,
  },
};
const authedHeaders = { headers: { Authorization: `Bearer ${apiToken}` } };

function post(path, payload, headers = jsonHeaders) {
  return http.post(`${baseUrl}${path}`, JSON.stringify(payload), headers);
}

function get(path, headers) {
  return http.get(`${baseUrl}${path}`, headers);
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

function collectChecks(group) {
  if (!group) return [];
  const ownChecks = group.checks || [];
  const childChecks = (group.groups || []).flatMap((child) => collectChecks(child));
  return [...ownChecks, ...childChecks];
}

function buildHtmlReport(data) {
  const checks = collectChecks(data.root_group);
  const metrics = data.metrics || {};

  const passed = checks.reduce((sum, item) => sum + (item.passes || 0), 0);
  const failed = checks.reduce((sum, item) => sum + (item.fails || 0), 0);
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
  group('api/mcp auth checks', () => {
    const unauthorizedToolsRes = get('/api/mcp/tools');
    check(unauthorizedToolsRes, {
      'api/mcp/tools without auth is 401': (r) => r.status === 401,
    });

    const toolsRes = get('/api/mcp/tools', authedHeaders);
    check(toolsRes, {
      'api/mcp/tools with auth is 200': (r) => r.status === 200,
      'api/mcp/tools returns an array': (r) => Array.isArray(r.json()),
      'api/mcp/tools includes market.quote': (r) =>
        Array.isArray(r.json()) && r.json().some((tool) => tool.id === 'market.quote'),
    });
  });

  group('api/mcp execute endpoint', () => {
    const missingToolIdRes = post('/api/mcp/execute', { params: { query: 'foo' } }, authedJsonHeaders);
    check(missingToolIdRes, {
      'api/mcp/execute missing toolId is 400': (r) => r.status === 400,
    });

    const marketExecuteRes = post(
      '/api/mcp/execute',
      {
        toolId: 'market.quote',
        toolName: 'Market Quote',
        params: { symbol: 'AAPL' },
      },
      authedJsonHeaders,
    );

    check(marketExecuteRes, {
      'api/mcp/execute market.quote is 200': (r) => r.status === 200,
      'api/mcp/execute response success true': (r) => r.json('success') === true,
      'api/mcp/execute response toolId matches': (r) => r.json('toolId') === 'market.quote',
      'api/mcp/execute response toolName matches': (r) => r.json('toolName') === 'Market Quote',
      'api/mcp/execute response result is string': (r) => typeof r.json('result') === 'string',
      'api/mcp/execute response simulated true': (r) => r.json('simulated') === true,
    });
  });

  group('legacy mcp endpoints still healthy', () => {
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
