# Android feed search / filters / sorting (t58u)

Implemented on both existing Android Compose tabs. No feed rows, settings,
identity/signing/OAuth or previously pending no-roundups files are changed here.
The original item order remains the default; this is not a redesign.

## Controls
- Visible Search, Filters and Sort buttons below the two tabs, standard Material
  components in the current theme. Wrapping FlowRow and scrollable filter sheet
  accommodate narrow screens and larger fonts; controls have >=48dp hit targets.
- Search opens an inline labelled field with back/close and clear buttons,
  immediate local results, standard system-back dismissal. Searches the selected
  language's title, FULL description, venue and address. Locale-independent
  case normalization, Unicode NFKC, Russian ё/е equivalence and whitespace tokens;
  every typed token must match somewhere in those fields.
- Filter sheet: multi-select available categories within existing interests;
  empty category selection means all available, NOT all categories outside the
  profile. Explicit price/admission: Any / Free / Paid / Unknown or ambiguous.
  Unknown price is never assumed free. Explicit positive KZT amounts and exact
  free wording (ru/kk/en) are recognized. No average-bill inference, popularity,
  ratings, distance or opening-hours claims. Place filters use the same actual
  category/price fields, not invented venue properties.
- Events only: Any date / Today / Next 7 days / Next 30 days. Inclusive windows
  use Asia/Qyzylorda (UTC+5), only exact full ISO dates or offset timestamps.
  Model has no separate structured date field: startsAt is mostly a raw display
  schedule; only its genuine ISO values are machine-comparable. No current-year
  inference for “13 ноя”, no extraction from description/publication timestamp,
  and no guessed range/recurrence expansion. Legacy intervals remain available
  under Any date, but are excluded from date-filtered results. This limitation
  is explicitly explained in the sheet.
- Events sort: Original / Nearest verified date / Localized name. Upcoming dated
  entries precede past dated entries; undated/ambiguous entries remain last in
  original order. Times within one date sort by actual instant. Name sort uses
  Java's locale Collator, stable ties. Places: Original / Localized name only.
  No price sorting because ranges/unknowns do not establish comparable prices.
