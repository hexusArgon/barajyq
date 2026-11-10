#!/usr/bin/env python3
"""Morning monitoring helpers: browser-side scanners for Telegram/Instagram and their importers.

The monitoring shells cannot reach t.me / instagram.com, but the built-in browser on the PC can.
These subcommands generate the JavaScript to run there and import what it returns.
Runbook: tools/MONITORING.md.

  tg-js                 print JS for a https://t.me/s/... tab: new candidate posts of all channels
  tg-import SCAN.json   import that result (same rules/dedup as tg_to_feed.py) and advance cursors
  ig-handles            list Instagram handles to scan
  ig-js HANDLE          print JS for the https://www.instagram.com/HANDLE/ tab: new posts + captions
  ig-seen RAW.json...   remember the shortcodes of reviewed posts (event or not)
  patch PATCH.json      apply {card id: {field: value}} to the feed (translations, fixes)
  add-cards CARDS.json  add hand-reviewed event cards (posts the strict rules sent to review)
"""
from __future__ import annotations
import datetime as dt
import json
import re
import sys
from pathlib import Path

import schedule
import tg_to_feed as tg
from event_policy import import_reason

ROOT = Path(__file__).resolve().parents[1]
TOOLS = ROOT / 'tools'
FEED = ROOT / 'feed/events.json'
ASSETS = ROOT / 'android/app/src/main/assets/events.json'
TG_CURSOR = TOOLS / 'tg_cursor.json'
IG_SEEN = TOOLS / 'ig_seen.json'
IG_MAX_AGE_DAYS = 21

TG_JS = r'''await (async () => {
  const cfg = __CFG__;
  const re = new RegExp(cfg.eventWords, 'i');
  const out = {scannedAt: new Date().toISOString(), posts: [], maxIds: {}, errors: {}};
  for (const ch of cfg.channels) {
    try {
      const r = await fetch('/s/' + ch.handle);
      if (!r.ok) { out.errors[ch.handle] = 'HTTP ' + r.status; continue; }
      const doc = new DOMParser().parseFromString(await r.text(), 'text/html');
      let max = ch.since;
      for (const n of doc.querySelectorAll('.tgme_widget_message[data-post]')) {
        const [owner, num] = n.dataset.post.split('/');
        if (owner.toLowerCase() !== ch.handle.toLowerCase()) continue;
        const id = Number(num); max = Math.max(max, id);
        if (id <= ch.since) continue;
        const b = n.querySelector('.tgme_widget_message_text'); if (!b) continue;
        b.querySelectorAll('br').forEach(br => br.replaceWith('\n'));
        const text = b.textContent.trim();
        if (!re.test(text)) continue;
        const t = n.querySelector('time[datetime]');
        const ph = n.querySelector('.tgme_widget_message_photo_wrap, .tgme_widget_message_video_thumb, .link_preview_image');
        const img = ph && (ph.getAttribute('style') || '').match(/url\(['"]?([^'")]+)/);
        out.posts.push({sourceUrl: 'https://t.me/' + n.dataset.post, text, image: img ? img[1] : null,
          publishedAt: t ? t.getAttribute('datetime') : null,
          links: [...b.querySelectorAll('a[href]')].map(a => a.getAttribute('href'))});
      }
      out.maxIds[ch.handle] = max;
    } catch (e) { out.errors[ch.handle] = String(e); }
    await new Promise(r => setTimeout(r, 300));
  }
  return out;
})()'''

IG_JS = r'''await (async () => {
  const cfg = __CFG__;
  for (let i = 0; i < 20 && !document.querySelector('a[href*="/p/"],a[href*="/reel/"]'); i++)
    await new Promise(r => setTimeout(r, 500));
  const seen = new Set(cfg.seen);
  const minDate = Date.now() - cfg.maxAgeDays * 86400000;
  const out = {handle: cfg.handle, scannedAt: new Date().toISOString(), posts: [], skipped: 0,
    loginWall: !document.querySelector('a[href*="/p/"],a[href*="/reel/"]')};
  const links = new Map();
  for (const a of document.querySelectorAll('a[href*="/p/"], a[href*="/reel/"]'))
    links.set(a.getAttribute('href'), a.querySelector('img')?.alt || '');
  for (const [href, alt] of links) {
    const m = href.match(/\/(p|reel)\/([A-Za-z0-9_-]+)\//); if (!m) continue;
    const altDate = alt.match(/on ([A-Z][a-z]+ \d{1,2}, \d{4})/);
    if (seen.has(m[2]) || (altDate && Date.parse(altDate[1]) < minDate)) { out.skipped++; continue; }
    const r = await fetch(href, {credentials: 'include'});
    const d = new DOMParser().parseFromString(await r.text(), 'text/html');
    const og = d.querySelector('meta[property="og:description"]')?.content || '';
    const cm = og.match(/ - \S+ (?:on )?([A-Z][a-z]+ \d{1,2}, \d{4}): "([\s\S]*)"\.?\s*$/);
    out.posts.push({code: m[2], postUrl: 'https://www.instagram.com' + href,
      published: cm ? ((d) => d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0') + '-' +
        String(d.getDate()).padStart(2, '0'))(new Date(Date.parse(cm[1]))) : null,
      caption: cm ? cm[2] : og, alt: alt.slice(0, 200)});
    await new Promise(r => setTimeout(r, 500));
  }
  return out;
})()'''


