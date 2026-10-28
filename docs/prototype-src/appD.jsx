import React, { useState, useRef, useEffect, useMemo } from 'react';
import { solveRoot, interpolate, tryCompile, findBrackets, explainStep, parsePoints, genNodes, fmt, fmtE, toCSV, toLatex } from './math.js';
import {
  cx, P, Icon, useModule, useToast, useUndo, copyText, Seg2, RoundBtn, Pill, NumField, Stepper, tolExp, sup, FxInput, Badge, ConvBadge, ErrorBox,
  GTable, rootCols, basisCols, ddCols, metricList, PointRows,
} from './kit.jsx';
import { Plot, Spark, domainFor } from './chart2.jsx';
import { DEFAULT_ROOT, DEFAULT_INTERP, PRESETS, METHOD_LABEL, METHOD_SHORT } from './defaults.js';
import { LogoMark, Wordmark, Splash, reduceMotion } from './Logo.jsx';
import { HistoryScreen, textOf } from './History.jsx';

const I = {
  clock: 'M12 7v5l3.2 2M12 3a9 9 0 100 18 9 9 0 000-18z',
  bookmark: 'M7 4h10v16l-5-4-5 4z',
  lock: 'M7 11V8a5 5 0 0110 0v3M5 11h14v9H5zM12 15v2',
  back: 'M15 6l-6 6 6 6',
};
const PEEK = 218;
const clamp = (v, a, b) => Math.max(a, Math.min(b, v));

const START_ROOT = { ...DEFAULT_ROOT, fx: '' };
const blankRows = () => [1, 2, 3].map((id) => ({ id, x: '', y: '' }));
const START_INTERP = { method: 'lagrange', rows: blankRows(), xEval: '', truth: 'none', fx: '', yTrue: '' };
const RUNGE = '1/(1+25x^2)';
const INTERP_EXAMPLES = [
  { label: 'sin x · 4 points', inp: DEFAULT_INTERP },
  { label: 'eˣ · 3 points', inp: { ...DEFAULT_INTERP, method: 'newtondd', fx: 'exp(x)', xEval: '1.5', rows: [{ id: 1, x: '0', y: '1' }, { id: 2, x: '1', y: '2.7183' }, { id: 3, x: '2', y: '7.3891' }] } },
  { label: 'Runge · 7 nodes', inp: { ...DEFAULT_INTERP, fx: RUNGE, xEval: '0.9', rows: genNodes(tryCompile(RUNGE).f, -1, 1, 7, 'equal') } },
];
const SEEDS = [
  { id: 1, kind: 'root', inputs: DEFAULT_ROOT, pinned: true, when: 'Sample' },
  { id: 2, kind: 'interp', inputs: DEFAULT_INTERP, pinned: false, when: 'Sample' },
  { id: 3, kind: 'root', inputs: { ...DEFAULT_ROOT, method: 'newton', fx: 'cos(x) - x', x0: '1', tol: '1e-8' }, pinned: false, when: 'Sample' },
];

const filled = (rows) => rows.filter((r) => String(r.x).trim() || String(r.y).trim());
const pointCount = (rows) => rows.filter((r) => String(r.x).trim() !== '' && String(r.y).trim() !== '' && Number.isFinite(Number(r.x)) && Number.isFinite(Number(r.y))).length;

/* count-up / settle animation for the headline number */
function useTween(target, ms = 520, initial = 0) {
  const [val, setVal] = useState(Number.isFinite(initial) ? initial : target);
  const cur = useRef(val);
  useEffect(() => {
    if (!Number.isFinite(target) || !Number.isFinite(cur.current) || reduceMotion() || Math.abs(target - cur.current) < 1e-12) { cur.current = target; setVal(target); return undefined; }
    const from = cur.current;
    const t0 = performance.now();
    let raf = 0;
    const step = (t) => {
      const p = Math.min(1, (t - t0) / ms);
      const e = 1 - Math.pow(1 - p, 4);
      cur.current = from + (target - from) * e;
      setVal(cur.current);
      if (p < 1) raf = requestAnimationFrame(step);
    };
    raf = requestAnimationFrame(step);
    return () => cancelAnimationFrame(raf);
  }, [target]);
  return val;
}
function BigNumber({ value, dp, className }) {
  const v = useTween(value, 560, 0);
  return <div className={className}>{Number.isFinite(v) ? fmt(v, dp) : '—'}</div>;
}

function useBoxH(ref) {
  const [H, setH] = useState(720);
  useEffect(() => {
    const el = ref.current;
    if (!el) return undefined;
    const ro = new ResizeObserver(() => setH(el.clientHeight));
    ro.observe(el);
    setH(el.clientHeight);
    return () => ro.disconnect();
  }, []);
  return H;
}

