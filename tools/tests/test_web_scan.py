import datetime as dt
import sys, unittest
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import web_scan as w

TODAY = dt.date(2026, 11, 12)

class WebScanTest(unittest.TestCase):
    def test_starts_at_formats(self):
        self.assertEqual(w.starts_at({'start': '2026-11-13T19:00:00+05:00', 'end': '2026-11-13T21:00:00+05:00'}, TODAY), '13 ноя, 19:00')
        self.assertEqual(w.starts_at({'start': '2026-10-29', 'end': '2026-12-04', 'time': None}, TODAY), '12 ноя – 4 дек')  # started earlier
        self.assertEqual(w.starts_at({'start': '2026-11-20', 'end': '2026-12-04', 'time': None}, TODAY), '20 ноя – 4 дек')
        many = [f'2026-11-{d}' for d in range(12, 31)]
        self.assertEqual(w.starts_at({'start': many[0], 'end': many[-1], 'dates': many, 'time': '11:00'}, TODAY), '12 ноя – 30 ноя, 11:00')
        self.assertEqual(w.starts_at({'start': '2026-11-12', 'end': '2026-11-26', 'dates': ['2026-11-12', '2026-11-26'], 'time': '20:00'}, TODAY), '12 ноя, 26 ноя, 20:00')
        self.assertEqual(w.starts_at({'start': ''}, TODAY), '')

    def test_card_from_sxodim(self):
        ev = {'url': 'https://sxodim.com/astana/event/x', 'city': 'astana', 'cat': 'Театр', 'title': '«Өліара» спектаклі',
              'start': '2026-11-20', 'end': '2026-11-20', 'time': '19:00', 'locality': 'Астана',
              'address': 'Жастар театры, Ілияс Есенберлин көш., 10', 'price': 5000, 'desc': 'Текст', 'image': 'https://i/x.jpg'}
        card, why = w.to_card('sxodim.com', ev, TODAY)
        self.assertEqual(why, '')
        self.assertEqual(card['placeRu'], 'Жастар театры')
        self.assertEqual(card['address'], 'Ілияс Есенберлин көш., 10')
        self.assertEqual(card['startsAt'], '20 ноя, 19:00')
        self.assertEqual(card['price'], 'от 5 000 ₸')
        self.assertEqual(card['interest'], 'THEATER')
        self.assertEqual(card['imageUrl'], 'https://i/x.jpg')

    def test_locality_wins_and_unknown_city_skipped(self):
        ev = {'url': 'https://ticketon.kz/concerts/event/a', 'city': 'astana', 'cat': 'concerts', 'title': 'A',
              'start': '2026-11-20T19:00:00+05:00', 'locality': 'Алматы', 'place': 'Дворец'}
        self.assertEqual(w.to_card('ticketon.kz', ev, TODAY)[0]['cityId'], 'almaty')
        ev2 = dict(ev, locality='Бишкек', city='bishkek')
        self.assertEqual(w.to_card('ticketon.kz', ev2, TODAY), (None, 'city'))
        self.assertEqual(w.to_card('ticketon.kz', dict(ev, online=True), TODAY), (None, 'online'))

    def test_interest_keywords(self):
        self.assertEqual(w.interest('kino.kz', {'cat': 'Punch Club', 'title': 'Вечер'}), 'STANDUP')
        self.assertEqual(w.interest('kino.kz', {'cat': 'Выставка Искусство', 'title': 'Шагал'}), 'EXHIBITIONS')
        self.assertEqual(w.interest('ticketon.kz', {'cat': 'theatres', 'title': 'X'}), 'THEATER')

    def test_near_duplicate(self):
        card = {'cityId': 'astana', 'titleRu': 'JONY в Астане', 'startsAt': '12 ноя, 20:00', 'reference': '2026-11-12'}
        events = [{'cityId': 'astana', 'titleRu': 'Концерт JONY', 'dateFrom': '2026-11-12'}]
        self.assertTrue(w.near_duplicate(card, events))
        self.assertFalse(w.near_duplicate(dict(card, startsAt='13 ноя, 20:00'), events))

    def test_js_embeds_config(self):
        code = w.js('ticketon.kz', 5)
        self.assertIn('"limit": 5', code); self.assertNotIn('__CFG__', code)

    def test_coords_and_chunks(self):
        self.assertEqual(w.coords(76.95, 43.23), {'lat': 43.23, 'lon': 76.95})
        self.assertEqual(w.coords(None, 1), {'lat': None, 'lon': None})
        self.assertEqual(w.coords(10, 10), {'lat': None, 'lon': None})
        import json, os, sys, tempfile
        sys.path.insert(0, os.path.join(os.path.dirname(__file__), '..'))
        import web_chunks
        with tempfile.TemporaryDirectory() as tmp:
            for n in (1, 0):
                part = json.dumps({'site': 's.kz', 'found': 3, 'chunk': n, 'of': 2, 'events': [{'url': f'u{n}'}]}) + ' ' * 50
                with open(f'{tmp}/{n}.txt', 'w') as fh:
                    json.dump([{'type': 'text', 'text': json.dumps(part) + '\n\n(captured at origin https://s.kz)'}], fh)
            import contextlib, io
            with contextlib.redirect_stdout(io.StringIO()): web_chunks.main(tmp, [f'{tmp}/1.txt', f'{tmp}/0.txt'])
            merged = json.load(open(f'{tmp}/web-s.kz.json'))
        self.assertEqual([e['url'] for e in merged['events']], ['u0', 'u1'])
        self.assertNotIn('chunk', merged)


if __name__ == '__main__': unittest.main()
