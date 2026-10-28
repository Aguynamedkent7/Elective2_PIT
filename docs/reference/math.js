// Numerical engine for the NumLab prototype.
// Mirrors what the Kotlin domain layer will do: parse f(x) safely, raise domain errors,
// then run the root-finding and interpolation algorithms.

export class MathError extends Error {}

export const fmt = (v, d = 6) => {
  if (v === null || v === undefined || !Number.isFinite(v)) return '—';
  if (v === 0) return '0';
  const a = Math.abs(v);
  if (a < 1e-4 || a >= 1e7) return v.toExponential(3);
  return v.toFixed(d);
};
export const fmtE = (v) => (v === null || v === undefined || !Number.isFinite(v) ? '—' : v === 0 ? '0' : v.toExponential(2));

// ---------- expression parsing (exp4j-style) ----------
const unary1 = {
  sin: Math.sin, cos: Math.cos, tan: Math.tan, asin: Math.asin, acos: Math.acos, atan: Math.atan,
  sinh: Math.sinh, cosh: Math.cosh, tanh: Math.tanh, exp: Math.exp,
  ln: Math.log, log: Math.log, log10: Math.log10, log2: Math.log2, sqrt: Math.sqrt, cbrt: Math.cbrt, abs: Math.abs,
};
const CONSTS = { pi: Math.PI, e: Math.E };

function applyFn(name, v) {
  if ((name === 'ln' || name === 'log' || name === 'log10' || name === 'log2') && v <= 0)
    throw new MathError(`Log of a non-positive number (${name}(${fmt(v, 4)}))`);
  if (name === 'sqrt' && v < 0) throw new MathError(`Square root of a negative number (${fmt(v, 4)})`);
  if ((name === 'asin' || name === 'acos') && Math.abs(v) > 1) throw new MathError(`${name}(x) needs |x| ≤ 1`);
  return unary1[name](v);
}

function tokenize(src) {
  const s = src.replace(/\s+/g, '').replace(/π/g, 'pi').replace(/×/g, '*').replace(/−/g, '-').replace(/÷/g, '/');
  const toks = [];
  let i = 0;
  while (i < s.length) {
    const c = s[i];
    if (/[0-9.]/.test(c)) {
      let j = i;
      while (j < s.length && /[0-9.]/.test(s[j])) j++;
      if ((s[j] === 'e' || s[j] === 'E') && /^[+-]?\d/.test(s.slice(j + 1))) {
        j++;
        if (s[j] === '+' || s[j] === '-') j++;
        while (j < s.length && /[0-9]/.test(s[j])) j++;
      }
      const v = Number(s.slice(i, j));
      if (Number.isNaN(v)) throw new MathError(`Bad number "${s.slice(i, j)}"`);
      toks.push({ t: 'num', v });
      i = j;
    } else if (/[a-zA-Z_]/.test(c)) {
      let j = i;
      while (j < s.length && /[a-zA-Z0-9_]/.test(s[j])) j++;
      toks.push({ t: 'id', v: s.slice(i, j).toLowerCase() });
      i = j;
    } else if (c === '√') {
      toks.push({ t: 'id', v: 'sqrt' });
      i++;
    } else if ('+-*/^(),'.includes(c)) {
      toks.push({ t: 'op', v: c });
      i++;
    } else {
      throw new MathError(`Unexpected character "${c}"`);
    }
  }
  return toks;
}

