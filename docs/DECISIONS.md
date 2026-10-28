# Decisions and deviations from PLAN.md

| Date | # | Decision | Why |
|---|---|---|---|
| 2026-10-28 | D4 | **Hand-written recursive-descent parser** (`domain/.../parser/ExprParser.kt`, port of `compileExpr`) instead of exp4j. `ExpressionEngine` interface kept. | The golden parser cases need behaviour exp4j doesn't have: `Unexpected character "$"`, `Missing ")"`, `Expression ends early`, function application without parentheses (`√x`), `x e` → `Unknown symbol "xe"`, and the exact unary-minus/`^` precedence. Porting was ~150 lines with no dependency; all 25 parser cases pass. |
| 2026-10-28 | — | Version catalog pinned to latest stable on 2026-10-28: AGP 9.4.1, Kotlin 2.4.21, KSP 2.3.12, Gradle 9.8.2, Compose BOM 2026.09.00, Hilt 2.60.1, Navigation 2.10.2, Lifecycle 2.11.0, compile/target SDK 37. | PLAN §0 rule. AGP 9 has built-in Kotlin, so Android modules don't apply `kotlin-android`. |
| 2026-10-28 | — | `:domain` adds `Max iterations must be at most 10000` (not in math.js). | PLAN §8 cap. Golden cases unaffected. |
| 2026-10-28 | — | Root solver errors raised mid-run are `RootFailure(message, rows)`, so the UI can keep rows computed before the error. | PLAN §8 ("rows computed so far remain visible"); math.js only returns the message. |
| 2026-10-28 | — | Room, DataStore, MPAndroidChart, Turbine/MockK not yet added. | Added in the phase that first uses them (2, 4, 5), to keep Phase 0 lean. |
| 2026-10-28 | — | Logo updated to the second handoff (S-curve through the root dot, from `docs/assets/logo-sketch.png`). | Owner's logo change; only `docs/assets`, `PLAN.md` §2/§7.2, prototype and screenshots differ between handoffs. `golden.json` identical. |
| 2026-10-28 | — | Fonts bundled as Google Fonts variable TTFs: Geist, Geist Mono, Bricolage Grotesque (`ui/src/main/res/font`). | D5; families taken from the `.cD` scope in `template3.html`. |
