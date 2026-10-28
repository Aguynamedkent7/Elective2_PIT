import React, { useState, useRef, useMemo } from 'react';
import { solveRoot, interpolate, explainStep, fmt, fmtE, toCSV, toLatex } from './math.js';
import { cx, P, Icon, RoundBtn, Pill, ErrorBox, GTable, rootCols, basisCols, ddCols, metricList } from './kit.jsx';
import { Plot, Spark, domainFor } from './chart2.jsx';
import { METHOD_LABEL, METHOD_SHORT } from './defaults.js';

const KEY = { bisection: 'bis', newton: 'new', secant: 'sec', lagrange: 'lag', newtondd: 'ndd' };
export const solveItem = (it) => (it.kind === 'root' ? solveRoot(it.inputs) : interpolate(it.inputs));
export const textOf = (it, res) => (res.error ? res.error : it.kind === 'root' ? `x* ≈ ${fmt(res.root, 6)} (${METHOD_LABEL[res.method]}, ${res.iterations} iterations)` : `P(${it.inputs.xEval}) = ${fmt(res.Pxe, 6)}`);

function Swipe({ onDelete, children }) {
  const [dx, setDx] = useState(0);
  const [leaving, setLeaving] = useState(false);
  const st = useRef(null);
  const down = (e) => {
    if (e.target.closest('button, input, textarea, [data-noswipe]')) return;
    st.current = { x: e.clientX, y: e.clientY, active: false };
  };
  const move = (e) => {
    const s = st.current;
    if (!s) return;
    const ddx = e.clientX - s.x;
    const ddy = e.clientY - s.y;
    if (!s.active && Math.abs(ddx) > 10 && Math.abs(ddx) > Math.abs(ddy) * 1.6) { s.active = true; e.currentTarget.setPointerCapture?.(e.pointerId); }
    if (s.active) setDx(Math.min(0, ddx));
  };
  const up = () => {
    const s = st.current;
    st.current = null;
    if (!s || !s.active) return;
    if (dx < -96) { setLeaving(true); setDx(-460); setTimeout(onDelete, 220); } else setDx(0);
  };
  return (
    <div className="relative" style={{ touchAction: 'pan-y' }}>
      <div className="absolute inset-0 flex items-center justify-end rounded-[28px] bg-c-bad pr-7 text-white" style={{ opacity: Math.min(1, Math.abs(dx) / 80) }} aria-hidden="true"><Icon d={P.trash} size={22} /></div>
      <div onPointerDown={down} onPointerMove={move} onPointerUp={up} onPointerCancel={up}
        style={{ transform: `translateX(${dx}px)`, transition: st.current && st.current.active ? 'none' : 'transform .28s cubic-bezier(.05,.7,.1,1), opacity .2s', opacity: leaving ? 0 : 1 }} className="relative">
        {children}
      </div>
    </div>
  );
}