export function compileExpr(src) {
  if (!src || !src.trim()) throw new MathError('Enter a function of x');
  const toks = tokenize(src);
  let p = 0;
  const peek = () => toks[p];
  const isOp = (t, v) => t && t.t === 'op' && t.v === v;
  const startsAtom = (t) => t && (t.t === 'num' || t.t === 'id' || isOp(t, '('));

  function expr() {
    let l = term();
    while (isOp(peek(), '+') || isOp(peek(), '-')) {
      const op = toks[p++].v;
      const r = term();
      const a = l;
      l = op === '+' ? (x) => a(x) + r(x) : (x) => a(x) - r(x);
    }
    return l;
  }
  function term() {
    let l = unary();
    for (;;) {
      const t = peek();
      if (!t) break;
      if (isOp(t, '*') || isOp(t, '/')) {
        p++;
        const r = unary();
        const a = l;
        if (t.v === '*') l = (x) => a(x) * r(x);
        else
          l = (x) => {
            const d = r(x);
            if (d === 0) throw new MathError(`Division by zero at x = ${fmt(x, 4)}`);
            return a(x) / d;
          };
      } else if (startsAtom(t)) {
        const r = power();
        const a = l;
        l = (x) => a(x) * r(x);
      } else break;
    }
    return l;
  }
  function unary() {
    if (isOp(peek(), '-')) {
      p++;
      const a = unary();
      return (x) => -a(x);
    }
    if (isOp(peek(), '+')) {
      p++;
      return unary();
    }
    return power();
  }
  function power() {
    const base = atom();
    if (isOp(peek(), '^')) {
      p++;
      const ex = unary();
      return (x) => {
        const b = base(x);
        const e = ex(x);
        if (b === 0 && e < 0) throw new MathError(`Division by zero at x = ${fmt(x, 4)}`);
        const r = Math.pow(b, e);
        if (Number.isNaN(r)) throw new MathError('Negative base with a fractional power');
        return r;
      };
    }
    return base;
  }
  function atom() {
    const t = toks[p++];
    if (!t) throw new MathError('Expression ends early');
    if (t.t === 'num') return () => t.v;
    if (t.t === 'id') {
      if (unary1[t.v]) {
        let arg;
        if (isOp(peek(), '(')) {
          p++;
          arg = expr();
          if (!isOp(peek(), ')')) throw new MathError('Missing ")"');
          p++;
        } else arg = power();
        const name = t.v;
        return (x) => applyFn(name, arg(x));
      }
      if (t.v === 'x') return (x) => x;
      if (t.v in CONSTS) {
        const c = CONSTS[t.v];
        return () => c;
      }
      throw new MathError(`Unknown symbol "${t.v}"`);
    }
    if (isOp(t, '(')) {
      const inner = expr();
      if (!isOp(peek(), ')')) throw new MathError('Missing ")"');
      p++;
      return inner;
    }
    throw new MathError(`Unexpected "${t.v}"`);
  }

  const fn = expr();
  if (p < toks.length) throw new MathError(`Unexpected "${toks[p].v}"`);
  return (x) => {
    const v = fn(x);
    if (!Number.isFinite(v)) throw new MathError(`f(x) is undefined at x = ${fmt(x, 4)}`);
    return v;
  };
}

export function tryCompile(src) {
  try {
    return { f: compileExpr(src), error: null };
  } catch (e) {
    if (e instanceof MathError) return { f: null, error: e.message };
    throw e;
  }
}

// ---------- numerical derivatives ----------
export const deriv = (f, x, h = 1e-5) => {
  const hh = h * Math.max(1, Math.abs(x));
  return (f(x + hh) - f(x - hh)) / (2 * hh);
};

const binom = (n, k) => {
  let r = 1;
  for (let i = 1; i <= k; i++) r = (r * (n - k + i)) / i;
  return r;
};
export function nthDeriv(f, x, k, h) {
  let s = 0;
  for (let i = 0; i <= k; i++) s += (i % 2 ? -1 : 1) * binom(k, i) * f(x + (k / 2 - i) * h);
  return s / Math.pow(h, k);
}

// ---------- root finding ----------
const num = (v, label) => {
  const n = Number(String(v).trim());
  if (String(v).trim() === '' || !Number.isFinite(n)) throw new MathError(`Enter a number for ${label}`);
  return n;
};

