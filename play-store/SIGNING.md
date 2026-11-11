# Подпись релиза Barajyq (kz.barajyq.app)

> Upload-ключ создан заново 2026-11-11 под именем Barajyq, до первой загрузки в Play (старый ключ с прежним названием в Play не загружался).

## Что где лежит (секреты НЕ в git)
| Файл | Box | PC |
|---|---|---|
| Upload keystore | `android/keystore/barajyq-upload.jks` | `C:\Users\eseid\barajyq\android\keystore\barajyq-upload.jks` |
| Пароли + отпечатки | `android/keystore/credentials.txt` | `C:\Users\eseid\barajyq\android\keystore\credentials.txt` |
| Gradle-конфиг подписи | `android/keystore.properties` | `C:\Users\eseid\barajyq\android\keystore.properties` |
| Шаблон (можно в git) | `android/keystore.properties.example` | то же |

`keystore/`, `keystore.properties`, `*.jks` — в `android/.gitignore`.

- Alias: `barajyq-upload` · PKCS12 · RSA 4096 · срок 10000 дней
- Upload cert **SHA-1**: `76:EB:1B:A5:78:EB:6F:35:A9:81:E9:E8:4F:20:40:F6:2F:38:09:E4`
- Upload cert **SHA-256**: `70:F0:56:A4:80:0E:46:BB:71:40:B6:4A:F5:24:B4:E9:EF:3E:FE:40:3F:02:77:36:52:37:7E:C2:52:BA:B9:29`

**Сделай бэкап** `barajyq-upload.jks` + `credentials.txt` (менеджер паролей + флешка/облако). Потеря upload key не фатальна при Play App Signing (можно сбросить через поддержку Play), но это недели ожидания.

## Play App Signing (как это работает)
1. Мы подписываем AAB **upload key** (`barajyq-upload`).
2. При первой загрузке в Play Console соглашаешься на **Play App Signing** (по умолчанию для новых приложений). Google создаёт и хранит **app signing key** и переподписывает им APK для пользователей.
3. Upload key Google только проверяет. Его можно сбросить, app signing key — нет (он у Google).

## ⚠️ Google Sign-In после публикации
Пользователи из Play получают APK, подписанный **ключом Google**, а не нашим. Чтобы вход через Google работал:
1. Play Console → Test and release → **App integrity → App signing** → скопировать **SHA-1 App signing key certificate**.
2. Google Cloud Console (проект с Web Client ID `384742056673-…`) → APIs & Services → Credentials → **Create OAuth client ID → Android**:
   - package `kz.barajyq.app`, SHA-1 = ключ из шага 1.
   - (опционально второй Android-клиент с upload SHA-1 выше — для локальной проверки release-сборки.)
3. Debug-клиент (SHA-1 debug.keystore) оставь как есть.
4. Если OAuth consent screen в режиме *Testing* — добавь тестеров в Test users или переведи в *In production* (для scopes email/profile проверка Google не нужна).

Без шага 2 приложение из Play покажет ошибку входа (выбор аккаунта появится, но токен не выдастся).

## Сборка
```powershell
cd C:\Users\eseid\barajyq\android
$env:JAVA_HOME="C:\Users\eseid\.jdks\jdk-17.0.21+9"   # Gradle 8.9 не работает на JBR 25 из Android Studio
.\gradlew.bat bundleRelease
# -> app\build\outputs\bundle\release\app-release.aab
```
Без `keystore.properties` release соберётся **неподписанным**, debug работает как обычно.

Перед каждой новой загрузкой: поднять `versionCode` (+1) и `versionName` в `app/build.gradle.kts`.

Smoke-проверка минифицированной сборки рядом с debug (отдельный пакет, НЕ загружать в Play):
```powershell
.\gradlew.bat assembleRelease -PreleaseSmoke   # -> kz.barajyq.app.smoke
```