/* ================= input forms (shared by Home and the Canvas sheet) ================= */
function RootInputs({ m, dp, setDp, brackets }) {
  const { inp } = m;
  const e = tolExp(inp.tol);
  return (
    <div className="space-y-5">
      <Seg2 label="Method" value={inp.method} onChange={(v) => m.set({ method: v })} options={[{ v: 'bisection', l: 'Bisection' }, { v: 'newton', l: 'Newton' }, { v: 'secant', l: 'Secant' }]} />
      <FxInput value={inp.fx} onChange={(v) => m.set({ fx: v })} />
      <div>
        <p className="mb-2 text-[11px] font-semibold uppercase tracking-wider text-c-ink2">Examples</p>
        <div className="-mx-5 flex gap-2 overflow-x-auto px-5 pb-1 [scrollbar-width:none]">
          {PRESETS.map((p) => <Pill key={p.fx} active={inp.fx === p.fx} onClick={() => m.set({ fx: p.fx, a: p.a, b: p.b, x0: p.x0, s0: p.s0, s1: p.s1 })}>{p.label}</Pill>)}
        </div>
      </div>
      {brackets.length > 0 && (
        <div className="pop-in">
          <p className="mb-2 flex items-center gap-1.5 text-[11px] font-semibold uppercase tracking-wider text-c-ink2"><Icon d={P.bolt} size={13} />Roots detected. Tap to start there</p>
          <div className="flex flex-wrap gap-2">
            {brackets.map(([a, b]) => (
              <Pill key={`${a},${b}`} active={Number(inp.a) === a && Number(inp.b) === b}
                onClick={() => m.set({ a: String(a), b: String(b), x0: String(+((a + b) / 2).toFixed(4)), s0: String(a), s1: String(b) })}>[{a}, {b}]</Pill>
            ))}
          </div>
        </div>
      )}
      <div className="grid grid-cols-2 gap-2">
        {inp.method === 'bisection' && (<><NumField id="a" label="a" value={inp.a} onChange={(v) => m.set({ a: v })} /><NumField id="b" label="b" value={inp.b} onChange={(v) => m.set({ b: v })} /></>)}
        {inp.method === 'newton' && <NumField id="x0" label="x₀ start" value={inp.x0} onChange={(v) => m.set({ x0: v })} className="col-span-2" />}
        {inp.method === 'secant' && (<><NumField id="s0" label="x₀" value={inp.s0} onChange={(v) => m.set({ s0: v })} /><NumField id="s1" label="x₁" value={inp.s1} onChange={(v) => m.set({ s1: v })} /></>)}
        <Stepper label="Tolerance" display={`10${sup(-e)}`} onMinus={() => m.set({ tol: `1e-${Math.min(12, e + 1)}` })} onPlus={() => m.set({ tol: `1e-${Math.max(1, e - 1)}` })} />
        <Stepper label="Max iterations" display={inp.maxIter} onMinus={() => m.set({ maxIter: String(Math.max(5, Number(inp.maxIter) - 10 || 100)) })} onPlus={() => m.set({ maxIter: String((Number(inp.maxIter) || 90) + 10) })} />
        <Stepper className="col-span-2" label="Decimals shown" display={dp} onMinus={() => setDp(Math.max(3, dp - 1))} onPlus={() => setDp(Math.min(10, dp + 1))} />
      </div>
    </div>
  );
}

function InterpInputs({ m }) {
  const { inp } = m;
  const [paste, setPaste] = useState(false);
  const [text, setText] = useState('');
  const parsed = useMemo(() => parsePoints(text), [text]);
  const setPts = (t) => { setText(t); const p = parsePoints(t); if (p.rows.length) m.set({ rows: p.rows }); };
  return (
    <div className="space-y-5">
      <Seg2 label="Method" value={inp.method} onChange={(v) => m.set({ method: v })} options={[{ v: 'lagrange', l: 'Lagrange' }, { v: 'newtondd', l: 'Newton DD' }]} />
      <div>
        <p className="mb-2 text-[11px] font-semibold uppercase tracking-wider text-c-ink2">Examples</p>
        <div className="-mx-5 flex gap-2 overflow-x-auto px-5 pb-1 [scrollbar-width:none]">
          {INTERP_EXAMPLES.map((x) => <Pill key={x.label} onClick={() => { m.replace({ ...x.inp, rows: x.inp.rows.map((r) => ({ ...r })) }); setText(''); }}>{x.label}</Pill>)}
        </div>
      </div>
      <div className="space-y-3">
        <div className="flex items-center justify-between">
          <p className="text-[11px] font-semibold uppercase tracking-wider text-c-ink2">Data points</p>
          <div className="flex gap-1.5"><Pill active={!paste} onClick={() => setPaste(false)}>Rows</Pill><Pill active={paste} onClick={() => setPaste(true)}>Paste</Pill></div>
        </div>
        {paste ? (
          <div>
            <textarea aria-label="Paste data points" value={text} onChange={(e) => setPts(e.target.value)} rows={5} spellCheck="false" placeholder={'0 0\n1 0.8415\n2 0.9093'}
              className="w-full resize-none rounded-2xl bg-c-surface2 p-3.5 font-mono text-[14px] leading-6 text-c-ink outline-none focus:ring-2 focus:ring-c-accent" />
            <p className={cx('mt-1.5 flex items-center gap-1.5 px-1 text-xs', parsed.odd ? 'text-c-bad' : 'text-c-ink2')}>
              <Icon d={parsed.odd || parsed.rows.length < 2 ? P.warn : P.check} size={13} />
              {parsed.odd ? 'One value is missing a partner. Each point needs x and y.' : parsed.rows.length < 2 ? 'Paste pairs from a spreadsheet, or type them.' : `${parsed.rows.length} points understood`}
            </p>
          </div>
        ) : <PointRows rows={inp.rows} onChange={(rows) => m.set({ rows })} />}
      </div>
      <NumField id="xe" label="Evaluate at x" value={inp.xEval} onChange={(v) => m.set({ xEval: v })} />
      <div className="space-y-2">
        <p className="text-[11px] font-semibold uppercase tracking-wider text-c-ink2">Compare with the exact value</p>
        <Seg2 label="Reference" value={inp.truth} onChange={(v) => m.set({ truth: v })} options={[{ v: 'none', l: 'None' }, { v: 'fn', l: 'True f(x)' }, { v: 'value', l: 'True value' }]} />
        {inp.truth === 'fn' && <FxInput id="truefx" value={inp.fx} onChange={(v) => m.set({ fx: v })} keys={false} />}
        {inp.truth === 'value' && <NumField id="yt" label="y true" value={inp.yTrue} onChange={(v) => m.set({ yTrue: v })} step={0.01} />}
      </div>
    </div>
  );
}

