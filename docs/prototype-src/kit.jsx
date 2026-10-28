import React, { useState, useRef, useEffect, useMemo } from 'react';
import { tryCompile, fmt, fmtE } from './math.js';

export const cx = (...a) => a.filter(Boolean).join(' ');

/* ---------------- icons ---------------- */
export const P = {
  plus: 'M12 5v14M5 12h14', minus: 'M5 12h14', x: 'M6 6l12 12M18 6L6 18',
  trash: 'M4 7h16M10 11v6M14 11v6M6 7l1 12a2 2 0 002 2h6a2 2 0 002-2l1-12M9 7V4h6v3',
  check: 'M5 12.5l4.5 4.5L19 7', warn: 'M12 4l9.5 16h-19L12 4zM12 10v4M12 17.2v.3',
  undo: 'M9 14L4 9l5-5M4 9h10a6 6 0 010 12h-3', redo: 'M15 14l5-5-5-5M20 9H10a6 6 0 000 12h3',
  share: 'M12 15V4M8 8l4-4 4 4M5 13v6h14v-6', copy: 'M9 9h10v11H9zM5 15V4h10',
  more: 'M5 12v.01M12 12v.01M19 12v.01', pin: 'M9 4h6l-1 6 3 3H7l3-3-1-6zM12 13v7',
  play: 'M8 5.5v13l11-6.5z', pause: 'M8 5v14M16 5v14', prev: 'M6 5v14M18 5.5v13L8 12z', next: 'M18 5v14M6 5.5v13l10-6.5z',
  up: 'M6 15l6-6 6 6', down: 'M6 9l6 6 6-6', right: 'M9 6l6 6-6 6', left: 'M15 6l-6 6 6 6',
  keyboard: 'M3 7h18v10H3zM7 11v.01M11 11v.01M15 11v.01M7 14h10', bolt: 'M13 3L5 14h6l-1 7 8-11h-6l1-7z',
  sliders: 'M4 7h10M18 7h2M4 17h2M10 17h10M14 5v4M6 15v4', edit: 'M4 20h4L19 9l-4-4L4 16v4z',
  graph: 'M4 4v16h16M7.5 15.5l3.5-4.5 3 2.5 4.5-6', table: 'M4 5h16v14H4zM4 10h16M4 15h16M10 5v14',
  list: 'M4 6h16M4 12h10M4 18h13', roots: 'M3 12h18M5 19c4 0 5-14 9-14 2 0 4 3 5 5', interp: 'M4 17c3-9 6-9 8-4s5 4 8-6M6 17.5v.01M12 13v.01M20 7v.01',
  arrow: 'M5 12h14M13 6l6 6-6 6', info: 'M12 11v6M12 7.5v.01M12 3a9 9 0 100 18 9 9 0 000-18z', reset: 'M4 12a8 8 0 103-6.2M4 4v5h5',
  swap: 'M7 4L3 8l4 4M3 8h14M17 20l4-4-4-4M21 16H7', clip: 'M9 4h6v3H9zM7 6H5v14h14V6h-2',
};
export function Icon({ d, size = 20, fill = false, sw = 1.9, className }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill={fill ? 'currentColor' : 'none'} stroke="currentColor" strokeWidth={sw} strokeLinecap="round" strokeLinejoin="round" className={className} aria-hidden="true">
      <path d={d} />
    </svg>
  );
}

/* ---------------- hooks ---------------- */
export function useModule(initial, solve) {
  const [inp, setInp] = useState(initial);
  const [done, setDone] = useState(initial);
  const res = useMemo(() => solve(done), [done]);
  return {
    inp, res, done,
    set: (patch) => { const n = { ...inp, ...patch }; setInp(n); setDone(n); },
    replace: (n) => { setInp(n); setDone(n); },
  };
}

export function useToast() {
  const [msg, setMsg] = useState(null);
  const t = useRef(null);
  const show = (text, action) => {
    clearTimeout(t.current);
    setMsg({ text, action });
    t.current = setTimeout(() => setMsg(null), action ? 4200 : 2000);
  };
  useEffect(() => () => clearTimeout(t.current), []);
  const node = msg && (
    <div role="status" className="pop-in absolute inset-x-4 z-50 flex h-12 items-center justify-between gap-3 rounded-2xl bg-c-ink px-4 text-[13px] text-c-bg shadow-xl" style={{ bottom: 'var(--toast-bottom, 20px)' }}>
      <span>{msg.text}</span>
      {msg.action && <button type="button" className="font-semibold text-c-hot" onClick={() => { msg.action.run(); setMsg(null); }}>{msg.action.label}</button>}
    </div>
  );
  return { node, show };
}

