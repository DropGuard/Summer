import sys
import json
from pathlib import Path

ROOT_DIR = Path(__file__).resolve().parent

def parse_summary(profile):
    summary_file = ROOT_DIR / 'k6-scripts' / f'summary-{profile}.json'
    if not summary_file.exists():
        print(f"Error: {summary_file} not found. Please run run-benchmarks.py first.")
        sys.exit(1)
        
    with open(summary_file, 'r', encoding='utf-8') as f:
        result = json.load(f)
        
    metrics = result['metrics']
    name = "Spring Boot 4.0 (Tomcat, Virtual Threads)" if profile == 'spring-boot' else "Summer Framework (Netty)"
    return {
        'name': name,
        'rps': metrics['http_reqs']['rate'],
        'total': metrics['http_reqs']['count'],
        'avgLatency': metrics['http_req_duration']['avg'],
        'p95Latency': metrics['http_req_duration']['p(95)']
    }

def calc_improvement(summer, spring, invert=False):
    if invert:
        # For latency: lower is better
        imp = ((spring - summer) / spring) * 100
    else:
        # For throughput: higher is better
        imp = ((summer - spring) / spring) * 100
    return f"{imp:+.2f}%"

def main():
    try:
        spring_boot_results = parse_summary('spring-boot')
        summer_results = parse_summary('summer')
    except Exception as e:
        print(f"Failed to parse summaries: {e}")
        sys.exit(1)

    print("\n\n=======================================================")
    print(" FULL CRUD BENCHMARK RESULTS (Docker Compose)")
    print("=======================================================")
    print(f"{'Metric':<25} | {spring_boot_results['name']:<25} | {summer_results['name']:<25} | Improvement")
    print("-" * 105)
    
    metrics_to_print = [
        ('Requests/sec (RPS)', 'rps', False, "{:.2f}"),
        ('Total Requests', 'total', False, "{:.0f}"),
        ('Avg Latency (ms)', 'avgLatency', True, "{:.2f}"),
        ('P95 Latency (ms)', 'p95Latency', True, "{:.2f}")
    ]
    
    report_lines = [
        "# Summer Framework Benchmark Results",
        "",
        "| Metric | Spring Boot 4.0 (Tomcat) | Summer Framework (Netty) | Improvement |",
        "|--------|--------------------------|--------------------------|-------------|"
    ]

    for label, key, invert, fmt in metrics_to_print:
        spring_val = spring_boot_results[key]
        summer_val = summer_results[key]
        imp_str = calc_improvement(summer_val, spring_val, invert)
        
        spring_str = fmt.format(spring_val)
        summer_str = fmt.format(summer_val)
        
        print(f"{label:<25} | {spring_str:<25} | {summer_str:<25} | {imp_str}")
        report_lines.append(f"| {label} | {spring_str} | {summer_str} | {imp_str} |")
        
    # Write Markdown Report
    report_file = ROOT_DIR / 'benchmark-results.md'
    report_file.write_text('\n'.join(report_lines), encoding='utf-8')
    print(f"\nReport written to: {report_file.as_posix()}")

if __name__ == "__main__":
    main()