export function solveRoot(inp, opts = {}) {
  try {
    const f = opts.f || compileExpr(inp.fx);
    const tol = num(inp.tol, 'tolerance');
    const maxIter = num(inp.maxIter, 'max iterations');
    if (tol <= 0) throw new MathError('Tolerance must be greater than 0');
    if (!Number.isInteger(maxIter) || maxIter < 1) throw new MathError('Max iterations must be a whole number ≥ 1');
    const rows = [];
    const relOf = (abs, x) => (abs === null || x === 0 ? null : (abs / Math.abs(x)) * 100);
    let converged = false;
    let x = 0;
    let initial = 0;
    let used = [];

    if (inp.method === 'bisection') {
      let lo = num(inp.a, 'a');
      let hi = num(inp.b, 'b');
      if (lo >= hi) throw new MathError('Need a < b');
      used = [lo, hi];
      let flo = f(lo);
      const fhi = f(hi);
      if (flo * fhi > 0) throw new MathError('f(a) and f(b) must have opposite signs');
      let prev = null;
      for (let i = 1; i <= maxIter; i++) {
        x = (lo + hi) / 2;
        const fc = f(x);
        const abs = prev === null ? null : Math.abs(x - prev);
        rows.push({ n: i, x, fx: fc, abs, rel: relOf(abs, x), lo, hi });
        if (fc === 0 || (abs !== null && abs < tol)) {
          converged = true;
          break;
        }
        if (flo * fc < 0) hi = x;
        else {
          lo = x;
          flo = fc;
        }
        prev = x;
      }
    } else if (inp.method === 'newton') {
      x = num(inp.x0, 'x₀');
      used = [x];
      initial = 1;
      let fcur = f(x);
      rows.push({ n: 0, x, fx: fcur, abs: null, rel: null });
      for (let i = 1; i <= maxIter; i++) {
        const fx = fcur;
        const d = deriv(f, x);
        if (Math.abs(d) < 1e-12) throw new MathError(`f′(x) is about 0 at x = ${fmt(x, 4)}. Newton cannot continue`);
        const xn = x - fx / d;
        const abs = Math.abs(xn - x);
        const fn = f(xn);
        fcur = fn;
        rows.push({ n: i, x: xn, fx: fn, abs, rel: relOf(abs, xn) });
        x = xn;
        if (fn === 0 || abs < tol) {
          converged = true;
          break;
        }
      }
    } else {
      let xp = num(inp.s0, 'x₀');
      x = num(inp.s1, 'x₁');
      if (xp === x) throw new MathError('x₀ and x₁ must differ');
      used = [xp, x];
      initial = 2;
      let fp = f(xp);
      let fc = f(x);
      rows.push({ n: 0, x: xp, fx: fp, abs: null, rel: null });
      rows.push({ n: 1, x, fx: fc, abs: null, rel: null });
      for (let i = 2; i <= maxIter + 1; i++) {
        const den = fc - fp;
        if (den === 0) throw new MathError('f(xₙ) = f(xₙ₋₁): the secant line is flat');
        const xn = x - (fc * (x - xp)) / den;
        const abs = Math.abs(xn - x);
        const fn = f(xn);
        rows.push({ n: i, x: xn, fx: fn, abs, rel: relOf(abs, xn) });
        xp = x;
        fp = fc;
        x = xn;
        fc = fn;
        if (fn === 0 || abs < tol) {
          converged = true;
          break;
        }
      }
    }
    const last = rows[rows.length - 1];
    return {
      error: null, f, method: inp.method, root: last.x, fRoot: last.fx, iterations: rows.length - initial,
      converged, rows, tol, finalAbs: last.abs, used,
    };
  } catch (e) {
    if (e instanceof MathError) return { error: e.message };
    throw e;
  }
}

// ---------- interpolation ----------
const polyMul = (p, q) => {
  const r = new Array(p.length + q.length - 1).fill(0);
  p.forEach((a, i) => q.forEach((b, j) => (r[i + j] += a * b)));
  return r;
};
const polyAdd = (p, q) => {
  const r = new Array(Math.max(p.length, q.length)).fill(0);
  p.forEach((a, i) => (r[i] += a));
  q.forEach((a, i) => (r[i] += a));
  return r;
};
const SUP = { 0: '⁰', 1: '¹', 2: '²', 3: '³', 4: '⁴', 5: '⁵', 6: '⁶', 7: '⁷', 8: '⁸', 9: '⁹' };
const sup = (n) => String(n).split('').map((d) => SUP[d]).join('');
const fmtC = (c) => String(+Number(c).toPrecision(5));

export function polyToString(c) {
  let out = '';
  for (let k = c.length - 1; k >= 0; k--) {
    const v = c[k];
    if (Math.abs(v) < 1e-9) continue;
    const mag = fmtC(Math.abs(v));
    const body = k === 0 ? mag : `${mag === '1' ? '' : mag}x${k > 1 ? sup(k) : ''}`;
    if (!out) out = (v < 0 ? '−' : '') + body;
    else out += (v < 0 ? ' − ' : ' + ') + body;
  }
  return out || '0';
}
const xTerm = (xj) => (xj === 0 ? 'x' : xj > 0 ? `(x − ${fmtC(xj)})` : `(x + ${fmtC(-xj)})`);