/* ================= table / working panes (Home result card and Canvas sheet) ================= */
function TablePane({ mod, res, dp, kk, onRow, dense, maxH }) {
  return mod === 'root'
    ? <GTable dense={dense} maxH={maxH} cols={rootCols(res, dp)} rows={res.rows} active={kk} onRow={onRow} />
    : res.method === 'lagrange' ? <GTable dense={dense} cols={basisCols(dp)} rows={res.basis} /> : <GTable dense={dense} cols={ddCols(res)} rows={res.X.map((x) => ({ x }))} />;
}
function WorkingPane({ mod, res, kk, setKk }) {
  if (mod === 'root') {
    const rows = res.rows;
    const ex = explainStep(res, kk);
    return (
      <div className="space-y-3 pt-1">
        <div className="flex items-center justify-between">
          <p className="text-[11px] font-semibold uppercase tracking-wider text-c-ink2">Iteration {rows[kk].n}</p>
          <span className="flex gap-1.5">
            <RoundBtn icon={P.left} label="Previous iteration" onClick={() => setKk(Math.max(0, kk - 1))} disabled={kk <= 0} className="h-8 w-8 bg-c-surface2" size={15} />
            <RoundBtn icon={P.right} label="Next iteration" onClick={() => setKk(Math.min(rows.length - 1, kk + 1))} disabled={kk >= rows.length - 1} className="h-8 w-8 bg-c-surface2" size={15} />
          </span>
        </div>
        {ex.lines.map(([t, body]) => (
          <div key={t} className="rounded-2xl bg-c-surface2 px-4 py-3">
            <div className="text-[11px] font-semibold text-c-ink2">{t}</div>
            <div className="mt-1 break-words font-mono text-[13px] leading-5">{body}</div>
          </div>
        ))}
        {ex.note && <p className="flex items-start gap-2 rounded-2xl bg-c-goodbg px-4 py-3 text-[13px] text-c-good"><Icon d={P.info} size={16} className="mt-0.5 shrink-0" />{ex.note}</p>}
      </div>
    );
  }
  return (
    <div className="space-y-3 pt-1">
      <div className="rounded-2xl bg-c-surface2 px-4 py-3">
        <div className="text-[11px] font-semibold text-c-ink2">P(x) expanded</div>
        <div className="mt-1 overflow-x-auto whitespace-nowrap font-mono text-[13px]">{res.poly}</div>
      </div>
      {res.metrics ? metricList(res).map((e) => (
        <div key={e.k} className="flex items-start justify-between gap-3 rounded-2xl bg-c-surface2 px-4 py-3">
          <div className="min-w-0"><div className="text-[13px] font-medium">{e.label}</div><div className="font-mono text-[11px] text-c-ink2">{e.cap}</div></div>
          <div className="shrink-0 text-right font-mono text-[14px]">{e.value}{e.k === 'bound' && <div className="mt-1">{e.holds ? <Badge kind="ok" icon={P.check}>Within bound</Badge> : <Badge kind="warn" icon={P.warn}>Above bound</Badge>}</div>}</div>
        </div>
      )) : <p className="text-sm text-c-ink2">Choose a reference in the inputs to see error metrics.</p>}
    </div>
  );
}

function ShareMenu({ res, onCopy, onSave, tone = 'surface' }) {
  const [open, setOpen] = useState(false);
  const run = (fn) => { setOpen(false); fn(); };
  return (
    <span className="relative">
      <RoundBtn icon={P.share} label="Share and copy" onClick={() => setOpen((v) => !v)} active={open} className={tone === 'glass' ? 'glass' : 'bg-c-surface'} />
      {open && (
        <>
          <button type="button" aria-label="Close menu" className="fixed inset-0 z-20 cursor-default" onClick={() => setOpen(false)} />
          <div className="pop-in absolute right-0 top-12 z-30 w-60 overflow-hidden rounded-2xl border border-c-line bg-c-surface p-1.5 shadow-2xl">
            {[
              ['Save to history', I.bookmark, onSave, false],
              ['Copy result', P.copy, () => onCopy('result'), true],
              ['Copy table as CSV', P.table, () => onCopy('csv'), true],
              ['Copy as LaTeX', P.clip, () => onCopy('latex'), true],
            ].map(([l, ic, fnc, needsRes]) => (
              <button key={l} type="button" disabled={needsRes && !!res.error} onClick={() => run(fnc)} className="flex h-11 w-full items-center gap-3 rounded-xl px-3 text-left text-[14px] font-medium hover:bg-c-surface2 disabled:opacity-40"><Icon d={ic} size={17} />{l}</button>
            ))}
          </div>
        </>
      )}
    </span>
  );
}

