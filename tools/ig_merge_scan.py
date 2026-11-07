#!/usr/bin/env python3
"""Merge browser IG scan JSON into feed/events.json and android assets."""
from __future__ import annotations
import json, hashlib, pathlib, datetime, sys, re

ROOT = pathlib.Path(__file__).resolve().parent.parent
FEED = ROOT / 'feed' / 'events.json'
SOURCES = ROOT / 'tools' / 'ig_sources.json'
ASSETS = ROOT / 'android' / 'app' / 'src' / 'main' / 'assets' / 'events.json'

KIND_INTEREST = {
    'standup': 'STANDUP', 'quiz': 'STANDUP', 'sport': 'SPORT',
    'venue': 'THEATER', 'afisha': 'FESTIVALS',
}

def slug(s: str) -> str:
    return 'ig-' + hashlib.sha1(s.encode()).hexdigest()[:10]

def interest_for(kind: str, caption: str) -> str:
    c = (caption or '').lower()
    if any(k in c for k in ('стендап', 'standup', 'stand-up', 'stand up')):
        return 'STANDUP'
    if any(k in c for k in ('квиз', 'quiz', 'викторин')):
        return 'STANDUP'
    if any(k in c for k in ('матч', 'хоккей', 'барыс', 'спорт', 'match', 'hockey')):
        return 'SPORT'
    if any(k in c for k in ('выставк', 'музей', 'exhibition', 'галере')):
        return 'EXHIBITIONS'
    if any(k in c for k in ('концерт', 'concert', 'оркестр', 'опер')):
        return 'CONCERTS'
    if any(k in c for k in ('балет', 'ballet', 'спектакл', 'театр', 'премьер')):
        return 'THEATER'
    if any(k in c for k in ('фестив', 'festival', 'ярмарк')):
        return 'FESTIVALS'
    if any(k in c for k in ('кино', 'фильм', 'cinema')):
        return 'CINEMA'
    if any(k in c for k in ('дет', 'kids', 'семей')):
        return 'KIDS'
    return KIND_INTEREST.get(kind, 'FREE')

def main(scan_path: str):
    scan = json.loads(pathlib.Path(scan_path).read_text(encoding='utf-8'))
    feed = json.loads(FEED.read_text(encoding='utf-8'))
    sources = {a['handle']: a for a in json.loads(SOURCES.read_text(encoding='utf-8'))['accounts']}
    existing_urls = {e.get('sourceUrl') for e in feed['events']}
    existing_ids = {e.get('id') for e in feed['events']}
    added = []
    for acc in scan.get('accounts', []):
        handle = acc.get('handle')
        meta = sources.get(handle, {'city': 'astana', 'kind': 'afisha'})
        for p in acc.get('posts', []) or []:
            caption = (p.get('caption') or '').strip()
            title = (p.get('title') or caption.split('\n')[0] or handle)[:120].strip()
            post_url = (p.get('postUrl') or '').strip()
            if not post_url:
                post_url = f'https://www.instagram.com/{handle}/#{slug(handle + "|" + title)}'
            eid = slug(post_url if '/p/' in post_url or '/reel/' in post_url else handle + '|' + title)
            if eid in existing_ids or post_url in existing_urls:
                continue
            place = (p.get('place') or handle).strip()
            starts = (p.get('startsAt') or p.get('approxDate') or '').strip()
            card = {
                'id': eid,
                'cityId': meta.get('city', 'astana'),
                'interest': interest_for(meta.get('kind', ''), caption + ' ' + title),
                'titleRu': title,
                'titleKk': title,
                'titleEn': title,
                'startsAt': starts,
                'placeRu': place,
                'placeKk': place,
                'placeEn': place,
                'address': 'Астана' if meta.get('city') == 'astana' else meta.get('city', ''),
                'price': None,
                'sourceUrl': post_url,
                'sourceName': f'Instagram @{handle}',
                'workingHours': '',
                'descriptionRu': caption or title,
                'descriptionKk': caption or title,
                'descriptionEn': caption or title,
                'lat': None,
                'lon': None,
                'isPlace': False,
            }
            feed['events'].append(card)
            existing_ids.add(eid)
            existing_urls.add(post_url)
            added.append({'id': eid, 'titleRu': title, 'sourceUrl': post_url, 'handle': handle})
    feed['updatedAt'] = datetime.datetime.now(datetime.timezone(datetime.timedelta(hours=5))).isoformat()
    FEED.write_text(json.dumps(feed, ensure_ascii=False, indent=2), encoding='utf-8')
    ASSETS.parent.mkdir(parents=True, exist_ok=True)
    ASSETS.write_bytes(FEED.read_bytes())
    out = {'added': added, 'total': len(feed['events']), 'updatedAt': feed['updatedAt']}
    print(json.dumps(out, ensure_ascii=False, indent=2))
    pathlib.Path('/workspace/astana-afisha/tools/ig_merge_result.json').write_text(
        json.dumps(out, ensure_ascii=False, indent=2), encoding='utf-8')

if __name__ == '__main__':
    if len(sys.argv) < 2:
        print('usage: ig_merge_scan.py SCAN.json', file=sys.stderr)
        sys.exit(2)
    main(sys.argv[1])
