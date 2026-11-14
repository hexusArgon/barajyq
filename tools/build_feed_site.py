#!/usr/bin/env python3
"""Builds feed-site/ (public repo hexusArgon/barajyq-feed, GitHub Pages) from feed/events.json.

  python3 tools/build_feed_site.py

The apps download https://hexusargon.github.io/barajyq-feed/events.json (BuildConfig.EVENTS_FEED_URL on
Android, FeedRepository.feedURL on iOS) and show it instead of the bundled copy, so pushing this folder
updates the feed on every installed app. The file is minified and kk/en copies equal to the Russian text are left out (Pages serves it gzip-compressed).
Events whose last day is before today (UTC+5) are dropped here too, the apps hide them on their own anyway.
Commit and push are done on Windows by tools/windows/publish.bat.
"""
from __future__ import annotations
import datetime as dt
import json
import pathlib

ROOT = pathlib.Path(__file__).resolve().parent.parent
SRC = ROOT / 'feed' / 'events.json'
SITE = ROOT / 'feed-site'
ZONE = dt.timezone(dt.timedelta(hours=5))


def not_over(event: dict, today: str) -> bool:
    last = event.get('dateTo') or event.get('dateFrom')
    return event.get('isPlace') or not last or last >= today


def slim(item: dict) -> dict:
    """kk/en copies identical to the Russian text are dropped: both apps fall back to Russian when blank."""
    out = dict(item)
    for base in ('title', 'place', 'description'):
        ru = out.get(base + 'Ru')
        for lang in ('Kk', 'En'):
            if out.get(base + lang) in ('', None, ru): out.pop(base + lang, None)
    return out


def build(today: str | None = None) -> dict:
    today = today or dt.datetime.now(ZONE).date().isoformat()
    feed = json.loads(SRC.read_text(encoding='utf-8'))
    before = len(feed.get('events', []))
    feed['events'] = [slim(e) for e in feed.get('events', []) if not_over(e, today)]
    feed['places'] = [slim(p) for p in feed.get('places', [])]
    SITE.mkdir(exist_ok=True)
    (SITE / 'events.json').write_text(json.dumps(feed, ensure_ascii=False, separators=(',', ':')), encoding='utf-8')
    (SITE / '.nojekyll').write_text('', encoding='utf-8')
    (SITE / 'index.html').write_text(
        '<!doctype html><meta charset="utf-8"><title>Barajyq feed</title>'
        '<p>Barajyq events feed: <a href="events.json">events.json</a></p>\n', encoding='utf-8')
    return {'events': len(feed['events']), 'droppedPast': before - len(feed['events']),
            'bytes': (SITE / 'events.json').stat().st_size, 'updatedAt': feed.get('updatedAt')}


if __name__ == '__main__':
    print(json.dumps(build(), ensure_ascii=False))
