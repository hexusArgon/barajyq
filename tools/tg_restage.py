#!/usr/bin/env python3
"""Re-classify already staged Telegram posts (tools/tg_review.json) offline, after a rules change.

No network: uses the post texts saved by previous scans. Same duplicate/merge path as tg_to_feed.run().
"""
import datetime as dt
import json
import tg_to_feed as tg

def main(now=None):
    staged = tg.load(tg.ROOT/'tools/tg_review.json', {'posts': {}})['posts']
    by_handle = {}
    for post in staged.values():
        handle = post['sourceUrl'].removeprefix('https://t.me/').split('/')[0].casefold()
        clean = {k: post[k] for k in ('sourceUrl', 'text', 'publishedAt', 'links') if k in post}
        by_handle.setdefault(handle, []).append(clean)
    return tg.run(scanner=lambda handle, pages: by_handle.get(handle.casefold(), []), now=now)

if __name__ == '__main__':
    result = main()
    print(json.dumps({'added': len(result['added']), 'errors': result['errors'],
                      'channels': result['channels']}, ensure_ascii=False, indent=1))