function Card({ it, menuOpen, onMenu, onPin, onDelete, onOpen, onOpenGraph, onDup, onTry, onCopy }) {
  const res = useMemo(() => solveItem(it), [it.inputs]);
  const key = KEY[it.inputs.method];
  const [view, setView] = useState(null);
  const [wk, setWk] = useState(null);
  const inp = it.inputs;
  const kind = it.kind;
  const tint = { '--tint-bg': `var(--mc-${key}-bg)`, '--tint-ink': `var(--mc-${key}-ink)`, background: `var(--mc-${key}-bg)`, color: `var(--mc-${key}-ink)` };
  const rows = kind === 'root' && !res.error ? res.rows : [];
  const wi = wk === null ? Math.max(0, rows.length - 1) : Math.min(wk, rows.length - 1);
  const others = kind === 'root' ? ['bisection', 'newton', 'secant'].filter((x) => x !== inp.method) : ['lagrange', 'newtondd'].filter((x) => x !== inp.method);
  const views = kind === 'root' ? [['graph', 'Graph', P.graph], ['table', 'Table', P.table], ['working', 'Working', P.list]] : [['graph', 'Graph', P.graph], ['table', 'Steps', P.table], ['working', 'Errors', P.list]];

  let graph = null;
  if (!res.error && view === 'graph') {
    if (kind === 'root') {
      const pts = [...res.rows.map((r) => ({ kind: 'iter', x: r.x, y: r.fx, opacity: 0.5 })), { kind: 'root', x: res.root, y: res.fRoot, label: `x* ≈ ${fmt(res.root, 5)}` }];
      graph = <Plot curves={[{ f: res.f, color: 'var(--c-curve)', w: 2.8, label: 'f(x)' }]} points={pts} domain={domainFor([res.root, ...res.rows.map((r) => r.x), ...res.used])} touch="pan-y" controls label="f(x)" />;
    } else {
      const pts = [...res.pts.map((p) => ({ kind: 'data', x: p.x, y: p.y })), { kind: 'target', x: res.xe, y: res.Pxe, label: `P(${fmt(res.xe, 3)})` }];
      if (res.metrics) pts.push({ kind: 'true', x: res.xe, y: res.metrics.yTrue });
      const curves = [{ f: res.P, color: 'var(--c-curve)', w: 2.8, label: 'P(x)', fit: !res.fTrue }];
      if (res.fTrue) curves.push({ f: res.fTrue, color: 'var(--c-ink2)', w: 2, dash: '6 5', fit: true, probe: false });
      graph = <Plot curves={curves} points={pts} includeZero={false} domain={domainFor(res.X, 0.3)} touch="pan-y" controls label="Interpolating polynomial" />;
    }
  }

  return (
    <Swipe onDelete={onDelete}>
      <article className="rounded-[28px] p-5" style={tint}>
        <header className="flex items-center gap-2">
          <span className="tint2 flex h-6 items-center rounded-full px-2.5 text-xs font-semibold">{METHOD_SHORT[inp.method]}</span>
          <span className="text-xs opacity-60">{it.when}</span>
          <span className="relative ml-auto flex items-center">
            <button type="button" aria-label={it.pinned ? 'Unpin' : 'Pin to top'} aria-pressed={it.pinned} onClick={onPin} className={cx('flex h-9 w-9 items-center justify-center rounded-full transition active:scale-90', it.pinned ? 'tint2' : 'opacity-60')}><Icon d={P.pin} size={17} fill={it.pinned} /></button>
            <button type="button" aria-label="More actions" aria-expanded={menuOpen} onClick={onMenu} className="flex h-9 w-9 items-center justify-center rounded-full opacity-70 transition active:scale-90"><Icon d={P.more} size={18} sw={2.6} /></button>
            {menuOpen && (
              <div data-noswipe className="pop-in absolute right-0 top-10 z-30 w-56 overflow-hidden rounded-2xl bg-c-surface p-1.5 text-c-ink shadow-2xl">
                {[['Open inputs', P.edit, onOpen], ['Open graph', P.graph, onOpenGraph], ['Duplicate', P.copy, onDup], ['Copy result', P.clip, () => onCopy(textOf(it, res), 'Result copied')], ['Copy table as CSV', P.table, () => onCopy(toCSV(res), 'CSV copied')], ['Copy as LaTeX', P.list, () => onCopy(toLatex(res), 'LaTeX copied')], ['Delete', P.trash, onDelete]].map(([l, ic, fnc]) => (
                  <button key={l} type="button" disabled={!!res.error && (l.startsWith('Copy') || l === 'Open graph')} onClick={fnc} className={cx('flex h-11 w-full items-center gap-3 rounded-xl px-3 text-left text-[14px] font-medium hover:bg-c-surface2 disabled:opacity-40', l === 'Delete' && 'text-c-bad')}><Icon d={ic} size={16} />{l}</button>
                ))}
              </div>
            )}
          </span>
        </header>

        <p className="mt-2 truncate font-mono text-[13px] opacity-75">{kind === 'root' ? `f(x) = ${inp.fx}` : `${res.error ? inp.rows.length : res.pts.length} points · evaluate at x = ${inp.xEval}`}</p>

        {res.error ? (
          <div className="mt-3"><ErrorBox>{res.error}</ErrorBox></div>
        ) : (
          <>
            <div className="mt-2 flex items-end justify-between gap-3">
              <div className="min-w-0">
                <div className="font-display whitespace-nowrap text-[38px] font-semibold leading-[44px] tracking-tight tabular-nums">{kind === 'root' ? fmt(res.root, 6) : fmt(res.Pxe, 6)}</div>
                <div className="mt-2 flex flex-wrap items-center gap-x-3 gap-y-1.5 text-xs">
                  {kind === 'root' ? (
                    <>
                      <span className="tint2 inline-flex h-6 items-center gap-1 rounded-full px-2.5 font-semibold"><Icon d={res.converged ? P.check : P.warn} size={13} sw={2.6} />{res.converged ? 'Converged' : 'Not converged'}</span>
                      <span className="opacity-70">{res.iterations} iterations · last change {fmtE(res.finalAbs)}</span>
                    </>
                  ) : (
                    <>
                      <span className="tint2 inline-flex h-6 items-center rounded-full px-2.5 font-semibold">Degree {res.n}</span>
                      <span className="opacity-70">{res.metrics ? `|error| ${fmtE(res.metrics.abs)}${res.metrics.bound ? ` · bound ${fmtE(res.metrics.bound.value)}` : ''}` : 'no error reference'}</span>
                    </>
                  )}
                </div>
              </div>
              {kind === 'root' && res.rows.length > 2 && <div className="shrink-0 opacity-90" title="Error per iteration, log scale"><Spark values={res.rows.map((r) => (r.abs === null ? NaN : r.abs))} /></div>}
            </div>

            <div className="mt-4 flex gap-1.5" role="tablist">
              {views.map(([id, l, ic]) => (
                <button key={id} type="button" role="tab" aria-selected={view === id} onClick={() => setView(view === id ? null : id)}
                  className={cx('flex h-9 items-center gap-1.5 rounded-full px-3.5 text-[13px] font-semibold transition active:scale-95', view === id ? 'bg-c-ink text-c-bg' : 'tint')}><Icon d={ic} size={15} />{l}</button>
              ))}
            </div>

            <div className="collapse-grid" data-open={view ? 'true' : 'false'}>
              <div className="min-h-0 overflow-hidden">
                {view && (
                  <div data-noswipe className="cardpanel mt-3 rounded-2xl p-2.5">
                    {view === 'graph' && <div className="h-[210px]">{graph}</div>}
                    {view === 'table' && (
                      kind === 'root' ? <GTable dense maxH={250} cols={rootCols(res)} rows={res.rows} active={wi} onRow={(i) => setWk(i)} />
                        : res.method === 'lagrange' ? <GTable dense cols={basisCols()} rows={res.basis} /> : <GTable dense cols={ddCols(res)} rows={res.X.map((x) => ({ x }))} />
                    )}
                    {view === 'working' && (kind === 'root' ? (
                      <div className="space-y-2 p-1">
                        <div className="flex items-center justify-between">
                          <span className="text-[11px] font-semibold uppercase tracking-wider text-c-ink2">Iteration {rows[wi].n}</span>
                          <span className="flex gap-1.5">
                            <RoundBtn icon={P.left} label="Previous iteration" onClick={() => setWk(Math.max(0, wi - 1))} disabled={wi <= 0} className="h-8 w-8 bg-c-surface2" size={15} />
                            <RoundBtn icon={P.right} label="Next iteration" onClick={() => setWk(Math.min(rows.length - 1, wi + 1))} disabled={wi >= rows.length - 1} className="h-8 w-8 bg-c-surface2" size={15} />
                          </span>
                        </div>
                        {(() => {
                          const ex = explainStep(res, wi);
                          return (<>
                            {ex.lines.map(([t, b]) => (<div key={t} className="rounded-xl bg-c-surface2 px-3 py-2"><div className="text-[11px] font-semibold text-c-ink2">{t}</div><div className="mt-0.5 break-words font-mono text-[12px] leading-5 text-c-ink">{b}</div></div>))}
                            {ex.note && <p className="px-1 text-[12px] text-c-ink2">{ex.note}</p>}
                          </>);
                        })()}
                      </div>
                    ) : (
                      <div className="space-y-2 p-1">
                        <div className="rounded-xl bg-c-surface2 px-3 py-2"><div className="text-[11px] font-semibold text-c-ink2">P(x) expanded</div><div className="mt-0.5 overflow-x-auto whitespace-nowrap font-mono text-[12px] text-c-ink">{res.poly}</div></div>
                        {res.metrics ? metricList(res).map((e) => (
                          <div key={e.k} className="flex items-start justify-between gap-3 rounded-xl bg-c-surface2 px-3 py-2"><div className="min-w-0"><div className="text-[12.5px] font-medium text-c-ink">{e.label}</div><div className="font-mono text-[10.5px] text-c-ink2">{e.cap}</div></div><div className="shrink-0 font-mono text-[13px] text-c-ink">{e.value}</div></div>
                        )) : <p className="px-1 text-[12px] text-c-ink2">This run has no reference value, so there are no error metrics.</p>}
                      </div>
                    ))}
                  </div>
                )}
              </div>
            </div>
          </>
        )}

        <div className="mt-4 flex flex-wrap items-center gap-2">
          <span className="text-xs opacity-60">Try</span>
          {others.map((o) => (
            <button key={o} type="button" onClick={() => onTry(o)} className="tint flex h-8 items-center gap-1.5 rounded-full px-3 text-[13px] font-semibold transition active:scale-95"><Icon d={P.bolt} size={13} />{METHOD_SHORT[o]}</button>
          ))}
          <button type="button" onClick={onOpen} className="ml-auto flex h-8 items-center gap-1 rounded-full bg-c-ink pl-3.5 pr-2.5 text-[13px] font-semibold text-c-bg transition active:scale-95">Open<Icon d={P.right} size={14} /></button>
        </div>
      </article>
    </Swipe>
  );
}

