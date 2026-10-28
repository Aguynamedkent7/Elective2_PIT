# NumLab — Implementation Plan for Claude Code

Native Android app (Kotlin · Jetpack Compose · Material 3) for a Numerical Analysis course.
Root finding (Bisection, Newton-Raphson, Secant) and interpolation (Lagrange, Newton divided difference), with iteration tables, error metrics, an interactive graph, step-by-step working and a notebook-style history.

**Source of truth for look, behaviour and numbers is in `docs/`:**

| Path | What it is | How to use it |
|---|---|---|
| `docs/prototype-src/prototype.html` | Working prototype (open in a browser). | Behaviour, copy, spacing, motion. When this plan and the prototype disagree, the prototype wins for UI, `golden.json` wins for numbers. |
| `docs/prototype-src/*.jsx, template3.html` | Prototype source (React). Tokens and every animation are in `template3.html`. | Read for exact colours, easings, durations, copy strings. Do not port React code literally. |
| `docs/reference/math.js` | Reference implementation of the parser, solvers, interpolation, error metrics. | Port to Kotlin function by function. |
| `docs/reference/golden.json` | ~80 generated test cases (roots, parser, interpolation, brackets, point parsing, formatting). | Becomes the domain test suite (section 11). |
| `docs/ui/*.png` | Reference screenshots, light + dark, six screens, plus `overview-*.png`. | Visual acceptance. |
| `docs/assets/` | Logo SVG, adaptive-icon vector drawables (foreground + monochrome), adaptive icon XML. | Copy into `res/`. |

The user wants a signed **.aab** at the end. No backend, no accounts, offline only.

---

## 0. How to run this plan

Work phase by phase (section 12). Every phase ends with a green build, passing tests, and a short demo checklist. Do not start a phase before the previous gate passes. Commit per phase.

Kick-off prompt to give Claude Code:

> Read PLAN.md and docs/. Set up the project per sections 2–3 and complete Phase 0 and Phase 1. Stop at the Phase 1 gate and show me the test report.

Rules of engagement:
- Use the latest **stable** AGP, Kotlin, Compose BOM, Hilt, Room and Navigation at project creation time. Pin them in `gradle/libs.versions.toml` (version catalog). Do not use alphas/betas. Do not hard-code versions from memory; resolve them when you create the project.
- Domain code has **no Android imports**. Anything that touches the framework lives in `:data` or `:app`.
- Never use `eval`-like shortcuts (`ScriptEngine`, `Function`). The expression engine is exp4j (section 4.1) or the fallback hand-written parser.
- All user-visible strings go in `strings.xml`. Numbers are formatted by the shared formatter (section 4.6), never ad-hoc `String.format`.

---

## 1. Product scope

### In scope
1. **Roots** — Bisection, Newton-Raphson, Secant. Inputs: `f(x)`, method-specific starting values, tolerance, max iterations, decimals shown. Outputs: root, convergence badge, iteration table (n, x, f(x), absolute error, relative error %), per-iteration working, interactive graph with a scrubber.
2. **Interpolation** — Lagrange and Newton divided difference. Inputs: data points (table or paste), x to evaluate, optional ground truth (none / function / value). Outputs: P(xₑ), polynomial string, steps (Lagrange basis terms or divided-difference table), error metrics (absolute, relative, relative %, theoretical bound when the true function is known), graph with draggable evaluation line.
3. **Notebook history** — every run can be saved, pinned, reopened, duplicated, re-run with another method, exported (CSV, LaTeX, copy).
4. **Splash, logo, motion** — section 7.
5. Light / dark / system theme, responsive on phones and tablets (foldables: keep content ≤ 640dp wide, centred).

### Out of scope for v1 (do not build)
Other root methods (false position, fixed-point), other interpolation (splines, Hermite), symbolic differentiation, accounts/sync, ads, analytics, in-app purchases. Keep seams so these can be added (section 3.3).

### Product rules that shape the UI
- **Input first.** The home screen is just inputs. The graph is *not* shown until there is a valid input *and* the user presses **Graph**. Until then the Graph button is locked with a caption: "Type a function or tap an example to unlock the graph".
- The graph opens as a full canvas with a bottom sheet (Inputs / Iterations / Working or Inputs / Steps / Errors). Back returns to inputs with state intact.
- History is a first-class screen reached from the header, with a count badge.

---

## 2. Identity

### Logo (final)
A **reversed N** drawn from the owner's sketch (the sketch is at `docs/assets/logo-sketch.png`): a straight **left leg** drops to a sharp minimum, then **one S-shaped curve** rises through the orange **root dot** (the flat middle, like a cubic crossing its root) and stops just short of the right leg. The right leg is a numeral **1** with a flag and a wide foot. The curve never touches the 1. Assets: `docs/assets/logo.svg`, `ic_launcher_foreground.xml`, `ic_launcher_monochrome.xml`, `ic_launcher.xml`.

