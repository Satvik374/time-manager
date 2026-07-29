# Kairo — AI time manager

A timetable app built around three ideas: every block is a **checklist** you can actually tick
off, every day feeds **real analytics**, and an **AI coach** that edits your schedule through
tools rather than just talking about it.

Two clients, same app:

| | Stack | Data |
|---|---|---|
| **[`app/`](app) — Android** | Kotlin, Jetpack Compose, Material 3, Room, Hilt, WorkManager | on-device (Room) |
| **[`web/`](web) — desktop web** | React 18, TypeScript, Vite | in-browser (`localStorage`) |

Both are local-only. The only thing that leaves the device is what you send to the AI provider you
configure yourself. The two don't share data yet — see [Not built yet](#not-built-yet).

## The five tabs

| Tab | What it does |
|---|---|
| **Today** | Day agenda with expandable checklists, a completion ring, streak and coin badges, and optional coach suggestion cards. Step through any day. |
| **Planner** | Week strip plus a proportional day timeline that shows gaps and clashes at a glance, or a list view of every block you've defined. |
| **Coach** | Chat with the AI. Replies render as formatted text, and it shows exactly which tools it ran. |
| **Insights** | Daily / weekly / monthly analysis — completion rate, trend, category split, time-of-day profile, weekday strengths, month heatmap, and a data table. |
| **Rewards** | Level and XP, coin shop, streak with freeze tokens, and 15 achievements. |

The web client has the same five, in a persistent sidebar instead of a bottom bar, with the
screens re-laid-out for a wide window: multi-column dashboards, hover readouts on every chart, and
keyboard navigation (`1`–`5` for tabs, `n` for a new block). Details in [`web/README.md`](web/README.md).

## The AI layer

### Custom provider

Nothing is hard-wired to one vendor. In **Settings → AI provider** you supply a base URL, an API
key and a model id, and pick the wire format:

- **OpenAI-compatible** — `POST {baseUrl}/chat/completions`, `Authorization: Bearer …`
- **Anthropic Messages** — `POST {baseUrl}/messages`, `x-api-key` + `anthropic-version`

Both dialects are implemented end to end, including tool calls and tool results, so anything that
speaks either protocol works: a hosted API, a gateway, or a model on your own machine. There's a
**Test connection** button that does a real round trip and reports what came back.

Also configurable: temperature, max reply length, and how many turns of conversation are replayed.

### System prompt

The coach's brief ships as an editable default plus three alternate presets (drill sergeant,
gentle accountability, study planner). Live device state — today's date, the local time, your
streak, level, coins, and a performance digest — is appended automatically at send time, so the
model never has to guess and never invents numbers.

### Tool list

Sixteen tools, each individually switchable in Settings, split into read-only and mutating:

| Read | Write |
|---|---|
| `get_schedule` · `get_task` · `search_tasks` | `create_task` · `update_task` · `delete_task` · `move_task` |
| `get_analytics` · `get_progress` | `set_task_completion` |
| `list_rewards` · `list_categories` | `add_checklist_items` · `update_checklist_item` · `remove_checklist_item` |
| | `suggest_recommendation` |

The orchestrator runs a bounded tool loop (max 6 rounds), feeding results back until the model
answers in prose. Failed calls return `{"ok": false, "error": …}` to the model instead of throwing,
so a bad argument produces a retry rather than a dead conversation. Argument parsing is deliberately
forgiving of what models actually emit — `7:30 pm`, `0730`, `19:05`, a comma-separated string where
an array was specified.

Two things the coach deliberately **cannot** do: award XP, coins or streak days, and redeem
rewards. Those are earned and spent by you.

### Formatted replies

Models emit Markdown whether you ask them to or not, so the app renders it rather than showing raw
asterisks. A small built-in parser (no third-party dependency) handles **bold**, *italic*, `code`,
~~strikethrough~~, bullet and numbered lists, headings, block quotes, fenced code blocks, rules,
links and simple pipe tables. Anything it doesn't recognise renders as literal text, so nothing is
ever lost to the parser.

Underscores inside words are deliberately left alone — an app whose own vocabulary is `task_id`
and `get_schedule` cannot afford to italicise half of them. The shipped prompts tell the coach that
Markdown renders, so it formats deliberately rather than by accident.

### Recommendations

Optional and rate-limited. The coach posts at most one suggestion card per interval (default 12h,
configurable 4–72h), and only with evidence from analytics. Cards appear on Today with accept /
dismiss; they never change the schedule on their own. Notifications for them are **off** by
default. You can also ask for one on demand from the Today tab. Turn the whole thing off in
Settings and the app is a plain planner.

## Profile and app lock

Kairo has no account and no server — so "sign in" here means a lock on this device, not a login to
anything. First run asks for a name and offers a PIN; you can skip the PIN and add it later, or
never.

- **PIN** — 4–8 digits, stored as a salted PBKDF2-HMAC-SHA256 digest at 120k iterations. The PIN
  itself is never written anywhere. Verification is a constant-time comparison.
- **Lockout** — escalating cooldowns after 5 wrong attempts (30s → 2m → 5m → 15m), capped so a
  fat-fingered sequence isn't a permanent lockout.
