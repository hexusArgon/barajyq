#!/usr/bin/env python3
"""Website sources for the monitoring: ticketon.kz, sxodim.com, kino.kz.

The monitoring shells cannot reach these sites, the built-in browser can. Same pattern as
browser_scan.py: this script prints the JavaScript to run in a tab of the site, the browser returns
JSON with the NEW event pages only (URLs not in tools/web_seen.json), and `import` turns them into
feed cards through browser_scan.add_cards (dates, policy, past and duplicate checks).

  js SITE [LIMIT] [--bg] print JS for a tab on https://SITE/ (ticketon.kz | sxodim.com | kino.kz);
                        --bg starts it in the background (window.__ws) and returns at once
  poll                  print JS that reports the status of background scans
  take SITE [N]         print JS that returns chunk N (0, 1, …; the answer says `of`) of a finished scan
  import SCAN.json...   add the events of browser results to the feed, mark their URLs as seen
  cities                print the city map used for every site

Facts come only from the site's own structured data (schema.org Event / kino.kz API): title,
dates, time, venue, address, price, image, description. Nothing is guessed: a missing field stays
empty and the card is skipped by the policy if the date or venue is missing.
"""
from __future__ import annotations
import datetime as dt
import hashlib
import json
import re
import sys
import tempfile
from pathlib import Path

import tg_to_feed as tg

ROOT = Path(__file__).resolve().parents[1]
TOOLS = ROOT / 'tools'
SEEN = TOOLS / 'web_seen.json'
SEEN_KEEP_DAYS = 120
DEFAULT_LIMIT = 30

# Our city ids (android KazakhstanCities) -> ru name, ticketon slug, sxodim slug, kino.kz city id.
CITIES = {
    'astana':          ('Астана', 'astana', 'astana', 1),
    'almaty':          ('Алматы', 'almaty', 'almaty', 2),
    'shymkent':        ('Шымкент', 'shymkent', 'shymkent', 18),
    'karaganda':       ('Караганда', 'karaganda', None, 7),
    'aktobe':          ('Актобе', 'aktobe', None, 4),
    'atyrau':          ('Атырау', 'atyrau', None, 5),
    'aktau':           ('Актау', 'aktau', None, 3),
    'pavlodar':        ('Павлодар', 'pavlodar', None, 10),
    'ust-kamenogorsk': ('Усть-Каменогорск', 'ust-kamenogorsk', None, 17),
    'semey':           ('Семей', 'semey', None, 12),
    'kostanay':        ('Костанай', 'kostanay', None, 8),
    'kokshetau':       ('Кокшетау', 'kokshetau', None, 20),
    'uralsk':          ('Уральск', 'uralsk', None, 16),
    'taraz':           ('Тараз', 'taraz', None, 14),
    'kyzylorda':       ('Кызылорда', 'kyzylorda', None, 9),
    'petropavlovsk':   ('Петропавловск', 'petropavlovsk', None, 11),
    'turkestan':       ('Туркестан', 'turkestan', None, 47),
    'taldykorgan':     ('Талдыкорган', 'taldykorgan', None, 13),
    'temirtau':        ('Темиртау', 'temirtau', None, 15),
    'konaev':          ('Конаев', 'kapchagay', None, 90),
}
CITY_BY_NAME = {name.casefold(): cid for cid, (name, *_rest) in CITIES.items()}
CITY_BY_NAME.update({'нур-султан': 'astana', 'алма-ата': 'almaty', 'капшагай': 'konaev', 'өскемен': 'ust-kamenogorsk',
                     'орал': 'uralsk', 'қарағанды': 'karaganda', 'ақтөбе': 'aktobe', 'қостанай': 'kostanay'})
SOURCE_NAMES = {'ticketon.kz': 'Ticketon', 'sxodim.com': 'Sxodim', 'kino.kz': 'Kino.kz'}

TICKETON_CATS = ['theatres', 'concerts', 'stand-up', 'museums', 'children', 'sports', 'entertainment', 'shows',
                 'knowledge-skills']  # no "cinema": regular film sessions are not events for the feed
TICKETON_INTEREST = {'theatres': 'THEATER', 'concerts': 'CONCERTS', 'stand-up': 'STANDUP', 'museums': 'EXHIBITIONS',
                     'children': 'KIDS', 'sports': 'SPORT', 'entertainment': 'FESTIVALS', 'shows': 'FESTIVALS',
                     'knowledge-skills': 'FESTIVALS'}
