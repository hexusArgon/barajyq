#!/usr/bin/env python3
"""Machine-readable schedule for feed cards.

Cards keep the human `startsAt` text from the source ("7 ноя – 30 янв, 18:30"). This module adds
structured fields next to it so the app can hide past events, filter by day and sort by date:

    dateFrom  "2026-11-07"   first day (ISO)
    dateTo    "2027-01-30"   last day (ISO); the card is expired after this day
    dates     ["2026-11-12", "2026-11-26"]   discrete days; empty for a continuous run
    time      "18:30" | null start time, Asia/Qyzylorda (UTC+5)

Display dates have no year. The year is resolved ONCE, at import time, against the date the
announcement was collected/published (`reference`), never against the clock of a later run:
a day more than ROLLBACK_DAYS before the reference belongs to the next year, later days in the
same string never go backwards in time.
"""
from __future__ import annotations
import datetime as dt
import re

ROLLBACK_DAYS = 60
MONTHS = {'янв': 1, 'фев': 2, 'мар': 3, 'апр': 4, 'мая': 5, 'май': 5, 'июн': 6, 'июл': 7, 'авг': 8,
          'сен': 9, 'окт': 10, 'ноя': 11, 'дек': 12}
FIELDS = ('dateFrom', 'dateTo', 'dates', 'time')
_TIME = re.compile(r'(?<!\d)([01]?\d|2[0-3]):([0-5]\d)(?!\d)')
_DAY = re.compile(r'^(\d{1,2})(?:\s+([а-я]+))?$')
_RANGE = re.compile(r'\s*[–—-]\s*')
_ISO = re.compile(r'^\d{4}-\d{2}-\d{2}')


def _month(word):
    if not word: return None
    return MONTHS.get(word[:3])


def parse(starts_at: str, reference: dt.date):
    """Returns a dict with FIELDS, or None when the text has no recognisable day."""
    raw = (starts_at or '').strip()
    if not raw: return None
    if _ISO.match(raw):
        try:
            if 'T' in raw:
                stamp = dt.datetime.fromisoformat(raw.replace('Z', '+00:00'))
                if stamp.tzinfo is not None:
                    stamp = stamp.astimezone(dt.timezone(dt.timedelta(hours=5)))
                day, time = stamp.date(), stamp.strftime('%H:%M')
            else:
                day, time = dt.date.fromisoformat(raw[:10]), None
        except ValueError:
            return None
        return {'dateFrom': day.isoformat(), 'dateTo': day.isoformat(), 'dates': [day.isoformat()], 'time': time}

    times = {f'{int(h):02d}:{m}' for h, m in _TIME.findall(raw)}
    time = next(iter(times)) if len(times) == 1 else None
    body = _TIME.sub('', raw).lower()
    # Tokens: either "D [MON]" or "D [MON] – D MON" (a continuous run).
    tokens = []  # (kind, [(day, month|None), ...])
    for part in [p.strip() for p in re.split(r',', body) if p.strip()]:
        ends = [e.strip() for e in _RANGE.split(part) if e.strip()]
        parsed = []
        for end in ends:
            m = _DAY.match(end)
            if not m: return None
            parsed.append((int(m.group(1)), _month(m.group(2))))
            if m.group(2) and parsed[-1][1] is None: return None
        if len(parsed) == 1: tokens.append(('day', parsed))
        elif len(parsed) == 2: tokens.append(('range', parsed))
        else: return None
    if not tokens: return None
    # A bare day borrows the month of the next mentioned day: "13–14 ноя", "13, 14 ноя".
    flat = [pair for _, pairs in tokens for pair in pairs]
    month = None
    for i in range(len(flat) - 1, -1, -1):
        d, m = flat[i]
        if m is None:
            if month is None: return None
            flat[i] = (d, month)
        else:
            month = m
    # Resolve years in reading order.
    resolved, previous = [], None
    for d, m in flat:
        try:
            if previous is None:
                year = reference.year
                day = dt.date(year, m, d)
                if day < reference - dt.timedelta(days=ROLLBACK_DAYS): day = dt.date(year + 1, m, d)
            else:
                day = dt.date(previous.year, m, d)
                if day < previous: day = dt.date(previous.year + 1, m, d)
        except ValueError:
            return None
        resolved.append(day)
        previous = day
    has_range = any(kind == 'range' for kind, _ in tokens)
    days = sorted(set(resolved))
    return {
        'dateFrom': days[0].isoformat(),
        'dateTo': days[-1].isoformat(),
        'dates': [] if has_range else [d.isoformat() for d in days],
        'time': time,
    }


def annotate(card: dict, reference: dt.date, overwrite: bool = False) -> bool:
    """Adds schedule fields to an event card in place. Returns True when fields were written."""
    if card.get('isPlace'): return False
    if not overwrite and card.get('dateTo'): return False
    fields = parse(card.get('startsAt', ''), reference)
    if not fields: return False
    card.update(fields)
    return True