- Tile `#2F5BEA`, ink white, dot `#FF6A1F` (no cut-out ring; the curve runs through the dot).
- Geometry (64-unit box, stroke 4.6, round caps and joins): leg + curve `M14.5 13 L13.5 50 C17 47 20 41 22 35 C23.5 30.5 26.5 31 30 30.3 C34.5 29.5 37 26.5 38.3 21.5`; the 1 `M47.5 50.5 L49 12 L44.3 18.6`; foot `M39.5 50.5 H54.5` (stroke 3.4); dot `(30, 30.3) r3.9`.
- Adaptive icon: foreground is the mark centred on a 108 dp canvas at scale 1.05 (inside the 66 dp safe zone); background colour `#2F5BEA`; monochrome layer is the same drawing in one colour.
- In-app header: 32 dp tile + wordmark "NumLab" (**Num** ExtraBold, **Lab** Medium at 80 % opacity).

### Colour, type, shape
Take exact values from the CSS variables in `docs/prototype-src/template3.html` (`.cD` scope = app tokens, `[data-cm="dark"]` = dark theme). Map them 1:1 to a Material 3 `ColorScheme` plus a small `NumLabColors` extension (curve, hot/root colour, per-method tints used by history cards). Do not rely on dynamic colour for the brand surfaces; offer **Dynamic colour** as an optional setting that only affects neutral surfaces (default off).

- Typeface: the prototype uses a geometric display face for headings and numbers, and a monospace for numerals and the f(x) field. Use Google Fonts via Downloadable Fonts is fine, but **bundle the fonts in `res/font`** so the app is offline-safe. Check the `<link>` tags in `template3.html` for the families.
- Shape: 16–28 dp radii, pill buttons for primary actions, 12 dp chips.
- Numbers always use tabular figures (`fontFeatureSettings = "tnum"`).

---

## 3. Architecture

### 3.1 Modules
```
:app        Application, MainActivity, navigation graph, theme, splash, DI wiring (Hilt)
:ui         Compose screens, components, charts, motion. Depends on :domain
:domain     Pure Kotlin/JVM. Models, use cases, repositories (interfaces), parser, solvers, formatter
:data       Room (history), DataStore (settings), repository implementations
:di         (optional) Hilt modules. Fine to fold into :app if it stays small
```
Dependency rule: `:app → :ui → :domain`, `:app → :data → :domain`. `:domain` depends only on exp4j and kotlin stdlib/coroutines.

### 3.2 Pattern
MVVM + unidirectional data flow. Each screen has one `UiState` (immutable data class), one `UiEvent` sealed interface, a `ViewModel` exposing `StateFlow<UiState>` and `Channel`/`SharedFlow` for one-off effects (navigation, snackbar, share intent). Use cases are single-purpose classes with `operator fun invoke`.

```
SolveRootUseCase           (RootInput) -> RootResult | DomainError
InterpolateUseCase         (InterpInput) -> InterpResult | DomainError
FindBracketsUseCase        (fx) -> List<Bracket>         // "Suggest interval"
ExplainStepUseCase         (RootResult, index) -> Working
ParsePointsUseCase         (String) -> ParsedPoints
BuildExportUseCase         (Result, Format) -> String    // CSV, LaTeX, plain
SaveRunUseCase / ObserveHistoryUseCase / DeleteRunUseCase / TogglePinUseCase
```
Heavy work (solve, brackets, error bound sampling) runs on `Dispatchers.Default`, results are debounced (see 6.2) and cancelled when inputs change (`collectLatest`).

### 3.3 Extension seams
`RootMethod` and `InterpMethod` are sealed/enum types with a registry; each method owns its input schema, solver and explanation. Adding "False position" later means one new class + one registry line + strings.

### 3.4 Libraries (resolve latest stable)
Compose BOM, Material 3, Navigation Compose, Lifecycle (viewmodel/runtime-compose), Hilt (+ hilt-navigation-compose), Room (+ KSP), DataStore Preferences, core-splashscreen, kotlinx-coroutines, kotlinx-serialization (history JSON blobs), **exp4j**, **MPAndroidChart** (graph, see 6.5), JUnit5 or JUnit4 + Turbine + MockK, Compose UI test, Paparazzi or Roborazzi (screenshot tests, optional but recommended).

Min SDK 26, target/compile = latest stable. `applicationId` = `app.numlab` (open decision D1).

---

## 4. Domain specification (port of `docs/reference/math.js`)

All of this is pinned by `golden.json`. Read `math.js`; it is 600 lines and is the spec.

### 4.1 Expression parser
Goal: parse `f(x)` once, evaluate many times, never crash, always return a human-readable error.