KINO_TYPES = [1, 3, 4, 5, 6, 8, 9, 10, 11]  # exhibitions, concerts, evenings, tours, plays, kids, fun, Punch Club
KEYWORDS = [  # (interest, pattern) on category + title, first match wins
    ('STANDUP', r'стендап|stand.?up|импров|квиз|quiz|юмор|comedy|punch'),
    ('KIDS', r'\bдет|детск|сказк|kids|семейн|мультфильм|мультик|утренник|ёлк|елк'),
    ('SPORT', r'спорт|матч|хоккей|футбол|бокс|забег|марафон|ufc|mma'),
    ('EXHIBITIONS', r'выстав|музе|галере|искусств|exhibit'),
    ('THEATER', r'театр|спектак|балет|опер|мюзикл|теат|драм'),
    ('CONCERTS', r'концерт|оркестр|музык|джаз|\bрок\b|трибьют|tribute|фортепиан|скрипк|вокал'),
    ('OUTDOOR', r'экскурс|тур\b|поход|прогулк'),
    ('BARS', r'вечеринк|party|dj|бар\b'),
    ('CINEMA', r'кино|фильм|cinema'),
    ('IT', r'\bit\b|айти|стартап|хакатон|митап|meetup'),
]
MONTHS = ['янв', 'фев', 'мар', 'апр', 'мая', 'июн', 'июл', 'авг', 'сен', 'окт', 'ноя', 'дек']

TICKETON_JS = r'''await (async () => {
  const cfg = __CFG__, seen = new Set(cfg.seen), P = new DOMParser();
  const out = {site: 'ticketon.kz', scannedAt: new Date().toISOString(), events: [], errors: {}};
  const links = new Map();
  for (const [cid, slug] of Object.entries(cfg.cities)) for (const cat of cfg.cats) for (let p = 1; p <= cfg.maxPages; p++) {
    try {
      const r = await fetch(`/${slug}/${cat}` + (p > 1 ? `?page=${p}` : ''));
      if (!r.ok) { if (p === 1 && r.status !== 404) out.errors[`${slug}/${cat}`] = r.status; break; }
      const d = P.parseFromString(await r.text(), 'text/html'); let n = 0;
      for (const a of d.querySelectorAll('a[href*="/event/"]')) {
        const u = new URL(a.getAttribute('href'), location.origin), k = u.origin + u.pathname;
        if (!links.has(k)) { links.set(k, cid); n++; }
      }
      if (!n) break;
    } catch (e) { out.errors[`${slug}/${cat}`] = String(e).slice(0, 80); break; }
  }
  const todo = [...links].filter(([u]) => !seen.has(u));
  out.found = links.size; out.remaining = Math.max(0, todo.length - cfg.limit);
  const one = async ([u, cid]) => {
    const h = await (await fetch(u)).text();
    const e = [...h.matchAll(/<script type="application\/ld\+json">([\s\S]*?)<\/script>/g)]
      .map(m => { try { return JSON.parse(m[1]); } catch (x) { return null; } })
      .find(x => x && /Event$/.test(x['@type'] || ''));
    if (!e) return {url: u, city: cid, skip: 'no_event_data'};
    const loc = e.location || {}, adr = loc.address || {};
    const prices = [].concat(e.offers || []).map(o => +o.price).filter(x => x > 0);
    return {url: u, city: cid, cat: new URL(u).pathname.split('/')[1], title: e.name, start: e.startDate || '',
      end: e.endDate || '', time: null, dates: [], place: loc.name || '', locality: adr.addressLocality || '',
      address: adr.streetAddress || '', price: prices.length ? Math.min(...prices) : null,
      image: [].concat(e.image || [])[0] || null, desc: (e.description || '').slice(0, 900),
      online: /Online/.test(e.eventAttendanceMode || '')};
  };
  const work = todo.slice(0, cfg.limit);
  for (let i = 0; i < work.length; i += 6) {
    const rs = await Promise.allSettled(work.slice(i, i + 6).map(one));
    rs.forEach((r, j) => r.status === 'fulfilled' ? out.events.push(r.value) : (out.errors[work[i + j][0]] = String(r.reason).slice(0, 80)));
  }
  return out;
})()'''

