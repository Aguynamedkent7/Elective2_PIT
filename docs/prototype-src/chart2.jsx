import React, { useState, useRef, useEffect, useId } from 'react';
import { fmt } from './math.js';
import { cx, Icon, P } from './kit.jsx';

const niceStep = (raw) => {
  const p = Math.pow(10, Math.floor(Math.log10(raw)));
  const f = raw / p;
  return (f < 1.5 ? 1 : f < 3 ? 2 : f < 7 ? 5 : 10) * p;
};
const makeTicks = (lo, hi, n) => {
  const st = niceStep((hi - lo) / n);
  const out = [];
  for (let v = Math.ceil(lo / st - 1e-9) * st; v <= hi + 1e-9; v += st) out.push(Math.abs(v) < st * 1e-6 ? 0 : +v.toPrecision(12));
  return { out, st };
};
const tickLabel = (v, st) => {
  if (v === 0) return '0';
  if (Math.abs(v) >= 1e5 || Math.abs(v) < 1e-4) return v.toExponential(0);
  return v.toFixed(Math.max(0, Math.min(6, -Math.floor(Math.log10(st) + 1e-9))));
};
export function domainFor(xs, k = 0.35) {
  const lo = Math.min(...xs);
  const hi = Math.max(...xs);
  const span = hi - lo || 2;
  return [lo - span * k, hi + span * k];
}

function useSize(ref, init) {
  const [size, setSize] = useState(init);
  useEffect(() => {
    const el = ref.current;
    if (!el) return undefined;
    const ro = new ResizeObserver(() => setSize({ w: Math.max(120, el.clientWidth), h: Math.max(100, el.clientHeight) }));
    ro.observe(el);
    setSize({ w: Math.max(120, el.clientWidth), h: Math.max(100, el.clientHeight) });
    return () => ro.disconnect();
  }, []);
  return size;
}

