Barajyq remote feed: Android 0.2.3 loads assets/events.json; set EVENTS_FEED_URL for remote. IG: tools/ig_to_feed.py + tools/ig_sources.json

Telegram public channels: see `tools/TELEGRAM.md`; run `python3 tools/tg_to_feed.py` after Instagram import (serially). Review staging: `tools/tg_review.json`. No automatic delivery to installed apps while EVENTS_FEED_URL is empty.
