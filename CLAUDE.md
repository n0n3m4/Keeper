# Keeper

A self-hosted, fully-offline Android clone of Google Keep: tile-based notes
with time reminders, backed by a local SQLite database that can be exported and
imported. No cloud, no inter-app integration, no location reminders.

## Design philosophy (respect this when editing)

This codebase is **deliberately flat and anti-fragmented**. The whole app is
three Kotlin files plus the manifest. There is **no ViewModel, no navigation
graph, no DAO/Room, no MVC/MVP/MVVM**. God objects are intentional. Keep it
readable top-to-bottom without IDE navigation. Prefer adding to an existing
file over creating a new one. Reuse Compose/Material 3 instead of hand-rolling.

## Layout

| File | Role |
|---|---|
| `app/src/main/java/com/example/keeper/Db.kt` | `object Db` — raw `SQLiteOpenHelper`. All CRUD/query/backup. Plain `Note`/`Item`/`Label` data classes. The single source of truth. |
| `.../Notifier.kt` | `object Notifier` + `AlarmReceiver` + `BootReceiver`. Exact-alarm scheduling, repeat math, notification building. |
| `.../MainActivity.kt` | All Compose UI: note grid, editor, reminder/label dialogs, drawer. State = one `editing: Note?` flips grid↔editor; DB is re-read after every change. |
| `.../theme/` | Stock Compose Material 3 theme from the template. |
| `app/src/main/AndroidManifest.xml` | Permissions + receiver registration. |

`MainActivity.kt` is large on purpose — that is the god object.

## Data model (SQLite, 4 tables, schema v2)

```
notes(id, title, body, checklist, color, pinned, archived,
      created, modified, reminder_at, reminder_repeat, reminder_fired)
items(id, note_id, text, checked, pos)        -- checklist rows, FK cascade
labels(id, name)
note_labels(note_id, label_id)                -- FK cascade
```

- One reminder per note (matches Keep). `reminder_repeat` ∈
  `NONE|DAILY|WEEKLY|MONTHLY|YEARLY`.
- `color` is an index into `NoteColors` in `MainActivity.kt` (0 = theme default).
- WAL is disabled so the DB file is always consistent — backup/restore is a
  plain byte copy of the file via the Storage Access Framework.
- Bump the version in `Db.init` and add an `onUpgrade` branch when changing
  the schema.

## Reminder reliability (important invariant)

`reminder_fired` exists so a **one-time** reminder that already fired never
re-fires on reboot/app-start. Rules, all enforced in `Notifier`/`Db`:

- `Db.withReminders()` (used by `rescheduleAll`) excludes one-time reminders
  where `reminder_fired=1`.
- `Notifier.schedule()` skips a one-time reminder once it has fired.
- `AlarmReceiver` on `FIRE`: repeating → reschedule next occurrence;
  one-time → set `reminder_fired=1`.
- `MainActivity` editor `persist()` clears `reminder_fired` when the user
  changes the reminder time/recurrence.
- A *missed* one-time reminder (device off) still has `reminder_fired=0`, so
  `BootReceiver` arms it and it fires once as catch-up.

Exact alarms use `setExactAndAllowWhileIdle`. The app declares
`USE_EXACT_ALARM` (install-granted, no prompt) and requests a Doze
exemption on launch. `BootReceiver` re-arms every reminder after reboot.

## Build & run

The `android` CLI is installed but only deploys prebuilt APKs; build with
Gradle. **This environment has no system JDK** — a Temurin 17 JDK was
downloaded to `~/jdk-17.0.13+11`. Always export `JAVA_HOME` first:

```sh
export JAVA_HOME=/home/claude/jdk-17.0.13+11
export PATH=$JAVA_HOME/bin:$PATH
./gradlew :app:assembleDebug --no-daemon --console=plain
```

APK: `app/build/outputs/apk/debug/app-debug.apk`.

SDK licenses live in `~/Android/Sdk/licenses/` (already accepted). If a build
fails on a missing SDK package, `android sdk install "<pkg>"` downloads it.

### Deploy to a device/emulator

```sh
~/Android/Sdk/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk
~/Android/Sdk/platform-tools/adb shell am start -n com.example.keeper/.MainActivity
```

Headless emulator (no display in this environment):

```sh
QT_QPA_PLATFORM=offscreen ~/Android/Sdk/emulator/emulator @medium_phone \
  -no-window -no-audio -no-boot-anim -gpu swiftshader_indirect -no-snapshot &
```

`minSdk = 31`, `targetSdk/compileSdk = 36`.

## Verification status

Built, deployed, and exercised end-to-end on an Android 36 emulator:

- Note CRUD (text + checklist), colors, pin, archive, search, delete
- Labels: create / rename / assign / drawer-filter / tile chips
- Reminders: date+time pickers, all repeat options
- Exact alarm scheduling — confirmed via `dumpsys alarm`
  (`exactAllowReason=policy_permission`)
- Notification fires with title/body/BigText, HIGH channel, 3 actions
- Daily repeat reschedules to the next day
- "1 hour" snooze re-arms the alarm +1h and dismisses the notification
- `BootReceiver` re-arms all alarms after `adb reboot` (no Activity launched)
- DB export → valid SQLite file; import → restores a deleted note
- Schema v1→v2 migration runs on reinstall

Not UI-tested but identical code paths: the "Done" / "Tomorrow" notification
actions (same `AlarmReceiver` dispatch as snooze).

## Conventions

- After any DB mutation, call `reload()` (grid) so the UI reflects it. The
  app also reloads on `ON_RESUME` to pick up notification-driven changes.
- Notification/alarm `PendingIntent` request codes are `noteId*10 + slot`
  (slot 0 = fire alarm, 1-3 = action buttons, 5 = content tap). Keep them
  unique per (note, action) — `PendingIntent` equality ignores extras.
- New top-level reminder behavior goes in `Notifier`; new screens/dialogs go
  in `MainActivity.kt`; new persistence goes in `Db`.
