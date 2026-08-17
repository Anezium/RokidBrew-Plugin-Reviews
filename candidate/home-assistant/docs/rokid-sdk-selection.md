# Rokid SDK selection

Проверено 2026-07-31 по upstream `Anezium/Rokid-Nexus`, опубликованному GitHub release и каноническому `plugins/sample`.

| Компонент | Выбор | Версия |
|---|---|---:|
| Plugin SDK | `com.github.Anezium.Rokid-Nexus:bus-client` | `sdk-v0.7.0` |
| Plugin API | manifest descriptor | `3` |
| Nexus hubs | phone + glasses | `1.0.48` |
| Repository | JitPack | `https://jitpack.io` |

SDK `0.7.0` — текущий опубликованный plugin SDK. Он транзитивно подключает Nexus `shared`, предоставляет `NexusPluginService`, `NexusSurfaceSession`, typed surface models и телефонные компоненты `NexusUi`/`BusTheme`.

Приложение теперь является одним headless phone plugin APK:

- ровно один exported service с action `com.anezium.rokidbus.action.PLUGIN`;
- plugin id `home-assistant`, API version `3`, capability `surfaces`;
- settings Activity экспортирована без `MAIN/LAUNCHER` filter;
- `minSdk 30`, `targetSdk 36`;
- нет `client-l`, `cxr-service-bridge`, CXR transport, Hi Rokid authorization или отдельной glasses APK.

HUD строится из `NexusCard` и `NexusCardLine`. Свайпы, tap и Back приходят как нормализованные `NexusInputEvent`. При root Back карточка не заявляет `handlesBack`, поэтому Nexus закрывает surface; в режиме выбора элементов `handlesBack=true`, и plugin сначала снимает фокус.

Телефонный settings UI повторяет структуру sample plugin и использует только публичный design kit SDK: `pluginHeader`, `contentColumn`, `sectionRow`, `card`, `navCard`, `pillButton`, `statusLine`, `field` и `uninstallCard`.