export async function copyText(text) {
  try {
    await navigator.clipboard.writeText(text);
    return true;
  } catch (e) {
    try {
      const ta = document.createElement('textarea');
      ta.value = text;
      document.body.appendChild(ta);
      ta.select();
      const ok = document.execCommand('copy');
      ta.remove();
      return ok;
    } catch (e2) { return false; }
  }
}

export function useUndo(value, replace) {
  const st = useRef({ stack: [value], i: 0, last: JSON.stringify(value) });
  const [, force] = useState(0);
  useEffect(() => {
    const j = JSON.stringify(value);
    if (j === st.current.last) return undefined;
    const t = setTimeout(() => {
      const s = st.current;
      s.stack = s.stack.slice(0, s.i + 1);
      s.stack.push(value);
      s.i = s.stack.length - 1;
      s.last = j;
      force((n) => n + 1);
    }, 600);
    return () => clearTimeout(t);
  }, [value]);
  const flush = () => {
    const s = st.current;
    const j = JSON.stringify(value);
    if (j !== s.last) {
      s.stack = s.stack.slice(0, s.i + 1);
      s.stack.push(value);
      s.i = s.stack.length - 1;
      s.last = j;
    }
  };
  const go = (d) => {
    flush();
    const s = st.current;
    const n = s.i + d;
    if (n < 0 || n >= s.stack.length) return;
    s.i = n;
    s.last = JSON.stringify(s.stack[n]);
    replace(s.stack[n]);
    force((x) => x + 1);
  };
  return { undo: () => go(-1), redo: () => go(1), canUndo: st.current.i > 0 || JSON.stringify(value) !== st.current.last, canRedo: st.current.i < st.current.stack.length - 1 };
}

/* ---------------- controls ---------------- */
export function Seg2({ options, value, onChange, label, className, thumb = 'bg-c-surface', size = 'md' }) {
  const i = Math.max(0, options.findIndex((o) => o.v === value));
  return (
    <div role="radiogroup" aria-label={label} className={cx('relative grid rounded-full p-1', className || 'bg-c-surface2')} style={{ gridTemplateColumns: `repeat(${options.length}, minmax(0, 1fr))` }}>
      <span aria-hidden="true" className={cx('absolute bottom-1 top-1 rounded-full shadow-sm transition-transform duration-300 ease-[cubic-bezier(.3,1.25,.5,1)]', thumb)}
        style={{ left: 4, width: `calc((100% - 8px) / ${options.length})`, transform: `translateX(${i * 100}%)` }} />
      {options.map((o) => (
        <button key={o.v} type="button" role="radio" aria-checked={o.v === value} onClick={() => onChange(o.v)}
          className={cx('relative z-10 truncate rounded-full px-2 font-medium transition-colors', size === 'sm' ? 'h-8 text-xs' : 'h-9 text-[13px]', o.v === value ? 'text-c-ink' : 'text-c-ink2')}>
          {o.l}
        </button>
      ))}
    </div>
  );
}

export function RoundBtn({ icon, label, onClick, disabled, className, size = 18, active }) {
  return (
    <button type="button" aria-label={label} title={label} disabled={disabled} onClick={onClick}
      className={cx('flex h-10 w-10 shrink-0 items-center justify-center rounded-full transition active:scale-90 disabled:opacity-35', active ? 'bg-c-ink text-c-bg' : '', className)}>
      <Icon d={icon} size={size} />
    </button>
  );
}

export function Pill({ children, active, onClick, className, icon }) {
  return (
    <button type="button" onClick={onClick} aria-pressed={active}
      className={cx('flex h-8 shrink-0 items-center gap-1.5 whitespace-nowrap rounded-full px-3 text-[13px] font-medium transition active:scale-95', active ? 'bg-c-ink text-c-bg' : 'bg-c-surface2 text-c-ink', className)}>
      {icon && <Icon d={icon} size={14} />}{children}
    </button>
  );
}

