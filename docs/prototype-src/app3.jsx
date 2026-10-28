import React, { useState } from 'react';
import { NumLabApp } from './appD.jsx';
import { LogoMark } from './Logo.jsx';

const CHANGES = [
  ['Inputs first', 'The app opens on the form. Nothing is plotted until you ask for it.'],
  ['Graph on demand', 'The Graph button stays locked until f(x) or your points are valid, then opens the canvas from the button.'],
  ['History built in', 'The notebook feed lives behind the clock button. Runs save when you open the graph or tap Save.'],
  ['One logo', 'A reversed N: a straight left leg, an S-curve that rises through the orange root dot, and a "1" for the right leg. The curve stops short of the 1 so both stay distinct.'],
  ['Motion with a job', 'Every animation explains something: where the graph came from, what changed, what just unlocked.'],
];
const MOTION = [
  ['Splash', '2.7 s, tap to skip', 'Tile springs in, the curve draws, the 1 draws with its foot, the root dot lands on the curve with a ring, the wordmark rises, then it all lifts away.'],
  ['Screen entrance', '550 ms, 55 ms stagger', 'Header, module switch, form and result rise in order. Replays when you change module.'],
  ['Segmented thumb', '300 ms spring', 'Slides with a small overshoot.'],
  ['Result number', '560 ms ease out', 'Counts up to the answer and settles on every change.'],
  ['Graph unlock', '1.2 s once', 'A ring pulses on the Graph button the moment the input becomes valid.'],
  ['Graph open', '560 ms', 'Circular reveal from the button. Home scales back to 94 %.'],
  ['Curve draw-on', '950 ms', 'The curve traces itself, then the root marker pops with a ring.'],
  ['Iteration autoplay', '2.6 s total', 'Plays once on open: 150 to 650 ms per step. Tap pause any time.'],
  ['Bottom sheet', '520 ms rise, 380 ms snap', 'Rises after the reveal, snaps between peek, half and full.'],
  ['History', '460 ms slide, 55 ms stagger', 'Slides up from the bottom. Cards stagger in, swipe-delete leaves in 220 ms.'],
  ['Reduced motion', 'instant', 'Reveals, draws and counts are replaced by plain fades or no motion.'],
];
const EASE = [
  ['Emphasized decelerate', 'cubic-bezier(.05, .7, .1, 1)', 'things entering'],
  ['Emphasized accelerate', 'cubic-bezier(.3, 0, .8, .15)', 'things leaving'],
  ['Standard', 'cubic-bezier(.2, 0, 0, 1)', 'drawing, small moves'],
  ['Spring overshoot', 'cubic-bezier(.3, 1.25, .5, 1)', 'thumbs, dots, badges'],
];

function initialMode() {
  try {
    const t = document.documentElement.dataset.theme;
    if (t === 'dark' || t === 'light') return t;
    return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
  } catch (e) { return 'light'; }
}

export function App() {
  const [mode, setMode] = useState(initialMode);
  const [run, setRun] = useState(0);
  return (
    <div className="page">
      <header>
        <p className="chrome-kicker">Numerical Analysis app · chosen direction</p>
        <h1 className="chrome-h1">NumLab</h1>
        <p className="chrome-sub">Concept D with an input-first home, the graph on demand, the notebook history, one logo and a motion pass. Everything is live: type a function, tap an example, press Graph.</p>
      </header>
      <div className="stage">
        <div data-cm={mode}>
          <div className="device">
            <div className="screen"><NumLabApp key={run} /></div>
          </div>
        </div>
        <div className="side">
          <div className="chrome-ctls" style={{ marginTop: 0 }}>
            <div className="ctl-group">
              <span className="ctl-label">App theme</span>
              <div className="pill-group" role="radiogroup" aria-label="App theme">
                {['light', 'dark'].map((m) => <button key={m} role="radio" aria-checked={mode === m} className="pill" onClick={() => setMode(m)}>{m === 'light' ? 'Light' : 'Dark'}</button>)}
              </div>
            </div>
            <button type="button" className="btn" onClick={() => setRun((n) => n + 1)}>Replay splash</button>
          </div>

          <div className="side-sec">
            <h2 className="chip-title">What changed</h2>
            <ul className="qol">{CHANGES.map(([t, d]) => <li key={t}><i /><div><b>{t}</b><span>{d}</span></div></li>)}</ul>
          </div>

          <div className="side-sec">
            <h2 className="chip-title">The logo</h2>
            <div className="logo-row" data-cm={mode}>
              <div className="logo-tilebox"><div className="logo-card on-light"><LogoMark size={72} /></div>On light</div>
              <div className="logo-tilebox"><div className="logo-card on-dark"><LogoMark size={72} /></div>On dark</div>
              <div className="logo-tilebox"><div className="logo-card on-light"><div className="icon-circle"><LogoMark size={84} /></div></div>Android icon</div>
            </div>
          </div>

          <div className="side-sec">
            <h2 className="chip-title">Motion spec</h2>
            <div className="mwrap">
              <table className="mtable">
                <thead><tr><th>Moment</th><th>Timing</th><th>What happens</th></tr></thead>
                <tbody>{MOTION.map(([a, b, c]) => <tr key={a}><td>{a}</td><td><code>{b}</code></td><td>{c}</td></tr>)}</tbody>
              </table>
            </div>
            <h2 className="chip-title" style={{ marginTop: 18 }}>Easing</h2>
            <div className="mwrap">
              <table className="mtable">
                <tbody>{EASE.map(([a, b, c]) => <tr key={a}><td>{a}</td><td><code>{b}</code></td><td>{c}</td></tr>)}</tbody>
              </table>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
