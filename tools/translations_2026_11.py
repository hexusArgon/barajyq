#!/usr/bin/env python3
"""One-off: kk/en titles, venues and short captions for Instagram/Telegram cards (Nov 2026).

Imported cards keep the source text in all three languages; these are proper translations.
Long Telegram announcements keep their original text. Re-running is idempotent.
"""
import datetime as dt
import json
from pathlib import Path
import schedule

ROOT = Path(__file__).resolve().parents[1]

def tryout(day_kk, day_ru, day_en, date_num, gather, start, name_ru=None, name_kk=None, name_en=None):
    head_ru = f'Проверочный концерт {name_ru} 🎤' if name_ru else '🎤 Проверочный концерт🎤'
    head_kk = f'{name_kk} сынақ концерті 🎤' if name_kk else '🎤 Сынақ концерті 🎤'
    head_en = f'{name_en}: stand-up try-out 🎤' if name_en else '🎤 Stand-up try-out show 🎤'
    intro_kk = '' if name_kk else '\n\nСынақ концертінде комиктер жаңа материалды сынап, көрерменнің шынайы реакциясына қарап әзілдерін шыңдайды'
    intro_en = '' if name_en else '\n\nAt a try-out show comedians test new material and polish their jokes on the audience\'s honest reaction'
    kk = (f'{head_kk}{intro_kk}\n\n📅 {date_num} қараша | {day_kk}\n📍 Carlin Comedy Bar | Сығанақ к-сі, 22/1\n'
          f'⏰ Жиналу: {gather} | Басталуы: {start}\n\nБилеттерді профиль сипаттамасындағы сілтеме арқылы Ticketon сайтынан алыңыз!')
    en = (f'{head_en}{intro_en}\n\n📅 {date_num} November | {day_en}\n📍 Carlin Comedy Bar | 22/1 Syganak St\n'
          f'⏰ Doors: {gather} | Start: {start}\n\nTickets on Ticketon — link in the profile bio!')
    return dict(titleKk=head_kk, titleEn=head_en, descriptionKk=kk, descriptionEn=en)

def quiz(title_kk, title_en, about_kk, about_en, date_num, day_kk, day_en, time):
    tail_kk = f'\n📅 {date_num} қараша ({day_kk})\n⏰ {time}\n📍 Garage Music Bar, Кравцов к-сі, 6\n💵 Бір адамға 3500 тг\n👥 Команда: 2-ден 12 адамға дейін\n📲 Тіркелу: astana.wowquiz.ru'
    tail_en = f'\n📅 {date_num} November ({day_en})\n⏰ {time}\n📍 Garage Music Bar, 6 Kravtsov St\n💵 3,500 KZT per person\n👥 Teams of 2 to 12\n📲 Registration: astana.wowquiz.ru'
    return dict(titleKk=title_kk, titleEn=title_en, descriptionKk=f'{title_kk}\n\n{about_kk}{tail_kk}',
                descriptionEn=f'{title_en}\n\n{about_en}{tail_en}')

BALLET = dict(placeKk='Астана Балет театры', placeEn='Astana Ballet Theatre')
CONGRESS = dict(placeKk='Астана қ., Конгресс-орталық', placeEn='Congress Center, Astana')
CARLIN = dict(placeKk='Carlin Comedy Bar', placeEn='Carlin Comedy Bar')

