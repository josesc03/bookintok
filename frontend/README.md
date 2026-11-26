# BookinTok · Android app

Being rebuilt on top of the new backend. Planned structure:

- **UI**: Jetpack Compose + Material 3, one ViewModel per screen (MVVM, `StateFlow`)
- **Data**: repositories over the API (Ktor client) and the chat service (WebSocket)
- **Offline**: Room (SQLite) caches exchanges and messages, so past conversations can be read
  without connection; the UI always reads from Room and the network refreshes it
- **Config**: server URLs and the Maps API key come from `local.properties` / build config, never
  from source code
