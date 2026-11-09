import contextlib
import importlib.util
import io
import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
import event_policy as ep
import tg_to_feed as tg
import ig_merge_scan as ig
import ig_to_feed as old_ig
import prune_invalid_events as prune
import gen_event_policy
ROOT=Path(__file__).resolve().parents[2]
FIXTURE=json.loads((Path(__file__).parent/'fixtures/rejected_roundups.json').read_text())

class PolicyTests(unittest.TestCase):
    def test_both_user_screenshot_texts_and_all_confirmed_roundups(self):
        self.assertEqual(len(FIXTURE),9)
        for card in FIXTURE:
            self.assertTrue(ep.rejected(card),card['titleRu'])
            # Generic filters catch title even if a new post URL/ID is assigned.
            self.assertTrue(ep.rejected(dict(card,id='new',sourceUrl='new')),card['titleRu'])
        self.assertEqual(FIXTURE[0]['titleRu'],'Главные события ноября в Астане')
        self.assertIn('Собрали самые интересные мероприятия месяца',FIXTURE[0]['descriptionRu'])
        self.assertTrue(any(c['titleRu']=='Репертуар на ноябрь' for c in FIXTURE))
    def test_language_and_schedule_negatives(self):
        for title in ['Куда сходить в Астане','Подборка концертов','Афиша ноября','События на выходных',
                      'Расписание на неделю','Қараша айының репертуары','Қараша афишасы','Апталық афиша',
                      'November events in Astana','Monthly repertoire','Events this month','Where to go this weekend']:
            self.assertTrue(ep.roundup_reason(title),title)
    def test_named_festivals_multiday_and_programs_stay(self):
        for title in ['Зимний фестиваль Wonderland Astana','Гик-фестиваль Multi Fest','Аниме-гик фестиваль Flavi Fest',
                      'Щелкунчик (декабрь)','Концерт «Программа ноября»','Дядя Ваня','ANI LORAK в Астане!',
                      'November Jazz Festival','Кітап фестивалі','Турандот']:
            self.assertIsNone(ep.roundup_reason(title,'В программе несколько выступлений','1–3 ноября'),title)
    def test_missing_extracted_details_are_not_invented(self):
        card=dict(FIXTURE[0],id='other',sourceUrl='other',titleRu='Концерт',descriptionRu='Анонс',startsAt='')
        self.assertEqual(ep.import_reason(card),'missing_event_date_review')
        card.update(startsAt='13 ноября',placeRu='afishi.astana')
        self.assertEqual(ep.import_reason(card),'missing_event_venue_review')
        self.assertTrue(ep.roundup_reason('Анонс','details are on image carousel only'))
    def test_price_normalization(self):
        for value in [None,'null',' NULL ','None','', '  ']: self.assertIsNone(ep.normalize_price(value))
        for value in ['Бесплатно','0','от 5000 ₸']: self.assertEqual(ep.normalize_price(value),value)
    def test_cleanup_idempotency_and_keeps_valid_records(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp)
            for name in ['feed','tools','android/app/src/main/assets']: (root/name).mkdir(parents=True)
            valid=dict(FIXTURE[0],id='valid',titleRu='Wonderland Astana',descriptionRu='Фестиваль',sourceUrl='https://example.com/fest',startsAt='1–3 декабря')
            feed=dict(events=FIXTURE+[valid],places=[],version=1)
            (root/'feed/events.json').write_text(json.dumps(feed))
            rows=[dict(id=c['id'],url=c['sourceUrl'],ev=True,t=[c['titleRu']]*3,d=[c['descriptionRu']]*3,st=c['startsAt'],price=None) for c in feed['events']]
            (root/'tools/feed_data.json').write_text(json.dumps(rows))
            self.assertEqual(len(prune.prune(root)['removed']),9)
            before=(root/'feed/events.json').read_bytes()
            self.assertEqual(prune.prune(root)['removed'],[])
            self.assertEqual(before,(root/'feed/events.json').read_bytes())
            self.assertEqual(before,(root/'android/app/src/main/assets/events.json').read_bytes())
            self.assertEqual(len(json.loads((root/'tools/feed_data.json').read_text())),1)
    def test_ig_reimport_scan_does_not_resurrect(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp);(root/'assets').mkdir();(root/'tools').mkdir()
            feed=root/'events.json';feed.write_text(json.dumps(dict(events=[],places=[])))
            sources=root/'sources.json';sources.write_text(json.dumps({'accounts':[{'handle':'afishi.astana','city':'astana','kind':'afisha'}]}))
            scan=root/'scan.json';scan.write_text(json.dumps({'accounts':[{'handle':'afishi.astana','posts':[
                {'title':c['titleRu'],'caption':c['descriptionRu'],'postUrl':c['sourceUrl'],'startsAt':c['startsAt'],'place':c['placeRu']} for c in FIXTURE]}]}))
            # Report output must be scoped to the temp root, never production data.
            with patch.object(ig,'FEED',feed),patch.object(ig,'SOURCES',sources),patch.object(ig,'ASSETS',root/'assets/events.json'),patch.object(ig,'ROOT',root),contextlib.redirect_stdout(io.StringIO()),contextlib.redirect_stderr(io.StringIO()):
                ig.main(str(scan))
            self.assertEqual(json.loads(feed.read_text())['events'],[])
    def test_tg_rejects_roundups_even_with_full_date_and_venue(self):
        p={'sourceUrl':'https://t.me/example/1','text':'Подборка лекций\n13 ноября 2026, 20:00\nАстана\n📍 Конгресс-Центр','links':[]}
        c,reason=tg.classify(p,{'handle':'example','interest':'FESTIVALS'},tg.dt.datetime(2026,11,7,tzinfo=tg.ZONE))
        self.assertIsNone(c);self.assertEqual(reason,'roundup_not_single_event')
    def test_data_and_fallbacks_have_no_rejected_entries(self):
        feed=json.loads((ROOT/'feed/events.json').read_text())
        self.assertGreaterEqual(len(feed['events']),64);self.assertEqual(len(feed['places']),27)
        self.assertTrue(all(not ep.rejected(c) for c in feed['events']))
        self.assertEqual((ROOT/'feed/events.json').read_bytes(),(ROOT/'android/app/src/main/assets/events.json').read_bytes())
        self.assertTrue(all(ep.keep_canonical(r) for r in json.loads((ROOT/'tools/feed_data.json').read_text())))
        for file in ['tools/descriptions_data.py','android/app/src/main/java/kz/astana/afisha/data/FakeEventsRepository.kt','android/app/src/main/java/kz/astana/afisha/data/Descriptions.kt','ios/Barajyq/Data/FakeEventsRepository.swift']:
            raw=(ROOT/file).read_text()
            self.assertTrue(all(i not in raw for i in ep.POLICY['rejectedIds']),file)
    def test_runtime_policy_is_generated_and_all_cache_paths_guarded(self):
        self.assertEqual((ROOT/'android/app/src/main/java/kz/astana/afisha/data/EventContentPolicy.kt').read_text(),gen_event_policy.render())
        runtime=(ROOT/'android/app/src/main/java/kz/astana/afisha/data/FeedRepository.kt').read_text()
        for guard in ['cache.get()?.let(::sanitize)','return sanitize(it)','return EventContentPolicy.clean(out)',
                      'events = EventContentPolicy.clean(ev.values.toList())',
                      'EventContentPolicy.clean(if (places) FakePlacesRepository.forCity(city) else FakeEventsRepository.forCity(city))',
                      'EventContentPolicy.normalizePrice(o.optString("price"))']:
            self.assertIn(guard,runtime)

if __name__=='__main__': unittest.main()
