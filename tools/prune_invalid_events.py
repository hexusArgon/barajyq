#!/usr/bin/env python3
"""Idempotently remove rejected roundups from feed/assets/canonical data only."""
import json
from pathlib import Path
from event_policy import rejected, keep_canonical, normalize_price
ROOT=Path(__file__).resolve().parents[1]

def prune(root=ROOT):
    feed_path=root/'feed/events.json'
    feed=json.loads(feed_path.read_text(encoding='utf-8'))
    removed=[{'id':c['id'],'title':c['titleRu'],'reason':rejected(c)} for c in feed['events'] if rejected(c)]
    feed['events']=[c for c in feed['events'] if not rejected(c)]
    for card in feed['events']+feed['places']: card['price']=normalize_price(card.get('price'))
    canonical_path=root/'tools/feed_data.json'
    rows=json.loads(canonical_path.read_text(encoding='utf-8'))
    canonical_removed=[c['id'] for c in rows if not keep_canonical(c)]
    rows=[c for c in rows if keep_canonical(c)]
    for row in rows: row['price']=normalize_price(row.get('price'))
    def save(path,value):
        raw=(json.dumps(value,ensure_ascii=False,indent=2)+'\n').encode()
        if not path.exists() or path.read_bytes()!=raw: path.write_bytes(raw)
    save(feed_path,feed)
    asset=root/'android/app/src/main/assets/events.json'
    asset.write_bytes(feed_path.read_bytes())
    save(canonical_path,rows)
    return {'removed':removed,'canonicalRemovedIds':canonical_removed,'remainingEvents':len(feed['events']),'places':len(feed['places'])}

if __name__=='__main__': print(json.dumps(prune(),ensure_ascii=False,indent=2))
