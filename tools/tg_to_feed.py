#!/usr/bin/env python3
"""Conservative, unauthenticated official Telegram public-preview importer."""
from __future__ import annotations
import argparse
from collections import Counter
import datetime as dt
import fcntl
import json
import os
from pathlib import Path
import re
import tempfile
import time
import urllib.request
from bs4 import BeautifulSoup
from event_policy import roundup_reason, import_reason
import schedule

ROOT = Path(__file__).resolve().parents[1]
ZONE = dt.timezone(dt.timedelta(hours=5))
INTERESTS = {'CONCERTS','THEATER','EXHIBITIONS','CINEMA','STANDUP','BARS','SPORT','OUTDOOR','IT','KIDS','FESTIVALS','FREE'}
MONTHS = ['января','февраля','марта','апреля','мая','июня','июля','августа','сентября','октября','ноября','декабря']
# IDs already present in Android KazakhstanCities, no new/default geography.
CITY_PATTERNS = {
    'astana': r'\b(?:астан[аеуы]?|astana)\b',
    'almaty': r'\b(?:алматы|алмата|алма-ата|almaty)\b',
    'shymkent': r'\b(?:шымкент[ае]?|shymkent)\b',
    'karaganda': r'\b(?:караганд[аеуы]|қарағанды|karaganda)\b',
    'kokshetau': r'\b(?:кокшетау|көкшетау|kokshetau)\b',
    'pavlodar': r'\b(?:павлодар[ае]?|pavlodar)\b',
    'uralsk': r'\b(?:уральск[ае]?|орал|oral|uralsk)\b',
    'ust-kamenogorsk': r'\b(?:усть[- ]каменогорск[ае]?|өскемен|оскемен|oskemen|ust-kamenogorsk)\b',
}


def load(path, default=None):
    return json.loads(path.read_text(encoding='utf-8')) if path.exists() else default

def save(path, data):
    raw = (json.dumps(data, ensure_ascii=False, indent=2) + '\n').encode()
    if path.exists() and path.read_bytes() == raw:
        return
    path.parent.mkdir(parents=True, exist_ok=True)
    fd, tmp = tempfile.mkstemp(dir=path.parent)
    try:
        with os.fdopen(fd, 'wb') as f:
            f.write(raw); f.flush(); os.fsync(f.fileno())
        os.replace(tmp, path)
    finally:
        if os.path.exists(tmp): os.unlink(tmp)

def fetch(url):
    # Only constructed official public URLs, no credentials/cookies/proxies.
    last = None
    for attempt in range(3):
        try:
            req = urllib.request.Request(url, headers={'User-Agent': 'BarajyqPublicFeed/1.0'})
            with urllib.request.urlopen(req, timeout=25) as r:
                if not r.url.startswith('https://t.me/s/'):
                    raise ValueError('Public preview redirected away from /s/')
                raw = r.read(4_000_001)
                if len(raw) > 4_000_000: raise ValueError('Oversized public preview')
                return raw.decode('utf-8')
        except Exception as exc:
            last = exc
            # Do not retry access denials / rate limits.
            if getattr(exc, 'code', 0) in (401,403,429): break
            if attempt < 2: time.sleep(attempt + 1)
    raise RuntimeError(str(last))

def parse_page(html, handle):
    soup = BeautifulSoup(html, 'html.parser')
    posts = {}
    for node in soup.select('.tgme_widget_message[data-post]'):
        key = node['data-post']
        if not re.fullmatch(re.escape(handle) + r'/\d+', key, re.I): continue
        body = node.select_one('.tgme_widget_message_text')
        text = ''
        if body:
            for br in body.select('br'): br.replace_with('\n')
            text = body.get_text().strip()
        stamp = node.select_one('time[datetime]')
        posts[key] = {'sourceUrl': 'https://t.me/' + key, 'text': text,
                      'publishedAt': stamp['datetime'] if stamp else None,
                      'links': [a.get('href','') for a in body.select('a[href]')] if body else []}
    if not posts:
        raise ValueError('No public message widgets: inaccessible/empty channel or changed markup')
    return list(posts.values())

def scan(handle, pages):
    posts = {}
    before = None
    for _ in range(pages):
        url = f'https://t.me/s/{handle}' + (f'?before={before}' if before else '')
        batch = parse_page(fetch(url), handle)
        new = {p['sourceUrl']: p for p in batch if p['sourceUrl'] not in posts}
        if not new: break
        posts.update(new)
        oldest = min(int(p['sourceUrl'].rsplit('/',1)[1]) for p in batch)
        if oldest <= 1 or oldest == before: break
        before = oldest
    return list(posts.values())