export function NumField({ id, label, value, onChange, step = 0.1, className, hint }) {
  const bump = (d) => {
    const v = Number(value);
    if (!Number.isFinite(v)) return;
    onChange(String(+(v + d * step).toFixed(10)));
  };
  const bad = String(value).trim() !== '' && !Number.isFinite(Number(value));
  return (
    <div className={cx('flex h-[54px] items-center rounded-2xl bg-c-surface2 px-1', bad && 'ring-2 ring-c-bad', className)}>
      <button type="button" aria-label={`Decrease ${label}`} onClick={() => bump(-1)} className="flex h-10 w-9 shrink-0 items-center justify-center rounded-xl text-c-ink2 active:scale-90 active:bg-c-line"><Icon d={P.minus} size={16} /></button>
      <label htmlFor={id} className="min-w-0 flex-1 text-center">
        <span className="block truncate text-[10px] font-medium uppercase tracking-wider text-c-ink2">{label}</span>
        <input id={id} value={value} onChange={(e) => onChange(e.target.value)} inputMode="text" autoComplete="off" spellCheck="false" aria-label={label}
          className="block w-full min-w-0 bg-transparent text-center font-mono text-[15px] font-medium text-c-ink outline-none" />
      </label>
      <button type="button" aria-label={`Increase ${label}`} onClick={() => bump(1)} className="flex h-10 w-9 shrink-0 items-center justify-center rounded-xl text-c-ink2 active:scale-90 active:bg-c-line"><Icon d={P.plus} size={16} /></button>
    </div>
  );
}

const SUPS = { 0: '⁰', 1: '¹', 2: '²', 3: '³', 4: '⁴', 5: '⁵', 6: '⁶', 7: '⁷', 8: '⁸', 9: '⁹', '-': '⁻' };
export const sup = (n) => String(n).split('').map((c) => SUPS[c] ?? c).join('');

export function Stepper({ label, display, onMinus, onPlus, className }) {
  return (
    <div className={cx('flex h-[54px] items-center rounded-2xl bg-c-surface2 px-1', className)}>
      <button type="button" aria-label={`Decrease ${label}`} onClick={onMinus} className="flex h-10 w-9 shrink-0 items-center justify-center rounded-xl text-c-ink2 active:scale-90 active:bg-c-line"><Icon d={P.minus} size={16} /></button>
      <div className="min-w-0 flex-1 text-center">
        <span className="block truncate text-[10px] font-medium uppercase tracking-wider text-c-ink2">{label}</span>
        <span className="block font-mono text-[15px] font-medium text-c-ink">{display}</span>
      </div>
      <button type="button" aria-label={`Increase ${label}`} onClick={onPlus} className="flex h-10 w-9 shrink-0 items-center justify-center rounded-xl text-c-ink2 active:scale-90 active:bg-c-line"><Icon d={P.plus} size={16} /></button>
    </div>
  );
}

export function tolExp(tol) {
  const v = Number(tol);
  return Number.isFinite(v) && v > 0 ? Math.max(1, Math.min(12, Math.round(-Math.log10(v)))) : 5;
}

const KEYS = [['x', 'x'], ['^', '^'], ['√', 'sqrt', 1], ['sin', 'sin', 1], ['cos', 'cos', 1], ['tan', 'tan', 1], ['e', 'e'], ['π', 'pi'], ['ln', 'ln', 1], ['exp', 'exp', 1], ['abs', 'abs', 1], ['(', '('], [')', ')']];
export function KeyRow({ value, onChange, inputRef }) {
  const sel = () => {
    const el = inputRef.current;
    return [el?.selectionStart ?? value.length, el?.selectionEnd ?? value.length, el];
  };
  const place = (el, pos) => requestAnimationFrame(() => { if (el) { el.focus(); el.setSelectionRange(pos, pos); } });
  const put = (t, fn) => {
    const [s, e, el] = sel();
    if (fn) {
      const inner = value.slice(s, e);
      onChange(`${value.slice(0, s)}${t}(${inner})${value.slice(e)}`);
      place(el, inner ? s + t.length + 2 + inner.length : s + t.length + 1);
    } else {
      onChange(value.slice(0, s) + t + value.slice(e));
      place(el, s + t.length);
    }
  };
  const del = () => {
    const [s, e, el] = sel();
    const from = s === e ? Math.max(0, s - 1) : s;
    onChange(value.slice(0, from) + value.slice(e));
    place(el, from);
  };
  return (
    <div className="flex gap-1.5 overflow-x-auto pb-0.5 [scrollbar-width:none]" role="group" aria-label="Math keys">
      {KEYS.map(([l, t, fn]) => (
        <button key={l} type="button" onPointerDown={(e) => e.preventDefault()} onClick={() => put(t, fn)}
          className="h-9 min-w-10 shrink-0 rounded-xl bg-c-surface2 px-3 font-mono text-[14px] text-c-ink transition active:scale-90 active:bg-c-line">{l}</button>
      ))}
      <button type="button" aria-label="Backspace" onPointerDown={(e) => e.preventDefault()} onClick={del} className="h-9 min-w-10 shrink-0 rounded-xl bg-c-surface2 px-3 text-c-ink transition active:scale-90 active:bg-c-line">⌫</button>
    </div>
  );
}

