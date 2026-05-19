# Keeper

A self-hosted Android clone of Google Keep: tile-based notes with time
reminders, backed by a local SQLite database that can be exported and
imported. No cloud, no inter-app integration, no location reminders. The one
network exception is **link previews** — fetching a page title + favicon for
URLs typed into a note (see below).

## Design philosophy (respect this when editing)

This codebase is **deliberately flat and anti-fragmented**. The whole app is
four Kotlin files plus the manifest. There is **no ViewModel, no navigation
graph, no DAO/Room, no MVC/MVP/MVVM**. God objects are intentional. Keep it
readable top-to-bottom without IDE navigation. Prefer adding to an existing
file over creating a new one. Reuse Compose/Material 3 instead of hand-rolling.

## Layout

| File | Role |
|---|---|
| `app/src/main/java/com/example/keeper/Db.kt` | `object Db` — raw `SQLiteOpenHelper`. All CRUD/query/backup. Plain `Note`/`Item`/`Label` data classes. The single source of truth. |
| `.../Notifier.kt` | `object Notifier` + `AlarmReceiver` + `BootReceiver`. Exact-alarm scheduling, repeat math, notification building. |
| `.../LinkPreviews.kt` | `object LinkPreviews`. Link-preview fetching: URL parsing, debounced `HttpURLConnection` fetch of page title + favicon, link-row reconciliation. |
| `.../MainActivity.kt` | All Compose UI: note grid, editor, reminder/label dialogs, drawer. State = one `editing: Note?` flips grid↔editor; DB is re-read after every change. |
| `.../theme/` | Stock Compose Material 3 theme from the template. |
| `app/src/main/AndroidManifest.xml` | Permissions + receiver registration. |

`MainActivity.kt` is large on purpose — that is the god object.

## Data model (SQLite, 5 tables, schema v4)

```
notes(id, title, body, checklist, color, pinned, archived, created, modified,
      reminder_at, reminder_repeat, reminder_fired, position)
items(id, note_id, text, checked, pos)        -- checklist rows, FK cascade
labels(id, name)
note_labels(note_id, label_id)                -- FK cascade
links(id, note_id, url, title, domain, favicon, status)  -- FK cascade,
                                              -- UNIQUE(note_id, url)
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

## Link previews (important invariant)

URLs in a note body are underlined and get a Keep-style preview chip (favicon,
page title, domain). The `links` table caches one row per `(note_id, url)`;
`status` ∈ `LOADING | OK | FAILED`, and **only `OK` rows draw a chip**. Rules,
enforced in `LinkPreviews`:

- A preview is fetched **once**, when the URL first appears, and **never
  refreshed**. Editing a URL yields a different URL string, so it is treated
  as new and fetched fresh; the stale row is dropped by reconciliation.
- A failed fetch is kept as a `FAILED` row, so a dead link is **not retried**
  on every keystroke or note close. `refresh()` skips any URL already cached,
  `OK` *or* `FAILED`.
- Fetching is triggered by the editor 3 s after the body text stabilises
  (a restarting `LaunchedEffect(body)` debounce) and again on note close.
- Work runs on `LinkPreviews`' app-scoped coroutine so a fetch started on
  close still completes. `LinkPreviews.version` is bumped after every link-row
  write; the editor and `App` key a `LaunchedEffect` off it to re-read the DB.
- The "preview" image is just the site favicon (no og:image). A missing
  favicon is fine — the chip renders a blank box. Requires `INTERNET`.

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
- Link previews: a typed URL is underlined; 3 s later an `OK` chip appears
  with favicon + page title + domain; the chip persists and shows on the
  grid tile; tapping it opens the browser. An unresolvable URL becomes a
  `FAILED` row (no chip) and is not re-fetched on further edits. Schema
  v3→v4 migration runs on reinstall.

Not UI-tested but identical code paths: the "Done" / "Tomorrow" notification
actions (same `AlarmReceiver` dispatch as snooze).

## Conventions

- Always commit to master
- Autocommit all changes if no conflicts
- Autocommit CLAUDE.md if changed, don't ask