/* ================= Canvas: the graph with a bottom sheet (opens only when asked) ================= */
function CanvasView({ mod, m, u, dp, setDp, brackets, onBack, onSave, doCopy, toast, ox, oy, rr, closing }) {
  const { res, inp } = m;
  const [tab, setTab] = useState('inputs');
  const [k, setK] = useState(null);
  const [playing, setPlaying] = useState(false);
  const frameRef = useRef(null);
  const H = useBoxH(frameRef);
  const snaps = [PEEK, Math.round(H * 0.56), Math.round(H * 0.9)];
  const [sheetH, setSheetH] = useState(0);
  const sheetRef = useRef(0);
  const anim = useRef(0);
  const drag = useRef(null);
  const snapTo = (target, ms = 380) => {
    cancelAnimationFrame(anim.current);
    if (reduceMotion()) { sheetRef.current = target; setSheetH(target); return; }
    const from = sheetRef.current;
    const t0 = performance.now();
    const step = (t) => {
      const p = Math.min(1, (t - t0) / ms);
      const v = from + (target - from) * (1 - Math.pow(1 - p, 4));
      sheetRef.current = v;
      setSheetH(v);
      if (p < 1) anim.current = requestAnimationFrame(step);
    };
    anim.current = requestAnimationFrame(step);
  };
  useEffect(() => { const t = setTimeout(() => snapTo(PEEK, 520), reduceMotion() ? 0 : 240); return () => { clearTimeout(t); cancelAnimationFrame(anim.current); }; }, []);
  const nearest = (v) => snaps.reduce((b, s) => (Math.abs(s - v) < Math.abs(b - v) ? s : b), snaps[0]);
  const hDown = (e) => { e.currentTarget.setPointerCapture?.(e.pointerId); drag.current = { y: e.clientY, h: sheetRef.current, moved: false }; cancelAnimationFrame(anim.current); };
  const hMove = (e) => {
    const d = drag.current;
    if (!d) return;
    const dy = d.y - e.clientY;
    if (Math.abs(dy) > 4) d.moved = true;
    const nh = clamp(d.h + dy, PEEK - 16, snaps[2]);
    sheetRef.current = nh;
    setSheetH(nh);
  };
  const hUp = () => {
    const d = drag.current;
    drag.current = null;
    if (!d) return;
    const cur = sheetRef.current;
    if (!d.moved) snapTo(cur < snaps[1] - 20 ? snaps[1] : cur < snaps[2] - 20 ? snaps[2] : snaps[0]);
    else snapTo(nearest(cur));
  };
  const openTab = (t) => { setTab(t); if (sheetRef.current < snaps[1] - 30) snapTo(snaps[1]); };

  const rows = mod === 'root' && !res.error ? res.rows : [];
  const last = Math.max(0, rows.length - 1);
  const first = useRef(true);
  useEffect(() => {
    if (first.current) {
      first.current = false;
      if (mod === 'root' && last > 0 && !reduceMotion()) { setK(0); setPlaying(true); return; }
    }
    setK(null);
    setPlaying(false);
  }, [res]);
  const kk = k === null ? last : Math.min(k, last);
  const stepMs = clamp(2600 / Math.max(1, last), 150, 650);
  useEffect(() => {
    if (!playing) return undefined;
    const t = setInterval(() => setK((p) => {
      const cur = p === null ? last : p;
      if (cur >= last) { setPlaying(false); return last; }
      return cur + 1;
    }), stepMs);
    return () => clearInterval(t);
  }, [playing, last, stepMs]);
  const play = () => {
    if (playing) { setPlaying(false); return; }
    if (kk >= last) setK(0);
    setPlaying(true);
  };

  const F = (v) => fmt(v, dp);
  let plot = null;
  const padB = Math.min(sheetH + 30, H - 190);
  if (!res.error) {
    if (mod === 'root') {
      const cur = rows[kk];
      const ov = [];
      const pts = rows.slice(0, kk + 1).map((r, i) => ({ kind: 'iter', x: r.x, y: r.fx, opacity: 0.35 + 0.4 * (i / Math.max(1, kk)), r: 3.5 }));
      if (res.method === 'bisection') {
        ov.push({ type: 'band', x0: cur.lo, x1: cur.hi, color: 'var(--c-accent)', opacity: 0.13 });
        ov.push({ type: 'vline', x: cur.x, color: 'var(--c-hot)', w: 1.6, dash: '4 4' });
      } else if (res.method === 'newton' && kk >= 1) {
        const p = rows[kk - 1];
        ov.push({ type: 'seg', x1: p.x, y1: 0, x2: p.x, y2: p.fx, color: 'var(--c-ink2)', dash: '3 4', w: 1.4 });
        ov.push({ type: 'seg', x1: p.x, y1: p.fx, x2: cur.x, y2: 0, color: 'var(--c-hot)', w: 2.2 });
      } else if (res.method === 'secant' && kk >= 2) {
        const p0 = rows[kk - 2];
        ov.push({ type: 'seg', x1: p0.x, y1: p0.fx, x2: cur.x, y2: 0, color: 'var(--c-hot)', w: 2.2 });
      }
      const atEnd = kk === last;
      pts[pts.length - 1] = atEnd
        ? { kind: 'root', x: cur.x, y: cur.fx, label: `x* ≈ ${fmt(cur.x, Math.min(dp, 6))}` }
        : { kind: 'data', x: cur.x, y: cur.fx, color: 'var(--c-hot)', label: `n = ${cur.n}` };
      plot = (
        <Plot key="root" curves={[{ f: res.f, color: 'var(--c-curve)', w: 3, label: 'f(x)' }]} points={pts} overlays={ov}
          domain={domainFor([res.root, ...res.rows.map((r) => r.x), ...res.used])} pad={{ t: 74, b: padB, l: 38, r: 14 }} controls controlsClass="right-3 top-[76px] flex-col" label="f(x) with iterations" />
      );
    } else {
      const dataPts = res.pts.map((p, i) => ({ id: `p${i}`, kind: 'data', x: p.x, y: p.y, draggable: true }));
      const pts = [...dataPts, { kind: 'target', x: res.xe, y: res.Pxe, label: `P(${fmt(res.xe, 3)})` }];
      if (res.metrics) pts.push({ kind: 'true', x: res.xe, y: res.metrics.yTrue });
      const curves = [{ f: res.P, color: 'var(--c-curve)', w: 3, label: 'P(x)', fit: !res.fTrue }];
      if (res.fTrue) curves.push({ f: res.fTrue, color: 'var(--c-ink2)', w: 2, dash: '6 5', fit: true, probe: false });
      plot = (
        <Plot key="interp" curves={curves} points={pts} includeZero={false}
          overlays={[{ type: 'vline', id: 'xe', x: res.xe, color: 'var(--c-hot)', dash: '4 4', draggable: true }]}
          domain={domainFor(res.X, 0.3)} pad={{ t: 74, b: padB, l: 38, r: 14 }} controls controlsClass="right-3 top-[76px] flex-col" label="Interpolating polynomial"
          onDragPoint={(id, y) => {
            const i = Number(String(id).slice(1));
            const ri = inp.rows.findIndex((r) => Number(r.x) === res.pts[i].x);
            if (ri >= 0) m.set({ rows: inp.rows.map((r, j) => (j === ri ? { ...r, y: String(+y.toFixed(4)) } : r)) });
          }}
          onDragLine={(id, x) => m.set({ xEval: String(+x.toFixed(3)) })} />
      );
    }
  }

  const tabs = mod === 'root' ? [['inputs', 'Inputs'], ['table', 'Iterations'], ['working', 'Working']] : [['inputs', 'Inputs'], ['table', 'Steps'], ['working', 'Errors']];
  const xr = mod === 'interp' && !res.error ? [res.X[0], res.X[res.X.length - 1]] : [0, 1];
  const title = mod === 'root' ? (inp.fx || '…') : `${res.error ? '…' : res.pts.length} points`;

  return (
    <div ref={frameRef} className={cx('canvas-layer absolute inset-0 z-40 overflow-hidden bg-c-bg', closing ? 'reveal-out' : 'reveal-in')}
      style={{ '--ox': `${ox}px`, '--oy': `${oy}px`, '--rr': `${rr}px`, '--toast-bottom': `${Math.round(sheetH + 14)}px` }}>
      <div className="absolute inset-0">{plot}</div>
      {res.error && <div className="absolute inset-x-4 top-24 z-10"><ErrorBox>{res.error}</ErrorBox></div>}

      <div className="rise-top absolute inset-x-3 top-3 z-20 flex items-center gap-2">
        <RoundBtn icon={I.back} label="Back to inputs" onClick={onBack} className="glass" />
        <div className="glass flex h-10 min-w-0 flex-1 items-center rounded-full px-4"><span className="truncate font-mono text-[12.5px] font-medium">{title}</span></div>
        <RoundBtn icon={P.undo} label="Undo" onClick={u.undo} disabled={!u.canUndo} className="glass" />
        <RoundBtn icon={P.redo} label="Redo" onClick={u.redo} disabled={!u.canRedo} className="glass" />
        <ShareMenu res={res} tone="glass" onSave={onSave} onCopy={doCopy} />
      </div>

      <div className="absolute inset-x-0 bottom-0 z-30 flex flex-col overflow-hidden rounded-t-[32px] bg-c-surface shadow-[0_-14px_44px_rgba(10,16,30,.16)]" style={{ height: sheetH }}>
        <div className="shrink-0 cursor-grab touch-none px-5 pb-1 pt-2.5" onPointerDown={hDown} onPointerMove={hMove} onPointerUp={hUp} onPointerCancel={hUp}>
          <div className="mx-auto mb-2.5 h-1.5 w-10 rounded-full bg-c-line" />
          {res.error ? (
            <p className="py-3 text-sm text-c-ink2">Fix the input to see a result.</p>
          ) : mod === 'root' ? (
            <div className="flex items-end justify-between gap-3">
              <div className="min-w-0">
                <div className="text-[11px] font-semibold uppercase tracking-wider text-c-ink2">Root x*</div>
                <BigNumber value={res.root} dp={dp} className="truncate font-mono text-[32px] font-medium leading-9 tracking-tight tabular-nums" />
              </div>
              <div className="flex shrink-0 flex-col items-end gap-1.5"><ConvBadge res={res} /><span className="text-xs text-c-ink2">{res.iterations} iterations</span></div>
            </div>
          ) : (
            <div className="flex items-end justify-between gap-3">
              <div className="min-w-0">
                <div className="text-[11px] font-semibold uppercase tracking-wider text-c-ink2">P({fmt(res.xe, 4)})</div>
                <BigNumber value={res.Pxe} dp={dp} className="truncate font-mono text-[32px] font-medium leading-9 tracking-tight tabular-nums" />
              </div>
              <div className="flex shrink-0 flex-col items-end gap-1.5">
                {res.metrics ? <Badge kind="neutral">|error| {fmtE(res.metrics.abs)}</Badge> : <Badge kind="neutral">Degree {res.n}</Badge>}
                <span className="text-xs text-c-ink2">{res.metrics && res.metrics.absRel !== null ? `${res.metrics.absRel.toFixed(3)} % off` : 'no reference'}</span>
              </div>
            </div>
          )}
        </div>

        {!res.error && (
          <div className="flex h-[52px] shrink-0 items-center gap-2 px-4">
            {mod === 'root' ? (
              <>
                <RoundBtn icon={P.prev} label="Previous iteration" onClick={() => { setPlaying(false); setK(Math.max(0, kk - 1)); }} disabled={kk <= 0} />
                <button type="button" aria-label={playing ? 'Pause' : 'Play iterations'} onClick={play} className="flex h-11 w-11 shrink-0 items-center justify-center rounded-full bg-c-ink text-c-bg transition active:scale-90"><Icon d={playing ? P.pause : P.play} size={18} fill={!playing} /></button>
                <RoundBtn icon={P.next} label="Next iteration" onClick={() => { setPlaying(false); setK(Math.min(last, kk + 1)); }} disabled={kk >= last} />
                <input type="range" aria-label="Iteration" min={0} max={last} value={kk} onChange={(e) => { setPlaying(false); setK(Number(e.target.value)); }} className="scrub min-w-0 flex-1" />
                <span className="w-12 shrink-0 text-right font-mono text-xs text-c-ink2">{rows[kk].n}/{rows[last].n}</span>
              </>
            ) : (
              <>
                <span className="shrink-0 pl-2 font-mono text-xs text-c-ink2">x</span>
                <input type="range" aria-label="Evaluate at x" min={xr[0]} max={xr[1]} step={(xr[1] - xr[0]) / 200} value={clamp(res.xe, xr[0], xr[1])} onChange={(e) => m.set({ xEval: String(+Number(e.target.value).toFixed(3)) })} className="scrub min-w-0 flex-1" />
                <span className="w-14 shrink-0 text-right font-mono text-xs text-c-ink2">{fmt(res.xe, 3)}</span>
              </>
            )}
          </div>
        )}

        <div className="flex shrink-0 gap-1 px-4 pb-1 pt-1" role="tablist">
          {tabs.map(([id, l]) => (
            <button key={id} type="button" role="tab" aria-selected={tab === id} onClick={() => openTab(id)}
              className={cx('h-9 flex-1 rounded-full text-[13px] font-medium transition', tab === id ? 'bg-c-surface2 text-c-ink' : 'text-c-ink2')}>{l}</button>
          ))}
        </div>

        <div className="min-h-0 flex-1 overflow-auto px-5 pb-8 pt-2">
          <div key={mod + tab} className="tab-in">
            {tab === 'inputs' && (mod === 'root' ? <RootInputs m={m} dp={dp} setDp={setDp} brackets={brackets} /> : <InterpInputs m={m} />)}
            {tab === 'table' && !res.error && <TablePane mod={mod} res={res} dp={dp} kk={kk} onRow={mod === 'root' ? (i) => { setPlaying(false); setK(i); } : undefined} />}
            {tab === 'working' && !res.error && <WorkingPane mod={mod} res={res} kk={kk} setKk={(i) => { setPlaying(false); setK(i); }} />}
          </div>
        </div>
      </div>
      {toast.node}
    </div>
  );
}