Recommended: **exp4j** with custom functions, behind an interface so it can be swapped.
```kotlin
interface ExpressionEngine { fun compile(src: String): Result<CompiledFn> }
fun interface CompiledFn { fun eval(x: Double): Double }   // throws DomainException
```
Behaviour to match (all in `golden.json → parser`):
- Normalise before parsing: strip all whitespace, `π→pi`, `×→*`, `−→-`, `÷→/`, `√→sqrt`. Case-insensitive identifiers.
- Variable `x`; constants `pi`, `e`.
- Functions: `sin cos tan asin acos atan sinh cosh tanh exp ln log log10 log2 sqrt cbrt abs`. **`log` is the natural log** (same as exp4j and as the prototype). `ln` is a custom function (exp4j has none).
- Operators `+ - * / ^`; `^` is right-associative; **unary minus binds looser than `^`** (`-x^2 = -(x²)`, `2^-1 = 0.5`).
- **Implicit multiplication**: `2x`, `2(x+1)`, `x(x+1)`, `2 sin(x)`, `(x+1)(x-1)`. Whitespace is stripped, so `x e` becomes the unknown symbol `xe` (this is intended and tested).
- Domain errors must be exceptions with the *exact messages* in the golden file, e.g. `Division by zero at x = 0`, `Log of a non-positive number (ln(0))`, `Square root of a negative number (-1.0000)`, non-finite results → `f(x) is undefined at x = …`. Register `ln/log/log10/log2/sqrt/asin/acos` as custom exp4j `Function`s that check the argument and throw; wrap `/` and `^` evaluation or post-check for `Infinity/NaN`.
- Syntax errors map to short messages (`Missing ")"`, `Unexpected "…"`, `Unknown symbol "…"`, `Expression ends early`, `Enter a function of x`).

**Gate:** if more than a handful of `golden.json → parser` cases cannot be made to pass with exp4j after reasonable effort (typically implicit multiplication or unary-minus precedence), replace the engine with a direct Kotlin port of `compileExpr` (≈150 lines, recursive descent, no dependency). Keep the `ExpressionEngine` interface so nothing else changes. Record the decision in `docs/DECISIONS.md`.

### 4.2 Root solvers
Common: validate inputs first (tolerance > 0, max iterations integer ≥ 1). Row model: `n, x, fx, abs, rel%` (+ `lo, hi` for bisection). `rel% = abs/|x|·100`, null when `x = 0` or `abs` null. Stop when `f = 0` or `abs < tol` (strict). Result: `root = last x`, `fRoot = f(root)`, `iterations = rows − initialRows`, `converged`, `finalAbs`, `used` (inputs used).

- **Bisection**: needs `a < b` and `f(a)·f(b) ≤ 0` (error `f(a) and f(b) must have opposite signs`). Row i: `x = (lo+hi)/2`; first row has no `abs` (compare to previous midpoint thereafter). Keep the half where the sign changes.
- **Newton-Raphson**: derivative by central difference `h = 1e-5·max(1,|x|)`. Row 0 is the starting guess (not counted as an iteration). If `|f′| < 1e-12` → error `f′(x) is about 0 at x = …`. Update `xₙ = x − f/f′`.
- **Secant**: rows 0 and 1 are the two starting values (not counted). Loop runs while `i ≤ maxIter + 1`. Errors: `x₀ and x₁ must differ`, `f(xₙ) = f(xₙ₋₁): the secant line is flat`.
- If the iteration limit is hit without meeting the tolerance, return `converged = false` with the rows so far (UI shows an amber "Not converged" badge, not an error).
- All numeric input fields arrive as strings; empty or non-numeric → `Enter a number for <label>`.

### 4.3 Bracket suggestion (`FindBracketsUseCase`)
Scan `[-10, 10]` in 400 steps, find sign changes, ignore poles (|f| at the bisected point > 1e-4 or non-finite), widen to integer endpoints when that still brackets, return up to 4. Used by the "Suggest interval" chips under the bisection inputs.

### 4.4 Interpolation
- Parse rows, ignore fully empty rows, require ≥ 2 points, sort by x, require distinct x (`x values must be distinct`).
- Divided-difference table (kept for display), Newton nested evaluation, Lagrange basis evaluation.
- Expanded polynomial coefficients (Lagrange: sum of scaled basis polynomials; Newton: Horner-style accumulation). `polyToString`: superscript exponents, `−` (U+2212) for negatives, coefficients with 5 significant digits, terms below 1e-9 dropped, `1x → x`.
- Newton form string (`polyForm`) shown in the Steps tab for Newton DD.
- Lagrange steps = list of `(i, xᵢ, yᵢ, Lᵢ(xₑ), yᵢ·Lᵢ(xₑ))`.
- **Error metrics** when ground truth exists (`truth = fn` evaluates `f(xₑ)`, `truth = value` uses the typed number): `|abs error|`, `relative error = (true − P)/true`, `relative % = ·100` (null when true = 0), `|relative %|`.
- **Theoretical bound** only when truth = function: `M/(n+1)! · |∏(xₑ − xᵢ)|`, with `M = max |f⁽ⁿ⁺¹⁾|` estimated by a central finite difference of order n+1 sampled at 61 points over `[min(x₀,xₑ), max(xₙ,xₑ)]`, `h = max((hi−lo)/30, 1e-3)`; only if n+1 ≤ 8, else `null`. Flag `extrapolating` when `xₑ` is outside the data range (UI shows a warning chip). Golden tolerance for the bound is 1e-6 relative.
- Paste parser (`ParsePointsUseCase`): accepts `0 0, 1 0.84`, `(0,0) (1,0.84)`, or one `x y` pair per line; reports odd count.
- Runge preset (7 equal nodes of `1/(1+25x²)` on `[-1,1]`) is generated by `genNodes`.