- Current order is always shown; active filter count is shown on its button.
  Filter sheet has draft Apply / Cancel / Reset filters. Reset filters retains
  query and sort. “Nothing found” provides reset query+filters (retains sort and
  user's profile/hidden exclusions). Existing no-profile-result state still
  links to settings.

## State and safety
FeedScreen holds separate event/place query state and search-open flags via
rememberSaveable. Primitive-string serialization is tested for restoration;
Navigation Compose retains it on normal detail/settings back navigation, and
saveable state supports rotation/process recreation of the back stack. It is
not a permanent preference saved across manually closing/removing the task.
Switching tabs never overwrites the other tab's query/filter/sort.

All three tools run together AFTER FeedRepository.forProfile (city, interests,
hidden IDs and existing no-roundups policy); FeedQuery defensively rechecks
city/interests/hidden/type and applies EventContentPolicy again. Explicit profile
category exclusions are also subtracted. No reset mutates SessionStore interests,
hidden IDs, city or language. Query layer cannot resurrect removed roundup cards.

## Validation
- 10 JUnit 4 tests compiled and executed on box with Kotlin compiler 2.0.21,
  isolated JDK17, JVM target 17. Real Models.kt, EventContentPolicy.kt and
  FeedQuery.kt were compiled; ONLY generated R constants and StringRes annotation
  were stubbed for SDK-independent execution. Covers combined tools/scope,
  ru/kk/en case/search, price/date ambiguity, inclusive windows, stable sorting,
  places vs events, hidden/roundup rejection, independent tab state and encode/
  decode/reset behavior. This is not a Compose UI/rotation instrumentation test.
- 24 Python offline tests pass (21 previous + 3 resource/integration checks).
- Android SDK and Gradle are absent on box. No APK or full Compose compilation,
  emulator, TalkBack or visual test performed; Windows build remains a separate
  step. No iOS changes: SwiftUI has its own static data/navigation path and safe
  parity is not a small symmetric patch. iOS is NOT claimed built or updated.

Normal SDK environment:
```
cd android
# JDK17; Windows: gradlew.bat testDebugUnitTest assembleDebug
```
SDK-independent query harness (requires compiler/JUnit jars, downloads nothing):
```
python3 tools/run_feed_query_jvm_tests.py --java /path/to/java --jars /path/to/jars
python3 -m unittest discover -s tools/tests -v
```

## Design intent
Read Apple HIG mirrors for search fields, toolbars, sheets, materials, color,
typography, motion and accessibility. Applied transferable principles: nearby
inline search with clear scope, immediate results, three consistent visible
commands, one brief dismissible sheet, explicit cancel/reset, semantic theme
colors, standard scalable type and labels rather than color-only state.
Implementation uses Android Material3, not Apple-specific glass/SF typography;
no extra motion beyond platform components and existing list item animation.

## Delivery
This task's ZIP is a delta only. Preserve all prior uncommitted no-roundups work,
especially EventContentPolicy.kt, FeedRepository.kt, feed/events.json, Android
assets, event_policy.* and the IG/TG ingestion/generator gates. They are required
prerequisites and are not replaced by the new controls. No commit/push, app install
or user-machine commands are performed in this task.

## Compact toolbar update (t65u)
Both tabs now use a single non-wrapping Row: always-visible borderless BasicTextField (localized placeholder, conditional clear) and two unfilled icon-only IconButtons. Search no longer needs an open/closed state. Icon touch targets stay 48dp; filter state is tonal and described to accessibility, current sort is selected in the dialog and included in its content description. The separate order caption is removed. Query/filter/sort logic and profile settings are unchanged.

## Planning / header (t66u)
City selector is screen-centered with equal reserved side widths (28% each, center 44%), ellipsized long labels, app name left and settings right. Redundant text hide actions become Plan; eye-off still hides with the existing behavior. Details retain eye-off in the app bar.
Plan hands a draft to an installed calendar using ACTION_INSERT / CalendarContract.Events.CONTENT_URI: user chooses calendar/date/time and explicitly saves. No calendar provider permission, account connector, background write or success claim. Missing editor/security rejection shows a localized error. Full localized text, venue/address, source URL and original date are carried; only ISO offset timestamps prefill begin time. Missing timezone/time, date-only/ranges and places do not supply begin/end/all-day extras. The model has no structured end. Calendar editor may propose its own defaults: review note explicitly asks the user to verify before saving.
Official intent documentation: https://developer.android.com/identity/providers/calendar-provider and https://developer.android.com/guide/components/intents-common .

## Bottom sections (t68u)
Events/Places moved from top tabs to FeedScreen Scaffold.bottomBar. Two equally weighted, selectable horizontal icon+label rows (56dp minimum target, up to two label lines) stay pinned above navigationBarsPadding; selected item uses the existing secondaryContainer palette. Scaffold content padding reserves bottom space, including empty states. Saveable independent tab/query state is unchanged; detail navigation is untouched.

## Vertical density (t69u)
Header minimum 64→48dp; toolbar vertical padding 4→0dp per side (48dp touch areas retained); list top padding 16→8dp; count-to-first-card 16→8dp. Cards and inter-card gaps remain unchanged (16dp gaps supplied as external top padding after the first card). Bottom item minimum 56→48dp, inner vertical padding 8→4dp; outer panel vertical padding 4→0dp. System status/navigation insets remain. Large text may grow rows naturally.

## Further vertical density (t70u)
Remaining list-top whitespace 8→0dp; count-to-first-card gap 8→4dp: another 12dp reduction above the first card. Header, search/filter/sort and bottom items already use minimum 48dp hit areas; they are not reduced further. BasicTextField has no Material text-field default content padding or outlined decoration. Bottom's 4dp inner padding fits inside its 48dp minimum and removing it would not shorten the normal panel, so it is retained. Insets, text metrics, cards and 16dp inter-card gaps remain unchanged.

## Structured schedule (0.3.0, 2026-11-10)
`startsAt` stays the human text from the source. Every event card also carries
`dateFrom`, `dateTo`, `dates` (discrete days, empty for a continuous run) and
`time` (UTC+5), written by `tools/schedule.py` at IMPORT time: the missing year
of "13 ноя" is resolved against the collection/publication day (a day more than
60 days before it belongs to the next year). Importers call it automatically
(`tg_to_feed.py`, `ig_merge_scan.py`); for hand-made cards run
`python3 tools/annotate_feed.py --reference <collection day>`. Never re-annotate
old cards months later: the reference day would be wrong.
The app (FeedQuery/EventSchedule) uses these fields to hide events after
`dateTo`, filter Today / Tomorrow / Weekend / 7 / 30 days, sort "Soonest first"
(default for events) with day groups Today / Tomorrow / This week / Later /
On now (ongoing exhibitions, by closing day) / Date TBA, and to prefill the
calendar start (next show day + time; runs and cards without time get none).
Cards without the fields keep working: an ISO `startsAt` is used, otherwise the
card is "Date TBA" and is excluded from day filters. The sections above about
"no current-year inference" describe the 0.2.x behaviour.
The Android disk cache now stores only the last REMOTE payload and is ignored
when the bundled feed is newer, so app updates always show their own data.

