# Telegram → Barajyq

Run from `/workspace/astana-afisha` (box only):

```sh
python3 -m pip install -r tools/requirements-telegram.txt
python3 tools/tg_to_feed.py
python3 -m unittest discover -s tools/tests -v
```

Source configuration: `tools/tg_sources.json`. Add a public channel's bare handle,
`enabled`, and a supported Android `interest` enum. `maxPages` (default 3, cap 10)
limits history per channel; newest page is always rescanned, then official
`?before=<post id>` pagination. No bot API, login, tokens, mirrors or secrets.
The bounded lookback is NOT a complete channel archive. Only visible caption text
is parsed; dates hidden in images/video are not OCR'd. Site markup can change.
Network retries are bounded; 401/403/429 stop retries. Errors produce nonzero exit
and `tg_import_result.json`, retain old feed and per-post staging. No bypasses.

Automation rule for the parent: run the command after existing Instagram import,
serially, from this directory. Do not run generators/importers concurrently.
The Telegram lock prevents simultaneous Telegram importers only. Check exit code
and `tools/tg_import_result.json.errors`; report failures, do not clear data.
No routine has been changed by this implementation.

Admission rules: recognizable event wording, exactly one event day, one start
time (a range "19:00–21:00" counts as its start), one city evidenced in
caption/links, and a `📍`/`Место:` venue line. Supported dates: Russian and
Kazakh month names ("22 ноября", "22 қарашада"), DD.MM, DD.MM.YYYY, ISO.
Year (since 2026-11-10): explicit year if present, otherwise the year of the
POST publication date (never the clock of the run); a day more than 14 days
before publication rolls over to the next year (a December post about
"15 января"). Posts without a publication timestamp and no explicit year go to
review. Announcements almost never print the year, so the old "explicit year
only" rule rejected ~400 real posts.
Recaps/cancellations, old events, several days/sessions and unsupported
formats go to `tools/tg_review.json` with full text, links, publication timestamp,
post permalink and a reason. After a rules change, `python3 tools/tg_restage.py`
re-classifies the staged posts offline (same duplicate checks, no network).
Generic channels (interest FESTIVALS) get a keyword category (concert, play,
quiz, exhibition…), see `refine_interest`.
Cancellation messages are staged; already imported cards are not auto-deleted.

Descriptions keep the complete visible source text and line breaks. Required
RU/KK/EN fields contain the SAME original text, not invented translations.
Unknown address/price/coordinates stay empty/null. `startsAt` is ISO +05:00;
city/venue are never inferred just from the channel identity. The source's
configured interest is editorial categorization (FESTIVALS for this club), not
inferred taxonomy or a claim that the meeting is free.

Stable channel/post IDs + exact source URL deduplicate posts. Identical captions
in a city, exact title/venue/start identity, and links to an existing event source
also deduplicate cross-posts. No fuzzy merging of recurring events; heterogeneous
legacy display dates may prevent cross-source detection. Rescans are append-only,
not an edit/cancellation reconciliation engine.

Writes preserve other sources and `places`. Telegram cards are also upserted
by ID into `tools/feed_data.json`, the input to native code/description generators.
No generators are executed by the importer. Existing generators do not rewrite
feed JSON; future JSON generators must merge rather than replace imported cards.
`feed/events.json` and Android `assets/events.json` are identical after successful
completion. Writes are atomic per file; next run repairs assets/canonical data
from the retained feed after interruption (not a multi-file transaction).

Initial live scan: official public preview was available, 3 pages / 31 posts.
No future event could be admitted safely. Latest visible announcement:
https://t.me/aqyldyclub/2280 — 30 October, no explicit year in event date;
older November announcements must not be promoted into the current year.
Fixture `tests/fixtures/aqyldyclub.html` is the actual first-page public response,
used offline (8 posts). Synthetic future test captions are NEVER imported.

`EVENTS_FEED_URL` is empty. Local assets changes do NOT update installed apps.
This tool publishes nothing, builds no APK, and changes no app ID/OAuth/keys.

## Enabled channel expansion (7 November 2026)

All six configured sources are enabled (no reserve groups):

| Handle | Display name | Configured category |
|---|---|---|
| aqyldyclub | existing club source | FESTIVALS |
| afisha_orsa | Объявления, афиша, события - Астана. | FESTIVALS |
| astana_afisha | Афиша Астана | FESTIVALS |
| adebi_club | Adebi_Club 📚 | FESTIVALS |
| sxodim_astana | Давай Сходим! Астана | FESTIVALS |
| quizplease_astana | Квиз, плиз! в Астане | STANDUP |