### 4.5 Working text (`ExplainStepUseCase`)
Returns a list of `(title, formula)` lines plus an optional stop note for the last row. Strings in `math.js → explainStep` are the spec (bisection midpoint and sign test, Newton slope + step, secant formula + with-numbers, "Change" line, stop note). Formulas are plain text with Unicode subscripts; show in a monospace block.

### 4.6 Number formatting (shared, tested)
`fmt(v, decimals=6)`: `—` for null/NaN/∞, `0` for 0, scientific (`toExponential(3)` style, **mantissa e-5 / e+7 without zero padding**) when `|v| < 1e-4` or `≥ 1e7`, otherwise fixed decimals. `fmtE(v)` = 3 significant digits scientific. Do **not** use Java's `%e` (it pads the exponent: `e-05`); write a small formatter and pin it with `golden.json → formatting`. "Decimals shown" (default 6) is a user setting that only affects display, never computation.

### 4.7 Exports
- CSV: `iter,x,f(x),abs_error,rel_error_pct` for roots, `x,y` for interpolation.
- LaTeX: `x^{*} \approx 1.521385` for roots; `P(x) = -0.010383x^{3} - …` for interpolation.
- Plain text "copy result".
Share via `Intent.ACTION_SEND` (text) and, for CSV, a `FileProvider` URI.

---

## 5. Data layer

### 5.1 Room — notebook history
```
HistoryEntity(
  id: Long PK autogen,
  kind: "root" | "interp",
  inputsJson: String,        // serialised RootInput / InterpInput (schema-versioned)
  pinned: Boolean,
  createdAt: Long, updatedAt: Long,
  title: String?             // optional user title (future)
)
```
- Store **inputs only**, not results. Re-solve on open (solvers are cheap and deterministic), so the history never contains stale numbers if an algorithm is fixed.
- Card summary (root value, iterations, converged, sparkline, mini plot) is computed lazily in the ViewModel from the inputs and cached in memory.
- Dedupe on save: if the JSON of the inputs equals the most recent entry's, update `updatedAt` instead of inserting.
- Cap at 200 entries (drop oldest unpinned).
- Export/import all history as one JSON file (post-v1, but keep the serializer versioned).
- Seed three sample entries on first launch only (flag in DataStore): bisection `x³−x−2` (pinned), Lagrange sin 4-point, Newton `eˣ−3x`. Marked "Sample".

### 5.2 DataStore — settings
`themeMode (system|light|dark)`, `dynamicColor`, `decimals`, `defaultTolerance`, `defaultMaxIter`, `reduceMotionOverride`, `seededHistory`, `lastModule (roots|interp)`, `lastRootInputs`/`lastInterpInputs` (draft restore, decision D2), `hapticsEnabled`.

---

## 6. UI specification

Match `docs/ui/*.png` and the live prototype. Below is the structure and the behaviour that is not obvious from pictures.

### 6.1 Navigation
Single `NavHost`: `home` → `canvas/{module}` (graph) → back; `home` → `history`; `history` → `home` (after "Open") or `canvas`. No bottom navigation bar. Back from canvas keeps the sheet state; back from history returns to the exact previous scroll position.
Transitions: see section 7; default `NavHost` transitions are replaced by the custom ones (reveal, slide-up).

### 6.2 Home (input first)
Top bar: logo + wordmark, undo, redo, history button with a badge showing the count of saved runs.
Body (scrollable column, max width 640 dp):
1. **Module switch** (segmented): Roots · Interpolate.
2. **Inputs card**
   - *Roots*: method segmented (Bisection · Newton · Secant); **f(x) field** (monospace, placeholder `e.g. x^3 - x - 2`, no error state while empty) with a keyboard-accessory row of insert chips (`x ^ √ sin cos tan …`, horizontally scrollable); **Examples** chips (5 presets); method-specific numeric steppers with −/+ (Bisection: a, b with "Suggest interval" chips from `FindBracketsUseCase`; Newton: x₀; Secant: x₀, x₁); Tolerance, Max iterations, Decimals steppers.
   - *Interpolate*: method segmented (Lagrange · Newton DD); **Rows / Paste** toggle; table of x,y rows (start with 3 blank rows, add/remove, Enter moves to next cell); paste box using `ParsePointsUseCase` with live "N points" feedback; x to evaluate; **Ground truth** selector (None · f(x) · Value) with its field; Examples chips (sin 4 pts, eˣ 3 pts, Runge 7 nodes).