# Event wording a post must contain (also used by the browser pre-filter, tools/browser_scan.py).
EVENT_WORDS = r'лекци|квиз|концерт|встреч|спектак|выставк|митап|мероприяти|презентаци|дәріс|кездесу|іс-шара'
KK_MONTHS = ['қаңтар','ақпан','наурыз','сәуір','мамыр','маусым','шілде','тамыз','қыркүйек','қазан','қараша','желтоқсан']
YEAR_ROLLOVER_DAYS = 14

def published_date(stamp):
    if not stamp: return None
    try: return dt.datetime.fromisoformat(stamp.replace('Z','+00:00')).astimezone(ZONE).date()
    except ValueError: return None

def event_day(text, published_at):
    """Exactly one distinct event day. Returns (date, None) or (None, review_reason)."""
    low = text.lower()
    found = []  # (day, month, year|None)
    found += [(d,m,y) for d,m,y in re.findall(r'(?<![\d.])(\d{1,2})[./](\d{1,2})[./](20\d{2})(?![\d.])', text)]
    found += [(d,m,y) for y,m,d in re.findall(r'(?<!\d)(20\d{2})-(\d{2})-(\d{2})(?!\d)', text)]
    found += [(d,MONTHS.index(m)+1,y or None) for d,m,y in re.findall(
        r'(?<!\d)(\d{1,2})\s+('+'|'.join(MONTHS)+r')(?:\s+(20\d{2}))?(?!\d)', low)]
    found += [(d,KK_MONTHS.index(m)+1,y or None) for d,m,y in re.findall(
        r'(?<!\d)(\d{1,2})\s+('+'|'.join(KK_MONTHS)+r')[а-яәіңғүұқөһ]*(?:\s+(20\d{2}))?(?!\d)', low)]
    # Short numeric "22.11" only when it cannot be a time, price or part of a full date.
    found += [(d,m,None) for d,m in re.findall(r'(?<![\d.:,])(\d{1,2})\.(\d{1,2})(?![\d.:]|,\d)', text)
              if 1 <= int(m) <= 12 and 1 <= int(d) <= 31]
    if not found: return None, 'missing_or_multiple_explicit_dates_review'
    pub = published_date(published_at)
    days = set()
    for d,m,y in found:
        try:
            if y: days.add(dt.date(int(y),int(m),int(d))); continue
            if pub is None: return None, 'missing_year_without_publication_date_review'
            day = dt.date(pub.year,int(m),int(d))
            if day < pub - dt.timedelta(days=YEAR_ROLLOVER_DAYS): day = dt.date(pub.year+1,int(m),int(d))
            days.add(day)
        except ValueError:
            return None, 'invalid_date_review'
    if len(days) != 1: return None, 'missing_or_multiple_explicit_dates_review'
    return next(iter(days)), None

def event_times(text):
    """Distinct start times; a range "19:00–21:00" counts as its start only."""
    ranged = re.sub(r'(?<!\d)([012]?\d:[0-5]\d)\s*(?:-|–|—|до)\s*[012]?\d:[0-5]\d(?!\d)', r'\1', text)
    times = {(str(int(h)),mi) for h,mi in re.findall(r'(?<!\d)([012]?\d):([0-5]\d)(?!\d)', ranged)}
    if len(times) > 1:
        # "Сбор: 18:30 | Начало: 19:00" — an explicit start label wins over doors/registration times.
        start = set(re.findall(r'(?:начало|старт|басталуы|start)\w*\s*(?:в|at|:)?\s*([012]?\d):([0-5]\d)', ranged, re.I))
        if len(start) == 1: return {(str(int(h)), mi) for h, mi in start}
    return times

# Generic channels (interest FESTIVALS) post concerts, plays, lectures… A strong keyword in the
# announcement picks a more precise category; specific channel categories are kept as configured.
INTEREST_KEYWORDS = [
    ('FESTIVALS', r'лекци|дәріс|lecture'),  # talks stay in the generic category even when about theatre
    ('STANDUP', r'стендап|stand-?up|квиз|quiz'),
    ('CONCERTS', r'концерт|\bтур\b|альбом|сольн|оркестр'),
    ('THEATER', r'спектакл|постановк|\bопер[аы]\b|балет|театр|қойылым'),
    ('EXHIBITIONS', r'выставк|экспозиц|көрме'),
    ('CINEMA', r'кинопоказ|кино-?вечер|показ фильма'),
    ('IT', r'митап|meetup|хакатон|hackathon'),
    ('OUTDOOR', r'забег|поход\b|трекинг|хайкинг'),
]

