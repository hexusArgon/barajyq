# Data safety — черновик ответов (Barajyq 0.2.0, kz.barajyq.app)

Основано на **реальном коде** на 05.11.2026 (Asia/Qyzylorda). Если появится сервер, пуши или Google Calendar — анкету надо обновить.

## Что приложение делает с данными сейчас
| Данные | Откуда | Где хранится | Уходит с устройства? |
|---|---|---|---|
| Google ID, email, имя, ID-токен | Sign in with Google (Credential Manager) | DataStore на устройстве (`session.preferences_pb`) | **Нет** — своего сервера нет, токен никуда не отправляется |
| Интересы, город, язык, скрытые карточки | Выбор пользователя | DataStore на устройстве | **Нет** |
| — | Android Auto Backup | Резервная копия Google (если у пользователя включён бэкап) | Только через системный бэкап Android, шифруется Google, разработчик к нему доступа не имеет |

- Нет аналитики, рекламы, Crashlytics, Firebase, своего бэкенда.
- События и места — встроенные данные в приложении (сеть для ленты не используется). Кнопки «Маршрут» и «Билеты» открывают 2ГИС / браузер — это внешние приложения.
- Разрешения: `INTERNET`, `POST_NOTIFICATIONS` (**уведомления пока не отправляются** — разрешение объявлено на будущее; лучше убрать его из манифеста до релиза, либо не упоминать пуши).
- Google Calendar, пуши, геолокация — **не реализованы**, в анкете не указываем.

## Ответы в Play Console → App content → Data safety

**Вариант A (строгий по правилам Play, рекомендую):** данные, которые обрабатываются только на устройстве, Play не считает «собранными». Наш Google ID/email/имя не покидают устройство → можно ответить «не собираем». Но Sign in with Google — это SDK Google Play services, и ревьюеры часто ожидают раскрытия. Поэтому безопаснее **Вариант B**.

**Вариант B (консервативный — заполнить так):**

1. *Does your app collect or share any of the required user data types?* → **Yes**
2. *Is all of the user data collected by your app encrypted in transit?* → **Yes** (вход идёт через Google по TLS)
3. *Which of the following methods of account creation does your app support?* → **OAuth (Sign in with Google)**. Своих аккаунтов на сервере нет.
4. *Do you provide a way for users to request that their data is deleted?* → **Yes** — «Выйти» в настройках удаляет Google-данные с устройства; «Очистить данные»/удаление приложения удаляет всё. Ссылка — на раздел «Удаление данных» в политике конфиденциальности.

**Data types → Collected (not shared):**
| Категория | Тип | Collected | Shared | Ephemeral? | Required/optional | Purposes |
|---|---|---|---|---|---|---|
| Personal info | **Name** | Yes | No | No | Required (вход обязателен) | App functionality, Account management |
| Personal info | **Email address** | Yes | No | No | Required | App functionality, Account management |
| Personal info | **User IDs** (Google account ID) | Yes | No | No | Required | App functionality, Account management |
| App activity | **Other user-generated content / In-app actions** — интересы, город, скрытые карточки | *Не отмечать* (только на устройстве). Если хотите максимально осторожно — отметьте «App interactions», purpose App functionality, optional | No | — | — | — |

Всё остальное (Location, Financial, Health, Messages, Photos, Audio, Files, Calendar, Contacts, Web browsing, Device IDs, Crash logs, Diagnostics) → **не собираем**.

**Sharing:** ничего не передаём третьим лицам. Данных не продаём. Рекламы нет.

## Связанные разделы App content
- **Ads:** No ads.
- **App access:** Вход обязателен → «All or some functionality is restricted» → инструкция: *«Tap “Sign in with Google” and use any Google account. No special credentials needed. Then choose a language and city (e.g. Astana) and any interests.»*
- **Account deletion:** серверных аккаунтов нет. Если Play спросит URL удаления — дать ссылку на раздел политики «Удаление данных» (там описано: Выйти / Очистить данные / удалить приложение).
- **Target audience:** 18+ (или 13+). Не «для детей» — иначе применятся Families policy.
- **Content rating:** анкета IARC — насилия/азартных игр/UGC нет; есть ссылки на внешние сайты (билеты). Ожидаемо 3+/Everyone. Упоминания баров/ночной жизни — это категория событий, не продажа алкоголя.
- **News app:** No. **Government app:** No. **Health / Financial features:** None.
