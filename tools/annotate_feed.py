#!/usr/bin/env python3
"""Fill structured schedule fields (dateFrom/dateTo/dates/time) for feed cards that lack them.

Usage: python3 tools/annotate_feed.py --reference 2026-11-05
`--reference` is the day the cards were COLLECTED (it resolves the missing year, see schedule.py).
Existing fields are never overwritten; run it right after an import, not months later.
Writes feed/events.json and the Android assets copy.
"""
import argparse
import datetime as dt
import json
from pathlib import Path
import schedule

ROOT = Path(__file__).resolve().parents[1]
TARGETS = [ROOT / 'feed/events.json', ROOT / 'android/app/src/main/assets/events.json']

def main(reference: dt.date):
    feed = json.loads(TARGETS[0].read_text(encoding='utf-8'))
    filled, unknown = 0, []
    for card in feed['events']:
        if schedule.annotate(card, reference): filled += 1
        elif not card.get('dateTo'): unknown.append((card['id'], card.get('startsAt', '')))
    raw = json.dumps(feed, ensure_ascii=False, indent=2) + '\n'
    for path in TARGETS: path.write_text(raw, encoding='utf-8')
    return filled, unknown

if __name__ == '__main__':
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument('--reference', required=True, type=dt.date.fromisoformat)
    filled, unknown = main(ap.parse_args().reference)
    print(f'filled: {filled}; without schedule: {len(unknown)}')
    for item in unknown: print('  ', *item)