The five new sources have `allowedCities: ["astana"]`: this is a FILTER,
not evidence or a default city. `afisha_orsa` can advertise Almaty events despite
an Astana heading; conflicting city mentions go to review, and an Almaty-only
candidate is rejected by the filter. City evidence in links is restricted to
explicit `/astana/`, `/almaty/`, `/shymkent/` paths at sxodim.com / 2gis.kz;
channel/account handles containing `astana` do not establish event location.
Sxodim links to existing website cards use existing exact-source deduplication.
Quizplease dates visible only in images remain review items; OCR is not enabled.
Broad FESTIVALS is an editorial fallback for mixed afisha/community sources,
not automatic per-post genre classification. The `name` value is descriptive
source configuration; stable card attribution remains `Telegram @handle`.
The existing routine reads the full configuration; no routine changes needed.

Live expansion validation (7 November 2026, Asia/Qyzylorda UTC+5): both runs
succeeded, six channels / 224 staged posts, zero newly admitted cards. Repeat
left feed, assets and feed_data byte-identical; existing feed contains 72 events.
Examples requiring review rather than invented dates:
- https://t.me/afisha_orsa/2631 — 22 November 19:00, no event year;
- https://t.me/sxodim_astana/8999 — several hockey sessions, no event year.
Captions without currently recognized event keywords can be marked `not_event`
even if an editor recognizes an announcement; their full text remains staged.
Eight offline tests pass, including the six-source config and mixed-city filter.

## Kazakhstan niche-source expansion (7 November 2026)

Research selection provenance is in `tools/tg_sources_audit.json`. All 32
confirmed community records are represented: 3 were already connected, 29 new
primary endpoints were added, plus 3 separately evidenced regional channels
(`quizplease_uralsk1`, `ac_karaganda`, `american_corner_oskemen`). Including the
other three preserved afisha sources, total configuration is 38 public channel
endpoints, NOT 38 newly discovered communities. 20 candidates and 9 rejected
research entries are not added; no related chats or archived Astana alias.
Some confirmed sources have old latest announcements; confirmed is evidence of
KZ offline activity, not a promise of future events. September/October 2026
sources remain enabled; the explicitly archived 2023 branch does not.

Configured `allowedCities` follows evidence, never an event-city default.
Primary asmsalmaty is Almaty-only; its two regional branches use their own cities.
Importer supports the existing app IDs astana, almaty, shymkent, karaganda,
kokshetau, pavlodar, uralsk, ust-kamenogorsk. National source qazvolunteer is
restricted to its evidenced Astana activity until other cities are verified.
No new app city models are required: KazakhstanCities already contains these IDs,
and FeedRepository filters bundled feed by cityId. No native fallback generators
are run, because their old generation loop only lists three cities; bundled
JSON is the app integration path for regional cards.

Foreign-geography and online/hybrid mentions are staged conservatively, never
published as KZ offline events. This can reject a local talk mentioning another
country: review is safer than invented geography. Only original visible caption
text is used, no OCR/translation. Venue labels
`Место:`, `Адрес:`, `Venue:`, `Location:` are also accepted alongside 📍.
Routine, package identity, OAuth, keys and app version are unchanged. Build and
emulator installation are separate Windows tasks; the box does not perform them.

Live result for niche expansion: 38/38 endpoints scanned successfully on both
passes, 1,491 posts staged, one real future card admitted:
https://t.me/afisha_orsa/2598 — ANI LORAK, Astana Congress Centre,
13 November 2026 at 20:00 (Asia/Qyzylorda UTC+5); year and venue are explicit
in the caption. None of the newly researched niche endpoints produced a card
meeting all strict admission rules in this lookback. This is not a failure to
connect them: their captions remain available for editorial review.
Second live pass added zero cards. Feed and assets have 73 events / 27 places;
feed/assets bytes match and every Telegram card matches feed_data canonical row.
Eleven offline tests pass; py_compile passes. Detailed per-channel statuses,
first/second import results and integrity checks: `tools/tg_validation_result.json`.
No app version bump; assets are consumed by the existing FeedRepository.

## Утренний мониторинг через браузер (с 2026-11-10)
Shell на ПК не открывает t.me, поэтому каналы читает встроенный браузер Claude: `tools/browser_scan.py tg-js`
(один вызов на все каналы, только посты новее `tools/tg_cursor.json` со словами-маркерами события), затем
`tg-import`. Дополнительные правила: канал ровно с одним `allowedCities` локальный, и пост без города
относится к этому городу. Явная метка «Начало/Старт» выбирает время среди нескольких («Сбор 18:30 | Начало 19:00»).
Площадка берётся из строки 📍 или из строки с меткой (Место, Адрес, Где, Локация, Площадка, Мекенжай), голая ссылка
площадкой не считается. Заголовок: первая строка со словами. Полный порядок работы описан в `tools/MONITORING.md`.