T = {
    'ig-fb4a1adb78': {**tryout('жексенбі', 'воскресенье', 'Sunday', 15, '18:00', '19:00'), **CARLIN},
    'ig-e64e245ac5': {**tryout('жұма', 'пятница', 'Friday', 13, '18:00', '19:00'), **CARLIN},
    'ig-a8f2305edf': {**tryout('бейсенбі', 'четверг', 'Thursday', 12, '18:00', '19:00'), **CARLIN},
    'ig-b23f942242': {**tryout('сенбі', 'суббота', 'Saturday', 28, '21:15', '21:45', 'Максата Сыдыкова',
                               'Мақсат Сыдықовтың', 'Maksat Sydykov'), **CARLIN},
    'ig-e46c05e840': {**quiz('ӘУЕНДІ ТАП — РЕТРОДИСКОТЕКА! 80-, 90- және 2000-жылдар',
                             'GUESS THE TUNE — RETRO DISCO! 80s, 90s & 00s',
                             '80-, 90- және 2000-жылдардың хиттері: Modern Talking, «Ласковый май», Ace of Base және т.б.',
                             'Hits of the 80s, 90s and 2000s: Modern Talking, Laskovy May, Ace of Base and more.',
                             13, 'жұма', 'Friday', '20:00'),
                      'placeKk': 'Garage Music Bar', 'placeEn': 'Garage Music Bar',
                      'descriptionRu': 'УГАДАЙ МЕЛОДИЮ — РЕТРОДИСКОТЕКА! 80-е, 90-е и 00-е\n\nХиты 80-х, 90-х и 2000-х: Modern Talking, Ласковый май, Ace of Base и др.\n📅 13 ноября (пятница)\n⏰ 20:00\n📍 Garage Music Bar, ул. Кравцова, 6\n💵 3500 тг с человека\n👥 Команда: от 2 до 12 человек\n📲 Регистрация: astana.wowquiz.ru'},
    'ig-25690af769': {**quiz('ТАП. БӘРІ ТУРАЛЫ. 100 СҰРАҚ!', 'GUESS. ABOUT EVERYTHING. 100 QUESTIONS!',
                             '«Бәрі туралы — 100 сұрақ» ойыны: «Р» әрпінен басталатын сөздер, 2010 жыл, ерлер мен әйелдер туралы.',
                             'The “About everything — 100 questions” game: words starting with “R”, the year 2010, men vs women.',
                             14, 'сенбі', 'Saturday', '17:00'),
                      'placeKk': 'Garage Music Bar', 'placeEn': 'Garage Music Bar',
                      'descriptionRu': 'УГАДАЙ. ОБО ВСЁМ. 100 ВОПРОСОВ!\n\nИгра «Обо всём — 100 вопросов»: слова на «Р», 2010 год, мужское/женское.\n📅 14 ноября (суббота)\n⏰ 17:00\n📍 Garage Music Bar, ул. Кравцова, 6\n💵 3500 тг с человека\n👥 Команда: от 2 до 12 человек\n📲 Регистрация: astana.wowquiz.ru'},
    'ig-f339481a43': {'titleRu': 'Bolero X / 13–14 ноября', 'titleKk': 'Bolero X / 13–14 қараша', 'titleEn': 'Bolero X / 13–14 November',
                      'startsAt': '13–14 ноя', **BALLET,
                      'descriptionRu': 'Bolero X / 13–14 ноября. Билеты: Ticketon.kz',
                      'descriptionKk': 'Bolero X / 13–14 қараша. Билеттер: Ticketon.kz',
                      'descriptionEn': 'Bolero X / 13–14 November. Tickets: Ticketon.kz'},
    'ig-6deba215d0': {'titleRu': 'LOVE. FEAR. LOSS | 13–14 ноября', 'titleKk': 'LOVE. FEAR. LOSS | 13–14 қараша',
                      'titleEn': 'LOVE. FEAR. LOSS | 13–14 November', 'startsAt': '13–14 ноя', **BALLET,
                      'descriptionKk': 'Эдит Пиафтың өмірі мен шығармашылығынан шабыт алған қойылым. Билеттер: Ticketon.kz',
                      'descriptionEn': 'A performance inspired by the life and work of Édith Piaf. Tickets: Ticketon.kz'},
    'ig-4b0128409e': {'titleRu': 'Падшие ангелы | 13–14 ноября', 'titleKk': '«Құлаған періштелер» | 13–14 қараша',
                      'titleEn': 'Falling Angels | 13–14 November', 'startsAt': '13–14 ноя', **BALLET,
                      'descriptionKk': '«Құлаған періштелер» | 13–14 қараша. Қоюшысы — Иржи Килиан, музыкасы — Стив Райх (Drumming). Билеттер: astanaballet.com',
                      'descriptionEn': 'Falling Angels | 13–14 November. Choreography by Jiří Kylián, music by Steve Reich (Drumming). Tickets: astanaballet.com'},
    'ig-a7b0e6f559': {'titleRu': 'Щелкунчик', 'titleKk': '«Щелкунчик»', 'titleEn': 'The Nutcracker',
                      'startsAt': '19 дек – 31 дек', **BALLET,
                      'descriptionKk': 'Екі нұсқа: балаларға арналған 50 минуттық — 19, 20, 22, 23 желтоқсан; оркестрмен толық 133 минуттық — 22, 23, 24, 25, 28, 31 желтоқсан. Билеттер: Ticketon.kz',
                      'descriptionEn': 'Two versions: a 50-minute version for children — 19, 20, 22, 23 December; the full 133-minute version with orchestra — 22, 23, 24, 25, 28, 31 December. Tickets: Ticketon.kz'},
    'ig-78a62b74e1': {'titleKk': '«Ұлы дала мұрасы» — Тәуелсіздік күніне арналған концерт, 15 желтоқсан',
                      'titleEn': '“Ұлы дала мұрасы” (Legacy of the Great Steppe) — Independence Day concert, 15 December', **BALLET,
                      'descriptionRu': 'Концертная программа народных танцев (хореография Айгуль Тати). 15 декабря. Билеты: Ticketon.kz',
                      'descriptionEn': 'A folk dance concert programme (choreography by Aigul Tati). 15 December. Tickets: Ticketon.kz'},
    'ig-59447133bd': {'titleRu': '«Пранк махаббат» — перенесён на 24 ноября', 'titleKk': '«Пранк махаббат» — 24 қарашаға ауыстырылды',
                      'titleEn': '“Prank Mahabbat” — moved to 24 November',
                      'placeRu': 'Театр «Жастар», Астана', 'placeKk': 'Жастар театры, Астана', 'placeEn': 'Zhastar Theatre, Astana',
                      'descriptionRu': 'Спектакль «Пранк махаббат», запланированный на 31 октября в 19:00, перенесён на 24 ноября, 19:00. Купленные билеты действительны (Ticketon).',
                      'descriptionEn': 'The play “Prank Mahabbat”, originally scheduled for 31 October at 19:00, has been moved to 24 November at 19:00. Purchased tickets remain valid (Ticketon).'},
    'tg-afisha_orsa-2598': {'titleKk': 'ANI LORAK Астанада!', 'titleEn': 'ANI LORAK in Astana!', 'placeRu': 'Конгресс-центр, Астана', **CONGRESS},
    'tg-afisha_orsa-2590': {'titleKk': 'Баста Астанада!', 'titleEn': 'Basta in Astana!', 'placeRu': 'Конгресс-центр, Астана', **CONGRESS},
    'tg-afisha_orsa-2620': {'titleKk': 'Анжелика Варум мен Леонид Агутин Астанада!', 'titleEn': 'Angelica Varum & Leonid Agutin in Astana!',
                            'placeRu': 'ЦКЗ «Qazaqstan», пр. Мәңгілік ел, 10/1', 'placeKk': '«Qazaqstan» ОКЗ, Мәңгілік ел даңғ., 10/1',
                            'placeEn': 'Qazaqstan Central Concert Hall, 10/1 Mangilik El Ave'},
    'tg-afisha_orsa-2629': {'titleKk': '«Точка Nemo» Астанада!', 'titleEn': 'Tochka Nemo in Astana!',
                            'placeRu': 'Overhead Club, ул. Женис, 36/1', 'placeKk': 'Overhead Club, Жеңіс к-сі, 36/1',
                            'placeEn': 'Overhead Club, 36/1 Zhenis St'},
    'tg-afisha_orsa-2631': {'titleKk': 'Sary Birthday Fest Астанада!', 'titleEn': 'Sary Birthday Fest in Astana!',
                            'placeRu': 'Harat’s Expo, Астана', 'placeKk': 'Harat’s Expo, Астана', 'placeEn': 'Harat’s Expo, Astana'},
    'tg-afisha_orsa-2576': {'titleKk': 'Платина Астанада!', 'titleEn': 'Platina in Astana!',
                            'placeRu': 'Nova, ул. Достык, 3', 'placeKk': 'Nova, Достық к-сі, 3', 'placeEn': 'Nova, 3 Dostyk St'},
    'tg-afisha_orsa-2578': {'titleKk': 'JONY Астанада!', 'titleEn': 'JONY in Astana!',
                            'placeRu': 'Дворец единоборств им. Ж. Ушкемпирова', 'placeKk': 'Ж. Үшкемпіров атындағы Жекпе-жек сарайы',
                            'placeEn': 'Zh. Ushkempirov Martial Arts Palace'},
    'tg-book_oqy-466': {'titleRu': 'Юбилейная 20-я встреча книжного клуба «Казахстанские страницы»',
                        'titleKk': '«Казахстанские страницы» кітап клубының мерейтойлық 20-кездесуі',
                        'titleEn': '20th anniversary meeting of the “Kazakhstan Pages” book club',
                        'placeRu': 'Пространство «Внутри книги»', 'placeKk': '«Внутри книги» кеңістігі', 'placeEn': '“Vnutri Knigi” space'},
    'tg-batylbol_almaty-1776': {'titleRu': 'Разговорный клуб Алматы', 'titleKk': 'Алматы сөйлесу клубы', 'titleEn': 'Almaty Speaking Club',
                                'placeRu': 'КИМЭП, пр. Абая, 2/1', 'placeKk': 'КИМЭП, Абай даңғ., 2/1', 'placeEn': 'KIMEP, 2/1 Abay Ave'},
}
OPERA_HALLS = {'Astana Opera, Большой зал': ('Astana Opera, Үлкен зал', 'Astana Opera, Main Hall'),
               'Astana Opera, Камерный зал': ('Astana Opera, Камералық зал', 'Astana Opera, Chamber Hall'),
               'Гриль-бар Draft': ('Draft гриль-бары', 'Draft Grill Bar')}