3. **Result card** appears as soon as the current input is valid (debounce 250 ms): root / P(xₑ) with tween (7.4), convergence badge, "N iterations · last change …", sparkline, buttons `Iterations` and `Working` (roots) / `Steps` and `Errors` (interp) that expand inline (Iterations → table, Working → per-step explanation).
4. **Bottom bar** (pinned): **Graph** button and **Save**.
   - Graph is *locked* (outlined, lock icon, caption above) until the input compiles and solves/interpolates without error. On unlock it plays the *ready pulse* (7.4).
   - Pressing Graph opens the canvas with the circular reveal from the button.
   - Save stores a history entry and shows a snackbar with Undo; also auto-saves when the graph is opened (deduped).
Errors: shown inline under the field in red with the exact domain message; the result card is replaced by a neutral "Fix the input to see results" state. Never show a stack trace or a toast for input errors.

### 6.3 Canvas (graph)
Full-screen plot with a draggable bottom sheet (`AnchoredDraggable`, three anchors: peek ≈ 140 dp, half, full). Top bar: back, the function title (e.g. `x^3 - x - 2`), undo/redo, share menu.
- *Roots*: curve f(x), x-axis, root marker (orange dot with pulse ring and label `x* ≈ 1.521385`), dashed vertical guide, iteration trail. **Scrubber/player** at the sheet's peek: play/pause, step back/forward, slider "n / N"; the canvas highlights the current iterate and, for bisection, shades the bracket [lo, hi]; for Newton draws the tangent; for Secant the secant line. Autoplay once on first open (speed `clamp(2600 / lastIteration, 150, 650)` ms per step) unless reduced motion.
- *Interpolation*: true function (dashed, if known), P(x) (solid), data points (draggable vertically: editing y re-interpolates live), evaluation vertical line (draggable horizontally, writes back to `xEval`), marker `P(0.900)`; zoom and pan; reset-view button.
- Sheet tabs: *Inputs · Iterations · Working* (roots) or *Inputs · Steps · Errors* (interp). Inputs tab reuses the same composables as Home, bound to the same state.
- Plot controls: zoom in/out, reset. Pinch zoom and pan. Auto domain from roots/points with padding; guard against huge values (`|y| > 1e100` → treat as NaN gap).

### 6.4 History ("notebook")
Header: collapse chevron, "History", run count. Filter chips: All · Roots · Interpolation · Pinned. Helper text: "Runs are saved when you open the graph or tap Save. Swipe a card left to delete."
Card (tinted by method via `--mc-*` tokens): method chip + "Sample/Just now/Yesterday", pin toggle, overflow menu; `f(x) = …` or "7 points · evaluate at x = 0.9"; big result number (tween); convergence chip; "17 iterations · last change 7.63e-6"; sparkline (roots) / degree + error + bound chips (interp); quick actions `Graph`, `Table`/`Steps`, `Working`/`Errors` which expand inline; **Try other method** chips (re-run the same inputs with another method, preview inline, "Open" to adopt); primary `Open` button.
Overflow menu: Open inputs, Open graph, Duplicate, Copy result, Copy CSV, Copy LaTeX, Delete. Swipe-to-delete with Undo snackbar. Empty state with illustration and "Run something and it will show up here".

### 6.5 Graph implementation
Use **MPAndroidChart** (`LineChart`/`ScatterChart` via `AndroidView`) for the curve, axes, grid, zoom/pan and data-point dragging. Draw the things MPAndroidChart is bad at in a **Compose `Canvas` overlay** aligned with the chart's `ViewPortHandler` transform: root marker + pulse ring, bracket shading, tangent/secant lines, evaluation line + label, draw-on animation. Sample the function at ~600 points across the visible x-range on `Dispatchers.Default`, re-sample (debounced) on zoom/pan; break the line at non-finite values. If the overlay alignment proves fragile, fall back to a fully custom Compose `Canvas` plot (the prototype's plot is ~250 lines of SVG logic in `chart2.jsx` and is a straightforward port); record the decision.

### 6.6 Settings (small sheet from the overflow menu)
Theme (system/light/dark), Dynamic colour (off), Decimals, default tolerance/max iterations, Haptics, "Reduce motion" (follows system, with override), About (version, licences, privacy: "No data leaves your device").

### 6.7 Accessibility and input quality
- Touch targets ≥ 48 dp; content descriptions on every icon button; the plot exposes a text alternative ("Graph of f(x). Root at x ≈ 1.521385.").
- The root and interpolation values are announced (`liveRegion = Polite`) when they change.
- Contrast ≥ 4.5:1 in both themes (the tokens in `template3.html` were chosen for this; re-check any change).
- Font scaling up to 200 %: steppers and bottom bar must reflow, never clip.
- Keyboard: `f(x)` field uses a text keyboard with `KeyboardType.Ascii`, `imeAction = Done`; numeric fields use decimal keyboards that allow `-` and `e`.
- Haptics: light tick on stepper change and on the Graph unlock; confirm on Save. Respect the setting.

---

## 7. Splash and motion design

