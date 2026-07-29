# Kairo Web

The desktop client. Same app as Kairo for Android — same five tabs, same AI coach, same streak and
reward rules — re-laid-out for a mouse, a keyboard and a wide screen instead of a thumb.

React 18 + TypeScript + Vite. No UI framework, no chart library, no Markdown library: the charts
and the Markdown renderer are the Android ones ported by hand, so both clients draw the same
picture from the same numbers.

## Run it

```bash
npm install
npm run dev        # http://127.0.0.1:5173
npm run build      # typecheck + production bundle into dist/
npm run preview    # serve the built bundle
npm test           # 65 unit tests
```

Requires Node 18+. `dist/` is a plain static bundle — any static host will serve it.

## What is different from the phone

Not a stretched phone screen. The layout is built for the desktop from the ground up:

- **Persistent sidebar** instead of a bottom nav bar, with live streak/level/coin readouts
- **Multi-column dashboards** — Today runs agenda and stats side by side; Insights lays six charts
  out on a grid rather than a scroll
- **Hover affordances** — every chart has a hover layer with exact values, including a crosshair on
  the trend line, which a touch screen can't offer
- **Keyboard** — `1`–`5` switch tabs, `n` opens the block editor, `Enter` sends in the coach,
  `Shift+Enter` for a newline
- **Dialog-based editor** rather than a full-screen sheet

Everything else is deliberately identical, down to the wording of the empty states.

## Where the data lives

`localStorage`, under one versioned JSON document. That is the whole storage layer — no server, no
account, no network calls except the ones you make to your own AI provider.

Consequences worth stating plainly:

- Data is per-browser and per-profile. A different browser is a different, empty Kairo.
- Clearing site data erases it. **Settings → Export as JSON** writes a backup; the export
  deliberately blanks your API key so a backup file isn't a credential leak.
- The app lock (PIN, PBKDF2-HMAC-SHA256 at 120k iterations via WebCrypto) gates the UI. It is not
  encryption at rest — anyone with the device and devtools can read `localStorage`. The lock screen
  says so.

## Shared shape with Android

Field names mirror the Room entities one-for-one, and the two rule engines are ports of each other
rather than reimplementations:

| Concept | Both clients |
|---|---|
| Calendar day | `epochDay` integer, never a formatted date |
| Time of day | `minuteOfDay`, 0–1439 |
| Recurrence | definitions with a Monday-bit-zero `daysMask`, resolved per day |
| Per-day state | immutable logs keyed `(entityId, epochDay)` |
| Streak | same precedence: rest day neutral → 60% extends → freeze token absorbs → reset |

`src/core/core.test.ts` mirrors the Android unit tests case for case, because any divergence in
these rules is a bug in one of the two clients rather than a difference of opinion.

## Layout

```
src/core/       time + recurrence rules (ports of the Kotlin core)
src/data/       types, defaults, localStorage, PBKDF2, selectors (read), mutations (write)
src/state/      the single app-state provider
src/ai/         provider client (two dialects), tool specs, executor, coach engine
src/components/ charts, Markdown renderer, shared UI primitives
src/screens/    Today, Planner, Coach, Insights, Rewards, Settings, editor, lock
src/styles/     design tokens and per-area stylesheets
scripts/        screenshot harness (seeds realistic data, drives Chromium)
```

## Still not sync

This is the website the Android app's roadmap mentions, built as a client — not as the sync
backend. The two apps hold separate data. Making them one account is a server-side job that
hasn't been started; the data model is shaped for it, and nothing in either UI claims otherwise.