def refine_interest(default, text):
    if default != 'FESTIVALS': return default
    low = text.lower()
    return next((interest for interest, pattern in INTEREST_KEYWORDS if re.search(pattern, low)), default)

VENUE_LABEL = r'(?:Место(?:\s+проведения)?|Адрес|Где|Локация|Площадка|Мекенжай|Өтетін\s+орны|Venue|Location|Where)'

def venue_line(text):
    """Venue from a 📍 line or a labelled line; a bare link (2GIS short URL) is not a venue."""
    candidates = [m.strip() for m in re.findall(r'📍\ufe0f?\s*:?\s*([^\n]+)', text)]
    candidates += [m.strip() for m in re.findall(r'(?:^|\n)[^\w\n]*' + VENUE_LABEL + r'\s*[:\-–—]\s*([^\n]+)', text, re.I)]
    for c in candidates:
        c = re.sub(r'^(?:' + VENUE_LABEL + r')\s*[:\-–—]\s*', '', c, flags=re.I).strip(' .')
        if c and not re.fullmatch(r'(?:https?://|@)\S+', c): return c
    return None

def classify(post, source, now):
    text = post['text']
    low = text.lower()
    if not text: return None, 'media_only'
    reason = roundup_reason(text.splitlines()[0], text)
    if reason: return None, reason
    # Conservative exclusion: foreign itineraries / online announcements need review.
    if re.search(r'\b(?:москв[аеуы]|петербург[ае]?|росси[яию]|бишкек[ае]?|кыргызстан|ташкент[ае]?|узбекистан|грузи[яию]|тбилиси|турци[яию]|стамбул[ае]?|дубай|дубае|дубаи|dubai|russia|georgia|kyrgyzstan|uzbekistan|turkey)\b', low):
        return None, 'foreign_geography_review'
    if re.search(r'\b(?:онлайн|online|zoom|вебинар)\b', low):
        return None, 'online_or_hybrid_review'
    if re.search(r'переносим|переносится|перенесен|перенесён|отменяется|отменено|отменили|как прош|вспоминаем|ауыстыр', low):
        return None, 'cancellation_or_recap_review'
    if not re.search(EVENT_WORDS, low):
        return None, 'not_event'
    # Event year: explicit in the text, otherwise taken from the POST publication date (never the
    # system clock). A date more than YEAR_ROLLOVER_DAYS before publication belongs to next year
    # (a December post about "15 января"). Channel city is never used as an event fact.
    day, reason = event_day(text, post.get('publishedAt'))
    if reason: return None, reason
    if day < now.date(): return None, 'past'
    times = event_times(text)
    if len(times) != 1: return None, 'missing_or_multiple_times_review'
    hour, minute = next(iter(times))
    if int(hour)>23: return None, 'invalid_time_review'
    start = dt.datetime.combine(day,dt.time(int(hour),int(minute)),ZONE)
    if start <= now: return None, 'past'
    cities = set()
    for city, pattern in CITY_PATTERNS.items():
        if re.search(pattern, low) or any(re.search(r'^https://(?:www\.)?(?:sxodim\.com|2gis\.kz)/'+city+r'/', u.lower()) for u in post['links']): cities.add(city)
    # A channel configured for exactly one city (researched, see tg_sources_audit.json) is local:
    # a post naming no city is about that city. Posts naming another city are still rejected below.
    if not cities and len(source.get('allowedCities') or []) == 1: cities = set(source['allowedCities'])
    if len(cities)!=1: return None, 'missing_or_multiple_cities_review'
    if source.get('allowedCities') and next(iter(cities)) not in source['allowedCities']:
        return None, 'city_outside_source_filter_review'
    place = venue_line(text)
    if not place: return None, 'missing_place_review'
    if source.get('interest') not in INTERESTS: return None, 'missing_category_review'
    # First line with real words: posts often open with "❗️❗️❗️" or hashtags.
    title = next((line.strip() for line in text.splitlines()
                  if not re.match(r'\s*[📅🗓⏰🕖📍⭕]', line)
                  and len(re.findall(r'[^\W\d_]', re.sub(r'#\w+', '', line))) >= 3), '')
    if len(title)>180: return None, 'missing_short_title_review'
    url = post['sourceUrl']
    card = dict(id='tg-'+url.removeprefix('https://t.me/').replace('/','-'),
                cityId=next(iter(cities)), interest=refine_interest(source['interest'], text), startsAt=start.isoformat(),
                address='', price=None, sourceUrl=url, sourceName='Telegram @'+source['handle'],
                workingHours='', lat=None, lon=None, isPlace=False)
    for lang in ('Ru','Kk','En'):
        # Schema requires all three; these are originals, NOT claimed translations.
        card['title'+lang]=title; card['place'+lang]=place; card['description'+lang]=text
    schedule.annotate(card, day)  # ISO start: no year resolution involved
    reason = import_reason(card)
    return (None, reason) if reason else (card, 'candidate')