export function interpolate(inp) {
  try {
    const pts = [];
    for (const r of inp.rows) {
      const xs = String(r.x).trim();
      const ys = String(r.y).trim();
      if (!xs && !ys) continue;
      pts.push({ x: num(xs, 'every x value'), y: num(ys, 'every y value') });
    }
    if (pts.length < 2) throw new MathError('Enter at least 2 data points');
    pts.sort((a, b) => a.x - b.x);
    for (let i = 1; i < pts.length; i++) if (pts[i].x === pts[i - 1].x) throw new MathError('x values must be distinct');
    const xe = num(inp.xEval, 'x to evaluate');
    const n = pts.length - 1;
    const X = pts.map((p) => p.x);
    const Y = pts.map((p) => p.y);

    // divided differences
    const dd = [Y.slice()];
    for (let j = 1; j <= n; j++) {
      const prev = dd[j - 1];
      dd.push(prev.slice(0, n + 1 - j).map((_, i) => (prev[i + 1] - prev[i]) / (X[i + j] - X[i])));
    }
    const newtonEval = (x) => {
      let r = dd[n][0];
      for (let k = n - 1; k >= 0; k--) r = r * (x - X[k]) + dd[k][0];
      return r;
    };
    const basisAt = (x, i) => {
      let L = 1;
      for (let j = 0; j <= n; j++) if (j !== i) L *= (x - X[j]) / (X[i] - X[j]);
      return L;
    };
    const lagrangeEval = (x) => Y.reduce((s, y, i) => s + y * basisAt(x, i), 0);

    let coeffs = [0];
    let polyForm = '';
    let basis = null;
    if (inp.method === 'lagrange') {
      for (let i = 0; i <= n; i++) {
        let b = [1];
        let den = 1;
        for (let j = 0; j <= n; j++)
          if (j !== i) {
            b = polyMul(b, [-X[j], 1]);
            den *= X[i] - X[j];
          }
        coeffs = polyAdd(coeffs, b.map((c) => (c * Y[i]) / den));
      }
      basis = X.map((x, i) => {
        const L = basisAt(xe, i);
        return { i, x, y: Y[i], L, term: Y[i] * L };
      });
    } else {
      let acc = [dd[n][0]];
      for (let k = n - 1; k >= 0; k--) acc = polyAdd(polyMul(acc, [-X[k], 1]), [dd[k][0]]);
      coeffs = acc;
      polyForm = dd.map((col, j) => {
        let t = fmtC(col[0]);
        for (let k = 0; k < j; k++) t += xTerm(X[k]);
        return t;
      }).join(' + ').replace(/\+ -/g, '− ').replace(/^-/, '−');
    }
    const P = inp.method === 'lagrange' ? lagrangeEval : newtonEval;
    const Pxe = P(xe);

    let yTrue = null;
    let fTrue = null;
    if (inp.truth === 'fn') {
      fTrue = compileExpr(inp.fx);
      yTrue = fTrue(xe);
    } else if (inp.truth === 'value') {
      yTrue = num(inp.yTrue, 'true value');
    }
    let metrics = null;
    if (yTrue !== null) {
      const abs = Math.abs(yTrue - Pxe);
      const rel = yTrue === 0 ? null : ((yTrue - Pxe) / yTrue) * 100;
      metrics = { yTrue, abs, rel, absRel: rel === null ? null : Math.abs(rel), bound: null };
      if (fTrue) {
        const k = n + 1;
        const lo = Math.min(X[0], xe);
        const hi = Math.max(X[n], xe);
        const h = Math.max((hi - lo) / 30, 1e-3);
        let M = null;
        if (k <= 8) {
          for (let s = 0; s <= 60; s++) {
            const xi = lo + ((hi - lo) * s) / 60;
            try {
              const v = Math.abs(nthDeriv(fTrue, xi, k, h));
              if (Number.isFinite(v) && (M === null || v > M)) M = v;
            } catch (e) {
              if (!(e instanceof MathError)) throw e;
            }
          }
        }
        let fact = 1;
        for (let i = 2; i <= k; i++) fact *= i;
        const prod = X.reduce((a, xi) => a * (xe - xi), 1);
        metrics.bound = M === null ? null : { M, fact, prod, value: (M / fact) * Math.abs(prod), k, extrapolating: xe < X[0] || xe > X[n] };
      }
    }
    return {
      error: null, method: inp.method, pts, n, X, Y, xe, P, Pxe, coeffs, poly: polyToString(coeffs), polyForm,
      dd, basis, metrics, fTrue,
    };
  } catch (e) {
    if (e instanceof MathError) return { error: e.message };
    throw e;
  }
}