/* ---------------------------------------------------------------
   Plot: pan, pinch, tap readout, draggable points and lines.
   curves:   [{ f, color, w, dash, hidden, probe, label }]
   points:   [{ id, x, y, kind: data|iter|root|target|true, draggable, label, color, opacity }]
   overlays: [{ type: band|vline|seg, ... }]
---------------------------------------------------------------- */
export function Plot({ curves = [], points = [], overlays = [], domain, pad: padIn, includeZero = true, grid = true, touch = 'none', controls = false, controlsClass = 'right-2 top-2', onDragPoint, onDragLine, className, label = 'Graph' }) {
  const wrapRef = useRef(null);
  const { w, h } = useSize(wrapRef, { w: 340, h: 260 });
  const [view, setView] = useState(domain);
  const [tip, setTip] = useState(null);
  const ptrs = useRef(new Map());
  const gesture = useRef(null);
  const pinch = useRef(null);
  const lockY = useRef(null);
  const uid = 'p' + useId().replace(/[^a-zA-Z0-9]/g, '');
  const dk = domain.join(',');
  useEffect(() => { setView(domain); setTip(null); }, [dk]);

  const pad = { l: 34, r: 10, t: 10, b: 22, ...padIn };
  const iw = Math.max(10, w - pad.l - pad.r);
  const ih = Math.max(10, h - pad.t - pad.b);
  const [v0, v1] = view;
  const N = Math.max(80, Math.min(360, Math.floor(iw / 1.3)));

  const samples = curves.map((c) => {
    if (c.hidden) return [];
    const pts = [];
    for (let i = 0; i <= N; i++) {
      const x = v0 + ((v1 - v0) * i) / N;
      let y;
      try { y = c.f(x); } catch (e) { y = NaN; }
      pts.push([x, Number.isFinite(y) ? y : NaN]);
    }
    return pts;
  });

  let ylo;
  let yhi;
  if (lockY.current) [ylo, yhi] = lockY.current;
  else {
    const ys = [];
    samples.forEach((pts, ci) => { if (curves[ci].fit !== false) pts.forEach(([, y]) => Number.isFinite(y) && ys.push(y)); });
    ys.sort((a, b) => a - b);
    let lo = ys.length ? ys[Math.floor(ys.length * 0.01)] : -1;
    let hi = ys.length ? ys[Math.min(ys.length - 1, Math.ceil(ys.length * 0.99) - 1)] : 1;
    if (includeZero) { lo = Math.min(lo, 0); hi = Math.max(hi, 0); }
    points.forEach((p) => { if (p.fit !== false && p.x >= v0 && p.x <= v1) { lo = Math.min(lo, p.y); hi = Math.max(hi, p.y); } });
    if (!Number.isFinite(lo) || !Number.isFinite(hi) || Math.abs(lo) > 1e100 || Math.abs(hi) > 1e100) { lo = -1; hi = 1; }
    if (!(hi > lo)) { lo -= 1; hi += 1; }
    const sp = hi - lo;
    ylo = lo - sp * 0.14;
    yhi = hi + sp * 0.14;
  }

  const sx = (x) => pad.l + ((x - v0) / (v1 - v0)) * iw;
  const sy = (y) => pad.t + ((yhi - y) / (yhi - ylo)) * ih;
  const unY = (py) => yhi - ((py - pad.t) / ih) * (yhi - ylo);
  const unX = (px) => v0 + ((px - pad.l) / iw) * (v1 - v0);
  const xt = makeTicks(v0, v1, Math.max(3, Math.floor(iw / 72)));
  const yt = makeTicks(ylo, yhi, Math.max(3, Math.floor(ih / 46)));
  const axisY = ylo < 0 && yhi > 0 ? sy(0) : pad.t + ih;

  const toPath = (pts) => {
    let d = '';
    let pen = false;
    let prev = null;
    for (const [x, y] of pts) {
      if (!Number.isFinite(y)) { pen = false; prev = null; continue; }
      if (prev !== null && Math.abs(y - prev) > (yhi - ylo) * 4) pen = false;
      d += `${pen ? 'L' : 'M'}${sx(x).toFixed(1)} ${Math.max(-4000, Math.min(4000, sy(y))).toFixed(1)}`;
      pen = true;
      prev = y;
    }
    return d;
  };

  const probe = (px) => {
    if (px < pad.l || px > pad.l + iw) return null;
    const x = unX(px);
    const c = curves.find((cv) => !cv.hidden && cv.probe !== false);
    if (!c) return null;
    try { const y = c.f(x); return { sx: px, sy: sy(y), x, y, title: c.label || 'Value' }; } catch (e) { return null; }
  };
  const local = (e) => {
    const r = wrapRef.current.getBoundingClientRect();
    return [e.clientX - r.left, e.clientY - r.top];
  };
  const hit = (px, py) => {
    let best = null;
    points.forEach((p) => {
      if (!p.draggable) return;
      const d = Math.hypot(sx(p.x) - px, sy(p.y) - py);
      if (d < 26 && (!best || d < best.d)) best = { d, t: 'point', id: p.id };
    });
    if (best) return best;
    for (const o of overlays) if (o.type === 'vline' && o.draggable && Math.abs(sx(o.x) - px) < 24 && py > pad.t) return { t: 'line', id: o.id };
    return null;
  };
  const zoomAbout = (xm, k, base = view) => {
    const na = xm - (xm - base[0]) * k;
    const nb = xm + (base[1] - xm) * k;
    if (nb - na < 1e-6 || nb - na > 1e6) return;
    setView([na, nb]);
  };

  const onDown = (e) => {
    wrapRef.current.setPointerCapture?.(e.pointerId);
    const [px, py] = local(e);
    ptrs.current.set(e.pointerId, { x: px });
    if (ptrs.current.size === 2) {
      const [a, b] = [...ptrs.current.values()];
      pinch.current = { d: Math.abs(a.x - b.x) || 1, base: [...view], xm: unX((a.x + b.x) / 2) };
      gesture.current = null;
      return;
    }
    const hh = hit(px, py);
    if (hh) {
      gesture.current = { kind: 'drag', hh };
      lockY.current = [ylo, yhi];
      setTip(null);
    } else gesture.current = { kind: 'pan', x: px, base: [...view], moved: false };
  };
  const onMove = (e) => {
    const [px, py] = local(e);
    if (ptrs.current.has(e.pointerId)) ptrs.current.set(e.pointerId, { x: px });
    const g = gesture.current;
    if (ptrs.current.size === 2 && pinch.current) {
      const [a, b] = [...ptrs.current.values()];
      zoomAbout(pinch.current.xm, pinch.current.d / (Math.abs(a.x - b.x) || 1), pinch.current.base);
      setTip(null);
    } else if (g && g.kind === 'drag') {
      if (g.hh.t === 'point') onDragPoint && onDragPoint(g.hh.id, unY(py));
      else onDragLine && onDragLine(g.hh.id, Math.max(v0, Math.min(v1, unX(px))));
    } else if (g && g.kind === 'pan') {
      const dx = px - g.x;
      if (Math.abs(dx) > 3) g.moved = true;
      if (g.moved) {
        const sh = (-dx / iw) * (g.base[1] - g.base[0]);
        setView([g.base[0] + sh, g.base[1] + sh]);
        setTip(null);
      }
    } else if (e.pointerType === 'mouse') {
      setTip(probe(px));
    }
  };
  const onUp = (e) => {
    const [px] = local(e);
    ptrs.current.delete(e.pointerId);
    if (ptrs.current.size < 2) pinch.current = null;
    const g = gesture.current;
    if (g && g.kind === 'pan' && !g.moved && e.type === 'pointerup') setTip(probe(px));
    if (g && g.kind === 'drag') lockY.current = null;
    gesture.current = null;
  };

  const tipLeft = tip ? Math.max(6, Math.min(w - 128, tip.sx + 12)) : 0;
  const tipTop = tip ? Math.max(pad.t, Math.min(h - 62, tip.sy - 58)) : 0;

  return (
    <div ref={wrapRef} className={cx('relative h-full w-full select-none overflow-hidden', className)} style={{ touchAction: touch }}
      onPointerDown={onDown} onPointerMove={onMove} onPointerUp={onUp} onPointerCancel={onUp} onPointerLeave={(e) => { if (e.pointerType === 'mouse' && !gesture.current) setTip(null); }}>
      <svg width={w} height={h} role="img" aria-label={`${label}. Drag to pan, pinch to zoom, tap for values.`}>
        <defs><clipPath id={uid}><rect x={pad.l} y={pad.t} width={iw} height={ih} /></clipPath></defs>
        {grid && (
          <g stroke="var(--c-line)" strokeWidth="1" opacity="0.7">
            {xt.out.map((v) => <line key={'gx' + v} x1={sx(v)} x2={sx(v)} y1={pad.t} y2={pad.t + ih} />)}
            {yt.out.map((v) => <line key={'gy' + v} x1={pad.l} x2={pad.l + iw} y1={sy(v)} y2={sy(v)} />)}
          </g>
        )}
        <line x1={pad.l} x2={pad.l + iw} y1={axisY} y2={axisY} stroke="var(--c-ink2)" strokeWidth="1.4" />
        <g fontFamily="var(--font-mono)" fontSize="10" fill="var(--c-ink2)">
          {xt.out.map((v) => <text key={'tx' + v} x={sx(v)} y={pad.t + ih + 15} textAnchor="middle">{tickLabel(v, xt.st)}</text>)}
          {yt.out.map((v) => <text key={'ty' + v} x={pad.l - 6} y={sy(v) + 3.5} textAnchor="end">{tickLabel(v, yt.st)}</text>)}
        </g>
        <g clipPath={`url(#${uid})`}>
          {overlays.map((o, i) => {
            if (o.type === 'band') return <rect key={i} x={sx(o.x0)} y={pad.t} width={Math.max(1, sx(o.x1) - sx(o.x0))} height={ih} fill={o.color} opacity={o.opacity ?? 0.14} />;
            if (o.type === 'seg') return <line key={i} x1={sx(o.x1)} y1={sy(o.y1)} x2={sx(o.x2)} y2={sy(o.y2)} stroke={o.color} strokeWidth={o.w || 2} strokeDasharray={o.dash} strokeLinecap="round" opacity={o.opacity ?? 1} />;
            if (o.type === 'vline') {
              const x = sx(o.x);
              return (
                <g key={i}>
                  <line x1={x} x2={x} y1={pad.t} y2={pad.t + ih} stroke={o.color || 'var(--c-ink)'} strokeWidth={o.w || 1.5} strokeDasharray={o.dash} opacity={o.opacity ?? 0.9} />
                  {o.draggable && (
                    <g>
                      <rect x={x - 15} y={pad.t + ih - 24} width="30" height="18" rx="9" fill="var(--c-ink)" />
                      <path d={`M${x - 6} ${pad.t + ih - 15}h12M${x - 3} ${pad.t + ih - 18}l-3 3 3 3M${x + 3} ${pad.t + ih - 18}l3 3-3 3`} stroke="var(--c-bg)" strokeWidth="1.6" fill="none" strokeLinecap="round" strokeLinejoin="round" />
                    </g>
                  )}
                </g>
              );
            }
            return null;
          })}
          {curves.map((c, i) => !c.hidden && (c.dash ? <path key={i} d={toPath(samples[i])} fill="none" stroke={c.color} strokeWidth={c.w || 2.6} strokeDasharray={c.dash} strokeLinejoin="round" strokeLinecap="round" className="fade-in" /> : <path key={i} d={toPath(samples[i])} pathLength="1" className="draw-on" fill="none" stroke={c.color} strokeWidth={c.w || 2.6} strokeLinejoin="round" strokeLinecap="round" />))}
          {points.map((p, i) => {
            const x = sx(p.x);
            const y = sy(p.y);
            const col = p.color;
            if (p.kind === 'iter') return <circle key={i} cx={x} cy={y} r={p.r || 3.5} fill={col || 'var(--c-ink2)'} opacity={p.opacity ?? 0.6} />;
            if (p.kind === 'true') return <circle key={i} cx={x} cy={y} r="5.5" fill="none" stroke={col || 'var(--c-ink)'} strokeWidth="2" />;
            if (p.kind === 'data') {
              return (
                <g key={i}>
                  {p.draggable && <circle cx={x} cy={y} r="13" fill={col || 'var(--c-accent)'} opacity="0.16" />}
                  <circle cx={x} cy={y} r="6" fill={col || 'var(--c-accent)'} stroke="var(--c-bg)" strokeWidth="2.2" />
                </g>
              );
            }
            return (
              <g key={i} className="pop-dot" style={{ transformOrigin: `${x}px ${y}px` }}>
                <circle className="pulse-ring" cx={x} cy={y} r="9" fill="none" stroke="var(--c-hot)" strokeWidth="2" />
                <circle cx={x} cy={y} r="13" fill="var(--c-hot)" opacity="0.2" />
                <circle cx={x} cy={y} r="6.5" fill="var(--c-hot)" stroke="var(--c-bg)" strokeWidth="2.2" />
              </g>
            );
          })}
        </g>
        {points.map((p, i) => {
          if (!p.label) return null;
          const x = sx(p.x);
          const y = sy(p.y);
          if (x < pad.l || x > pad.l + iw) return null;
          const left = x > w - 130;
          return <text key={'l' + i} x={left ? x - 12 : x + 12} y={Math.max(pad.t + 12, y - 12)} textAnchor={left ? 'end' : 'start'} fontFamily="var(--font-body)" fontSize="11.5" fontWeight="600" fill="var(--c-ink)" stroke="var(--c-bg)" strokeWidth="3.5" paintOrder="stroke">{p.label}</text>;
        })}
        {tip && (
          <g pointerEvents="none">
            <line x1={tip.sx} x2={tip.sx} y1={pad.t} y2={pad.t + ih} stroke="var(--c-ink)" strokeWidth="1" strokeDasharray="3 3" opacity="0.5" />
            <circle cx={tip.sx} cy={tip.sy} r="4.5" fill="var(--c-bg)" stroke="var(--c-ink)" strokeWidth="2" />
          </g>
        )}
      </svg>
      {tip && (
        <div className="pointer-events-none absolute z-10 rounded-xl bg-c-ink px-2.5 py-1.5 font-mono text-[11px] leading-4 text-c-bg shadow-xl" style={{ left: tipLeft, top: tipTop }}>
          <div className="font-sans text-[10px] font-semibold opacity-70">{tip.title}</div>
          <div>x = {fmt(tip.x, 5)}</div>
          <div>y = {fmt(tip.y, 5)}</div>
        </div>
      )}
      {controls && (
        <div className={cx('absolute z-10 flex gap-1.5', controlsClass)}>
          {[['Zoom in', P.plus, () => zoomAbout((v0 + v1) / 2, 0.7)], ['Zoom out', P.minus, () => zoomAbout((v0 + v1) / 2, 1.4)], ['Reset view', P.reset, () => { setView(domain); setTip(null); }]].map(([l, ic, fn]) => (
            <button key={l} type="button" aria-label={l} onPointerDown={(e) => e.stopPropagation()} onClick={fn}
              className="glass flex h-9 w-9 items-center justify-center rounded-full text-c-ink active:scale-90"><Icon d={ic} size={16} /></button>
          ))}
        </div>
      )}
    </div>
  );
}

