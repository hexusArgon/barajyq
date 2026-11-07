#!/usr/bin/env python3
"""Merge Instagram event posts into feed/events.json.

Instagram has no public feed API for arbitrary accounts. This helper:
1) reads tools/ig_sources.json
2) tries public HTML mirrors (imginn) for recent captions
3) appends new cards (dedup by sourceUrl / id) into feed/events.json
4) copies to android assets

Run: python3 tools/ig_to_feed.py
"""
from __future__ import annotations
import json, re, hashlib, pathlib, urllib.request, datetime
ROOT = pathlib.Path(__file__).resolve().parent.parent
FEED = ROOT / 'feed' / 'events.json'
SOURCES = ROOT / 'tools' / 'ig_sources.json'
ASSETS = ROOT / 'android' / 'app' / 'src' / 'main' / 'assets' / 'events.json'
UA = {'User-Agent': 'Mozilla/5.0 (compatible; BarajyqBot/0.2; +https://github.com/hexusArgon)'}

def fetch(url: str) -> str:
    req = urllib.request.Request(url, headers=UA)
    with urllib.request.urlopen(req, timeout=20) as r:
        return r.read().decode('utf-8', 'replace')

def slug(s: str) -> str:
    h = hashlib.sha1(s.encode()).hexdigest()[:10]
    return 'ig-' + h

def interest_for(kind: str) -> str:
    return {
        'standup': 'STANDUP', 'quiz': 'STANDUP', 'sport': 'SPORT',
        'venue': 'THEATER', 'afisha': 'FESTIVALS',
    }.get(kind, 'FREE')

def parse_imginn(handle: str) -> list[dict]:
    url = f'https://imginn.com/{handle}/'
    try:
        html = fetch(url)
    except Exception as e:
        print(f'  skip {handle}: {e}')
        return []
    # captions often in alt or item desc; keep short heuristics
    caps = re.findall(r'(?:alt|content)="([^"]{40,400})"', html)
    out = []
    seen = set()
    for c in caps:
        c = re.sub(r'\s+', ' ', c).strip()
        if c in seen or 'imginn' in c.lower():
            continue
        seen.add(c)
        if not any(k in c.lower() for k in ('билет', 'концерт', 'спектакл', 'выставк', 'стендап', 'квиз', 'матч', 'афиш', 'шоу', 'премьер', 'ticket', 'show', 'concert')):
            continue
        title = c.split('.')[0][:120]
        out.append({'caption': c, 'title': title})
        if len(out) >= 5:
            break
    return out

def main():
    feed = json.loads(FEED.read_text(encoding='utf-8'))
    sources = json.loads(SOURCES.read_text(encoding='utf-8'))['accounts']
    existing = {e['sourceUrl'] for e in feed['events']} | {e['id'] for e in feed['events']}
    added = 0
    for acc in sources:
        handle = acc['handle']
        print('scan', handle)
        posts = parse_imginn(handle)
        for p in posts:
            ig_url = f'https://instagram.com/{handle}/'
            eid = slug(handle + '|' + p['title'])
            if eid in existing or ig_url + '#' + eid in existing:
                continue
            card = {
                'id': eid,
                'cityId': acc.get('city', 'astana'),
                'interest': interest_for(acc.get('kind', '')),
                'titleRu': p['title'],
                'titleKk': p['title'],
                'titleEn': p['title'],
                'startsAt': '',
                'placeRu': handle,
                'placeKk': handle,
                'placeEn': handle,
                'address': 'Астана' if acc.get('city') == 'astana' else acc.get('city', ''),
                'price': None,
                'sourceUrl': ig_url + '#' + eid,
                'sourceName': f'Instagram @{handle}',
                'workingHours': '',
                'descriptionRu': p['caption'],
                'descriptionKk': p['caption'],
                'descriptionEn': p['caption'],
                'lat': None,
                'lon': None,
                'isPlace': False,
            }
            feed['events'].append(card)
            existing.add(card['id']); existing.add(card['sourceUrl'])
            added += 1
            print('  +', p['title'][:80])
    feed['updatedAt'] = datetime.datetime.now(datetime.timezone(datetime.timedelta(hours=5))).isoformat()
    FEED.write_text(json.dumps(feed, ensure_ascii=False, indent=2), encoding='utf-8')
    ASSETS.parent.mkdir(parents=True, exist_ok=True)
    ASSETS.write_bytes(FEED.read_bytes())
    print(f'done: +{added}, total events={len(feed["events"])}')

if __name__ == '__main__':
    main()
