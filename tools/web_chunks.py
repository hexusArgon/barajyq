#!/usr/bin/env python3
"""Merge chunks of web scans saved by the harness into one JSON per site (runs in the cloud container).

  python3 web_chunks.py OUTDIR FILE...   FILE = /tmp/claude-tool-results/...txt (a tool answer to `take SITE N`)

Writes OUTDIR/web-<site>.json and prints which chunks were found. Standard library only, no project imports,
so the file can be staged into the container alone.
"""
import json
import sys


def decode(path):
    raw = open(path, encoding='utf-8').read()
    try:  # harness format: [{"type": "text", "text": "\\"{...}   \\"\\n\\n(captured at ...)"}, ...]
        raw = json.loads(raw)[0]['text']
    except (ValueError, KeyError, IndexError, TypeError):
        pass
    value, _ = json.JSONDecoder().raw_decode(raw.lstrip())
    return json.loads(value) if isinstance(value, str) else value


def main(out_dir, paths):
    sites = {}
    for path in paths:
        part = decode(path)
        sites.setdefault(part['site'], {})[part.get('chunk', 0)] = part
    for site, chunks in sites.items():
        total = max(p.get('of', 1) for p in chunks.values())
        missing = [n for n in range(total) if n not in chunks]
        first = chunks[min(chunks)]
        merged = {k: v for k, v in first.items() if k not in ('chunk', 'of', 'events')}
        merged['events'] = [e for n in sorted(chunks) for e in chunks[n]['events']]
        with open(f'{out_dir}/web-{site}.json', 'w', encoding='utf-8') as fh: json.dump(merged, fh, ensure_ascii=False)
        print(json.dumps({'site': site, 'chunks': sorted(chunks), 'of': total, 'missing': missing,
                          'events': len(merged['events'])}))


if __name__ == '__main__':
    if len(sys.argv) < 3: print(__doc__); raise SystemExit(2)
    main(sys.argv[1], sys.argv[2:])