// ================= round-two additions =================
const safe = (f, x) => {
  try { const v = f(x); return Number.isFinite(v) ? v : NaN; } catch (e) { if (e instanceof MathError) return NaN; throw e; }
};

// Scan for sign changes and widen each to whole numbers when that still brackets a root.
export function findBrackets(f, lo = -10, hi = 10, steps = 400, max = 4) {
  const out = [];
  const h = (hi - lo) / steps;
  let px = lo;
  let py = safe(f, px);
  for (let i = 1; i <= steps && out.length < max; i++) {
    const x = lo + i * h;
    const y = safe(f, x);
    if (Number.isFinite(py) && Number.isFinite(y) && py * y < 0) {
      let lo2 = px;
      let hi2 = x;
      let flo = py;
      let c = lo2;
      for (let k = 0; k < 45; k++) {
        c = (lo2 + hi2) / 2;
        const fc = safe(f, c);
        if (!Number.isFinite(fc)) break;
        if (flo * fc < 0) hi2 = c; else { lo2 = c; flo = fc; }
      }
      const fend = safe(f, c);
      const pole = !Number.isFinite(fend) || Math.abs(fend) > 1e-4;
      if (!pole) {
        let a = Math.floor(px);
        let b = Math.ceil(x);
        if (a === b) b = a + 1;
        const fa = safe(f, a);
        const fb = safe(f, b);
        if (!(fa * fb < 0)) { a = +px.toFixed(4); b = +x.toFixed(4); }
        if (!out.some((r) => r[0] === a && r[1] === b)) out.push([a, b]);
      }
    }
    px = x;
    py = y;
  }
  return out;
}

export function explainStep(res, idx) {
  const r = res.rows[idx];
  const d = 6;
  const L = [];
  const f = res.f;
  if (res.method === 'bisection') {
    const fa = f(r.lo);
    L.push(['Midpoint', `c = (a + b) / 2 = (${fmt(r.lo, d)} + ${fmt(r.hi, d)}) / 2 = ${fmt(r.x, d)}`]);
    L.push(['Evaluate', `f(c) = ${fmt(r.fx, d)}`]);
    L.push(['Keep the half with the sign change', r.fx === 0 ? 'f(c) = 0, so c is an exact root' : `f(a)·f(c) = ${fmtE(fa * r.fx)} ${fa * r.fx < 0 ? '< 0, so b ← c' : '> 0, so a ← c'}`]);
  } else if (res.method === 'newton') {
    if (idx === 0) L.push(['Starting guess', `x₀ = ${fmt(r.x, d)}`]);
    else {
      const p = res.rows[idx - 1];
      const dv = deriv(f, p.x);
      L.push(['Slope by central difference', `f′(xₙ₋₁) ≈ (f(x+h) − f(x−h)) / 2h = ${fmt(dv, d)}`]);
      L.push(['Newton step', `xₙ = xₙ₋₁ − f(xₙ₋₁) / f′(xₙ₋₁) = ${fmt(p.x, d)} − (${fmt(p.fx, d)}) / (${fmt(dv, d)}) = ${fmt(r.x, d)}`]);
    }
  } else if (idx < 2) {
    L.push(['Starting guess', `x${idx === 0 ? '₀' : '₁'} = ${fmt(r.x, d)}`]);
  } else {
    const p1 = res.rows[idx - 1];
    const p0 = res.rows[idx - 2];
    L.push(['Secant step', `xₙ = xₙ₋₁ − f(xₙ₋₁)(xₙ₋₁ − xₙ₋₂) / (f(xₙ₋₁) − f(xₙ₋₂))`]);
    L.push(['With numbers', `${fmt(p1.x, d)} − (${fmt(p1.fx, d)})(${fmt(p1.x - p0.x, d)}) / (${fmt(p1.fx - p0.fx, d)}) = ${fmt(r.x, d)}`]);
  }
  if (r.abs !== null && r.abs !== undefined) L.push(['Change', `|xₙ − xₙ₋₁| = ${fmtE(r.abs)}`]);
  const last = idx === res.rows.length - 1;
  const note = !last ? null : res.converged
    ? `Stopped here because the change ${fmtE(r.abs)} is below the tolerance ${res.tol}.`
    : 'Stopped at the iteration limit before the change dropped below the tolerance.';
  return { lines: L, note };
}

