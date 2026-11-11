# Barajyq

Локальный проект афиши Казахстана: Android (Kotlin/Compose), iOS (SwiftUI),
данные событий и мест, импорт публичных анонсов. Это локальный Git-репозиторий:
GitHub/remote и публикация не настроены.

## Структура
- `android/` — приложение `kz.barajyq.app` (пакет кода `kz.astana.afisha`), SDK 36, min SDK 26.
- `ios/` — Xcode-проект; нужен Mac с Xcode 15+ (см. `ios/README.md`).
  iOS использует собственные статические Swift-данные, не Android JSON-feed.
- `feed/events.json` — лента; `android/app/src/main/assets/events.json` — её
  bundled-копия; `tools/feed_data.json` — вход для генераторов нативных данных.
- `tools/` — импорт, конфигурации источников Instagram/Telegram, генераторы и тесты.
- `branding/`, `play-store/` — графика и инструкции; ключи и сборки вне Git.

## Android: локальная сборка
Установите **JDK 17**, Android SDK Platform 36 и задайте `JAVA_HOME`.
SDK укажите через `ANDROID_HOME` либо игнорируемый `android/local.properties`
(`sdk.dir=...`). На Windows из `android/`: `gradlew.bat assembleDebug`.
В текущей копии нет Unix-скрипта `gradlew`: на Linux/macOS используйте установленный
Gradle соответствующей wrapper-версии (`android/gradle/wrapper/gradle-wrapper.properties`).
Android Studio также может открыть каталог `android/`.
Release-подпись локальна: пример `android/keystore.properties.example`, инструкции
`play-store/SIGNING.md`. Без файла подписи debug доступен, release не подписан.
OAuth client ID — публичная конфигурация, не пароль; рабочие ключи не коммитить.

## Данные и импорт
`EVENTS_FEED_URL` сейчас пуст: Android читает bundled assets. Изменение JSON не
обновляет установленное приложение — нужна новая сборка/установка. Сервер не поднят.
iOS не получает эти JSON-обновления автоматически. Генераторы имеют ограничения
по городам и могут перезаписать статические данные: не запускайте их вслепую.

Telegram (38 настроенных публичных каналов):
```sh
python3 -m venv .venv
# активируйте .venv для своей ОС
python3 -m pip install -r tools/requirements-telegram.txt
python3 tools/tg_to_feed.py
python3 -m unittest discover -s tools/tests -v
```
Подробности: `tools/TELEGRAM.md`. Только видимый текст, без OCR и входа в аккаунт;
неоднозначные даты/город и анонсы без явного года остаются на ручную проверку.
Instagram: `tools/ig_sources.json`, `ig_to_feed.py`, `ig_merge_scan.py` — обработка
подготовленных результатов сканирования, не автономный авторизованный клиент.
Импорты и генераторы запускайте последовательно, проверяя отчёты и дубли.
Расписание мониторинга живёт вне репозитория: checkout сам не создаёт рутину,
не запускает фоновые задачи и ничего не публикует.

## Git и приватные файлы
В Git сохраняются исходники, необходимые assets, feed, конфигурации источников и
фикстуры тестов. `.gitignore` исключает подпись, пароли, `.env`, SDK-пути, сборки,
кэши и runtime staging/отчёты. Локальные исключённые файлы сохраняются на диске;
Git/bundle не являются резервной копией ключей. Перед любым push проверяйте diff.
