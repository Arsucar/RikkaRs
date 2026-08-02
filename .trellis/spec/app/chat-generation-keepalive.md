# Chat Generation Keep-Alive FGS Contract

## Scenario: Experimental generation-time specialUse FGS (#219)

### 1. Scope / Trigger

- Trigger: adding, wiring, or mutating generation keep-alive while chat generation runs.
- Applies to: Settings flag, `ChatKeepAliveController`, `ChatGenerationService`, `ChatService` generation lifecycle, `ChatNotificationManager` live-update coordination, notification channel `chat_keepalive`, Manifest specialUse service.

### 2. Signatures

- `Settings.enableKeepAliveNotification: Boolean = false`
- DataStore key: `enable_keep_alive_notification`
- `SettingsStore.updateEnableKeepAliveNotification(enabled: Boolean)` — mutex + single-key edit
- `ChatKeepAliveController.onGenerationStart / onGenerationEnd / onGenerationProgress`
- `ChatGenerationService` actions: `ACTION_START` / `ACTION_UPDATE` / `ACTION_STOP`; `NOTIFICATION_ID = 2002`
- Channel id: `chat_keepalive` (`CHAT_KEEPALIVE_NOTIFICATION_CHANNEL_ID`)

### 3. Contracts

- **Default off**: flag false → zero FGS starts, no keep-alive ongoing notification; live update behaves as before.
- **Partial Settings write only** for the toggle; never full `writeFullSettings` for this switch.
- **Ref-count**: global `AtomicInteger` (+ optional per-conversation counts). Start FGS when count becomes 1 (or FGS not foreground yet); stop only when count returns to 0.
- **Pairing**: every successful `onGenerationStart` must have exactly one matching `onGenerationEnd` on success, cancel, pre-stream failure, and stream failure paths (`ChatService` local `keepAliveStarted` flag).
- **OEM refusal**: `startForeground` / `startForegroundService` failures are logged; generation continues; `isActive` stays false so live update can still show.
- **Dual notification**: when `keepAliveController.isActive` (count > 0 **and** FGS actually foreground), live-update path cancels any prior live notification and merges progress into the FGS ongoing (1000ms throttle). Do not post both.
- **PendingIntent**: opens `RouteActivity` with `conversationId` extra (same pattern as live-update / completion notifications).
- **Stop path**: prefer `startService(ACTION_STOP)` so `onStartCommand` clears FGS; `onDestroy` also `stopForeground(REMOVE)` as safety net. Do not rely on `Context.stopService()` alone to clear the notification.

### 4. Validation & Error Matrix

| Condition | Result |
|-----------|--------|
| Setting off | no start; unpaired end ignored at controller if never started |
| SecurityException / OEM reject FGS | log; `foregroundActive=false`; generation continues; live update allowed |
| Multi concurrent generations | FGS stays until last end |
| Count would go negative | clamp to 0; clear maps; stop |
| No POST_NOTIFICATIONS | UI uses existing permission request flow; no crash |

### 5. Good / Base / Bad Cases

- Good: enable switch → send message → one ongoing notification → end → service gone.
- Base: default install never starts `ChatGenerationService`.
- Bad: full settings rewrite for the toggle; stopping FGS on first of two concurrent gens; posting live update while FGS active.

### 6. Tests Required

- Unit (when harness exists): ref-count start/stop; unpaired end ignored; setting off no-op.
- Manual matrix: switch × permission × success/fail/cancel × multi-conversation; `dumpsys activity services` for `ChatGeneration`.
- Device install after app service/Manifest changes.

### 7. Wrong vs Correct

#### Wrong

```kotlin
context.stopService(Intent(context, ChatGenerationService::class.java))
// alone — may not deliver ACTION_STOP / may leave ongoing notification
```

#### Correct

```kotlin
context.startService(ChatGenerationService.stopIntent(context))
// onStartCommand(ACTION_STOP) → stopForeground(REMOVE) + stopSelf
```

**Related**: WebServerService specialUse pattern; chat live-update notification channels.
