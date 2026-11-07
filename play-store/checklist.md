# Barajyq → Google Play: чек-лист

## ✅ Уже готово (05.11.2026)
- [x] targetSdk / compileSdk **36** (Android 16), SDK 36 установлен на PC
- [x] Upload keystore + `keystore.properties`, подпись release в Gradle — см. `SIGNING.md`
- [x] R8 minify + shrinkResources для release, keep-правила для Credentials/Google ID/DataStore; smoke-тест: release-сборка запускается, «Войти через Google» открывает выбор аккаунта, без крэшей
- [x] Подписанный AAB: `play-store/barajyq-0.2.0.aab` (versionCode 2, versionName 0.2.0)
- [x] Тексты витрины kk / ru / en: `listing-*.md`
- [x] Data safety черновик: `data-safety.md`
- [x] Политика конфиденциальности ru + en: `privacy-policy.md`
- [x] Графика: `graphics/icon-512.png`, `graphics/feature-graphic-{ru,kk,en}.png`, скриншоты `graphics/phone-play/*.png` (1080×2160)

## 👤 Только ты
1. **Аккаунт разработчика** — https://play.google.com/console/signup, тип *Personal*, взнос **$25**, верификация личности (ID) и телефона. Публичный email разработчика.
2. **Опубликовать политику конфиденциальности** — вписать `[ИМЯ]` и `[EMAIL]` в `privacy-policy.md`, выложить публично (Google Docs → «Опубликовать в интернете», Google Sites, GitHub Pages или Notion) и получить **публичный URL**.
3. **Create app** в Play Console: имя `Barajyq`, язык по умолчанию (ru-RU или kk-KZ), App, Free, принять декларации.
4. **App content**: Privacy policy URL · Ads = No · App access (инструкция в `data-safety.md`) · Content rating (IARC) · Target audience 13+/18+ · Data safety (по `data-safety.md`) · News/Government = No.
5. **Store listing**: вставить тексты из `listing-*.md` (добавить переводы kk-KZ и en-US), загрузить иконку, feature graphic и 2–8 скриншотов, категория **Events**, контактный email.
6. **Closed testing** (обязательно для личных аккаунтов): создать трек, загрузить `barajyq-0.2.0.aab`, добавить **≥12 тестеров** (Google-группа или список email) — они должны принять приглашение и оставаться в тесте **14 дней подряд**.
7. **После первой загрузки AAB**: App integrity → App signing → SHA-1 → добавить Android OAuth client в Google Cloud (см. `SIGNING.md`). Иначе вход через Google в сборке из Play не заработает. Проверить на тестерах.
8. Через 14 дней → **Apply for production** (ответить на вопросы о тесте) → ревью (обычно 1–7 дней).
9. Сохранить бэкап `android/keystore/kettik-upload.jks` + `credentials.txt`.

## 💡 Рекомендации до релиза
- Убрать `POST_NOTIFICATIONS` из `AndroidManifest.xml`, пока пушей нет (меньше вопросов на ревью).
- Kk-тексты дать вычитать носителю.
- Для каждой новой загрузки +1 к `versionCode`.