export function compareRoots(inp) {
  let f0;
  try { f0 = compileExpr(inp.fx); } catch (e) { if (e instanceof MathError) return { error: e.message }; throw e; }
  const runs = {};
  for (const method of ['bisection', 'newton', 'secant']) {
    let evals = 0;
    const f = (x) => { evals++; return f0(x); };
    const res = solveRoot({ ...inp, method }, { f });
    runs[method] = { res, evals };
  }
  let ref = null;
  let best = Infinity;
  for (const k of Object.keys(runs)) {
    const r = runs[k].res;
    if (!r.error && Math.abs(r.fRoot) < best) { best = Math.abs(r.fRoot); ref = r.root; }
  }
  for (const k of Object.keys(runs)) {
    const r = runs[k].res;
    if (!r.error && ref !== null) r.errs = r.rows.map((row) => Math.abs(row.x - ref));
  }
  return { error: null, runs, ref };
}

const lagEval = (X, Y, x) => {
  let s = 0;
  for (let i = 0; i < X.length; i++) {
    let L = 1;
    for (let j = 0; j < X.length; j++) if (j !== i) L *= (x - X[j]) / (X[i] - X[j]);
    s += Y[i] * L;
  }
  return s;
};
export const nodeXs = (a, b, n, kind) => {
  const X = [];
  for (let i = 0; i < n; i++) X.push(kind === 'cheb' ? (a + b) / 2 + ((b - a) / 2) * Math.cos(((2 * i + 1) * Math.PI) / (2 * n)) : a + ((b - a) * i) / (n - 1));
  return X.sort((p, q) => p - q);
};
export function genNodes(f, a, b, n, kind) {
  return nodeXs(a, b, n, kind).map((x, i) => ({ id: i + 1, x: String(+x.toFixed(6)), y: String(+f(x).toFixed(6)) }));
}
export function errorByN(f, a, b, nmin = 2, nmax = 10) {
  const out = [];
  for (let n = nmin; n <= nmax; n++) {
    const row = { n };
    for (const kind of ['equal', 'cheb']) {
      const X = nodeXs(a, b, n, kind);
      const Y = X.map((x) => safe(f, x));
      let m = 0;
      for (let s = 0; s <= 160; s++) {
        const x = a + ((b - a) * s) / 160;
        const t = safe(f, x);
        if (Number.isFinite(t)) m = Math.max(m, Math.abs(t - lagEval(X, Y, x)));
      }
      row[kind] = m;
    }
    out.push(row);
  }
  return out;
}

// Accepts "0 0, 1 0.84", "(0,0) (1,0.84)", or one "x y" pair per line (spreadsheet paste).
export function parsePoints(text) {
  const re = /[-+]?(?:\d+\.?\d*|\.\d+)(?:[eE][-+]?\d+)?/g;
  const lines = text.split(/\n+/).map((l) => l.trim()).filter(Boolean);
  const perLine = lines.map((l) => l.match(re) || []);
  let nums;
  if (lines.length > 1 && perLine.every((n) => n.length === 2)) nums = perLine.flat();
  else nums = (text.match(re) || []);
  const rows = [];
  for (let i = 0; i + 1 < nums.length; i += 2) rows.push({ id: rows.length + 1, x: nums[i], y: nums[i + 1] });
  return { rows, odd: nums.length % 2 === 1, count: nums.length };
}

export const toCSV = (res) => {
  if (res.rows) return ['iter,x,f(x),abs_error,rel_error_pct', ...res.rows.map((r) => [r.n, r.x, r.fx, r.abs ?? '', r.rel ?? ''].join(','))].join('\n');
  return ['x,y', ...res.pts.map((p) => `${p.x},${p.y}`)].join('\n');
};
export const toLatex = (res) => {
  if (res.rows) return `x^{*} \\approx ${res.root.toFixed(6)}`;
  const terms = res.coeffs.map((c, k) => [c, k]).reverse().filter(([c]) => Math.abs(c) > 1e-9)
    .map(([c, k], i) => `${i === 0 ? (c < 0 ? '-' : '') : c < 0 ? ' - ' : ' + '}${+Math.abs(c).toPrecision(5)}${k ? `x${k > 1 ? `^{${k}}` : ''}` : ''}`);
  return `P(x) = ${terms.join('') || '0'}`;
};