Reference: the prototype (`Replay splash` button in the page header), `template3.html` for CSS, and the "Motion spec" table on the prototype page. Implement with Compose `animate*AsState`, `Animatable`, `updateTransition`, `AnimatedVisibility`, `graphicsLayer`, `Path` + `PathMeasure` for draw-on.

### 7.1 Easing tokens (define once in `Motion.kt`)
| Token | Cubic-bezier | Use |
|---|---|---|
| `Decelerate` | per `--ease-dec` | entering, settle |
| `Accelerate` | per `--ease-acc` | exiting |
| `Standard` | per `--ease-std` | state changes, draw-on |
| `Spring` | per `--ease-spring` (slight overshoot) | pops, marker landings |
Copy the exact control points from `template3.html`.

### 7.2 Splash (≈ 2.7 s, tap to skip)
Android 12+ system splash is configured with `core-splashscreen`: window background = app background, animated icon = a short AVD (or static adaptive icon on devices that don't animate), `postSplashScreenTheme` = app theme, and a keep-on-screen condition until the first frame is ready. Then an **in-app splash composable** continues seamlessly and plays the brand sequence:
| t (s) | Event |
|---|---|
| 0.00 | Tile pops (scale .35 → 1, Spring, .6 s) |
| 0.60 | Left leg + S-curve draw on (0.8 s, Standard) |
| 1.20 | The 1's foot draws (0.35 s) |
| 1.30 | The 1 (stem, then flag) draws (0.5 s) |
| 1.75 | Root dot pops on the curve with overshoot (0.5 s, Spring) |
| 1.80 | Ring pulse from the dot (scale .7 → 3.4, fade out, 0.9 s) |
| 1.90 + 45 ms·i | Wordmark letters rise in, staggered |
| 1.40 | "Tap to skip" hint fades in |
| 2.70 | Whole splash lifts away (0.52 s, Accelerate); Home enters with the stagger (7.3) |
Reduced motion: no draw-on or pops; a 0.9 s cross-fade to Home.
Don't make the cold start slower than needed: initialise Room/DataStore in parallel with the splash, and start the splash exit as soon as both the animation and the first state are ready (never wait on the 2.7 s if the user taps).

### 7.3 Screen-level motion
| Element | Motion |
|---|---|
| Home first appearance | Children rise 12 dp + fade, stagger 55 ms, Decelerate, 420 ms |
| Module switch | Cross-fade + 8 dp horizontal shift of the inputs card, 260 ms; result card re-tweens |
| Result card appear | Rise + fade 320 ms; badge pops with Spring |
| Graph → canvas | **Circular reveal** from the Graph button centre (`clipPath` circle, radius 0 → screen diagonal, 440 ms Decelerate); reverse on back (380 ms Accelerate) |
| History open/close | Slide-up sheet 380–440 ms; cards enter with 40 ms stagger (`card-in`) |
| Card expand (Graph/Table/Working) | Height animate 260 ms Standard, content cross-fade |
| Swipe to delete | Follows finger, then collapses height 200 ms |
| Pin | Icon rotate + scale Spring 280 ms |

### 7.4 Data-driven motion
- **Curve draw-on**: first time a curve appears, trim path 0 → 1 over 700 ms Standard; later updates cross-fade (no re-draw while the user is typing).
- **Root marker**: pops (Spring) then a single pulse ring (scale to 3×, fade, 900 ms).
- **Number tween** (`BigNumber`): animate from previous to new value in 560 ms, `easeOutQuart`; use the same decimals; skip when reduced motion.
- **Graph ready pulse**: when the Graph button unlocks, one soft ring pulse and the icon swaps lock → graph with a 180 ms cross-rotate.
- **Iteration player**: current iterate marker slides along the curve (Standard, ≤ the step duration); bracket shading animates its edges.
- **Interpolation**: changing a node's y morphs P(x) by interpolating samples for 180 ms; dragging is immediate (no animation).

### 7.5 Reduced motion
Read `Settings.Global.ANIMATOR_DURATION_SCALE == 0` and the in-app override. In reduced mode: no stagger, no draw-on, no tweens, no pulses, no autoplay; transitions become 120 ms fades. Never gate functionality on animation.

### 7.6 Performance budget
60 fps on a mid-range device (≈ Pixel 6a class); no allocations in `draw`; hoist `Path`/`Paint`; use `graphicsLayer` for transforms; avoid recomposing the whole screen on tween ticks (read animated values in the draw/layout phase, `drawWithContent`, or pass lambdas).

---

## 8. State, errors and edge cases (acceptance behaviours)

- Empty input: no error text, no result card, Graph locked.
- Invalid f(x): inline error with the exact message; keep the last valid result hidden, not stale.
- Domain errors during iteration (e.g. `ln(x)` stepping below 0): the run stops with the domain message shown in the result area; rows computed so far remain visible in the table.
- Bisection without sign change: error message plus **Suggest interval** chips.
- Not converged: amber badge "Not converged after N iterations", last change shown, never labelled as an error.
- Newton with `f′ ≈ 0`, Secant with flat line: clear message, the starting rows are kept.
- Huge / tiny numbers: formatter switches to scientific; plot ignores |y| > 1e100.
- Orientation/process death: inputs survive via `SavedStateHandle`; draft restore across launches per decision D2.
- Large tables: iteration table is a `LazyColumn` with a sticky header; max iterations is capped at 10 000 (validation message).
- Interpolation with 30+ points: warn that high-degree polynomials oscillate; no hard limit below 100 points.

---

## 9. Build, signing and release

- `build.gradle.kts` per module, version catalog, `kotlin { jvmToolchain(17) }` (or the toolchain the latest AGP requires).
- `release` build type: R8 full mode, resource shrinking, `isDebuggable = false`. Keep rules for: exp4j (reflection-free but keep `net.objecthunter.exp4j.**` to be safe), MPAndroidChart, Room, kotlinx-serialization, Hilt (generated rules suffice). Verify the release build runs the full golden-test flow on a device (a `release` instrumentation smoke test).
- Signing: generate an upload keystore **outside the repo**, read from `keystore.properties` (git-ignored) or CI secrets; Gradle config reads it if present and otherwise leaves the release unsigned. Document the exact `keytool` command in `docs/RELEASE.md`. Enrol in Play App Signing when publishing.
- Output: `./gradlew :app:bundleRelease` → `app/build/outputs/bundle/release/app-release.aab`. Version name from the catalog, version code from a single integer in `gradle.properties`.
- Pre-launch checks: lint clean (no errors), `strict mode` clean in debug, ANR/crash-free on API 26, 30, 34 and latest, tablet and foldable emulator, TalkBack pass, font scale 200 %, dark/light, RTL pseudo-locale, bundle size < 15 MB.
- Play listing assets are out of scope; do generate a 512 px icon from `logo.svg`.
- Privacy: no network permission at all (`INTERNET` must not appear in the manifest). Data safety form: "no data collected".

---

## 10. Repository layout
```
numlab/
  CLAUDE.md                     # copy of CLAUDE.md.template from the handoff
  PLAN.md  docs/                # this handoff, committed as reference
  gradle/libs.versions.toml
  app/        src/main/{kotlin,res}   (MainActivity, NavGraph, NumLabApp)
  ui/         src/main/kotlin/app/numlab/ui/{theme,components,home,canvas,history,splash,motion,chart}
  domain/     src/main/kotlin/app/numlab/domain/{model,parser,roots,interp,format,export,usecase}
              src/test/kotlin/...  src/test/resources/golden.json
  data/       src/main/kotlin/app/numlab/data/{db,prefs,repo}
```
Package root `app.numlab` (decision D1).

---

## 11. Testing strategy

1. **Golden-driven domain tests (the main safety net).** Copy `docs/reference/golden.json` to `domain/src/test/resources/`. Write parameterised tests that load it and assert: parser values and error messages, every root case (root, iterations, converged, each row's n/x/fx/abs/rel), interpolation (Pxe, coefficients 1e-9 relative, poly string, dd table, basis terms, metrics, bound 1e-6 relative), brackets, point parsing, formatting. A failing golden case is a bug in the port, not in the test. If the reference is wrong, fix `math.js`, regenerate with `node docs/reference/gen-golden.mjs`, and note it in `docs/DECISIONS.md`.
2. **Property tests** (kotest-property or hand-rolled loops): random cubics with a known root → bisection/secant/Newton agree within tolerance; polynomial through n+1 random points reproduces all points (Lagrange and Newton agree to 1e-9); parser never throws anything but `DomainException` on fuzzed strings (fuzz with random token sequences, 10 000 cases).
3. **ViewModel tests** with Turbine: empty → valid → invalid → valid transitions, debounce, cancellation, Graph unlock rules, save dedupe, history ops.
4. **Room tests** (instrumented or Robolectric): insert/dedupe/cap/pin/delete/order.
5. **Compose UI tests**: input → result → Graph button unlocks → canvas opens; history card expand/open; swipe delete + undo; reduced-motion path.
6. **Screenshot tests** (Paparazzi/Roborazzi) for Home empty/result/error, Canvas roots/interp, History, Splash end state, light + dark + 200 % font. Compare visually against `docs/ui/*.png` during review; don't pixel-diff against the PNGs (different renderer).
7. **Performance**: Macrobenchmark for cold start (target < 700 ms to first frame on mid-range) and a jank check on graph pan/zoom.

CI (optional): GitHub Actions running `./gradlew test lint assembleDebug` on PR.

---

## 12. Phased work plan with gates

**Phase 0 — Scaffold (½ day)**
Create the multi-module project, version catalog, Hilt, theme (tokens from the prototype), Navigation with empty screens, adaptive icon, `core-splashscreen`, CI.
*Gate:* app installs, shows empty Home with correct colours in light/dark, correct icon (round, themed, monochrome).

**Phase 1 — Domain (1–2 days)** — no UI.
Parser (exp4j + custom functions or fallback port), formatter, solvers, interpolation, errors, brackets, explain-step, parse-points, exports. Golden tests wired.
*Gate:* 100 % of `golden.json` passes; property tests pass; `:domain` has no Android dependency.

**Phase 2 — Home: roots (2 days)**
Inputs card, f(x) field with chips and examples, steppers, debounced solve, result card with tween, iterations table, working view, Graph lock/unlock, error states.
*Gate:* walk through the "Acceptance script A" below on a device.

**Phase 3 — Home: interpolation (1–2 days)**
Rows/Paste, truth selector, examples (incl. Runge), result card, steps, errors tab with metrics and bound.
*Gate:* Acceptance script B.

**Phase 4 — Canvas & graph (3 days)**
MPAndroidChart + overlay (or custom Canvas), sampling, zoom/pan, root/iteration player, interpolation drag, bottom sheet with tabs, reveal transition.
*Gate:* Acceptance script C; 60 fps pan/zoom; no overlay misalignment at any zoom.

**Phase 5 — History (2 days)**
Room, repository, save/dedupe/seed, History screen, cards, try-other-method, exports, swipe delete + undo, badge on Home.
*Gate:* Acceptance script D; process-death restore OK.

**Phase 6 — Splash & motion polish (1–2 days)**
System splash + animated brand splash, all motion in section 7, reduced-motion path, haptics, tween polish.
*Gate:* side-by-side with the prototype at 0.5× speed; reduced-motion QA.

**Phase 7 — Settings, a11y, hardening (1–2 days)**
Settings sheet, TalkBack pass, font scale, tablets, RTL, edge cases from section 8, lint and R8 rules, screenshot tests.
*Gate:* section 9 pre-launch checklist.

**Phase 8 — Release**
Signing, `bundleRelease`, smoke test of the release build on device, deliver `app-release.aab` + `docs/RELEASE.md` + mapping file.

### Acceptance scripts
**A — Roots.** Fresh install → Home is inputs only, Graph locked with caption. Tap example `x³ − x − 2` → result appears: **1.521385**, "Converged", 17 iterations, last change 7.63e-6; Graph unlocks with a pulse. Switch to Newton (x₀ = 1.5) → converges in 3 iterations (1.521380); Secant (1, 2) converges in 6. Type `ln(x)+x-2` with bisection a=−1, b=2 → inline error `Log of a non-positive number (ln(-1.0000))`; tap the suggested interval chip `[1, 2]` → result appears. Enter `x^2+1` bisection 0..1 → "f(a) and f(b) must have opposite signs". Max iterations 3 on the default → amber "Not converged".
**B — Interpolation.** Interpolate → paste `0 0, 1 0.8415, 2 0.9093, 3 0.1411`, x = 1.5, truth f(x) = `sin(x)` → P(1.5) = **0.976006**, polynomial `−0.010383x³ − 0.3557x² + 1.2076x`, abs error 0.0215, theoretical bound ≈ 0.0234. Runge example (7 equal nodes on [−1, 1]) at x = 0.9 → visibly large error and a large bound; Lagrange and Newton DD give the same P(0.9).
**C — Canvas.** Press Graph → circular reveal; curve draws on; root marker pops with a ring; iteration player autoplays once; scrub to step 5 → marker and bracket update; drag the sheet to full → Iterations table; rotate the device → state preserved. Interpolation: drag a data point → polynomial updates live; drag the evaluation line → xₑ field updates.
**D — History.** Open graph on two runs → both appear in History with the badge on Home incrementing; pin one; filter Pinned; expand Table on a card; "Try Newton" chip previews; Open adopts; swipe delete + Undo; kill the app and relaunch → entries persist; reopened entries re-solve and match.

---

## 13. Open decisions (defaults chosen, change if you disagree)

| # | Decision | Default |
|---|---|---|
| D1 | `applicationId` / package | `app.numlab` |
| D2 | Cold start | Start **blank** (input-first). Optional setting "Restore last session" (off) |
| D3 | Graph library | MPAndroidChart + Compose overlay; fallback custom Canvas if overlay alignment fights us |
| D4 | Parser | exp4j first; fall back to the Kotlin port of `compileExpr` if golden parser cases can't be met |
| D5 | Fonts | Bundle the two families used in the prototype in `res/font` |
| D6 | Dynamic colour | Optional, off by default, neutrals only |
| D7 | Languages | English only for v1; all strings externalised |
| D8 | Analytics / crash reporting | None. If crash reporting is wanted later, add it behind a consent screen |

---

## 14. Definition of done
- All acceptance scripts pass on a physical device (API ≥ 26) and a tablet emulator.
- `golden.json` passes 100 %; coverage on `:domain` ≥ 90 % lines.
- Lint clean; no `INTERNET` permission; no debug logging of user input in release.
- Release `.aab` signed, installs via `bundletool`, cold start < 700 ms on mid-range hardware, APK size under budget.
- `docs/DECISIONS.md` lists every deviation from this plan with the reason.
