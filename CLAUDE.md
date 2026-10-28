# NumLab — project instructions for Claude Code

Native Android (Kotlin, Jetpack Compose, Material 3) numerical-analysis app. Offline only.

## Source of truth
- `PLAN.md` — scope, architecture, phases, gates. Read it before any task.
- `docs/prototype-src/prototype.html` — behaviour, copy, motion. UI changes must match it.
- `docs/reference/math.js` + `docs/reference/golden.json` — numeric behaviour. Domain tests load `golden.json`; never loosen a tolerance to make a test pass.

## Architecture rules
- Modules: `:app → :ui → :domain`, `:app → :data → :domain`. `:domain` is pure Kotlin/JVM (no Android imports).
- MVVM + unidirectional data flow: one immutable `UiState` per screen, `StateFlow`, events in, effects out. Use cases are single-purpose classes.
- Solvers and sampling run on `Dispatchers.Default`; cancel on new input (`collectLatest`).
- No `INTERNET` permission. No analytics. No `eval`/script engines.
- All strings in `strings.xml`. All numbers through the shared formatter (never `%e`).

## Commands
- Tests: `./gradlew test`
- Lint: `./gradlew lint`
- Debug build: `./gradlew :app:assembleDebug`
- Release bundle: `./gradlew :app:bundleRelease` (needs `keystore.properties`, git-ignored)
- Regenerate golden data: `node docs/reference/gen-golden.mjs` (only when the reference math is deliberately changed; record why in `docs/DECISIONS.md`)

## Workflow
- Work one phase of `PLAN.md` at a time; stop at the gate and report test results.
- Pin dependency versions in `gradle/libs.versions.toml`; stable releases only.
- Commit per phase with a message that names the phase.
- Record any deviation from the plan in `docs/DECISIONS.md`.