export function FxInput({ id = 'fx', value, onChange, keys = true, big = false, bare = false, trailing, placeholder = 'e.g. x^3 - x - 2' }) {
  const ref = useRef(null);
  const { error } = value.trim() ? tryCompile(value) : { error: null };
  const domain = error && /undefined|Division|Log of|Square root|Negative base/.test(error);
  const bad = error && !domain;
  return (
    <div className="space-y-2">
      <label htmlFor={id} className={cx('flex items-center gap-2 rounded-2xl px-3.5', bare ? '' : 'bg-c-surface2', big ? 'h-16' : 'h-[54px]', bad && 'ring-2 ring-c-bad')}>
        <span className="shrink-0 font-mono text-[13px] text-c-ink2">f(x) =</span>
        <input ref={ref} id={id} value={value} placeholder={placeholder} onChange={(e) => onChange(e.target.value)} autoComplete="off" spellCheck="false" autoCapitalize="off"
          className={cx('min-w-0 flex-1 bg-transparent font-mono font-medium text-c-ink outline-none placeholder:font-normal placeholder:text-c-ink2/60', big ? 'text-[22px]' : 'text-[16px]')} />
        {trailing || (value.trim() && (bad ? <Icon d={P.warn} size={18} className="shrink-0 text-c-bad" /> : <Icon d={P.check} size={18} className="shrink-0 text-c-good" />))}
      </label>
      {bad && <p className="px-1 text-xs text-c-bad">{error}</p>}
      {keys && <KeyRow value={value} onChange={onChange} inputRef={ref} />}
    </div>
  );
}

/* ---------------- display ---------------- */
export function Badge({ kind = 'neutral', icon, children, className }) {
  const t = { ok: 'bg-c-goodbg text-c-good', warn: 'bg-c-warnbg text-c-warn', bad: 'bg-c-badbg text-c-bad', neutral: 'bg-c-surface2 text-c-ink2' }[kind];
  return (
    <span className={cx('inline-flex h-6 items-center gap-1 whitespace-nowrap rounded-full px-2.5 text-xs font-semibold', t, className)}>
      {icon && <Icon d={icon} size={13} sw={2.4} />}{children}
    </span>
  );
}
export const ConvBadge = ({ res }) => (res.converged ? <Badge kind="ok" icon={P.check}>Converged</Badge> : <Badge kind="warn" icon={P.warn}>Hit iteration limit</Badge>);

export function ErrorBox({ children }) {
  return (
    <div role="alert" className="flex items-start gap-2.5 rounded-2xl bg-c-badbg px-3.5 py-3 text-[13px] text-c-bad">
      <Icon d={P.warn} size={17} className="mt-px shrink-0" />
      <span>{children}</span>
    </div>
  );
}

