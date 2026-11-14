# Утренний мониторинг Barajyq (runbook)

Через день (по нечётным числам, 07:47 по Астане) запускается задача Claude, которая работает на ПК Keranis и выполняет этот файл по шагам.
Проект лежит в `C:\Users\eseid\barajyq`, в `device_bash` это `$HOME/mnt/barajyq`.
Shell на ПК (Linux VM) не открывает t.me и instagram.com, а встроенный браузер Claude на том же ПК открывает.
Поэтому страницы читает браузер, а импорт делает Python. Коммит и push делает не Claude, а Windows-задача
`Barajyq\Publish feed` в 10:07 (`tools/windows/publish.bat`).

## Правила (не нарушать)
- Только офлайн-события в Казахстане, которые ещё не прошли. Город, дату, время, площадку и цену берём только из текста
  поста или из страницы источника. Ничего не придумываем: если факта нет, поле остаётся пустым или null.
- Год в «13 ноя» определяется по дате публикации поста (`reference`), а не по сегодняшней дате.
- Подборки, расписания на месяц, отчёты о прошедшем, онлайн-события и розыгрыши в ленту не попадают.
- Названия пишем как название события, без эмодзи и даты. Казахский и английский переводим сами.
  Описание остаётся текстом поста. Короткие подписи (меньше ~400 символов) можно перевести.
- Без входа в аккаунты. Если Instagram показал стену входа, пропускаем аккаунт и пишем об этом в отчёте.

## 0. Рабочая копия (чтобы не удалять файлы в папке пользователя)
```sh
P=$HOME/mnt/barajyq; W=$HOME/work/barajyq; D=$(TZ=Asia/Qyzylorda date +%F)
rm -rf $W && mkdir -p $W/android/app $W/ios/Barajyq && cp -r $P/tools $P/feed $W/ && cp -r $P/android/app/src $W/android/app/ \
  && cp -r $P/ios/Barajyq/Data $W/ios/Barajyq/
mkdir -p $W/tools/scan_tmp && cd $W && python3 -m unittest discover -s tools/tests 2>&1 | tail -1
```
Если базовые тесты не прошли, остановись и сообщи (данные на ПК изменились вручную).

## 1. Telegram (38 каналов за один вызов)
1. `cd $W && python3 tools/browser_scan.py tg-js > tools/scan_tmp/tg.js && cat tools/scan_tmp/tg.js`
2. Во встроенном браузере открой `https://t.me/s/aqyldyclub` (`preview_start`). Если сайт не разрешён, вызови
   `request_access` с scope `site`.
3. Выполни содержимое `tg.js` через `javascript_tool` в этой вкладке без изменений.
4. Сохрани результат JSON целиком (`posts`, `maxIds`, `errors`) в `$W/tools/scan_tmp/tg.json`
   (Write или heredoc с `'EOF'`; поле `image` у постов — обложка, сохраняй как есть), затем выполни `python3 tools/browser_scan.py tg-import tools/scan_tmp/tg.json`.
5. Разбор отложенного: посты из этого скана со статусом `*_review` в `tools/tg_review.json`. Настоящее событие
   с ясными датой, площадкой и городом добавь через `add-cards` (шаг 3). Расписание из нескольких событий
   и неясные посты пропусти.

## 2. Instagram (по аккаунту)
С 2026-11-12 в `ig_sources.json` 72 аккаунта: Астана и Алматы, площадки и организаторы, которые использует keen.events
(у них поле `via: keen.events`). Аккаунтов много: если время поджимает, сначала пройди `kind` venue/concert/standup/museum,
потом cultural/it/dance, бары в конце. Город карточки берётся из поля `city` аккаунта.
Для каждого handle из `python3 tools/browser_scan.py ig-handles`:
1. `navigate` на `https://www.instagram.com/<handle>/`.
2. `python3 tools/browser_scan.py ig-js <handle>`: выполни выведенный JS через `javascript_tool` и сохрани ответ
   в `tools/scan_tmp/ig-raw-<handle>.json`. JS возвращает только новые посты за 21 день с полной подписью.
3. Из подписей выбери анонсы событий. Для каждого добавь пост в `tools/scan_tmp/ig-scan.json`:
   `{"scannedAt": "<D>", "accounts": [{"handle": "...", "posts": [{"postUrl", "publishedAt": "YYYY-MM-DD",
   "title", "startsAt": "13 ноя, 19:00" | "13–14 ноя" | "6 янв, 7 янв", "place", "caption"}]}]}`.
4. После всех аккаунтов выполни `python3 tools/ig_merge_scan.py tools/scan_tmp/ig-scan.json`
   (если событий нет, пропусти), затем `python3 tools/browser_scan.py ig-seen tools/scan_tmp/ig-raw-*.json`.