/* ---------------------------------------------------------------
   LogChart: log10 of positive values against xs. Used for convergence
   (error per iteration) and error against node count.
---------------------------------------------------------------- */
export function LogChart({ xs, series, height = 190, markX, xFmt = (v) => v, yLabel }) {
  const wrapRef = useRef(null);
  const { w } = useSize(wrapRef, { w: 320, h: height });
  const [hov, setHov] = useState(null);
  const pad = { l: 40, r: 14, t: 12, b: 24 };
  const iw = Math.max(10, w - pad.l - pad.r);
  const ih = height - pad.t - pad.b;
  const lg = (v) => Math.log10(Math.max(v, 1e-16));
  const vis = series.filter((s) => !s.hidden);
  const all = vis.flatMap((s) => s.ys.filter((v) => Number.isFinite(v)).map(lg));
  let lo = all.length ? Math.floor(Math.min(...all)) : -6;
  let hi = all.length ? Math.ceil(Math.max(...all)) : 0;
  if (hi - lo < 3) lo = hi - 3;
  const xmin = xs[0];
  const xmax = xs[xs.length - 1] === xmin ? xmin + 1 : xs[xs.length - 1];
  const sx = (x) => pad.l + ((x - xmin) / (xmax - xmin)) * iw;
  const sy = (l) => pad.t + ((hi - l) / (hi - lo)) * ih;
  const stepY = hi - lo > 9 ? 3 : hi - lo > 5 ? 2 : 1;
  const yTicks = [];
  for (let v = lo; v <= hi; v += stepY) yTicks.push(v);
  const xStep = Math.max(1, Math.ceil(xs.length / 7));
  const onMove = (e) => {
    const r = wrapRef.current.getBoundingClientRect();
    const px = e.clientX - r.left;
    let bi = 0;
    xs.forEach((x, i) => { if (Math.abs(sx(x) - px) < Math.abs(sx(xs[bi]) - px)) bi = i; });
    setHov(bi);
  };
  return (
    <div ref={wrapRef} className="relative w-full select-none" style={{ height, touchAction: 'pan-y' }} onPointerMove={onMove} onPointerDown={onMove} onPointerLeave={(e) => e.pointerType === 'mouse' && setHov(null)}>
      <svg width={w} height={height} role="img" aria-label={yLabel || 'Log scale chart'}>
        <g stroke="var(--c-line)" strokeWidth="1">{yTicks.map((v) => <line key={v} x1={pad.l} x2={pad.l + iw} y1={sy(v)} y2={sy(v)} />)}</g>
        <g fontFamily="var(--font-mono)" fontSize="10" fill="var(--c-ink2)">
          {yTicks.map((v) => <text key={v} x={pad.l - 6} y={sy(v) + 3.5} textAnchor="end">10{String(v).replace('-', '⁻').replace(/\d/g, (d) => '⁰¹²³⁴⁵⁶⁷⁸⁹'[d])}</text>)}
          {xs.map((x, i) => (i % xStep === 0 ? <text key={x} x={sx(x)} y={height - 7} textAnchor="middle">{xFmt(x)}</text> : null))}
        </g>
        {markX !== undefined && markX !== null && (
          <g>
            <line x1={sx(markX)} x2={sx(markX)} y1={pad.t} y2={pad.t + ih} stroke="var(--c-ink)" strokeDasharray="4 4" opacity="0.5" />
          </g>
        )}
        {vis.map((s) => {
          const pts = s.ys.map((v, i) => (Number.isFinite(v) && i < xs.length ? [sx(xs[i]), sy(lg(v))] : null)).filter(Boolean);
          if (!pts.length) return null;
          const d = pts.map((p, i) => `${i ? 'L' : 'M'}${p[0].toFixed(1)} ${p[1].toFixed(1)}`).join('');
          const last = pts[pts.length - 1];
          return (
            <g key={s.id}>
              <path d={d} fill="none" stroke={s.color} strokeWidth="2.6" strokeLinejoin="round" strokeLinecap="round" />
              {pts.length <= 20 && pts.map((p, i) => <circle key={i} cx={p[0]} cy={p[1]} r="3" fill="var(--c-bg)" stroke={s.color} strokeWidth="2" />)}
              <circle cx={last[0]} cy={last[1]} r="4.5" fill={s.color} stroke="var(--c-bg)" strokeWidth="2" />
            </g>
          );
        })}
        {hov !== null && <line x1={sx(xs[hov])} x2={sx(xs[hov])} y1={pad.t} y2={pad.t + ih} stroke="var(--c-ink)" strokeWidth="1" opacity="0.4" />}
      </svg>
      {hov !== null && (
        <div className="pointer-events-none absolute top-1 z-10 rounded-xl bg-c-ink px-2.5 py-1.5 font-mono text-[11px] leading-4 text-c-bg shadow-xl" style={{ left: Math.max(4, Math.min(w - 132, sx(xs[hov]) + 8)) }}>
          <div className="font-sans text-[10px] font-semibold opacity-70">{yLabel ? `${yLabel} ` : ''}{xFmt(xs[hov])}</div>
          {vis.map((s) => (Number.isFinite(s.ys[hov]) ? <div key={s.id} style={{ color: s.tipColor || undefined }}>{s.label}: {s.ys[hov].toExponential(1)}</div> : null))}
        </div>
      )}
    </div>
  );
}

