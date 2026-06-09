#!/usr/bin/env python3
"""
Aggregate JaCoCo coverage data from all modules.
Run this after: mvn clean test jacoco:report
"""

import csv
import os
from pathlib import Path

def aggregate_coverage():
    root = Path(".")
    total_instructions_missed = 0
    total_instructions_covered = 0
    total_branches_missed = 0
    total_branches_covered = 0
    total_lines_missed = 0
    total_lines_covered = 0
    
    modules_with_coverage = []
    
    for module_dir in sorted(root.iterdir()):
        if not module_dir.is_dir():
            continue
        
        csv_file = module_dir / "target" / "site" / "jacoco" / "jacoco.csv"
        if not csv_file.exists():
            continue
        
        modules_with_coverage.append(module_dir.name)
        
        with open(csv_file, 'r') as f:
            reader = csv.DictReader(f)
            for row in reader:
                total_instructions_missed += int(row['INSTRUCTION_MISSED'])
                total_instructions_covered += int(row['INSTRUCTION_COVERED'])
                total_branches_missed += int(row['BRANCH_MISSED'])
                total_branches_covered += int(row['BRANCH_COVERED'])
                total_lines_missed += int(row['LINE_MISSED'])
                total_lines_covered += int(row['LINE_COVERED'])
    
    total_instructions = total_instructions_missed + total_instructions_covered
    total_branches = total_branches_missed + total_branches_covered
    total_lines = total_lines_missed + total_lines_covered
    
    instruction_coverage = (total_instructions_covered / total_instructions * 100) if total_instructions > 0 else 0
    branch_coverage = (total_branches_covered / total_branches * 100) if total_branches > 0 else 0
    line_coverage = (total_lines_covered / total_lines * 100) if total_lines > 0 else 0
    
    print("=" * 60)
    print("Summer Framework - Test Coverage Summary")
    print("=" * 60)
    print(f"\nModules with coverage data: {len(modules_with_coverage)}")
    for module in modules_with_coverage:
        print(f"  - {module}")
    
    print(f"\n{'Metric':<20} {'Covered':>10} {'Total':>10} {'Coverage':>10}")
    print("-" * 60)
    print(f"{'Instructions':<20} {total_instructions_covered:>10} {total_instructions:>10} {instruction_coverage:>9.2f}%")
    print(f"{'Branches':<20} {total_branches_covered:>10} {total_branches:>10} {branch_coverage:>9.2f}%")
    print(f"{'Lines':<20} {total_lines_covered:>10} {total_lines:>10} {line_coverage:>9.2f}%")
    print("=" * 60)
    
    # Generate HTML report
    html_content = f"""<!DOCTYPE html>
<html>
<head>
    <title>Summer Framework - Coverage Summary</title>
    <style>
        body {{ font-family: Arial, sans-serif; margin: 40px; }}
        table {{ border-collapse: collapse; width: 100%; max-width: 600px; }}
        th, td {{ border: 1px solid #ddd; padding: 12px; text-align: right; }}
        th {{ background-color: #4CAF50; color: white; }}
        tr:nth-child(even) {{ background-color: #f2f2f2; }}
        .metric {{ text-align: left; font-weight: bold; }}
        .high {{ color: green; }}
        .medium {{ color: orange; }}
        .low {{ color: red; }}
    </style>
</head>
<body>
    <h1>Summer Framework - Test Coverage Summary</h1>
    <p>Generated from {len(modules_with_coverage)} modules</p>
    
    <table>
        <tr>
            <th>Metric</th>
            <th>Covered</th>
            <th>Total</th>
            <th>Coverage</th>
        </tr>
        <tr>
            <td class="metric">Instructions</td>
            <td>{total_instructions_covered}</td>
            <td>{total_instructions}</td>
            <td class="{'high' if instruction_coverage >= 80 else 'medium' if instruction_coverage >= 60 else 'low'}">{instruction_coverage:.2f}%</td>
        </tr>
        <tr>
            <td class="metric">Branches</td>
            <td>{total_branches_covered}</td>
            <td>{total_branches}</td>
            <td class="{'high' if branch_coverage >= 80 else 'medium' if branch_coverage >= 60 else 'low'}">{branch_coverage:.2f}%</td>
        </tr>
        <tr>
            <td class="metric">Lines</td>
            <td>{total_lines_covered}</td>
            <td>{total_lines}</td>
            <td class="{'high' if line_coverage >= 80 else 'medium' if line_coverage >= 60 else 'low'}">{line_coverage:.2f}%</td>
        </tr>
    </table>
    
    <h2>Coverage by Module</h2>
    <table>
        <tr>
            <th>Module</th>
            <th>Instruction Coverage</th>
        </tr>
"""
    
    for module in modules_with_coverage:
        csv_file = root / module / "target" / "site" / "jacoco" / "jacoco.csv"
        module_missed = 0
        module_covered = 0
        
        with open(csv_file, 'r') as f:
            reader = csv.DictReader(f)
            for row in reader:
                module_missed += int(row['INSTRUCTION_MISSED'])
                module_covered += int(row['INSTRUCTION_COVERED'])
        
        module_total = module_missed + module_covered
        module_coverage = (module_covered / module_total * 100) if module_total > 0 else 0
        
        html_content += f"""        <tr>
            <td class="metric">{module}</td>
            <td class="{'high' if module_coverage >= 80 else 'medium' if module_coverage >= 60 else 'low'}">{module_coverage:.2f}%</td>
        </tr>
"""
    
    html_content += """    </table>
</body>
</html>"""
    
    # Generate in target/ (already gitignored)
    output_dir = Path("target")
    output_dir.mkdir(exist_ok=True)
    output_file = output_dir / "coverage-summary.html"
    with open(output_file, "w") as f:
        f.write(html_content)
    print(f"\nHTML report saved to: {output_file}")

if __name__ == "__main__":
    aggregate_coverage()