/* ================= Home result card ================= */
function ResultCard({ mod, res, inp, dp, onCopy, onSave }) {
  const [pane, setPane] = useState(null);
  const [hk, setHk] = useState(null);
  const rows = mod === 'root' ? res.rows : [];
  const last = Math.max(0, rows.length - 1);
  const kk = hk === null ? last : Math.min(hk, last);
  const panes = mod === 'root' ? [['table', 'Iterations', P.table], ['working', 'Working', P.list]] : [['table', 'Steps', P.table], ['working', 'Errors', P.list]];
  return (
    <section className="pop-in rounded-[28px] bg-c-surface p-5">
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <div className="text-[11px] font-semibold uppercase tracking-wider text-c-ink2">{mod === 'root' ? `Root x* · ${METHOD_SHORT[inp.method]}` : `P(${fmt(res.xe, 4)}) · ${METHOD_SHORT[inp.method]}`}</div>
          <BigNumber value={mod === 'root' ? res.root : res.Pxe} dp={dp} className="mt-1 truncate font-mono text-[38px] font-medium leading-[44px] tracking-tight tabular-nums" />
        </div>
        <ShareMenu res={res} onSave={onSave} onCopy={onCopy} />
      </div>
      <div className="mt-3 flex items-center justify-between gap-3">
        <div className="flex min-w-0 flex-wrap items-center gap-x-3 gap-y-1.5">
          {mod === 'root' ? (
            <>
              <ConvBadge res={res} />
              <span className="text-xs text-c-ink2">{res.iterations} iterations · last change {fmtE(res.finalAbs)}</span>
            </>
          ) : (
            <>
              {res.metrics ? <Badge kind="neutral">|error| {fmtE(res.metrics.abs)}</Badge> : <Badge kind="neutral">Degree {res.n}</Badge>}
              <span className="text-xs text-c-ink2">{res.metrics && res.metrics.absRel !== null ? `${res.metrics.absRel.toFixed(3)} % off` : 'no reference'}</span>
            </>
          )}
        </div>
        {mod === 'root' && res.rows.length > 2 && <div className="shrink-0 text-c-curve" title="Error per iteration, log scale"><Spark values={res.rows.map((r) => (r.abs === null ? NaN : r.abs))} /></div>}
      </div>
      <div className="mt-4 flex gap-1.5" role="tablist">
        {panes.map(([id, l, ic]) => (
          <button key={id} type="button" role="tab" aria-selected={pane === id} onClick={() => setPane(pane === id ? null : id)}
            className={cx('flex h-9 items-center gap-1.5 rounded-full px-3.5 text-[13px] font-semibold transition active:scale-95', pane === id ? 'bg-c-ink text-c-bg' : 'bg-c-surface2 text-c-ink')}><Icon d={ic} size={15} />{l}</button>
        ))}
      </div>
      <div className="collapse-grid" data-open={pane ? 'true' : 'false'}>
        <div className="min-h-0 overflow-hidden">
          {pane && (
            <div className="pt-3">
              {pane === 'table' ? <TablePane mod={mod} res={res} dp={dp} kk={kk} dense maxH={260} onRow={mod === 'root' ? setHk : undefined} /> : <WorkingPane mod={mod} res={res} kk={kk} setKk={setHk} />}
            </div>
          )}
        </div>
      </div>
    </section>
  );
}