- **Biometrics** — optional fingerprint/face unlock on top of the PIN, offered automatically on the
  lock screen. Never a replacement for the PIN, so removing biometrics can't strand you.
- **Auto-lock** — instantly, or after 1 / 5 / 15 / 60 minutes in the background. Lock state lives in
  memory, so a cold start is always locked.
- **Settings** — rename yourself, change or remove the PIN, toggle biometrics, or lock immediately.

The web client has the same lock — same PBKDF2 parameters, same escalating lockout, via WebCrypto —
minus biometrics, and re-locking on a tab that has been in the background past your grace period.

Scope, stated plainly: this gates the UI. It is not encryption at rest — on Android your data is
protected by the app sandbox, and in the browser it is plain `localStorage` that devtools can read.
And because there is no account, a forgotten PIN can only be cleared by wiping the data. Both lock
screens say exactly that.

## Streaks and rewards

A day counts toward the streak once **60%** of its blocks are done. Rules, all in
`core/StreakEngine.kt` as a pure function with a dedicated test suite:

- A day with nothing scheduled is **neutral** — a real rest day never breaks a streak.
- Missing a past day burns a **freeze token** if you have one; otherwise the streak resets.
- Today is never judged as missed while it's still today.
- Every 7th day grants a freeze token (max 3); milestones at 3 / 7 / 14 / 30 / 100 pay coins.

XP and coins come from completing blocks (scaled by priority), ticking checklist steps, and
perfect days. Levels follow a gentle super-linear curve with titles. Coins buy rewards you define
yourself, plus streak freezes at 120 coins.

## Analytics

All derived from the current task definitions joined with immutable per-day logs:

- Completion rate, focused minutes, perfect days, consistency, period-over-period delta
- Per-day bars (week), month heatmap, completion trend line
- Category donut, six time-of-day bands, weekday strength profile
- A day-by-day table backing every chart

Only **elapsed** days are scored. A week opened on Wednesday still contains Thursday through
Sunday, and counting those as 0% would report the rest of the week as a failure before it had
happened — so rates, best/worst day, the trend line and the weekday profile all stop at today,
while the bar chart still draws the days ahead as pale planned bars.

Chart colours follow a fixed categorical order validated for colour-vision deficiency against the
app's own light and dark surfaces (adjacent-pair CVD ΔE 9.1 light / 8.4 dark, normal-vision 19.6 /
19.3). Three light-mode hues sit under 3:1 contrast, which is why every chart ships visible labels,
a legend, or the table — identity never depends on colour alone.

## Build

**Android** — JDK 17+ and the Android SDK (compileSdk 35, minSdk 26):

```bash
echo "sdk.dir=/path/to/android-sdk" > local.properties
./gradlew :app:assembleDebug        # debug APK
./gradlew :app:testDebugUnitTest    # 92 unit tests
./gradlew :app:assembleRelease      # R8-shrunk release (~2.8 MB)
```

**Web** — Node 18+:

```bash
cd web
npm install
npm run dev                         # http://127.0.0.1:5173
npm run build                       # typecheck + static bundle into web/dist
npm test                            # 65 unit tests
```

157 tests across the two clients. They cover the streak engine (freeze tokens, rest days, clock
jumps, idempotency), recurrence resolution, the level curve, the reward economy, time/date parsing,
both AI wire formats in each direction, PIN hashing and lockout escalation, the analytics selectors
including the elapsed-day rule above, and the Markdown parser — including the `snake_case`
regression the tests caught before it shipped. The web suite mirrors the Android one case for case:
a divergence there is a bug in one of the clients, not a difference of opinion.

## Layout

```
app/src/main/java/com/kairo/timemanager/
  core/        TimeUtils, DayMask, StreakEngine, RecurrenceRules, Economy, Leveling, PinHasher
  data/local/  Room entities, DAOs, seed catalogues
  data/repo/   Task, Analytics, Progress, Chat, Recommendation repositories
  data/prefs/  DataStore-backed settings (AI config, app prefs, auth) + the runtime lock
  ai/          Provider client (two dialects), tool specs, tool executor, coach orchestrator
  work/        Reminder alarms, boot receiver, periodic housekeeping worker
  ui/          Theme, chart/markdown/component library, setup + lock screens, five tabs,
               settings, block editor

web/src/
  core/        time + recurrence rules, ported from the Kotlin core
  data/        types, defaults, localStorage, PBKDF2, selectors (read), mutations (write)
  ai/          provider client, tool specs, executor, coach engine
  components/  charts, Markdown renderer, shared UI primitives
  screens/     the five tabs, plus editor and lock
```

## Not built yet

**Cross-device sync.** The website exists now, but as a second client — not as a backend. Both
apps are local-only: no account, no server, and nothing leaves the device except AI requests. Open
Kairo on your phone and on your laptop and you have two separate timetables.

The data model is shaped for closing that gap — every table has stable ids, every per-day record
is an append-only log keyed by `(entityId, epochDay)`, and the web types mirror the Room entities
field for field — but no sync code exists on either side, and both apps say so plainly in Settings
rather than implying otherwise.
