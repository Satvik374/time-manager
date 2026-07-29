# Kairo — AI time manager for Android

A timetable app built around three ideas: every block is a **checklist** you can actually tick
off, every day feeds **real analytics**, and an **AI coach** that edits your schedule through
tools rather than just talking about it.

Native Android — Kotlin, Jetpack Compose, Material 3, Room, Hilt, WorkManager. Everything is
stored on-device; the only thing that leaves the phone is what you send to the AI provider you
configure yourself.

## The five tabs

| Tab | What it does |
|---|---|
| **Today** | Day agenda with expandable checklists, a completion ring, streak and coin badges, and optional coach suggestion cards. Step through any day. |
| **Planner** | Week strip plus a proportional day timeline that shows gaps and clashes at a glance, or a list view of every block you've defined. |
| **Coach** | Chat with the AI. Replies render as formatted text, and it shows exactly which tools it ran. |
| **Insights** | Daily / weekly / monthly analysis — completion rate, trend, category split, time-of-day profile, weekday strengths, month heatmap, and a data table. |
| **Rewards** | Level and XP, coin shop, streak with freeze tokens, and 15 achievements. |

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

Scope, stated plainly: this gates the UI. It is not encryption at rest — your data is protected by
Android's app sandbox, as with any app. And because there is no account, a forgotten PIN can only
be cleared by reinstalling, which erases the data. The lock screen says exactly that.

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

Chart colours follow a fixed categorical order validated for colour-vision deficiency against the
app's own light and dark surfaces (adjacent-pair CVD ΔE 9.1 light / 8.4 dark, normal-vision 19.6 /
19.3). Three light-mode hues sit under 3:1 contrast, which is why every chart ships visible labels,
a legend, or the table — identity never depends on colour alone.

## Build

Requires JDK 17+ and the Android SDK (compileSdk 35, minSdk 26).

```bash
echo "sdk.dir=/path/to/android-sdk" > local.properties
./gradlew :app:assembleDebug        # debug APK
./gradlew :app:testDebugUnitTest    # 92 unit tests
./gradlew :app:assembleRelease      # R8-shrunk release (~2.8 MB)
```

Tests cover the streak engine (including freeze tokens, rest days, clock jumps and idempotency),
recurrence resolution, the level curve, the reward economy, time/date parsing, both AI wire formats
in each direction, PIN hashing and lockout escalation, and the Markdown parser — including the
`snake_case` regression the tests caught before it shipped.

## Layout

```
core/        TimeUtils, DayMask, StreakEngine, RecurrenceRules, Economy, Leveling, PinHasher
data/local/  Room entities, DAOs, seed catalogues
data/repo/   Task, Analytics, Progress, Chat, Recommendation repositories
data/prefs/  DataStore-backed settings (AI config, app prefs, auth) + the runtime lock
ai/          Provider client (two dialects), tool specs, tool executor, coach orchestrator
work/        Reminder alarms, boot receiver, periodic housekeeping worker
ui/          Theme, chart/markdown/component library, setup + lock screens, five tabs,
             settings, block editor
```

## Not built yet

**Cross-device sync via a companion website.** The app is local-only today: there is no account,
no server, and nothing leaves the device except AI requests. The data model is already shaped for
it — every table has stable ids and every per-day record is an append-only log keyed by
`(entityId, epochDay)`, which is what a sync layer would need — but no sync code exists, and the
Settings screen says so plainly rather than implying otherwise.