/* generic table: cols [{h, k, align, render, mono}] */
export function GTable({ cols, rows, active, onRow, dense, maxH, className }) {
  const pad = dense ? 'px-2 py-1.5' : 'px-3 py-2';
  return (
    <div className={cx('zebra overflow-auto rounded-2xl bg-c-surface', className)} style={{ maxHeight: maxH }}>
      <table className={cx('w-full border-collapse', dense ? 'text-[11.5px]' : 'text-[12.5px]')}>
        <thead>
          <tr>
            {cols.map((c, i) => (
              <th key={i} className={cx('sticky top-0 z-10 whitespace-nowrap bg-c-surface2 text-[10px] font-semibold uppercase tracking-wider text-c-ink2', pad, c.align === 'left' ? 'text-left' : 'text-right')}>{c.h}</th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((r, ri) => (
            <tr key={ri} onClick={onRow ? () => onRow(ri) : undefined} className={cx(onRow && 'cursor-pointer', active === ri && 'row-active')}>
              {cols.map((c, ci) => (
                <td key={ci} className={cx('whitespace-nowrap font-mono tabular-nums', pad, c.align === 'left' ? 'text-left text-c-ink2' : 'text-right')}>{c.render ? c.render(r, ri) : r[c.k]}</td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

export function RelPill({ row, tol }) {
  if (row.rel === null || row.rel === undefined) return <span className="text-c-ink2">—</span>;
  const conv = row.abs < tol;
  const high = row.rel >= 1;
  const txt = row.rel >= 1e-3 || row.rel === 0 ? row.rel.toFixed(4) : row.rel.toExponential(2);
  return <span className={cx('inline-block rounded-md px-1.5 py-0.5', conv ? 'bg-c-goodbg text-c-good' : high ? 'bg-c-warnbg text-c-warn' : '')}>{txt}</span>;
}

export function rootCols(res, dp = 6) {
  return [
    { h: 'Iter', k: 'n', align: 'left' },
    { h: 'xₙ', render: (r) => r.x.toFixed(Math.max(dp, 4)) },
    { h: 'f(xₙ)', render: (r) => fmtE(r.fx) },
    { h: '|xₙ − xₙ₋₁|', render: (r) => (r.abs === null ? '—' : fmtE(r.abs)) },
    { h: 'Rel. err %', render: (r) => <RelPill row={r} tol={res.tol} /> },
  ];
}
export const basisCols = (dp = 6) => [
  { h: 'i', render: (_, i) => i, align: 'left' }, { h: 'xᵢ', render: (b) => fmt(b.x, 4) }, { h: 'yᵢ', render: (b) => fmt(b.y, 4) },
  { h: 'Lᵢ(x)', render: (b) => fmt(b.L, dp) }, { h: 'yᵢ·Lᵢ', render: (b) => fmt(b.term, dp) },
];
export function ddCols(r, dp = 5) {
  const names = ['f[xᵢ]', '1st', '2nd', '3rd', '4th', '5th', '6th', '7th', '8th', '9th'];
  return [
    { h: 'xᵢ', align: 'left', render: (row) => fmt(row.x, 4) },
    ...r.dd.map((col, j) => ({ h: names[j], render: (row, i) => (i < col.length ? <span className={cx(i === 0 && 'rounded-md bg-c-accent px-1.5 py-0.5 font-semibold text-c-onaccent')}>{fmt(col[i], dp)}</span> : '') })),
  ];
}

export function metricList(r) {
  const m = r.metrics;
  if (!m) return [];
  const out = [
    { k: 'abs', label: 'True absolute error', cap: '|f(x) − P(x)|', value: fmtE(m.abs) },
    { k: 'rel', label: 'Relative error', cap: '(f − P) / f × 100', value: m.rel === null ? '—' : `${m.rel.toFixed(4)} %` },
    { k: 'arel', label: 'Absolute relative error', cap: '|relative error|', value: m.absRel === null ? '—' : `${m.absRel.toFixed(4)} %` },
  ];
  if (m.bound) out.push({ k: 'bound', label: 'Error bound', cap: `max|f⁽${m.bound.k}⁾| / ${m.bound.k}! · |Π(x − xᵢ)|`, value: fmtE(m.bound.value), holds: m.abs <= m.bound.value * 1.0001 + 1e-12 });
  return out;
}

const cellCls = 'h-11 w-full min-w-0 rounded-xl bg-c-surface2 px-3 font-mono text-[14px] text-c-ink outline-none focus:ring-2 focus:ring-c-accent';
export function PointRows({ rows, onChange, max = 12 }) {
  const upd = (id, k, v) => onChange(rows.map((r) => (r.id === id ? { ...r, [k]: v } : r)));
  const add = () => onChange([...rows, { id: Math.max(0, ...rows.map((r) => r.id)) + 1, x: '', y: '' }]);
  return (
    <div className="space-y-2">
      <div className="grid grid-cols-[1fr_1fr_40px] gap-2 px-1 text-[10px] font-semibold uppercase tracking-wider text-c-ink2"><span>x</span><span>y</span><span /></div>
      {rows.map((r, i) => (
        <div key={r.id} className="row-in grid grid-cols-[1fr_1fr_40px] items-center gap-2">
          <input aria-label={`x${i}`} className={cellCls} value={r.x} onChange={(e) => upd(r.id, 'x', e.target.value)} spellCheck="false" autoComplete="off" />
          <input aria-label={`y${i}`} className={cellCls} value={r.y} onChange={(e) => upd(r.id, 'y', e.target.value)} spellCheck="false" autoComplete="off" />
          <button type="button" aria-label={`Remove point ${i}`} disabled={rows.length <= 2} onClick={() => onChange(rows.filter((q) => q.id !== r.id))}
            className="flex h-10 w-10 items-center justify-center rounded-full text-c-ink2 transition active:scale-90 disabled:opacity-30"><Icon d={P.trash} size={17} /></button>
        </div>
      ))}
      <button type="button" onClick={add} disabled={rows.length >= max} className="flex h-10 items-center gap-1.5 rounded-full bg-c-surface2 px-4 text-[13px] font-medium text-c-ink transition active:scale-95 disabled:opacity-40"><Icon d={P.plus} size={15} />Add point</button>
    </div>
  );
}
