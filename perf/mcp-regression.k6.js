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
const jsonHeaders = { headers: { 'Content-Type': 'application/json' } };

function post(path, payload) {
  return http.post(`${baseUrl}${path}`, JSON.stringify(payload), jsonHeaders);
}

function get(path) {
  return http.get(`${baseUrl}${path}`);
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
