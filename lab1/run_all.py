#!/usr/bin/env python3
"""Compile, check, and benchmark every point in a separate JVM using only the Python standard library."""
import argparse
import csv
from datetime import datetime
from html import escape
import os
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parent

SERIES = [
    ('sequential', 'Stage 0: no lock', '#2563eb'),
    ('empty', 'Stage 1: empty lock', '#059669'),
    ('locked', 'Stage 1: global lock', '#dc2626'),
    ('striped', 'Stage 2: 16 locks', '#d97706'),
    ('thread-local', 'Stage 3: thread-local state', '#7c3aed'),
    ('double-buffered', 'Stage 4: double buffer', '#0891b2'),
]


def command(args, timeout=120):
    return subprocess.run(args, check=True, capture_output=True, text=True, timeout=timeout)


def chart(rows, destination):
    # SVG — обычный векторный рисунок, открывается в браузере без библиотек.
    width, height = 840, 480
    left, top, right, bottom = 90, 65, 800, 395
    ymax = max(float(r['median_mops']) for r in rows) * 1.12
    xmax = max(int(r['threads']) for r in rows)
    def x(t):
        return left + (int(t) - 1) / max(1, xmax - 1) * (right - left)
    def y(v):
        return bottom - float(v) / ymax * (bottom - top)
    parts = [f'<svg xmlns="http://www.w3.org/2000/svg" width="{width}" height="{height}" viewBox="0 0 {width} {height}">',
             '<rect width="100%" height="100%" fill="white"/>',
             '<g font-family="sans-serif" font-size="13" fill="#172033">',
             '<text x="90" y="28" font-size="20">Stages 0–4: record throughput</text>',
             '<text x="90" y="49">Million record() calls per second; median of five runs</text>']
    for tick in range(6):
        value = ymax * tick / 5
        yy = y(value)
        parts += [f'<path d="M {left} {yy} H {right}" stroke="#e0e5ed"/>',
                  f'<text x="{left - 10}" y="{yy + 4}" text-anchor="end">{value:.1f}</text>']
    for t in sorted({int(r['threads']) for r in rows}):
        parts.append(f'<text x="{x(t)}" y="{bottom + 22}" text-anchor="middle">{t}</text>')
    parts.append(f'<text x="{(left + right) / 2}" y="{bottom + 45}" text-anchor="middle">Thread count</text>')
    for i, (variant, title, color) in enumerate(SERIES):
        series = sorted((r for r in rows if r['variant'] == variant), key=lambda r: int(r['threads']))
        points = ' '.join(f"{x(r['threads'])},{y(r['median_mops'])}" for r in series)
        parts.append(f'<polyline points="{points}" fill="none" stroke="{color}" stroke-width="2.5"/>')
        for r in series:
            parts.append(f'<circle cx="{x(r["threads"])}" cy="{y(r["median_mops"])}" r="4" fill="{color}"><title>{escape(variant)} T={r["threads"]}: {r["median_mops"]} M ops/s</title></circle>')
        xx = 90 + (i % 3) * 245
        yy = 438 + (i // 3) * 24
        parts += [f'<circle cx="{xx}" cy="{yy}" r="4" fill="{color}"/>',
                  f'<text x="{xx + 10}" y="{yy + 4}">{title}</text>']
    parts.append('</g></svg>')
    destination.write_text('\n'.join(parts), encoding='utf-8')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--smoke', action='store_true', help='Quick smoke test; not a final benchmark')
    args = parser.parse_args()
    mode = 'smoke' if args.smoke else 'full'
    seconds, repeats = ('0.05', '1') if args.smoke else ('5', '5')
    output = ROOT / 'results' / (datetime.now().strftime('%Y%m%d-%H%M%S-%f') + '-' + mode)
    output.mkdir(parents=True)
    # JAVA_HOME, если задан, определяет и компилятор, и JVM.
    java_home = os.environ.get('JAVA_HOME')
    java = str(Path(java_home) / 'bin/java') if java_home else 'java'
    javac = str(Path(java_home) / 'bin/javac') if java_home else 'javac'
    with tempfile.TemporaryDirectory(prefix='lab1-classes-') as classes:
        command([javac, '--release', '17', '-encoding', 'UTF-8', '-d', classes,
                 *map(str, sorted((ROOT / 'src/lab1').glob('*.java')))])
        info = command([java, '-cp', classes, 'lab1.Bench', '--info']).stdout
        (output / 'environment.txt').write_text(info, encoding='utf-8')
        processors = int(dict(line.split('=', 1) for line in info.splitlines())['processors'])
        tests = command([java, '-cp', classes, 'lab1.CollectorCheck']).stdout
        (output / 'checks.txt').write_text(tests, encoding='utf-8')
        print(tests, end='', flush=True)
        points = [('sequential', 1)]
        for variant in ('empty', 'locked', 'striped', 'thread-local', 'double-buffered'):
            points.extend((variant, t) for t in (1, 2, 4, 8, 16) if t <= processors)
        rows = []
        raw = []
        for index, (variant, threads) in enumerate(points, 1):
            print(f'[{index}/{len(points)}] {variant}, T={threads}', flush=True)
            run = command([java, '-cp', classes, 'lab1.Bench', variant, str(threads), seconds, repeats],
                          timeout=float(seconds) * (int(repeats) + 1) + 120)
            (output / f'{variant}-{threads}.txt').write_text(run.stdout + '\n' + run.stderr, encoding='utf-8')
            for line in run.stdout.splitlines():
                fields = line.split(',')
                if fields[0] == 'MEDIAN':
                    rows.append({'variant': variant, 'threads': threads,
                                 'median_mops': f'{float(fields[3]) / 1e6:.6f}'})
                elif fields[0] in {variant for variant, _, _ in SERIES}:
                    raw.append(fields)
            with (output / 'summary.csv').open('w', newline='') as f:
                writer = csv.DictWriter(f, fieldnames=['variant', 'threads', 'median_mops'])
                writer.writeheader()
                writer.writerows(rows)
            print('  median: ' + rows[-1]['median_mops'] + ' M ops/s', flush=True)
        with (output / 'runs.csv').open('w', newline='') as f:
            writer = csv.writer(f)
            writer.writerow(['variant', 'threads', 'trial', 'operations', 'seconds', 'ops_per_second'])
            writer.writerows(raw)
        stress_rows = []
        for variant in ('striped', 'thread-local', 'double-buffered', 'double-buffered-broken'):
            print('stress: ' + variant, flush=True)
            run = command([java, '-cp', classes, 'lab1.ConsistencyCheck', variant, '10000'], timeout=180)
            lines = run.stdout.splitlines()
            stress_rows.append(lines[-1].split(','))
            (output / f'stress-{variant}.txt').write_text(run.stdout + '\n' + run.stderr, encoding='utf-8')
        with (output / 'stress.csv').open('w', newline='') as f:
            writer = csv.writer(f)
            writer.writerow(['variant', 'snapshots', 'broken', 'broken_percent', 'less', 'greater',
                             'final_difference', 'calls', 'final_count'])
            writer.writerows(stress_rows)
        if not args.smoke:
            chart(rows, output / 'throughput.svg')
        print('Results: ' + str(output), flush=True)
        if args.smoke:
            print('This is only a smoke test, not a final benchmark.')


if __name__ == '__main__':
    main()