/* ================= the app ================= */
export function NumLabApp() {
  const rootM = useModule(START_ROOT, solveRoot);
  const interpM = useModule(START_INTERP, interpolate);
  const undoR = useUndo(rootM.inp, rootM.replace);
  const undoI = useUndo(interpM.inp, interpM.replace);
  const [mod, setMod] = useState('root');
  const [dp, setDp] = useState(6);
  const [phase, setPhase] = useState('splash');
  const [view, setView] = useState('home');
  const [closingCanvas, setClosingCanvas] = useState(false);
  const [origin, setOrigin] = useState({ ox: 200, oy: 700, rr: 1000 });
  const [hist, setHist] = useState(false);
  const [closingHist, setClosingHist] = useState(false);
  const [filter, setFilter] = useState('all');
  const [items, setItems] = useState(SEEDS);
  const [pulse, setPulse] = useState(false);
  const nid = useRef(10);
  const frameRef = useRef(null);
  const toast = useToast();
  const canvasToast = useToast();
  const histToast = useToast();

  const m = mod === 'root' ? rootM : interpM;
  const u = mod === 'root' ? undoR : undoI;
  const { res, inp } = m;
  const idle = mod === 'root' ? !inp.fx.trim() : pointCount(inp.rows) < 2;
  const needX = mod === 'interp' && !idle && !!res.error && !String(inp.xEval).trim();
  const canGraph = !res.error;
  const fn = useMemo(() => tryCompile(rootM.inp.fx).f, [rootM.inp.fx]);
  const brackets = useMemo(() => (fn ? findBrackets(fn) : []), [fn]);

  const prevCan = useRef(canGraph);
  useEffect(() => {
    if (!prevCan.current && canGraph && !reduceMotion()) { setPulse(true); const t = setTimeout(() => setPulse(false), 1300); prevCan.current = canGraph; return () => clearTimeout(t); }
    prevCan.current = canGraph;
    return undefined;
  }, [canGraph]);

  const runInputs = (mm) => (mm === 'root' ? rootM.inp : { ...interpM.inp, rows: filled(interpM.inp.rows) });
  const saveRun = (announce, t = toast) => {
    if (res.error) return;
    const inputs = runInputs(mod);
    const j = JSON.stringify(inputs);
    if (items.some((i) => i.kind === mod && JSON.stringify(i.inputs) === j)) { if (announce) t.show('Already in history', { label: 'View', run: openHistory }); return; }
    setItems((l) => [{ id: nid.current++, kind: mod, inputs, pinned: false, when: 'Just now' }, ...l]);
    if (announce) t.show('Saved to history', { label: 'View', run: openHistory });
  };
  const doCopy = (t) => async (what) => {
    const text = what === 'result' ? textOf({ kind: mod, inputs: inp }, res) : what === 'csv' ? toCSV(res) : toLatex(res);
    t.show((await copyText(text)) ? { result: 'Result copied', csv: 'CSV copied', latex: 'LaTeX copied' }[what] : 'Copy is not available in this preview');
  };

  const openGraph = (e) => {
    if (!canGraph) return;
    const fr = frameRef.current.getBoundingClientRect();
    const b = e.currentTarget.getBoundingClientRect();
    const ox = b.left - fr.left + b.width / 2;
    const oy = b.top - fr.top + b.height / 2;
    const rr = Math.hypot(Math.max(ox, fr.width - ox), Math.max(oy, fr.height - oy)) + 24;
    setOrigin({ ox, oy, rr });
    saveRun(false);
    setClosingCanvas(false);
    setView('canvas');
  };
  const closeGraph = () => {
    setClosingCanvas(true);
    setTimeout(() => { setView('home'); setClosingCanvas(false); }, reduceMotion() ? 0 : 440);
  };
  const openHistory = () => { setClosingHist(false); setHist(true); };
  const closeHistory = () => { setClosingHist(true); setTimeout(() => { setHist(false); setClosingHist(false); }, reduceMotion() ? 0 : 380); };

  const loadItem = (it) => {
    const mm = it.kind === 'root' ? rootM : interpM;
    mm.replace(it.kind === 'interp' ? { ...it.inputs, rows: it.inputs.rows.map((r) => ({ ...r })) } : { ...it.inputs });
    setMod(it.kind);
  };
  const addItem = (kind, inputs, when) => setItems((l) => [{ id: nid.current++, kind, inputs, pinned: false, when }, ...l]);
  const delItem = (id) => {
    const idx = items.findIndex((i) => i.id === id);
    const item = items[idx];
    setItems((l) => l.filter((i) => i.id !== id));
    histToast.show('Run deleted', { label: 'Undo', run: () => setItems((l) => { const c = l.slice(); c.splice(Math.min(idx, c.length), 0, item); return c; }) });
  };

  const home = view === 'canvas' && !closingCanvas;
  return (
    <div ref={frameRef} className="cD relative h-full overflow-hidden bg-c-bg text-c-ink" style={{ '--toast-bottom': '104px' }}>
      <div className={cx('home absolute inset-0 flex flex-col', phase !== 'splash' && 'ready', home && 'home-covered')} aria-hidden={view === 'canvas' || undefined}>
        <header className="rise flex shrink-0 items-center gap-2.5 px-5 pb-1 pt-5" style={{ '--i': 0 }}>
          <LogoMark size={32} />
          <Wordmark size={24} />
          <span className="flex-1" />
          <RoundBtn icon={P.undo} label="Undo" onClick={u.undo} disabled={!u.canUndo} className="bg-c-surface" />
          <RoundBtn icon={P.redo} label="Redo" onClick={u.redo} disabled={!u.canRedo} className="bg-c-surface" />
          <span className="relative">
            <RoundBtn icon={I.clock} label="History" onClick={openHistory} className="bg-c-surface" />
            {items.length > 0 && <span key={items.length} className="badge-pop pointer-events-none absolute -right-0.5 -top-0.5 flex h-[18px] min-w-[18px] items-center justify-center rounded-full bg-c-hot px-1 text-[10px] font-bold text-white">{items.length}</span>}
          </span>
        </header>
        <div className="rise shrink-0 px-4 pt-3" style={{ '--i': 1 }}>
          <Seg2 label="Module" value={mod} onChange={setMod} options={[{ v: 'root', l: 'Roots' }, { v: 'interp', l: 'Interpolate' }]} className="bg-c-surface" thumb="bg-c-bg" />
        </div>

        <main className="min-h-0 flex-1 overflow-auto px-4 pb-44 pt-3">
          <div key={mod} className="space-y-3">
            <section className="rise rounded-[28px] bg-c-surface p-5" style={{ '--i': 2 }}>
              {mod === 'root' ? <RootInputs m={rootM} dp={dp} setDp={setDp} brackets={brackets} /> : <InterpInputs m={interpM} />}
            </section>
            {canGraph ? (
              <ResultCard key={mod} mod={mod} res={res} inp={inp} dp={dp} onSave={() => saveRun(true)} onCopy={doCopy(toast)} />
            ) : (idle || needX) ? null : (
              <div className="rise" style={{ '--i': 3 }}><ErrorBox>{res.error}</ErrorBox></div>
            )}
          </div>
        </main>

        <div className="absolute inset-x-0 bottom-0 z-20 flex flex-wrap items-center gap-x-3 gap-y-2 px-4 pb-5 pt-10" style={{ background: 'linear-gradient(to top, var(--c-bg) 62%, transparent)' }}>
          {!canGraph && <p className="flex w-full items-center justify-center gap-1.5 text-xs text-c-ink2"><Icon d={I.lock} size={13} />{idle ? (mod === 'root' ? 'Type a function or tap an example to unlock the graph' : 'Add two points or tap an example to unlock the graph') : needX ? 'Enter x to evaluate to unlock the graph' : 'Fix the input to unlock the graph'}</p>}
          <button type="button" disabled={!canGraph} onClick={openGraph} aria-label="Show graph"
            className={cx('flex h-14 min-w-0 flex-1 items-center justify-center gap-2.5 rounded-full text-[16px] font-semibold transition active:scale-[0.98]', canGraph ? 'bg-c-accent text-c-onaccent shadow-[0_10px_28px_-8px_var(--c-accent)]' : 'bg-c-surface text-c-ink2', pulse && 'ready-pulse')}>
            <Icon d={canGraph ? P.graph : I.lock} size={20} />Graph{canGraph && <Icon d={P.arrow} size={18} />}
          </button>
          <button type="button" disabled={!canGraph} onClick={() => saveRun(true)} aria-label="Save to history" className="flex h-14 items-center gap-2 rounded-full bg-c-surface px-5 text-[15px] font-semibold transition active:scale-95 disabled:text-c-ink2 disabled:opacity-70"><Icon d={I.bookmark} size={19} />Save</button>
        </div>
        {toast.node}
      </div>

      {view === 'canvas' && (
        <CanvasView mod={mod} m={m} u={u} dp={dp} setDp={setDp} brackets={brackets} closing={closingCanvas} ox={origin.ox} oy={origin.oy} rr={origin.rr}
          toast={canvasToast} onBack={closeGraph} onSave={() => saveRun(true, canvasToast)} doCopy={doCopy(canvasToast)} />
      )}

      {hist && (
        <HistoryScreen items={items} filter={filter} setFilter={setFilter} closing={closingHist} onClose={closeHistory}
          onPin={(id) => setItems((l) => l.map((i) => (i.id === id ? { ...i, pinned: !i.pinned } : i)))}
          onDelete={delItem}
          onOpen={(it) => { loadItem(it); closeHistory(); if (view === 'canvas') closeGraph(); }}
          onOpenGraph={(it) => {
            loadItem(it);
            closeHistory();
            const fr = frameRef.current.getBoundingClientRect();
            setOrigin({ ox: fr.width / 2, oy: fr.height - 60, rr: Math.hypot(fr.width, fr.height) });
            setClosingCanvas(false);
            setView('canvas');
          }}
          onDup={(it) => { addItem(it.kind, it.inputs, 'Duplicate'); histToast.show('Duplicated'); }}
          onTry={(it, mm) => { addItem(it.kind, { ...it.inputs, method: mm }, 'Just now'); histToast.show(`Ran ${METHOD_SHORT[mm]} on the same input`); }}
          onCopy={async (t, ok) => histToast.show((await copyText(t)) ? ok : 'Copy is not available in this preview')}
          toastNode={histToast.node} />
      )}

      {phase !== 'done' && <Splash onExit={() => setPhase('out')} onDone={() => setPhase('done')} />}
    </div>
  );
}
