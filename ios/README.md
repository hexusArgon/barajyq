# Barajyq — iOS (App Store)

iOS-копия Android-приложения **Barajyq** (`kz.barajyq.app`): SwiftUI, iOS 16+, iPhone и iPad.
Те же экраны, данные, переводы и логика, что в `../android`. Android-проект не изменялся.

## Что сделано

| Экран | Файл | Что делает |
|---|---|---|
| Язык | `Barajyq/UI/LanguageView.swift` | Первый запуск: қазақша / русский / English. Меняется в настройках, без перезапуска |
| Город | `Barajyq/UI/CitySelectView.swift` | Все 96 городов РК: поиск (kk/ru/latin), популярные города, полный список. Меняется в настройках и по тапу на город в ленте |
| Вход | `Barajyq/UI/SignInView.swift` | **Войти через Google** (обязательный, как на Android) + **Sign in with Apple** (этого требует App Store, см. ниже) |
| Интересы | `Barajyq/UI/OnboardingView.swift` | 12 тем, без блока «исключить» |
| Лента | `Barajyq/UI/FeedView.swift` | Вкладки «События / Места», счётчик, карточки: тема, дата, место и адрес, часы работы, цена, **Маршрут** (2ГИС), «Билеты/Подробнее», источник, **«Не интересно»** с «Отменить» |
| Настройки | `Barajyq/UI/SettingsView.swift` | Язык, город, интересы (минимум одна тема), вернуть скрытое, выйти, **удалить аккаунт и данные** |

Порядок экранов такой же, как в Android `MainActivity`: Язык → Город → Вход → Интересы → Лента.