def load(path, default=None):
    return json.loads(path.read_text(encoding='utf-8')) if path.exists() else default


def tg_since() -> dict[str, int]:
    """Last seen post id per channel: cursor file, else the newest staged post."""
    cursor = load(TG_CURSOR, {})
    staged = load(TOOLS / 'tg_review.json', {'posts': {}})['posts']
    since = {}
    for url in list(staged) + [e['sourceUrl'] for e in load(FEED)['events'] if e['id'].startswith('tg-')]:
        handle, _, num = url.removeprefix('https://t.me/').partition('/')
        if num.isdigit(): since[handle.casefold()] = max(since.get(handle.casefold(), 0), int(num))
    for handle, num in cursor.items(): since[handle.casefold()] = max(since.get(handle.casefold(), 0), num)
    return since


def tg_js() -> str:
    since = tg_since()
    channels = [{'handle': c['handle'], 'since': since.get(c['handle'].casefold(), 0)}
                for c in load(TOOLS / 'tg_sources.json')['channels'] if c.get('enabled', True)]
    cfg = {'channels': channels, 'eventWords': tg.EVENT_WORDS}
    return TG_JS.replace('__CFG__', json.dumps(cfg, ensure_ascii=False))


def tg_import(scan_path: str, now=None) -> dict:
    scan = json.loads(Path(scan_path).read_text(encoding='utf-8'))
    by_handle = {}
    for post in scan.get('posts', []):
        handle = post['sourceUrl'].removeprefix('https://t.me/').split('/')[0].casefold()
        by_handle.setdefault(handle, []).append(post)
    result = tg.run(root=ROOT, scanner=lambda handle, pages: by_handle.get(handle.casefold(), []), now=now)
    cursor = load(TG_CURSOR, {})
    for handle, num in scan.get('maxIds', {}).items():
        cursor[handle] = max(int(num), cursor.get(handle, 0))
    tg.save(TG_CURSOR, dict(sorted(cursor.items())))
    result['browserErrors'] = scan.get('errors', {})
    return result


def ig_handles() -> list[str]:
    return [a['handle'] for a in load(TOOLS / 'ig_sources.json')['accounts'] if a.get('enabled', True)]


def ig_seen_codes() -> set[str]:
    seen = set(load(IG_SEEN, {}).get('codes', []))
    for e in load(FEED)['events']:
        m = re.search(r'/(?:p|reel)/([A-Za-z0-9_-]+)/', e.get('sourceUrl', ''))
        if m: seen.add(m.group(1))
    return seen


def ig_js(handle: str) -> str:
    cfg = {'handle': handle, 'seen': sorted(ig_seen_codes()), 'maxAgeDays': IG_MAX_AGE_DAYS}
    return IG_JS.replace('__CFG__', json.dumps(cfg, ensure_ascii=False))


def ig_mark_seen(paths: list[str]) -> int:
    data = load(IG_SEEN, {'codes': []})
    codes = set(data['codes'])
    before = len(codes)
    for path in paths:
        raw = json.loads(Path(path).read_text(encoding='utf-8'))
        for item in raw if isinstance(raw, list) else [raw]:
            codes.update(p['code'] for p in item.get('posts', []))
    tg.save(IG_SEEN, {'codes': sorted(codes)})
    return len(codes) - before


