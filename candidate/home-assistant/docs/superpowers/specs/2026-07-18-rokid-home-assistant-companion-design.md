# Home Assistant for Rokid Nexus — утверждённый дизайн

Исходный дизайн: 2026-07-18  
Миграция на Nexus: 2026-07-31  
OpenSpec change: `build-rokid-home-assistant-companion`

## Цель

Создать headless Rokid Nexus plugin на телефоне. Он хранит доступ к Home Assistant, настраивает страницы, вычисляет контекст, публикует typed HUD surface и выполняет назначенные actions. Nexus отвечает за транспорт и отрисовку на очках.

## Архитектура

- `phone`: `NexusPluginService`, settings Activity на `NexusUi`, Room, Android Keystore, HA WebSocket client, context engine и action gateway.
- `shared`: чистые Kotlin-модели, validation, checksum и state machines.
- `glasses`: прежний CXR-клиент исключён из Gradle и не является deliverable.

SDK: `com.github.Anezium.Rokid-Nexus:bus-client:sdk-v0.7.0`, plugin API 3, capability `surfaces`, `minSdk 30`, `targetSdk 36`.

Plugin не имеет launcher Activity, boot receiver или собственного постоянного background service. Nexus запускает service между `PLUGIN_OPEN` и `PLUGIN_CLOSE`; settings Activity открывается Nexus по explicit component.

## Телефонный UI

Все экраны используют design kit sample plugin:

- фиксированный `pluginHeader` с Nexus glyph;
- чёрно-зелёная палитра `NexusUi`/`BusTheme`;
- `sectionRow`, bordered cards и `navCard` для иерархии;
- pill/outline actions;
- стандартный `uninstallCard` в конце корневого settings screen.

Редактор сохраняет прежние возможности: HA connection, страницы и порядок, Text/Status/Button/Toggle/Progress, literal/entity/template bindings, actions, context rules и atomic publish.

## HUD и управление

Опубликованная страница преобразуется в `NexusCard` с `NexusCardLine`:

- Text — одна или две строки;
- Status — label и badge состояния;
- Button — фокусируемая строка с badge `ACTION`;
- Toggle — фокусируемая строка с badge `ON/OFF`;
- Progress — label и percentage badge.

Nexus доставляет нормализованные DPAD input events. В page mode scroll перелистывает страницы, tap включает control focus, root Back закрывается платформой. В control mode scroll циклически меняет выбранный Button/Toggle, tap выполняет action, Back снимает фокус. Карточка устанавливает `handlesBack=true` только в control mode.

Action выбирается по widget id из активной опубликованной конфигурации, а не из произвольного input payload. При offline действие блокируется. Таймаут с неизвестным результатом не вызывает retry.

## Контекстная страница 0

Контекстный движок выбирает настроенную страницу по boolean Jinja template, priority, order и activation/deactivation delay. Default page используется, когда ни одно правило не активно.

- Активная страница 0 исключается из обычной позиции и учитывается в счётчике один раз.
- Видимая страница 0 закреплена; новое назначение остаётся latest-only pending.
- Runtime values видимой карточки продолжают обновляться.
- Pending применяется при уходе со страницы 0 или `PLUGIN_CLOSE`.
- Если открытая обычная страница становится нулевой, содержимое остаётся на экране, а выбранная позиция становится 0.
- Каждый `onNexusOpen`, включая re-entrant open, применяет pending и начинает с page 0.

## Lifecycle и безопасность

Home Assistant URL/token находятся только на телефоне. Token защищён Android Keystore и не попадает в Room configuration, Nexus descriptor, surface JSON или logs.

`PhoneRuntime` reference-counted. HA socket и template loop работают, только пока открыт Nexus surface или settings Activity. Когда последний owner закрывается, WebSocket отключается и timer loop отменяется.

## Приёмочная проверка

1. Nexus обнаруживает plugin API 3 и запрашивает только Surfaces.
2. Settings screen визуально соответствует sample plugin и позволяет менять/публиковать полный конфиг.
3. При open HUD всегда показывает актуальную page 0.
4. Scroll/tap/Back работают через Nexus input callbacks с двухрежимным фокусом.
5. Настроенные пользователем контекстные правила выбирают нужную page 0 без дубля в счётчике.
6. Tap по выбранной кнопке приводит ровно к одному HA action.
7. При HA offline навигация остаётся доступной, action блокируется.
