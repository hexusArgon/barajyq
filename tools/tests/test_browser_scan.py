import datetime as dt
import json
from pathlib import Path
import shutil
import sys
import tempfile
import unittest
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import browser_scan as bs
import tg_to_feed as tg

ROOT = Path(__file__).resolve().parents[2]

class BrowserScanTests(unittest.TestCase):
    def setUp(self):
        self.tmp = Path(tempfile.mkdtemp())
        for rel in ('tools/tg_sources.json', 'tools/ig_sources.json', 'tools/feed_data.json', 'feed/events.json'):
            (self.tmp / rel).parent.mkdir(parents=True, exist_ok=True)
            shutil.copy(ROOT / rel, self.tmp / rel)
        (self.tmp / 'android/app/src/main/assets').mkdir(parents=True)
        shutil.copy(ROOT / 'feed/events.json', self.tmp / 'android/app/src/main/assets/events.json')
        self.saved = {k: getattr(bs, k) for k in ('ROOT', 'TOOLS', 'FEED', 'ASSETS', 'TG_CURSOR', 'IG_SEEN')}
        bs.ROOT, bs.TOOLS = self.tmp, self.tmp / 'tools'
        bs.FEED, bs.ASSETS = self.tmp / 'feed/events.json', self.tmp / 'android/app/src/main/assets/events.json'
        bs.TG_CURSOR, bs.IG_SEEN = bs.TOOLS / 'tg_cursor.json', bs.TOOLS / 'ig_seen.json'

    def tearDown(self):
        for k, v in self.saved.items(): setattr(bs, k, v)
        shutil.rmtree(self.tmp)

    def test_tg_js_carries_all_channels_cursors_and_event_words(self):
        js = bs.tg_js()
        cfg = json.loads(js[js.index('const cfg = ') + 12:js.index(';\n')])
        self.assertEqual(len(cfg['channels']), 38)
        self.assertTrue(all(c['since'] >= 0 for c in cfg['channels']))
        self.assertEqual(cfg['eventWords'], tg.EVENT_WORDS)

    def test_tg_import_advances_cursor_and_imports(self):
        post = {'sourceUrl': 'https://t.me/aqyldyclub/99999', 'publishedAt': '2026-11-09T10:00:00+00:00', 'links': [],
                'text': 'Лекция «Тест»\n20 ноября, 19:00\nАстана\n📍 Letus Coffee, Кабанбай Батыра 11/2'}
        scan = self.tmp / 'scan.json'
        scan.write_text(json.dumps({'posts': [post], 'maxIds': {'aqyldyclub': 99999}}), encoding='utf-8')
        r = bs.tg_import(str(scan), now=dt.datetime(2026, 11, 10, 9, 0, tzinfo=tg.ZONE))
        self.assertEqual(r['added'], [post['sourceUrl']])
        self.assertEqual(json.loads(bs.TG_CURSOR.read_text())['aqyldyclub'], 99999)
        self.assertIn('99999', bs.tg_js().split('"aqyldyclub", "since": ')[1][:6])
        card = next(e for e in json.loads(bs.FEED.read_text())['events'] if e['sourceUrl'] == post['sourceUrl'])
        self.assertEqual((card['dateFrom'], card['time']), ('2026-11-20', '19:00'))

    def test_add_cards_resolves_year_from_reference_and_skips_duplicates(self):
        card = {'id': 'tg-x-1', 'cityId': 'astana', 'interest': 'CONCERTS', 'reference': '2026-12-20', 'startsAt': '15 янв, 19:00',
                'titleRu': 'Концерт', 'placeRu': 'Зал', 'sourceUrl': 'https://t.me/x/1', 'sourceName': 'Telegram @x', 'descriptionRu': 'Текст'}
        path = self.tmp / 'cards.json'
        path.write_text(json.dumps([card, dict(card, id='tg-x-2')]), encoding='utf-8')
        r = bs.add_cards(str(path))
        self.assertEqual(r['added'], ['tg-x-1']); self.assertEqual(r['skipped'], {'tg-x-2': 'duplicate'})
        added = next(e for e in json.loads(bs.FEED.read_text())['events'] if e['id'] == 'tg-x-1')
        self.assertEqual((added['dateFrom'], added['titleKk']), ('2027-01-15', 'Концерт'))
        self.assertEqual(bs.FEED.read_bytes(), bs.ASSETS.read_bytes())

    def test_patch_updates_feed_assets_and_canonical_rows(self):
        cid = next(r['id'] for r in json.loads((bs.TOOLS / 'feed_data.json').read_text()) if r['id'].startswith('tg-'))
        path = self.tmp / 'patch.json'
        path.write_text(json.dumps({cid: {'titleEn': 'New title'}}), encoding='utf-8')
        self.assertEqual(bs.patch(str(path)), [cid])
        row = next(r for r in json.loads((bs.TOOLS / 'feed_data.json').read_text()) if r['id'] == cid)
        self.assertEqual(row['t'][2], 'New title')
        self.assertEqual(bs.FEED.read_bytes(), bs.ASSETS.read_bytes())

    def test_ig_seen_includes_feed_permalinks(self):
        self.assertIn('DfXFsJZDsdK', bs.ig_seen_codes())
        raw = self.tmp / 'raw.json'; raw.write_text(json.dumps({'handle': 'h', 'posts': [{'code': 'NEWCODE123'}]}))
        self.assertEqual(bs.ig_mark_seen([str(raw)]), 1)
        self.assertIn('NEWCODE123', bs.ig_seen_codes())

if __name__ == '__main__': unittest.main()