PLACE_EN = {'place-almaty-7': 'Medeu'}
REFERENCE = dt.date(2026, 11, 5)

def apply_card(card):
    patch = T.get(card['id'], {})
    card.update(patch)
    if card.get('placeRu') in OPERA_HALLS and card.get('placeKk') == card['placeRu']:
        card['placeKk'], card['placeEn'] = OPERA_HALLS[card['placeRu']]
    if card['id'] in PLACE_EN: card['placeEn'] = PLACE_EN[card['id']]
    if 'startsAt' in patch: schedule.annotate(card, REFERENCE, overwrite=True)

def apply_row(row):  # tools/feed_data.json canonical rows: t/p/d = [ru, kk, en]
    c = dict(id=row['id'], titleRu=row['t'][0], titleKk=row['t'][1], titleEn=row['t'][2],
             placeRu=row['p'][0], placeKk=row['p'][1], placeEn=row['p'][2],
             descriptionRu=row['d'][0], descriptionKk=row['d'][1], descriptionEn=row['d'][2], startsAt=row['st'], isPlace=not row['ev'])
    apply_card(c)
    row['t'] = [c['titleRu'], c['titleKk'], c['titleEn']]
    row['p'] = [c['placeRu'], c['placeKk'], c['placeEn']]
    row['d'] = [c['descriptionRu'], c['descriptionKk'], c['descriptionEn']]
    row['st'] = c['startsAt']

def main():
    feed_path = ROOT / 'feed/events.json'
    feed = json.loads(feed_path.read_text(encoding='utf-8'))
    for card in feed['events'] + feed['places']: apply_card(card)
    raw = json.dumps(feed, ensure_ascii=False, indent=2) + '\n'
    for path in (feed_path, ROOT / 'android/app/src/main/assets/events.json'): path.write_text(raw, encoding='utf-8')
    data_path = ROOT / 'tools/feed_data.json'
    data = json.loads(data_path.read_text(encoding='utf-8'))
    for row in data: apply_row(row)
    data_path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    missing = sorted(set(T) - {c['id'] for c in feed['events']})
    print('translated', len(T), 'missing ids:', missing)

if __name__ == '__main__':
    main()