SXODIM_JS = r'''await (async () => {
  const cfg = __CFG__, seen = new Set(cfg.seen), P = new DOMParser();
  const out = {site: 'sxodim.com', scannedAt: new Date().toISOString(), events: [], errors: {}};
  const links = new Map();
  for (const [cid, slug] of Object.entries(cfg.cities)) for (let p = 1; p <= cfg.maxPages; p++) {
    try {
      const r = await fetch(`/${slug}/afisha` + (p > 1 ? `?page=${p}` : ''));
      if (!r.ok) { if (p === 1) out.errors[slug] = r.status; break; }
      const d = P.parseFromString(await r.text(), 'text/html'); let n = 0;
      for (const a of d.querySelectorAll(`a[href*="/${slug}/event/"]`)) {
        const u = new URL(a.getAttribute('href'), location.origin), k = u.origin + u.pathname;
        if (!links.has(k)) { links.set(k, cid); n++; }
      }
      if (!n) break;
    } catch (e) { out.errors[slug] = String(e).slice(0, 80); break; }
  }
  const todo = [...links].filter(([u]) => !seen.has(u));
  out.found = links.size; out.remaining = Math.max(0, todo.length - cfg.limit);
  const one = async ([u, cid]) => {
    const h = await (await fetch(u)).text();
    const lds = [...h.matchAll(/<script type="application\/ld\+json">([\s\S]*?)<\/script>/g)]
      .map(m => { try { return JSON.parse(m[1]); } catch (x) { return null; } }).filter(Boolean);
    const e = lds.find(x => x['@type'] === 'Event');
    if (!e) return {url: u, city: cid, skip: 'no_event_data'};
    const crumbs = (lds.find(x => x['@type'] === 'BreadcrumbList') || {}).itemListElement || [];
    const d = P.parseFromString(h, 'text/html');
    const head = (d.querySelector('h1') ? d.querySelector('h1').parentElement.parentElement.textContent : '').replace(/\s+/g, ' ');
    const tm = head.match(/(?:^|\s)([01]?\d|2[0-3]):([0-5]\d)(?:\s|$)/);
    const adr = ((e.location || {}).address || {});
    const prices = [].concat(e.offers || []).map(o => +o.price).filter(x => x > 0);
    return {url: u, city: cid, cat: (crumbs[1] || {}).name || '', title: e.name, start: e.startDate || '', end: e.endDate || '',
      time: tm ? `${tm[1].padStart(2, '0')}:${tm[2]}` : null, dates: [], place: '', locality: adr.addressLocality || '',
      address: adr.streetAddress || '', price: prices.length ? Math.min(...prices) : null,
      image: [].concat(e.image || [])[0] || null, desc: (e.description || '').slice(0, 900),
      online: /Online/.test(e.eventAttendanceMode || '')};
  };
  const work = todo.slice(0, cfg.limit);
  for (let i = 0; i < work.length; i += 6) {
    const rs = await Promise.allSettled(work.slice(i, i + 6).map(one));
    rs.forEach((r, j) => r.status === 'fulfilled' ? out.events.push(r.value) : (out.errors[work[i + j][0]] = String(r.reason).slice(0, 80)));
  }
  return out;
})()'''

