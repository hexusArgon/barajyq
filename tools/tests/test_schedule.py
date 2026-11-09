import datetime as dt
from pathlib import Path
import sys
import unittest
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import schedule as sc

REF = dt.date(2026, 11, 4)

class ScheduleTests(unittest.TestCase):
    def p(self, text, ref=REF): return sc.parse(text, ref)

    def test_single_with_time(self):
        self.assertEqual(self.p('6 ноя, 19:00'), {'dateFrom': '2026-11-06', 'dateTo': '2026-11-06', 'dates': ['2026-11-06'], 'time': '19:00'})
        self.assertEqual(self.p('13 ноя')['time'], None)

    def test_range_crosses_new_year(self):
        r = self.p('7 ноя – 30 янв, 18:30')
        self.assertEqual((r['dateFrom'], r['dateTo'], r['dates'], r['time']), ('2026-11-07', '2027-01-30', [], '18:30'))
        self.assertEqual(self.p('6 дек – 27 янв')['dateTo'], '2027-01-27')

    def test_recent_past_stays_this_year(self):
        r = self.p('9 окт – 9 ноя, 10:00')
        self.assertEqual((r['dateFrom'], r['dateTo']), ('2026-10-09', '2026-11-09'))
        self.assertEqual(self.p('30 янв, 19:30')['dateFrom'], '2027-01-30')

    def test_multiple_days_and_shared_month(self):
        self.assertEqual(self.p('12 ноя, 26 ноя, 19:00')['dates'], ['2026-11-12', '2026-11-26'])
        self.assertEqual(self.p('13, 14 ноя')['dates'], ['2026-11-13', '2026-11-14'])
        r = self.p('13–14 ноя, 19:00')
        self.assertEqual((r['dateFrom'], r['dateTo'], r['dates']), ('2026-11-13', '2026-11-14', []))

    def test_iso(self):
        self.assertEqual(self.p('2026-11-13T20:00:00+05:00'), {'dateFrom': '2026-11-13', 'dateTo': '2026-11-13', 'dates': ['2026-11-13'], 'time': '20:00'})
        self.assertEqual(self.p('2026-11-07T22:30:00Z')['dateFrom'], '2026-11-08')
        self.assertEqual(self.p('2026-11-09')['time'], None)

    def test_unparseable_is_none(self):
        for text in ['', 'по расписанию', '31 ноя', 'каждую субботу', '7 ноя – 9 ноя – 10 ноя', '13']:
            with self.subTest(text=text): self.assertIsNone(self.p(text))

    def test_two_times_mean_unknown_time(self):
        self.assertIsNone(self.p('12 ноя 19:00, 26 ноя 20:00')['time'])

    def test_annotate_is_idempotent_and_skips_places(self):
        card = {'startsAt': '13 ноя', 'isPlace': False}
        self.assertTrue(sc.annotate(card, REF)); self.assertFalse(sc.annotate(card, dt.date(2027, 6, 1)))
        self.assertEqual(card['dateTo'], '2026-11-13')
        place = {'startsAt': '13 ноя', 'isPlace': True}
        self.assertFalse(sc.annotate(place, REF)); self.assertNotIn('dateTo', place)

if __name__ == '__main__': unittest.main()