export function Spark({ values, w = 92, h = 34, color = 'currentColor' }) {
  const lg = (v) => Math.log10(Math.max(v, 1e-16));
  const vs = values.filter((v) => Number.isFinite(v)).map(lg);
  if (vs.length < 2) return <svg width={w} height={h} aria-hidden="true" />;
  const lo = Math.min(...vs);
  const hi = Math.max(...vs, lo + 1);
  const pts = vs.map((v, i) => [(i / (vs.length - 1)) * (w - 6) + 3, 3 + ((hi - v) / (hi - lo)) * (h - 6)]);
  const d = pts.map((p, i) => `${i ? 'L' : 'M'}${p[0].toFixed(1)} ${p[1].toFixed(1)}`).join('');
  const last = pts[pts.length - 1];
  return (
    <svg width={w} height={h} viewBox={`0 0 ${w} ${h}`} aria-hidden="true">
      <path d={d} fill="none" stroke={color} strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" opacity="0.9" />
      <circle cx={last[0]} cy={last[1]} r="3" fill={color} />
    </svg>
  );
}

export function MiniPlot({ f, domain, marks = [], height = 84, color = 'var(--c-curve)' }) {
  const W = 300;
  const N = 120;
  const pts = [];
  for (let i = 0; i <= N; i++) {
    const x = domain[0] + ((domain[1] - domain[0]) * i) / N;
    let y;
    try { y = f(x); } catch (e) { y = NaN; }
    pts.push([x, Number.isFinite(y) ? y : NaN]);
  }
  const ys = pts.map((p) => p[1]).filter(Number.isFinite).sort((a, b) => a - b);
  if (!ys.length) return <div style={{ height }} className="grid place-items-center text-xs text-c-ink2">Nothing to plot yet</div>;
  let lo = Math.min(ys[Math.floor(ys.length * 0.03)], 0);
  let hi = Math.max(ys[Math.ceil(ys.length * 0.97) - 1], 0);
  if (!Number.isFinite(lo) || !Number.isFinite(hi) || Math.abs(lo) > 1e100 || Math.abs(hi) > 1e100) { lo = -1; hi = 1; }
  if (!(hi > lo)) { lo -= 1; hi += 1; }
  const sp = hi - lo;
  lo -= sp * 0.1;
  hi += sp * 0.1;
  const X = (x) => ((x - domain[0]) / (domain[1] - domain[0])) * W;
  const Y = (y) => ((hi - y) / (hi - lo)) * height;
  let d = '';
  let pen = false;
  pts.forEach(([x, y]) => {
    if (!Number.isFinite(y)) { pen = false; return; }
    d += `${pen ? 'L' : 'M'}${X(x).toFixed(1)} ${Math.max(-50, Math.min(height + 50, Y(y))).toFixed(1)}`;
    pen = true;
  });
  return (
    <svg viewBox={`0 0 ${W} ${height}`} preserveAspectRatio="none" width="100%" height={height} role="img" aria-label="Preview of f(x)">
      <line x1="0" x2={W} y1={Y(0)} y2={Y(0)} stroke="var(--c-line)" strokeWidth="1.2" vectorEffect="non-scaling-stroke" />
      <path d={d} fill="none" stroke={color} strokeWidth="2.4" vectorEffect="non-scaling-stroke" strokeLinejoin="round" />
      {marks.map((m, i) => <circle key={i} cx={X(m)} cy={Y(0)} r="4.5" fill="var(--c-hot)" stroke="var(--c-bg)" strokeWidth="1.5" vectorEffect="non-scaling-stroke" />)}
    </svg>
  );
}
