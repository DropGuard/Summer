import sys
import json
from pathlib import Path

ROOT_DIR = Path(__file__).resolve().parent

def parse_summary(profile):
    summary_file = ROOT_DIR / 'k6-scripts' / f'summary-{profile}.json'
    if not summary_file.exists():
        return None
        
    with open(summary_file, 'r', encoding='utf-8') as f:
        result = json.load(f)
        
    metrics = result['metrics']
    return {
        'rps': metrics['http_reqs']['rate'],
        'total': metrics['http_reqs']['count'],
        'avgLatency': metrics['http_req_duration']['avg'],
        'p50Latency': metrics['http_req_duration'].get('med', 0),
        'p95Latency': metrics['http_req_duration']['p(95)'],
        'p99Latency': metrics['http_req_duration'].get('p(99)', 0)
    }

def main():
    profiles = {
        'spring-boot': 'Spring Boot (Java / Jackson)',
        'summer': 'Summer (Java / Jackson)',
        'summer-jsonb': 'Summer (Java / Avaje)',
        'gin': 'Gin (Go / Stdlib)',
        'fastify': 'Fastify (Node.js / V8)'
    }
    
    results = {}
    for p, name in profiles.items():
        res = parse_summary(p)
        if res:
            results[p] = (name, res)

    if not results:
        print("No results found.")
        sys.exit(1)

    print("\n=======================================================")
    print(" FULL CROSS-LANGUAGE CRUD BENCHMARK RESULTS (2 CPU / 512M)")
    print("=======================================================")
    
    headers = ["Metric"] + [r[0] for r in results.values()]
    print(" | ".join(f"{h:<20}" for h in headers))
    print("-" * (23 * len(headers)))
    
    metrics_to_print = [
        ('Requests/sec (RPS)', 'rps', "{:.2f}"),
        ('Total Requests', 'total', "{:.0f}"),
        ('Avg Latency (ms)', 'avgLatency', "{:.2f}"),
        ('P50 Latency (ms)', 'p50Latency', "{:.2f}"),
        ('P95 Latency (ms)', 'p95Latency', "{:.2f}"),
        ('P99 Latency (ms)', 'p99Latency', "{:.2f}")
    ]
    
    report_lines = [
        "# Cross-Language Framework Benchmark Results",
        "",
        "| " + " | ".join(headers) + " |",
        "|" + "|".join(["---" for _ in headers]) + "|"
    ]

    for label, key, fmt in metrics_to_print:
        row = [f"{label:<20}"]
        md_row = [label]
        for p, (name, res) in results.items():
            val = fmt.format(res[key])
            row.append(f"{val:<20}")
            md_row.append(val)
        print(" | ".join(row))
        report_lines.append("| " + " | ".join(md_row) + " |")
        
    report_file = ROOT_DIR / 'benchmark-results.md'
    report_file.write_text('\n'.join(report_lines), encoding='utf-8')
    print(f"\nReport written to: {report_file.as_posix()}")

if __name__ == "__main__":
    main()
