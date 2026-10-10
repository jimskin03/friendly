# Issue #26 — compact response activity

Visual previews of the implemented WebView UI at 393 × 852 using illustrative data.
These screenshots show the web chat area; the native Android top bar is outside it.

- [Completed response](collapsed-mobile.png): one folded Activity bubble, with the chart and answer visible.
- [Expanded activity](expanded-mobile.png): reasoning, tool calls and generation statistics share a scrollable history.

Activity expands during generation and folds when generation finishes or stops. Users can expand completed activity manually. Individual tools and reasoning remain expandable. Pending approval and question forms stay visible outside the folded history. Server-side tools are included. The existing Show stats preference controls statistics.

## Compilation

- `pnpm typecheck` — passed.
- `pnpm build` — passed; embedded WebView assets regenerated.
- `ANDROID_HOME=/home/jimskin03/Android/Sdk ./gradlew :app:compileNightlyDebugKotlin` — passed.

No automated tests were added or run for this change. A device run has not been performed.

Per AGENTS.md, Greg must approve these UI previews before the change reaches `friendly-2.0`.
