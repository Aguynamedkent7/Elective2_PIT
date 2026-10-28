# NumLab — continuity log

## 2026-10-28 — Phase 0 + Phase 1
- Extracted handoff; then applied second handoff (logo-only change: new S-curve mark).
- Phase 0: multi-module scaffold (:app :ui :domain :data), version catalog, Hilt, theme tokens from
  template3.html, bundled fonts, nav (home / canvas/{module} / history placeholders), adaptive +
  monochrome icon, core-splashscreen, GitHub Actions CI. `assembleDebug` + `lint` green (0 errors).
- Phase 1: full port of math.js in :domain (parser, formatter, solvers, brackets, explain, compare,
  interpolation, bound, parsePoints, genNodes, errorByN, exports, use cases). 87/87 tests pass
  (82 golden at 1e-9 rel / 1e-6 for bounds, + property tests incl. 10k-case parser fuzz).
- Decisions: see docs/DECISIONS.md (hand-written parser instead of exp4j).
- Blockers: no device/emulator attached, so the Phase 0 "installs on device" gate is unverified.
  Coverage measurement (≥90 % DoD) not wired yet.
- Next: Phase 2 (Home: roots).

## 2026-10-28 — Phases 2–8
- Built Home (roots + interpolation), graph canvas (custom Compose plot, player, sheet, reveal),
  history notebook (Room), settings sheet (DataStore), brand splash + motion, signed release.
- Verified on a Pixel 6 API 35 emulator (installed this session): acceptance A/B/C/D by hand and
  4 Compose UI tests (`connectedDebugAndroidTest`), dark mode, 200 % font, rotation, R8 release
  smoke test, cold start 429–791 ms.
- Bugs found on device and fixed: parser `(?U)` regex crash (ICU), segmented-control height,
  label halo, zoom controls under status bar, dark-mode icon colour, swipe-undo state.
- Tests: domain 91 (golden 82/82) at 96.9 % line coverage; data 4 (Robolectric Room); ui 9
  (ViewModel); app 4 on-device. Lint 0 errors.
- Upload keystore generated at ~/.android/keystores/numlab-upload.jks; passwords in git-ignored
  keystore.properties. User must back these up.
- Decisions: docs/DECISIONS.md (custom Canvas plot, canvas overlay, etc.).
- Blockers / not done: no physical device, tablet, API 26/30, TalkBack or RTL pass; no
  macrobenchmark or screenshot tests. main not promoted (AGENTS.md: deliberate release only).
