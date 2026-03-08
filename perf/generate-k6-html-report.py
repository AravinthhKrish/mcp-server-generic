#!/usr/bin/env python3
"""Generate a readable dynamic HTML report from k6 summary JSON."""

import argparse
import datetime as dt
import html
import json
from pathlib import Path


def _num(value, digits=2):
    if isinstance(value, (int, float)):
        return f"{value:.{digits}f}"
    return "-"


def _esc(value):
    return html.escape(str(value))


def metric_rows(metrics):
    rows = []
    for name in sorted(metrics.keys()):
        metric = metrics.get(name) or {}
        values = metric.get("values") or {}
        rows.append(
            "<tr>"
            f"<td>{_esc(name)}</td>"
            f"<td>{_esc(metric.get('type', ''))}</td>"
            f"<td>{_esc(metric.get('contains', ''))}</td>"
            f"<td>{_num(values.get('count'), 0)}</td>"
            f"<td>{_num(values.get('rate'), 4)}</td>"
            f"<td>{_num(values.get('avg'))}</td>"
            f"<td>{_num(values.get('min'))}</td>"
            f"<td>{_num(values.get('med'))}</td>"
            f"<td>{_num(values.get('p(90)'))}</td>"
            f"<td>{_num(values.get('p(95)'))}</td>"
            f"<td>{_num(values.get('max'))}</td>"
            "</tr>"
        )
    return "\n".join(rows)


def render(summary, source_path):
    root_group = summary.get("root_group") or {}
    checks = root_group.get("checks") or []
    metrics = summary.get("metrics") or {}

    passed = sum(1 for c in checks if c.get("fails", 0) == 0)
    failed = sum(1 for c in checks if c.get("fails", 0) > 0)

    check_rows = []
    for check in checks:
        fails = check.get("fails", 0)
        status = "PASS" if fails == 0 else "FAIL"
        css = "ok" if status == "PASS" else "bad"
        check_rows.append(
            "<tr>"
            f"<td>{_esc(check.get('name', ''))}</td>"
            f"<td class=\"{css}\">{status}</td>"
            f"<td>{check.get('passes', 0)}</td>"
            f"<td>{fails}</td>"
            "</tr>"
        )

    checks_html = "\n".join(check_rows) if check_rows else '<tr><td colspan="4" class="muted">No checks found.</td></tr>'
    metrics_html = metric_rows(metrics) if metrics else '<tr><td colspan="11" class="muted">No metrics found.</td></tr>'
    generated = dt.datetime.utcnow().replace(microsecond=0).isoformat() + "Z"

    return f"""<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1" />
  <title>k6 Regression Report</title>
  <style>
    body {{ font-family: Arial, sans-serif; margin: 24px; color: #1f2937; }}
    .meta {{ color: #6b7280; margin: 8px 0 20px; }}
    .cards {{ display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); gap: 12px; margin-bottom: 16px; }}
    .card {{ border: 1px solid #e5e7eb; border-radius: 8px; padding: 12px; background: #fafafa; }}
    .label {{ font-size: 12px; color: #6b7280; text-transform: uppercase; }}
    .value {{ font-size: 24px; font-weight: 700; }}
    .ok {{ color: #15803d; font-weight: 700; }}
    .bad {{ color: #b91c1c; font-weight: 700; }}
    .table-wrap {{ max-height: 55vh; overflow: auto; border: 1px solid #e5e7eb; border-radius: 8px; }}
    table {{ width: 100%; border-collapse: collapse; font-size: 14px; }}
    th, td {{ border: 1px solid #e5e7eb; padding: 8px; text-align: left; }}
    th {{ background: #f3f4f6; position: sticky; top: 0; }}
    input {{ width: 100%; max-width: 420px; padding: 8px; border: 1px solid #d1d5db; border-radius: 6px; margin-bottom: 10px; }}
    .muted {{ color: #6b7280; }}
  </style>
</head>
<body>
  <h1>k6 Regression Report</h1>
  <div class="meta">Generated {generated} · Source {_esc(source_path)}</div>

  <div class="cards">
    <div class="card"><div class="label">Total Checks</div><div class="value">{len(checks)}</div></div>
    <div class="card"><div class="label">Passed</div><div class="value ok">{passed}</div></div>
    <div class="card"><div class="label">Failed</div><div class="value {'bad' if failed else 'ok'}">{failed}</div></div>
    <div class="card"><div class="label">Metrics</div><div class="value">{len(metrics)}</div></div>
  </div>

  <h2>Checks</h2>
  <div class="table-wrap">
    <table>
      <thead><tr><th>Name</th><th>Status</th><th>Passes</th><th>Fails</th></tr></thead>
      <tbody>{checks_html}</tbody>
    </table>
  </div>

  <h2>Metrics</h2>
  <input id="filter" type="search" placeholder="Filter metrics by name/type..." />
  <div class="table-wrap">
    <table id="metrics">
      <thead>
        <tr><th>Metric</th><th>Type</th><th>Contains</th><th>Count</th><th>Rate</th><th>Avg</th><th>Min</th><th>Median</th><th>P90</th><th>P95</th><th>Max</th></tr>
      </thead>
      <tbody>{metrics_html}</tbody>
    </table>
  </div>

  <script>
    const input = document.getElementById('filter');
    const rows = Array.from(document.querySelectorAll('#metrics tbody tr'));
    input.addEventListener('input', () => {{
      const q = input.value.toLowerCase().trim();
      rows.forEach((row) => {{
        row.style.display = row.innerText.toLowerCase().includes(q) ? '' : 'none';
      }});
    }});
  </script>
</body>
</html>
"""


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", required=True)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()

    summary = json.loads(Path(args.input).read_text(encoding="utf-8"))
    out = render(summary, args.input)
    out_path = Path(args.output)
    out_path.parent.mkdir(parents=True, exist_ok=True)
    out_path.write_text(out, encoding="utf-8")


if __name__ == "__main__":
    main()