KINO_JS = r'''await (async () => {
  const cfg = __CFG__, seen = new Set(cfg.seen), P = new DOMParser();
  const out = {site: 'kino.kz', scannedAt: new Date().toISOString(), events: [], errors: {}};
  const trpc = async (proc, json, meta) => {
    const inp = {'0': meta ? {json, meta} : {json}};
    const r = await fetch(`/api/trpc/${proc}?batch=1&input=` + encodeURIComponent(JSON.stringify(inp)));
    return (await r.json())[0].result.data.json;
  };
  const day = d => d.toISOString().slice(0, 10), now = new Date(), until = new Date(now.getTime() + 180 * 864e5);
  const found = new Map();
  const before = document.cookie.split('; ').find(c => c.startsWith('city='));
  try {
    for (const [cid, kid] of Object.entries(cfg.cities)) {
      document.cookie = `city=${kid}; path=/`;
      for (const t of cfg.types) {
        try {
          const list = await trpc('events.findEvents', {startDate: day(now), endDate: day(until), eventTypeId: t, lang: null},
                                  {values: {lang: ['undefined']}, v: 1});
          for (const e of list) {
            const u = `https://kino.kz/ru/concert/event/${e.id}`;
            if (!found.has(u)) found.set(u, {e, cid});
          }
        } catch (x) { out.errors[`${cid}/${t}`] = String(x).slice(0, 80); }
      }
    }
  } finally { document.cookie = before ? `${before}; path=/` : 'city=; path=/; max-age=0'; }
  const todo = [...found].filter(([u]) => !seen.has(u));
  out.found = found.size; out.remaining = Math.max(0, todo.length - cfg.limit);
  const one = async ([u, {e, cid}]) => {
    const sessions = await trpc('events.getSessions', {id: e.id});
    const h = await (await fetch(u)).text();
    const text = (P.parseFromString(h, 'text/html').body.textContent || '').replace(/\s+/g, ' ');
    const m = text.match(/Описание\s*(.{20,900}?)(?:Похожие|Возрастное|Купить билет|$)/);
    const dates = [...new Set(sessions.map(s => s.event_date))].sort();
    const times = [...new Set(sessions.map(s => s.event_time).filter(Boolean))];
    return {url: u, city: cid, cat: `${e.event_type_name || ''} ${e.partner_type_name || ''}`.trim(), title: e.name,
      start: dates[0] || '', end: dates[dates.length - 1] || '', time: times.length === 1 ? times[0] : null, dates,
      place: e.partner_name || '', locality: e.partner_city_name || '', address: '', lat: e.latitude || null,
      lon: e.longitude || null, price: e.price_from || null, image: e.small_poster || null,
      desc: m ? m[1].trim() : '', online: false};
  };
  const work = todo.slice(0, cfg.limit);
  for (let i = 0; i < work.length; i += 4) {
    const rs = await Promise.allSettled(work.slice(i, i + 4).map(one));
    rs.forEach((r, j) => r.status === 'fulfilled' ? out.events.push(r.value) : (out.errors[work[i + j][0]] = String(r.reason).slice(0, 80)));
  }
  return out;
})()'''


def load_seen() -> dict:
    return tg.load(SEEN, {}) or {}


def js(site: str, limit: int = DEFAULT_LIMIT, bg: bool = False) -> str:
    seen = sorted(load_seen().get(site, {}))
    if site == 'ticketon.kz':
        cfg = {'cities': {c: v[1] for c, v in CITIES.items() if v[1]}, 'cats': TICKETON_CATS, 'maxPages': 15}
        tpl = TICKETON_JS
    elif site == 'sxodim.com':
        cfg = {'cities': {c: v[2] for c, v in CITIES.items() if v[2]}, 'maxPages': 25}
        tpl = SXODIM_JS
    elif site == 'kino.kz':
        cfg = {'cities': {c: v[3] for c, v in CITIES.items() if v[3]}, 'types': KINO_TYPES}
        tpl = KINO_JS
    else:
        raise SystemExit(f'unknown site {site}')
    cfg.update(seen=seen, limit=limit)
    code = tpl.replace('__CFG__', json.dumps(cfg, ensure_ascii=False))
    if not bg: return code
    # Фоновый режим: вызов javascript_tool ограничен 45 с, поэтому скан запускается
    # в window.__ws[site] и сразу возвращает 'started'; потом опрашивают POLL_JS/TAKE_JS.
    key = json.dumps(site)
    body = code[len('await '):] if code.startswith('await ') else code
    return ("window.__ws = window.__ws || {}; window.__ws[%s] = {status: 'running', t0: Date.now()}; "
            "%s.then(r => window.__ws[%s] = {status: 'done', t0: window.__ws[%s].t0, t1: Date.now(), result: r})"
            ".catch(e => window.__ws[%s] = {status: 'error', error: String(e)}); 'started'") % (key, body, key, key, key)


POLL_JS = ("Object.fromEntries(Object.entries(window.__ws || {}).map(([k, v]) => [k, {status: v.status, "
           "sec: Math.round(((v.t1 || Date.now()) - v.t0) / 1000), events: v.result ? v.result.events.length : null, "
           "error: v.error || null}]))")
