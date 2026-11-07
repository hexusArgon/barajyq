# Barajyq — Android skeleton

Kotlin + Jetpack Compose. MVP flow:

1. Google Sign-In через **Credential Manager** (`GoogleAuth.kt`)
2. Анкета: интересы → исключения
3. Лента с демо-событиями + открытие URL источника
4. «В Google Calendar» — заглушка под итерацию 2

## Google Sign-In — настройка

1. [Google Cloud Console](https://console.cloud.google.com/) → APIs & Services → Credentials
2. Создай **OAuth client ID → Web application** — его client id вставь в `GOOGLE_WEB_CLIENT_ID` в `app/build.gradle.kts` (`setServerClientId` ждёт именно Web client)
3. Создай **OAuth client ID → Android**: package `kz.astana.afisha`, SHA-1 debug-ключа:
   `keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android`
4. На устройстве должен быть Google-аккаунт; Play Services актуальны
5. `idToken` после входа готов к проверке на бэкенде (пока только в памяти)

## Открыть у себя

1. Android Studio Ladybug+ → Open → эта папка `android/`
2. Пропиши `GOOGLE_WEB_CLIENT_ID` (шаги выше)
3. Sync Gradle → Run (minSdk 26)

## Продуктовый план

См. `../plan.md`, `../analysis.md`, `../iteration-1/plan.md`.

## Пока нет

- Реальный Credential Manager Sign-In
- Бэкенд ежедневного скана
- FCM-пуши
- Google Calendar API insert

## Release / Google Play

- `compileSdk`/`targetSdk` = **36** (требование Play для новых приложений с 2025-08-31 — API 35+, у нас 36).
- Подпись: `keystore.properties` (gitignore) → `signingConfigs.release`; шаблон `keystore.properties.example`.
- `release`: R8 minify + shrinkResources, правила в `app/proguard-rules.pro`.
- Сборка: `gradlew bundleRelease` с JDK 17 (Gradle 8.9 не запускается на JBR 25). Подробно — `../play-store/SIGNING.md`, чек-лист — `../play-store/checklist.md`.
