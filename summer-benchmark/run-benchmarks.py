import sys
import subprocess
from pathlib import Path

ROOT_DIR = Path(__file__).resolve().parent

def run(cmd, cwd=None):
    print(f"\n>>> {cmd}")
    result = subprocess.run(cmd, shell=True, cwd=cwd)
    if result.returncode != 0:
        print(f"Command failed with exit code {result.returncode}")
        sys.exit(1)

def build_projects():
    print("========================================")
    print(" Compiling Benchmark Projects")
    print("========================================")
    run("make install", cwd=ROOT_DIR.parent)
    print("Compilation successful.\n")

def bench(profile):
    print(f"\n========================================")
    print(f" Running Benchmark Profile: {profile}")
    print(f"========================================")
    run(f"docker compose --profile {profile} up --build --abort-on-container-exit", cwd=ROOT_DIR)
    run(f"docker compose --profile {profile} down --remove-orphans", cwd=ROOT_DIR)
    
    summary_file = ROOT_DIR / 'k6-scripts' / f'summary-{profile}.json'
    if not summary_file.exists():
        print(f"Benchmark failed: {summary_file} not generated")
        sys.exit(1)
    else:
        print(f"Benchmark finished. Summary saved to {summary_file}")

def main():
    build_projects()
    
    bench('spring-boot')
    bench('summer')
    bench('gin')
    bench('nextjs')

    print("\nAll benchmarks finished. Generating comparison report...")
    run("python compare-benchmarks.py", cwd=ROOT_DIR)

if __name__ == "__main__":
    main()
