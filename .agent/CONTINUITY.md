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