export function HistoryScreen({ items, filter, setFilter, closing, onClose, onPin, onDelete, onOpen, onOpenGraph, onDup, onTry, onCopy, toastNode }) {
  const [menu, setMenu] = useState(null);
  const shown = items.filter((i) => filter === 'all' || (filter === 'root' && i.kind === 'root') || (filter === 'interp' && i.kind === 'interp') || (filter === 'pinned' && i.pinned))
    .sort((a, b) => Number(b.pinned) - Number(a.pinned));
  return (
    <div className={cx('absolute inset-0 z-[45] flex flex-col bg-c-bg', closing ? 'hist-out' : 'hist-in')}>
      <div className="flex h-full flex-col" onClick={() => menu && setMenu(null)}>
        <header className="shrink-0 px-5 pt-5">
          <div className="flex items-center gap-3">
            <RoundBtn icon={P.down} label="Close history" onClick={onClose} className="bg-c-surface" />
            <div className="min-w-0 flex-1">
              <h1 className="font-display text-[26px] font-extrabold leading-8 tracking-tight">History</h1>
            </div>
            <span className="text-xs text-c-ink2">{items.length} {items.length === 1 ? 'run' : 'runs'}</span>
          </div>
          <div className="-mx-5 mt-4 flex gap-2 overflow-x-auto px-5 pb-1 [scrollbar-width:none]" role="tablist" aria-label="Filter history">
            {[['all', 'All'], ['root', 'Roots'], ['interp', 'Interpolation'], ['pinned', 'Pinned']].map(([v, l]) => <Pill key={v} active={filter === v} onClick={() => setFilter(v)}>{l}</Pill>)}
          </div>
          <p className="mt-3 text-xs text-c-ink2">Runs are saved when you open the graph or tap Save. Swipe a card left to delete.</p>
        </header>
        <main className="min-h-0 flex-1 space-y-4 overflow-auto px-4 pb-10 pt-3">
          {shown.length === 0 && <div className="rounded-[28px] bg-c-surface p-6 text-center text-sm text-c-ink2">{items.length === 0 ? 'No runs yet. Solve something and it appears here.' : 'Nothing matches this filter.'}</div>}
          {shown.map((it, idx) => (
            <div key={it.id} className="card-in" style={{ animationDelay: `${Math.min(idx, 6) * 55}ms` }}>
              <Card it={it} menuOpen={menu === it.id}
                onMenu={(e) => { e.stopPropagation(); setMenu(menu === it.id ? null : it.id); }}
                onPin={() => onPin(it.id)}
                onDelete={() => { setMenu(null); onDelete(it.id); }}
                onOpen={() => { setMenu(null); onOpen(it); }}
                onOpenGraph={() => { setMenu(null); onOpenGraph(it); }}
                onDup={() => { setMenu(null); onDup(it); }}
                onTry={(m) => onTry(it, m)}
                onCopy={(t, ok) => { setMenu(null); onCopy(t, ok); }} />
            </div>
          ))}
        </main>
      </div>
      {toastNode}
    </div>
  );
}
