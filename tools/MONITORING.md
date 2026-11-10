# Утренний мониторинг Barajyq (runbook)

Каждое утро запускается задача Claude, которая работает на ПК Keranis и выполняет этот файл по шагам.
Проект лежит в `C:\Users\eseid\astana-afisha`, в `device_bash` это `$HOME/mnt/astana-afisha`.
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
P=$HOME/mnt/astana-afisha; W=$HOME/work/barajyq; D=$(TZ=Asia/Qyzylorda date +%F)
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
Для каждого handle из `python3 tools/browser_scan.py ig-handles`:
1. `navigate` на `https://www.instagram.com/<handle>/`.
2. `python3 tools/browser_scan.py ig-js <handle>`: выполни выведенный JS через `javascript_tool` и сохрани ответ
   в `tools/scan_tmp/ig-raw-<handle>.json`. JS возвращает только новые посты за 21 день с полной подписью.
3. Из подписей выбери анонсы событий. Для каждого добавь пост в `tools/scan_tmp/ig-scan.json`:
   `{"scannedAt": "<D>", "accounts": [{"handle": "...", "posts": [{"postUrl", "publishedAt": "YYYY-MM-DD",
   "title", "startsAt": "13 ноя, 19:00" | "13–14 ноя" | "6 янв, 7 янв", "place", "caption"}]}]}`.
4. После всех аккаунтов выполни `python3 tools/ig_merge_scan.py tools/scan_tmp/ig-scan.json`
   (если событий нет, пропусти), затем `python3 tools/browser_scan.py ig-seen tools/scan_tmp/ig-raw-*.json`.

## 3. Ручные карточки и редактура
- Новые карточки по отложенным постам: `tools/scan_tmp/cards.json` (список; поля `id` = `tg-<handle>-<n>`,
  `cityId`, `interest`, `reference`, `startsAt`, `titleRu/Kk/En`, `placeRu/Kk/En`, `sourceUrl`, `sourceName`,
  `descriptionRu` = текст поста, `price` если указана, `imageUrl` = поле `image` поста из скана, если есть), затем
  `python3 tools/browser_scan.py add-cards tools/scan_tmp/cards.json`.
- Для каждой карточки, добавленной сегодня импортом, составь `tools/scan_tmp/patch.json`
  (`{id: {titleRu?, titleKk, titleEn, placeRu?, placeKk, placeEn, interest?, price?}}`) и выполни
  `python3 tools/browser_scan.py patch tools/scan_tmp/patch.json`.
- Категории: CONCERTS, THEATER, EXHIBITIONS, CINEMA, STANDUP (стендап и квизы), BARS, SPORT, OUTDOOR, IT, KIDS, FESTIVALS
  (фестивали, лекции, клубы, встречи), FREE.

## 4. Проверка и возврат на ПК
```sh
cd $W && python3 -m unittest discover -s tools/tests 2>&1 | tail -1 && cmp feed/events.json android/app/src/main/assets/events.json
for f in feed/events.json android/app/src/main/assets/events.json tools/feed_data.json tools/tg_review.json \
         tools/tg_import_result.json tools/tg_cursor.json tools/ig_seen.json tools/ig_merge_result.json; do
  [ -f $W/$f ] && cp $W/$f $P/$f; done
mkdir -p $P/_claude/monitoring/$D && cp $W/tools/scan_tmp/* $P/_claude/monitoring/$D/
```
Если тесты упали, ничего не копируй и сообщи.

## 5. Отчёт
Допиши в `$P/_claude/monitoring/log.md` строку: дата, сколько добавлено (TG / IG / вручную), ошибки и стены входа.
Затем сообщи Keranis по-русски (SendUserMessage): список новых событий (название, дата, город, ссылка) и проблемы,
если они были. Если новых событий нет, хватит одной строки. Коммит и push сделает Windows-задача в 10:07.