## 2а. Сайты: ticketon.kz, sxodim.com, kino.kz (обязательно)
Shell сайты не открывает, браузер открывает, и `fetch()` внутри вкладки сайта работает. JS сам обходит все города
(ticketon и kino.kz: 20 городов, sxodim: Астана, Алматы, Шымкент), собирает ссылки, отбрасывает уже виденные
(`tools/web_seen.json`) и читает страницы новых событий (schema.org Event / API kino.kz). Вызов `javascript_tool`
обрывается через 45 с, поэтому скан идёт в фоне:
1. Для каждого SITE из `ticketon.kz sxodim.com kino.kz`: `python3 tools/web_scan.py js SITE 2000 --bg > tools/scan_tmp/web-SITE.js`
   и `cat` этого файла. Открой отдельную вкладку (`preview_start`): `https://ticketon.kz/astana`, `https://sxodim.com/astana/afisha`,
   `https://kino.kz/ru` и выполни JS без изменений. Ответ `"started"` значит, что скан пошёл. Все три можно запустить сразу.
2. Опрос: `python3 tools/web_scan.py poll` даёт JS, выполняй его в той же вкладке раз в 30–60 с, пока `status` не станет `done`
   (полный проход ticketon ~1,5 мин, kino.kz ~30 с, sxodim ~1 мин).
3. Результат кусками: `python3 tools/web_scan.py take SITE 0` даёт JS, выполни его в вкладке. Ответ (~130 000 символов)
   harness не показывает, а сохраняет в файл `/tmp/claude-tool-results/…txt` облачного контейнера и пишет путь; в ответе
   поле `of` = число кусков. Повтори для `take SITE 1`, `2`, … до `of - 1`. Затем в облачном `Bash`:
   `python3 web_chunks.py /mnt/user-data/outputs <все файлы кусков>` (сам `tools/web_chunks.py` сначала передай в облако
   через `device_stage_files`, путь `~/barajyq/tools/web_chunks.py`). Скрипт пишет `/mnt/user-data/outputs/web-SITE.json`
   и печатает `missing` — если не пусто, повтори недостающие куски. Перенос на ПК: `device_commit_files` в
   `~/barajyq/_claude/monitoring/inbox/web-SITE.json`, потом `cp $P/_claude/monitoring/inbox/web-*.json $W/tools/scan_tmp/`.
4. `python3 tools/web_scan.py import tools/scan_tmp/web-*.json`: добавляет карточки (даты, площадка, прошедшие и дубли
   проверяет `browser_scan.add_cards`) и помечает ссылки как виденные. В отчёт: `added` и `skippedByReason` по сайтам.
   Затем убери входящие, чтобы не импортировать их повторно:
   `mkdir -p $P/_claude/monitoring/$D && mv $P/_claude/monitoring/inbox/web-*.json $P/_claude/monitoring/$D/`.
5. Казахский и английский для карточек сайтов можно не переводить: приложение показывает русский текст.

## 3. Ручные карточки и редактура
- Новые карточки по отложенным постам: `tools/scan_tmp/cards.json` (список; поля `id` = `tg-<handle>-<n>`,
  `cityId`, `interest`, `reference`, `startsAt`, `titleRu/Kk/En`, `placeRu/Kk/En`, `sourceUrl`, `sourceName`,
  `descriptionRu` = текст поста, `price` если указана, `imageUrl` = поле `image` поста из скана, если есть), затем
  `python3 tools/browser_scan.py add-cards tools/scan_tmp/cards.json`.
- Для каждой карточки, добавленной сегодня импортом Telegram/Instagram (не `web-*`), составь `tools/scan_tmp/patch.json`
  (`{id: {titleRu?, titleKk, titleEn, placeRu?, placeKk, placeEn, interest?, price?}}`) и выполни
  `python3 tools/browser_scan.py patch tools/scan_tmp/patch.json`.
- Категории: CONCERTS, THEATER, EXHIBITIONS, CINEMA, STANDUP (стендап и квизы), BARS, SPORT, OUTDOOR, IT, KIDS, FESTIVALS
  (фестивали, лекции, клубы, встречи), FREE.

## 4. Проверка и возврат на ПК
```sh
cd $W && python3 -m unittest discover -s tools/tests 2>&1 | tail -1 && cmp feed/events.json android/app/src/main/assets/events.json
for f in feed/events.json android/app/src/main/assets/events.json tools/feed_data.json tools/tg_review.json \
         tools/tg_import_result.json tools/tg_cursor.json tools/ig_seen.json tools/ig_merge_result.json tools/web_seen.json \
         tools/ig_sources.json; do
  [ -f $W/$f ] && cp $W/$f $P/$f; done
mkdir -p $P/_claude/monitoring/$D && cp $W/tools/scan_tmp/* $P/_claude/monitoring/$D/
cd $P && python3 tools/build_feed_site.py   # feed-site/events.json: удалённая лента для всех установленных приложений
```
Если тесты упали, ничего не копируй и сообщи.

## 5. Отчёт
Допиши в `$P/_claude/monitoring/log.md` строку: дата, сколько добавлено (TG / IG / сайты / вручную), ошибки и стены входа.
Затем сообщи Keranis по-русски (SendUserMessage): список новых событий (название, дата, город, ссылка) и проблемы,
если они были. Если новых событий нет, хватит одной строки. Коммит и push (и публикацию `feed-site/` на https://hexusargon.github.io/barajyq-feed/events.json) сделает Windows-задача в 10:07.