- **Данные:** `Data/FakeEventsRepository.swift` и `FakePlacesRepository.swift` перенесены 1:1 из Kotlin скриптом. Это Астана (33 события и 15 мест), Алматы (13 и 10), Шымкент (10 и 7) и шаблон для остальных городов (6 и 4). Переводы kk/en взяты из `Translations.swift`, при пустом переводе показывается русский текст.
- **Локализация:** `Resources/{kk,ru,en}.lproj/Localizable.strings` сделаны из Android `strings.xml`, плюс `InfoPlist.strings`. Даты, часы и цены переводятся в `Domain/Localize.swift` (порт `Localize.kt`).
- **Хранение настроек:** `Data/SessionStore.swift` использует `UserDefaults` с теми же ключами, что Android DataStore. Настройки хранятся в контейнере приложения и **сохраняются при обновлениях** из App Store или TestFlight. Пропадают только при удалении приложения.
- **2ГИС:** если у места есть координаты, открывается официальный deep link `dgis://2gis.ru/routeSearch/rsType/car/to/{lon},{lat}╎{название}` — старт = текущее местоположение, поле «Куда поедем» заполнено. Без приложения — `https://2gis.kz/{город}/directions/points/%7C{lon}%2C{lat}`. Без координат — поиск 2ГИС по «место, адрес, город». Как на Android.
- **Иконка:** `Resources/Assets.xcassets/AppIcon` сделана из `branding/qaida-bary-icon-1024.png`. Альфа-канал убран, иначе App Store отклонит иконку.
- **Тема:** цвета взяты из Android (teal #0E7C70, amber #E8863A), только светлая тема.
- **Также добавлены:** `PrivacyInfo.xcprivacy` (privacy manifest), `Barajyq.entitlements` (Sign in with Apple) и `ITSAppUsesNonExemptEncryption = NO` в Info.plist. Последнее нужно, чтобы не отвечать на вопрос про шифрование при каждой загрузке.

### Отличия от Android (сделано намеренно)
- **Добавлен Sign in with Apple.** Правило App Store 4.8 требует его, если в приложении есть вход через Google. Без него ревью не пройти.
- **Добавлена кнопка «Удалить аккаунт и данные».** Это правило 5.1.1(v). Бэкенда пока нет, поэтому кнопка стирает все данные на устройстве и отзывает доступ Google.
- **«Отменить» после «Не интересно»** сделано своим тостом вместо Material Snackbar. Иконки заменены на SF Symbols.
- **Календаря нет**, на Android его тоже нет. Позже можно добавить через EventKit.
- **idToken Google на устройстве не сохраняется:** он нужен только будущему бэкенду. Android его хранит.

## Как открыть и запустить (нужен Mac)

1. Mac с **Xcode 15 или новее** (лучше 16). Xcode бесплатный, ставится из Mac App Store.
2. Откройте `ios/Barajyq.xcodeproj`. Xcode сам скачает Swift Package **GoogleSignIn-iOS 8.x**. Если не скачал: *File → Packages → Resolve Package Versions*.
3. Выберите симулятор iPhone и нажмите ▶︎ Run. Без настройки Google вход через Apple работает только с командой разработчика (шаг 5). На экране входа будет предупреждение «Google не настроен».
4. **Настройка Google Sign-In:**
   1. Откройте [Google Cloud Console](https://console.cloud.google.com/), **тот же проект, что у Android**, затем *APIs & Services → Credentials → Create credentials → OAuth client ID*.
   2. Тип **iOS**, Bundle ID `kz.barajyq.app`.
   3. Скопируйте **Client ID** и **iOS URL scheme** в `Barajyq/Config/Google.xcconfig`:
      ```
      GOOGLE_IOS_CLIENT_ID = 1234567890-xxxx.apps.googleusercontent.com
      GOOGLE_REVERSED_CLIENT_ID = com.googleusercontent.apps.1234567890-xxxx
      ```
   4. Web Client ID и `applicationId` Android **не трогайте**: iOS-клиент отдельный. Экран согласия OAuth общий, он уже настроен для Android.
5. **Подпись:** *Target Barajyq → Signing & Capabilities → Team* (ваш Apple Developer аккаунт). Там же должна быть capability **Sign in with Apple**. Она уже указана в entitlements, Xcode включит её для App ID автоматически.
6. Если `kz.barajyq.app` уже занят в вашем аккаунте или вы хотите другой ID, поменяйте `PRODUCT_BUNDLE_IDENTIFIER` в `Google.xcconfig` (например, `kz.barajyq.app.ios`) и создайте iOS OAuth client под новый ID.

Проект можно пересоздать: `python3 tools/gen_xcodeproj.py` или `xcodegen generate` (по `project.yml`).

> Проект собран и проверен на Linux без Xcode. Логика (данные, локализация дат и цен, хранение настроек) скомпилирована и прогнана через `swiftc`. SwiftUI-экраны можно собрать только на Mac. Если Xcode покажет ошибку, обычно это мелкая правка в одной строке.

## Публикация в App Store: что нужно от вас

1. **Apple Developer Program**, **$99 в год**: <https://developer.apple.com/programs/enroll/>.
   - Как физлицо достаточно Apple ID с двухфакторной аутентификацией.
   - Как организации нужен **D-U-N-S номер** (бесплатно, 1–2 недели).
   - Для Казахстана оплата картой проходит обычно, проверка длится от 1 до 2 дней.
2. **Mac с Xcode.** Без Mac можно собрать через облачные сервисы (Codemagic, Xcode Cloud, GitHub Actions macOS), но Mac сильно проще.
3. В **App Store Connect** (<https://appstoreconnect.apple.com>) нажмите *My Apps → + New App*.
   - Название **Barajyq**, основной язык русский, Bundle ID `kz.barajyq.app`, SKU любой.
   - Категория *Lifestyle* или *Entertainment*.
4. **Скриншоты** обязательны:
   - iPhone 6.9″ (1320×2868) или 6.7″ (1290×2796), от 1 до 10 штук;
   - iPad 13″ (2064×2752), потому что приложение поддерживает iPad. Если iPad не нужен, поставьте `TARGETED_DEVICE_FAMILY = 1`, и тогда хватит скриншотов iPhone.
   - Снять можно в симуляторе: ⌘S.
5. **Тексты** на каждом языке (ru, kk, en): описание, ключевые слова, подзаголовок, URL поддержки.
6. **Privacy Policy URL** обязателен. Подойдёт та же страница, что для Google Play.
   - В разделе *App Privacy* укажите: **Email и Имя** собираются для работы приложения, связаны с пользователем, не используются для трекинга.
   - Это уже прописано в `PrivacyInfo.xcprivacy`.
7. **Возрастной рейтинг** заполняется анкетой. Если в ленте есть бары и ночная жизнь, скорее всего выйдет 12+ или 17+, в зависимости от ответов.
8. **Демо-доступ для ревью:**
   - вход обязателен, поэтому в *App Review Information* дайте тестовый Google-аккаунт;
   - либо напишите, что можно войти через Sign in with Apple.
9. Сборка: *Product → Archive → Distribute App → App Store Connect*. Затем сборка проходит обработку в TestFlight. После этого выберите её в версии и нажмите *Submit for Review*. Ревью обычно занимает 1–3 дня.
10. **Типичные причины отказа**, которые уже учтены:
    - нет Sign in with Apple — добавлен;
    - нет удаления аккаунта — добавлено;
    - у иконки есть альфа-канал — убран.

    Ещё может быть замечание 4.2 «мало функциональности» из-за демо-данных. Чем живее контент, тем легче пройти ревью.

## Структура
```
ios/
├── Barajyq.xcodeproj/          ← открыть в Xcode
├── project.yml                ← для XcodeGen (альтернатива)
├── tools/gen_xcodeproj.py     ← пересоздать .xcodeproj
└── Barajyq/
    ├── App/BarajyqApp.swift    ← @main, RootView (навигация)
    ├── Domain/                ← Models, KazakhstanCities, Localize
    ├── Data/                  ← SessionStore, AuthService (Google/Apple), L10n, Links (2ГИС), Fake*Repository, Translations
    ├── UI/                    ← экраны + Theme/Common
    ├── Config/Google.xcconfig ← GOOGLE_IOS_CLIENT_ID, bundle id, версия
    └── Resources/             ← Info.plist, entitlements, PrivacyInfo, Assets (AppIcon), kk/ru/en.lproj
```