# Результат отдают кусками ~130 000 символов, добитыми пробелами до этой длины: такой ответ harness всегда
# сохраняет в файл и не показывает в контексте, а мост устройства ещё не обрезает (обрезает после ~170 000).
TAKE_JS = r"""(() => {
  const site = %s, n = %d, r = window.__ws[site].result, today = new Date().toISOString().slice(0, 10);
  if (!window.__wsc || window.__wsc.site !== site) {
    const evs = r.events.filter(e => e.skip || (e.end || e.start || '9999').slice(0, 10) >= today)
      .map(e => ({...e, desc: (e.desc || '').replace(/\s+/g, ' ').slice(0, 500)}));
    const ch = []; let cur = [], size = 0;
    for (const e of evs) { const l = JSON.stringify(e).length; if (size + l > 120000 && cur.length) { ch.push(cur); cur = []; size = 0; } cur.push(e); size += l; }
    ch.push(cur); window.__wsc = {site, ch};
  }
  const ch = window.__wsc.ch, {events, ...meta} = r;
  const s = JSON.stringify({...meta, chunk: n, of: ch.length, events: ch[n] || []});
  return s + ' '.repeat(Math.max(0, 130000 - s.length));
})()"""


def _day(iso: str) -> dt.date | None:
    try:
        return dt.date.fromisoformat((iso or '')[:10])
    except ValueError:
        return None


def _label(d: dt.date) -> str:
    return f'{d.day} {MONTHS[d.month - 1]}'


def starts_at(ev: dict, today: dt.date) -> str:
    """Human startsAt in the feed's style ("13 ноя, 19:00", "29 окт – 4 дек", "12 ноя, 26 ноя, 20:00")."""
    time = ev.get('time')
    raw = ev.get('start') or ''
    if not time and 'T' in raw:
        try:
            stamp = dt.datetime.fromisoformat(raw.replace('Z', '+00:00'))
            if stamp.tzinfo: stamp = stamp.astimezone(tg.ZONE)
            if (stamp.hour, stamp.minute) != (0, 0): time = stamp.strftime('%H:%M')
        except ValueError:
            pass
    dates = [d for d in (_day(x) for x in ev.get('dates') or []) if d and d >= today]
    first, last = _day(ev.get('start')), _day(ev.get('end'))
    if dates:
        # many sessions (daily exhibitions, tours) read better as a range than as a long list
        text = ', '.join(_label(d) for d in dates) if len(dates) <= 4 else f'{_label(dates[0])} – {_label(dates[-1])}'
    elif first and last and last > first:
        first = max(first, today)  # a run that started months ago is shown from today
        text = f'{_label(first)} – {_label(last)}' if last > first else _label(last)
    elif first:
        text = _label(first)
    else:
        return ''
    return f'{text}, {time}' if time else text


def interest(site: str, ev: dict) -> str:
    if site == 'ticketon.kz' and ev.get('cat') in TICKETON_INTEREST and ev['cat'] not in ('entertainment', 'shows', 'knowledge-skills'):
        return TICKETON_INTEREST[ev['cat']]
    for hay in (ev.get('cat', '') or '', ev.get('title', '') or ''):  # the site's category wins over title words
        for value, pattern in KEYWORDS:
            if re.search(pattern, hay.casefold()): return value
    return TICKETON_INTEREST.get(ev.get('cat'), 'FESTIVALS')


def coords(lat, lon) -> dict:
    """Kazakhstan lies at lat 40–56, lon 46–88; some sources swap the two, others give junk."""
    try:
        lat, lon = float(lat), float(lon)
    except (TypeError, ValueError):
        return {'lat': None, 'lon': None}
    if 40 <= lon <= 56 and 46 <= lat <= 88: lat, lon = lon, lat
    if not (40 <= lat <= 56 and 46 <= lon <= 88): return {'lat': None, 'lon': None}
    return {'lat': lat, 'lon': lon}


def price_text(value) -> str | None:
    try:
        amount = int(float(value))
    except (TypeError, ValueError):
        return None
    return f'от {amount:,} ₸'.replace(',', ' ') if amount > 0 else None


def clean(text: str) -> str:
    text = re.sub(r'<[^>]+>', ' ', text or '')
    return re.sub(r'[ \t]+', ' ', text).strip()


