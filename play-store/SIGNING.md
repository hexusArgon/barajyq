# Подпись релиза Barajyq (kz.barajyq.app)

> Приложение переименовано в **Barajyq**. Upload-ключ прежний: файл `kettik-upload.jks` и alias `kettik-upload` оставлены как есть намеренно — менять нельзя, иначе сломается подпись / SHA-1 в Google Cloud.

## Что где лежит (секреты НЕ в git)
| Файл | Box | PC |
|---|---|---|
| Upload keystore | `android/keystore/kettik-upload.jks` | `C:\Users\eseid\astana-afisha\android\keystore\kettik-upload.jks` |
| Пароли + отпечатки | `android/keystore/credentials.txt` | `C:\Users\eseid\astana-afisha\android\keystore\credentials.txt` |
| Gradle-конфиг подписи | `android/keystore.properties` | `C:\Users\eseid\astana-afisha\android\keystore.properties` |
| Шаблон (можно в git) | `android/keystore.properties.example` | то же |

`keystore/`, `keystore.properties`, `*.jks` — в `android/.gitignore`.

- Alias: `kettik-upload` · PKCS12 · RSA 4096 · срок 10000 дней
- Upload cert **SHA-1**: `E5:4B:8C:38:74:16:F5:44:7D:EC:D1:B0:6A:47:77:7A:ED:2F:6F:C0`
- Upload cert **SHA-256**: `59:B8:E0:66:7F:59:ED:2B:10:56:C4:BF:75:F5:BC:81:2A:6C:3E:9B:12:CA:B5:9E:27:95:35:D5:7E:86:BF:51`

**Сделай бэкап** `kettik-upload.jks` + `credentials.txt` (менеджер паролей + флешка/облако). Потеря upload key не фатальна при Play App Signing (можно сбросить через поддержку Play), но это недели ожидания.

## Play App Signing (как это работает)
1. Мы подписываем AAB **upload key** (`kettik-upload`).
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
cd C:\Users\eseid\astana-afisha\android
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