def patch(patch_path: str) -> list[str]:
    """{id: {field: value, ..., "reference": "YYYY-MM-DD"?}}; a new startsAt is re-annotated."""
    changes = json.loads(Path(patch_path).read_text(encoding='utf-8'))
    feed = load(FEED)
    done = []
    for card in feed['events'] + feed['places']:
        fields = dict(changes.get(card['id'], {}))
        if not fields: continue
        reference = fields.pop('reference', None)
        card.update(fields)
        if 'startsAt' in fields:
            ref = dt.date.fromisoformat(reference) if reference else dt.datetime.now(tg.ZONE).date()
            schedule.annotate(card, ref, overwrite=True)
        done.append(card['id'])
    raw = json.dumps(feed, ensure_ascii=False, indent=2) + '\n'
    for path in (FEED, ASSETS): path.write_text(raw, encoding='utf-8')
    # Keep the canonical rows (tools/feed_data.json: t/p/d = [ru, kk, en]) in step with the feed.
    rows = load(TOOLS / 'feed_data.json', [])
    cards = {c['id']: c for c in feed['events'] + feed['places']}
    for row in rows:
        c = cards.get(row['id'])
        if row['id'] not in done or not c: continue
        row.update(t=[c['titleRu'], c['titleKk'], c['titleEn']], p=[c['placeRu'], c['placeKk'], c['placeEn']],
                   d=[c['descriptionRu'], c['descriptionKk'], c['descriptionEn']], st=c['startsAt'], price=c['price'])
    tg.save(TOOLS / 'feed_data.json', rows)
    return done


CARD_DEFAULTS = dict(address='', price=None, workingHours='', lat=None, lon=None, isPlace=False)


def add_cards(cards_path: str) -> dict:
    """List of cards; each needs id, cityId, interest, titleRu, startsAt, placeRu, sourceUrl, sourceName,
    descriptionRu and `reference` (YYYY-MM-DD the post was published: resolves the year of "13 ноя").
    Missing kk/en fields fall back to ru. Duplicates and policy rejects are skipped and reported."""
    items = json.loads(Path(cards_path).read_text(encoding='utf-8'))
    feed = load(FEED)
    staged = load(TOOLS / 'tg_review.json', {'posts': {}})
    added, skipped = [], {}
    for raw in items:
        card = {**CARD_DEFAULTS, **raw}
        reference = dt.date.fromisoformat(card.pop('reference'))
        for base in ('title', 'place', 'description'):
            for lang in ('Kk', 'En'):
                card.setdefault(base + lang, card[base + 'Ru'])
        if card['interest'] not in tg.INTERESTS: skipped[card['id']] = 'bad_interest'; continue
        if not schedule.annotate(card, reference, overwrite=True): skipped[card['id']] = 'unparsed_date'; continue
        if dt.date.fromisoformat(card['dateTo']) < dt.datetime.now(tg.ZONE).date(): skipped[card['id']] = 'past'; continue
        reason = import_reason(card)
        if reason: skipped[card['id']] = reason; continue
        if tg.duplicate(card, feed['events']): skipped[card['id']] = 'duplicate'; continue
        feed['events'].append(card); added.append(card['id'])
        if card['sourceUrl'] in staged['posts']: staged['posts'][card['sourceUrl']]['status'] = 'imported_manual'
    if added:
        feed['updatedAt'] = dt.datetime.now(tg.ZONE).isoformat()
        raw_feed = json.dumps(feed, ensure_ascii=False, indent=2) + '\n'
        for path in (FEED, ASSETS): path.write_text(raw_feed, encoding='utf-8')
        tg.save(TOOLS / 'tg_review.json', staged)
    return {'added': added, 'skipped': skipped}


if __name__ == '__main__':
    cmd, args = (sys.argv[1], sys.argv[2:]) if len(sys.argv) > 1 else ('', [])
    if cmd == 'tg-js': print(tg_js())
    elif cmd == 'tg-import':
        r = tg_import(args[0])
        print(json.dumps({'added': r['added'], 'errors': r['errors'], 'browserErrors': r['browserErrors']},
                         ensure_ascii=False, indent=1))
    elif cmd == 'ig-handles': print('\n'.join(ig_handles()))
    elif cmd == 'ig-js': print(ig_js(args[0]))
    elif cmd == 'ig-seen': print('newly seen:', ig_mark_seen(args))
    elif cmd == 'patch': print('patched:', patch(args[0]))
    elif cmd == 'add-cards': print(json.dumps(add_cards(args[0]), ensure_ascii=False, indent=1))
    else:
        print(__doc__); raise SystemExit(2)