def to_card(site: str, ev: dict, today: dt.date) -> tuple[dict | None, str]:
    if ev.get('skip'): return None, ev['skip']
    if ev.get('online'): return None, 'online'
    city = CITY_BY_NAME.get((ev.get('locality') or '').strip().casefold()) or ev.get('city')
    if city not in CITIES: return None, 'city'
    place, address = (ev.get('place') or '').strip(), clean(ev.get('address') or '')
    if site == 'sxodim.com' and address:  # "Venue, street, house"
        place, _, address = address.partition(',')
        place, address = place.strip(), address.strip()
    title = clean(ev.get('title') or '')
    st = starts_at(ev, today)
    card = {
        'id': f"web-{site.split('.')[0]}-{hashlib.sha1(ev['url'].encode()).hexdigest()[:10]}",
        'cityId': city, 'interest': interest(site, ev), 'reference': today.isoformat(),
        'titleRu': title, 'startsAt': st, 'placeRu': place, 'address': address,
        'price': price_text(ev.get('price')), 'sourceUrl': ev['url'], 'sourceName': SOURCE_NAMES[site],
        'descriptionRu': clean(ev.get('desc') or '') or title, **coords(ev.get('lat'), ev.get('lon')),
    }
    if ev.get('image'): card['imageUrl'] = ev['image']
    return card, ''


def _words(s: str) -> set[str]:
    return {w for w in re.findall(r'\w+', (s or '').casefold()) if len(w) > 2 and w not in {'астане', 'алматы', 'астана', 'концерт'}}


def near_duplicate(card: dict, events: list[dict]) -> bool:
    """Same event announced on two ticket sites: same city and first day, titles mostly the same words."""
    import schedule
    probe = dict(card); schedule.annotate(probe, dt.date.fromisoformat(card['reference']), overwrite=True)
    words = _words(card['titleRu'])
    if not words or not probe.get('dateFrom'): return False
    for e in events:
        if e.get('cityId') != card['cityId'] or e.get('dateFrom') != probe['dateFrom']: continue
        other = _words(e.get('titleRu', ''))
        if other and len(words & other) / min(len(words), len(other)) >= 0.6: return True
    return False


def import_scans(paths: list[str], today: dt.date | None = None) -> dict:
    import browser_scan as bs
    today = today or dt.datetime.now(tg.ZONE).date()
    seen = load_seen()
    feed = tg.load(bs.FEED)
    cards, skipped, sites = [], {}, {}
    for path in paths:
        scan = json.loads(Path(path).read_text(encoding='utf-8'))
        site = scan['site']
        bucket = seen.setdefault(site, {})
        sites[site] = {'found': scan.get('found'), 'remaining': scan.get('remaining'), 'errors': len(scan.get('errors') or {})}
        for ev in scan.get('events', []):
            bucket[ev['url']] = today.isoformat()
            card, why = to_card(site, ev, today)
            if not card: skipped[ev['url']] = why; continue
            if near_duplicate(card, feed['events'] + cards): skipped[ev['url']] = 'duplicate_other_site'; continue
            cards.append(card)
    with tempfile.NamedTemporaryFile('w', suffix='.json', delete=False, encoding='utf-8') as tmp:
        json.dump(cards, tmp, ensure_ascii=False)
    result = bs.add_cards(tmp.name) if cards else {'added': [], 'skipped': {}}
    Path(tmp.name).unlink(missing_ok=True)
    limit = (today - dt.timedelta(days=SEEN_KEEP_DAYS)).isoformat()
    for bucket in seen.values():
        for url in [u for u, d in bucket.items() if d < limit]: del bucket[url]
    tg.save(SEEN, seen)
    skipped.update(result['skipped'])
    return {'added': result['added'], 'skipped': skipped, 'sites': sites}


if __name__ == '__main__':
    cmd, args = (sys.argv[1], sys.argv[2:]) if len(sys.argv) > 1 else ('', [])
    if cmd == 'js':
        bg = '--bg' in args; args = [a for a in args if a != '--bg']
        print(js(args[0], int(args[1]) if len(args) > 1 else DEFAULT_LIMIT, bg))
    elif cmd == 'poll': print(POLL_JS)
    elif cmd == 'take': print(TAKE_JS % (json.dumps(args[0]), int(args[1]) if len(args) > 1 else 0))
    elif cmd == 'import':
        r = import_scans(args)
        reasons = {}
        for why in r['skipped'].values(): reasons[why] = reasons.get(why, 0) + 1
        print(json.dumps({'added': r['added'], 'skippedByReason': reasons, 'sites': r['sites']}, ensure_ascii=False, indent=1))
    elif cmd == 'cities': print(json.dumps(CITIES, ensure_ascii=False, indent=1))
    else:
        print(__doc__); raise SystemExit(2)