def norm(s): return re.sub(r'[^\w]+','',s.casefold())

def duplicate(card, events):
    for e in events:
        if e['id']==card['id'] or e['sourceUrl'].rstrip('/')==card['sourceUrl']: return True
        if e.get('cityId') != card['cityId']: continue
        if e.get('descriptionRu') == card['descriptionRu']: return True
        # Exact date/time + title + venue only: never fuzzy-merge recurring shows.
        if (e.get('startsAt')==card['startsAt'] and norm(e.get('titleRu',''))==norm(card['titleRu'])
                and norm(e.get('placeRu',''))==norm(card['placeRu'])): return True
    return False

def canonical(c):
    return dict(id=c['id'], city=c['cityId'], ev=True, interest=c['interest'],
                t=[c['title'+l] for l in ('Ru','Kk','En')], st=c['startsAt'],
                p=[c['place'+l] for l in ('Ru','Kk','En')], addr=c['address'], price=c['price'],
                url=c['sourceUrl'], src=c['sourceName'], hours=c['workingHours'],lat=c['lat'],lon=c['lon'],
                d=[c['description'+l] for l in ('Ru','Kk','En')])

def run(root=ROOT, scanner=scan, now=None):
    now = now or dt.datetime.now(ZONE)
    with (root/'tools/.tg_import.lock').open('w') as lock:
        fcntl.flock(lock,fcntl.LOCK_EX)
        config=load(root/'tools/tg_sources.json')
        feed=load(root/'feed/events.json')
        data=load(root/'tools/feed_data.json')
        staging=load(root/'tools/tg_review.json', {'posts':{}})
        errors=[]; added=[]; channel_results=[]
        for source in config['channels']:
            if not source.get('enabled',True): continue
            handle=source['handle']
            if not re.fullmatch(r'[A-Za-z][A-Za-z0-9_]{3,31}',handle): raise ValueError('Invalid channel handle')
            print('scan',handle,flush=True)
            try: posts=scanner(handle,max(1,min(int(config.get('maxPages',3)),10)))
            except Exception as exc:
                errors.append({'channel':handle,'error':str(exc)}); print('error',handle,str(exc),flush=True); continue
            statuses=Counter()
            for post in posts:
                card,reason=classify(post,source,now)
                if card:
                    if duplicate(card,feed['events']) or any(e['sourceUrl'].rstrip('/') in {u.rstrip('/') for u in post['links']} for e in feed['events']): reason='duplicate'
                    else:
                        feed['events'].append(card); added.append(card['sourceUrl']); reason='imported'
                staging['posts'][post['sourceUrl']]={**post,'status':reason}
                statuses[reason]+=1
            channel_results.append({'channel':handle,'posts':len(posts),'statuses':dict(statuses)})
            print('done',handle,len(posts),dict(statuses),flush=True)
        # Repair canonical/asset writes on next run even after interrupted multi-file write.
        ids={x['id'] for x in data}
        for card in feed['events']:
            if card['id'].startswith('tg-') and card['id'] not in ids:
                data.append(canonical(card)); ids.add(card['id'])
        if added: feed['updatedAt']=now.isoformat()
        save(root/'tools/feed_data.json',data)
        save(root/'feed/events.json',feed)
        save(root/'android/app/src/main/assets/events.json',feed)
        save(root/'tools/tg_review.json',staging)
        result={'checkedAt':now.isoformat(),'added':added,'errors':errors,
                'stagedPosts':len(staging['posts']),'channels':channel_results}
        save(root/'tools/tg_import_result.json',result)
        return result

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__); parser.parse_args()
    result=run(); print(json.dumps(result,ensure_ascii=False,indent=2))
    raise SystemExit(1 if result['errors'] else 0)
