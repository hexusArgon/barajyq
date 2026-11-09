"""Shared conservative event identity policy: roundups are not event cards."""
import json
from pathlib import Path
import re

POLICY_PATH = Path(__file__).with_name('event_policy.json')
POLICY = json.loads(POLICY_PATH.read_text(encoding='utf-8'))

def roundup_reason(title, text='', starts=''):
    first_line = next((line for line in (text or '').splitlines() if line.strip()), '')
    headings = [re.sub(r'^\W+', '', h.strip(), flags=re.UNICODE) for h in (title or '', first_line)]
    if any(re.search(pattern, heading, re.I) for heading in headings for pattern in POLICY['roundupTitlePatterns']):
        return 'roundup_not_single_event'
    if not starts and any(marker in (text or '').lower() for marker in POLICY['imageOnlyMarkers']):
        return 'image_only_without_event_details'
    return None

def rejected(card):
    if card.get('isPlace', False): return None
    if card.get('id') in POLICY['rejectedIds'] or card.get('sourceUrl') in POLICY['rejectedUrls']:
        return 'previously_rejected_roundup'
    return roundup_reason(card.get('titleRu',''),card.get('descriptionRu',''),card.get('startsAt',''))

def import_reason(card):
    reason = rejected(card)
    if reason: return reason
    if not card.get('startsAt','').strip(): return 'missing_event_date_review'
    place=card.get('placeRu','').strip()
    if not place or place.casefold() in {card.get('sourceName','').removeprefix('Instagram @').casefold()}:
        return 'missing_event_venue_review'
    return None

def normalize_price(value):
    if value is None: return None
    value=str(value).strip()
    return None if not value or value.lower() in {'null','none'} else value

def keep_canonical(row):
    return not row.get('ev',False) or not rejected({'id':row['id'],'sourceUrl':row['url'],
        'titleRu':row['t'][0],'descriptionRu':row['d'][0],'startsAt':row['st']})
