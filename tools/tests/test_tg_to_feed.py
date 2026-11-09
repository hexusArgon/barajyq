import datetime as dt
import json
from pathlib import Path
import sys
import tempfile
import unittest
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
import tg_to_feed as tg

NOW=dt.datetime(2026,11,7,18,20,tzinfo=tg.ZONE)
SOURCE={'handle':'aqyldyclub','interest':'FESTIVALS'}
def post(text, n=1):
    return {'sourceUrl':f'https://t.me/aqyldyclub/{n}','text':text,'publishedAt':'2026-11-01T10:00:00+00:00','links':[]}
GOOD='Лекция «Тест»\n8 ноября 2026, 19:00\nАстана\n📍 Letus Coffee, Кабанбай Батыра 11/2\nПолный текст & подробности.'

class TelegramTests(unittest.TestCase):
    def test_real_preview(self):
        posts=tg.parse_page((Path(__file__).parent/'fixtures/aqyldyclub.html').read_text(),'aqyldyclub')
        self.assertEqual(len(posts),8)
        last=next(p for p in posts if p['sourceUrl'].endswith('/2280'))
        self.assertIn('CANCEL CULTURE',last['text'])
        self.assertIn('пр. Кабанбай батыра, 53',last['text'])
        self.assertFalse(any(tg.classify(p,SOURCE,NOW)[0] for p in posts))
    def test_full_text_and_original_languages(self):
        c,r=tg.classify(post(GOOD),SOURCE,NOW)
        self.assertEqual(r,'candidate'); self.assertEqual(c['descriptionRu'],GOOD)
        self.assertEqual(c['descriptionKk'],GOOD); self.assertEqual(c['startsAt'],'2026-11-08T19:00:00+05:00')
        self.assertEqual(c['address'],''); self.assertIsNone(c['price'])
    def test_reject_uncertainty(self):
        for text in [GOOD.replace('2026','2025'),GOOD.replace('Астана',''),
                     GOOD.replace('19:00',''),GOOD.replace('📍',''),GOOD+'\n9 ноября 2026',
                     'Вспоминаем, как прошла '+GOOD,GOOD+'\nПереносим лекцию',GOOD.replace('8 ноября','31 ноября')]:
            with self.subTest(text=text): self.assertIsNone(tg.classify(post(text),SOURCE,NOW)[0])
        self.assertEqual(tg.classify(post('Пост про английские слова'),SOURCE,NOW)[1],'not_event')
    def test_year_comes_from_publication_date_not_clock(self):
        no_year=GOOD.replace(' 2026','')
        c,r=tg.classify(post(no_year),SOURCE,NOW)
        self.assertEqual(r,'candidate'); self.assertEqual(c['startsAt'],'2026-11-08T19:00:00+05:00')
        p=post(no_year); p['publishedAt']=None
        self.assertEqual(tg.classify(p,SOURCE,NOW)[1],'missing_year_without_publication_date_review')
        # An old post about "8 ноября" is last year's event, not a future one.
        p=post(no_year); p['publishedAt']='2025-11-01T10:00:00+00:00'
        self.assertEqual(tg.classify(p,SOURCE,NOW)[1],'past')
        # December post about January rolls over to next year.
        p=post(no_year.replace('8 ноября','15 января')); p['publishedAt']='2026-12-20T10:00:00+00:00'
        self.assertEqual(tg.classify(p,SOURCE,NOW)[0]['startsAt'],'2027-01-15T19:00:00+05:00')
    def test_kazakh_months_short_dates_and_time_ranges(self):
        for text in [GOOD.replace('8 ноября 2026','8 қарашада'),GOOD.replace('8 ноября 2026','08.11'),
                     GOOD.replace('19:00','19:00–21:00'),GOOD+'\n8 ноября (суббота)']:
            with self.subTest(text=text):
                c,r=tg.classify(post(text),SOURCE,NOW)
                self.assertEqual(r,'candidate'); self.assertEqual(c['startsAt'],'2026-11-08T19:00:00+05:00')
        self.assertIsNone(tg.classify(post(GOOD+'\nСбор в 18:30'),SOURCE,NOW)[0])
        self.assertIsNone(tg.classify(post(GOOD.replace('8 ноября 2026','8 ноября и 15 ноября')),SOURCE,NOW)[0])
    def test_city_filter_and_no_channel_city_inference(self):
        source=dict(SOURCE,allowedCities=['astana'])
        card,reason=tg.classify(post(GOOD.replace('Астана','Алматы')),source,NOW)
        self.assertIsNone(card); self.assertEqual(reason,'city_outside_source_filter_review')
        card,reason=tg.classify(post(GOOD+'\nАлматы'),source,NOW)
        self.assertIsNone(card); self.assertEqual(reason,'missing_or_multiple_cities_review')
        p=post(GOOD.replace('Астана',''))
        p['links']=['https://t.me/astana_afisha','https://instagram.com/sxodim_astana']
        self.assertIsNone(tg.classify(p,source,NOW)[0])
        p['links']=['https://sxodim.com/astana/event/test']
        self.assertIsNotNone(tg.classify(p,source,NOW)[0])

    def test_confirmed_configured_channels(self):
        config=tg.load(tg.ROOT/'tools/tg_sources.json')
        self.assertTrue({s['handle'] for s in config['channels'] if s.get('enabled',True)}.issuperset(
            {'aqyldyclub','afisha_orsa','astana_afisha','adebi_club','sxodim_astana','quizplease_astana'}))
        self.assertEqual(len(config['channels']),38)
        self.assertEqual(len({s['handle'].casefold() for s in config['channels']}),38)
        audit=tg.load(tg.ROOT/'tools/tg_sources_audit.json')
        confirmed={e['id'] for e in audit['entries'] if e['tier']=='confirmed'}
        for source in config['channels']:
            if source.get('researchId'):
                self.assertIn(source['researchId'],confirmed)
                self.assertTrue(set(source['allowedCities']).issubset(tg.CITY_PATTERNS))
        for source in config['channels']:
            self.assertIn(source['interest'],tg.INTERESTS)

    def test_regional_cities_and_foreign_filter(self):
        for city,word in [('karaganda','Караганда'),('kokshetau','Көкшетау'),('pavlodar','Павлодар'),('uralsk','Уральск'),('ust-kamenogorsk','Өскемен')]:
            source=dict(SOURCE,allowedCities=[city])
            card,reason=tg.classify(post(GOOD.replace('Астана',word)),source,NOW)
            self.assertEqual(reason,'candidate'); self.assertEqual(card['cityId'],city)
        self.assertIsNone(tg.classify(post(GOOD+'\nТур в Бишкек'),SOURCE,NOW)[0])
        self.assertIsNone(tg.classify(post(GOOD+'\nУчастие онлайн в Zoom'),SOURCE,NOW)[0])
        c,reason=tg.classify(post(GOOD.replace('📍 ','Место: ')),SOURCE,NOW)
        self.assertEqual(reason,'candidate'); self.assertIn('Letus',c['placeRu'])

    def test_real_future_announcement(self):
        p=tg.load(Path(__file__).parent/'fixtures/afisha_orsa_2598.json')
        c,reason=tg.classify(p,dict(SOURCE,handle='afisha_orsa',allowedCities=['astana']),NOW)
        self.assertEqual(reason,'candidate')
        self.assertEqual(c['startsAt'],'2026-11-13T20:00:00+05:00')
        self.assertEqual(c['descriptionRu'],p['text'])
        self.assertIn('Конгресс-Центр',c['placeRu'])

    def test_preview_case_insensitive_channel(self):
        html='<div class="tgme_widget_message" data-post="everjazz_Almaty/1"><div class="tgme_widget_message_text">Привет</div></div>'
        self.assertEqual(len(tg.parse_page(html,'everjazz_almaty')),1)

    def test_preview_integrity(self):
        with self.assertRaises(ValueError): tg.parse_page('<html>Login</html>','aqyldyclub')
        html='<div class="tgme_widget_message" data-post="aqyldyclub/1"><div class="tgme_widget_message_text">A &amp; <b>B</b><br>C<a href="https://example.com">link</a></div></div>'
        p=tg.parse_page(html,'aqyldyclub')[0]
        self.assertEqual(p['text'],'A & B\nClink'); self.assertEqual(p['links'],['https://example.com'])
    def test_merge_idempotency_recovery_and_network_failure(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp); (root/'tools').mkdir(); (root/'feed').mkdir()
            tg.save(root/'tools/tg_sources.json',{'channels':[SOURCE],'maxPages':1})
            tg.save(root/'feed/events.json',{'version':1,'updatedAt':'old','events':[],'places':[]})
            tg.save(root/'tools/feed_data.json',[])
            scanner=lambda h,p:[post(GOOD),post(GOOD,2)]
            first=tg.run(root,scanner,NOW); self.assertEqual(len(first['added']),1)
            before=(root/'feed/events.json').read_bytes()
            self.assertEqual(tg.run(root,scanner,NOW)['added'],[])
            self.assertEqual(before,(root/'feed/events.json').read_bytes())
            self.assertEqual(before,(root/'android/app/src/main/assets/events.json').read_bytes())
            self.assertEqual(len(tg.load(root/'tools/feed_data.json')),1)
            # An interrupted canonical write is repaired from the retained feed.
            tg.save(root/'tools/feed_data.json',[]); tg.run(root,scanner,NOW)
            self.assertEqual(len(tg.load(root/'tools/feed_data.json')),1)
            def fail(h,p): raise OSError('offline')
            self.assertEqual(len(tg.run(root,fail,NOW)['errors']),1)
            self.assertEqual(before,(root/'feed/events.json').read_bytes())
    def test_cross_source_exact_identity(self):
        c,_=tg.classify(post(GOOD),SOURCE,NOW)
        other=dict(c,id='ig-other',sourceUrl='https://instagram.com/example/p/123',descriptionRu='Different caption')
        self.assertTrue(tg.duplicate(c,[other]))
        other['startsAt']='2026-11-09T19:00:00+05:00'
        self.assertFalse(tg.duplicate(c,[other]))

if __name__=='__main__': unittest.main()
