import React, { useState, useEffect, useRef } from 'react';
import { cx } from './kit.jsx';

export const reduceMotion = () => typeof window !== 'undefined' && !!window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches;

/* The one NumLab mark: a reversed N (И). A straight left leg drops to a sharp minimum, then one S-shaped curve rises through the
   orange root dot (the flat middle, like a cubic crossing its root); the right leg is a numeral "1" with a flag and a wide foot.
   The curve stops short of the 1 so the two stay distinct. */
export function LogoMark({ size = 64, animate = false, tile = true, className }) {
  const ink = tile ? '#FFFFFF' : 'currentColor';
  return (
    <svg viewBox="0 0 64 64" width={size} height={size} className={cx(animate && 'logo-anim', className)} role="img" aria-label="NumLab logo">
      {tile && <rect className="lg-tile" width="64" height="64" rx="18" fill="var(--logo-tile)" />}
      <path className="lg-curve" d="M14.5 13L13.5 50C17 47 20 41 22 35C23.5 30.5 26.5 31 30 30.3C34.5 29.5 37 26.5 38.3 21.5" pathLength="1" fill="none" stroke={ink} strokeWidth="4.6" strokeLinecap="round" strokeLinejoin="round" />
      <path className="lg-base" d="M39.5 50.5H54.5" pathLength="1" fill="none" stroke={ink} strokeWidth="3.4" strokeLinecap="round" />
      <path className="lg-one" d="M47.5 50.5L49 12L44.3 18.6" pathLength="1" fill="none" stroke={ink} strokeWidth="4.6" strokeLinecap="round" strokeLinejoin="round" />
      {animate && <circle className="lg-ring" cx="30" cy="30.3" r="5.5" fill="none" stroke="var(--logo-dot)" strokeWidth="2" />}
      <circle className="lg-dot" cx="30" cy="30.3" r="3.9" fill="var(--logo-dot)" />
    </svg>
  );
}

export function Wordmark({ animate = false, size = 34 }) {
  const letters = 'NumLab'.split('');
  return (
    <span className="font-display inline-flex tracking-tight" style={{ fontSize: size, lineHeight: 1 }} aria-label="NumLab">
      {letters.map((c, i) => (
        <span key={i} aria-hidden="true" className={cx(animate && 'wm-letter', i >= 3 ? 'font-medium opacity-80' : 'font-extrabold')} style={{ '--i': i }}>{c}</span>
      ))}
    </span>
  );
}

/* Splash: tile pops, axis and "1" draw, the root dot lands with a ring pulse, wordmark rises, then everything lifts away.
   Tap to skip. With reduced motion it is a quiet cross-fade. */
export function Splash({ onExit, onDone }) {
  const [out, setOut] = useState(false);
  const t = useRef([]);
  const go = () => {
    if (t.current.length > 1) return;
    setOut(true);
    onExit && onExit();
    t.current.push(setTimeout(() => onDone && onDone(), reduceMotion() ? 120 : 520));
  };
  useEffect(() => {
    const r = reduceMotion();
    t.current = [setTimeout(go, r ? 900 : 2350)];
    return () => t.current.forEach(clearTimeout);
  }, []);
  return (
    <div className={cx('splash absolute inset-0 z-[70] grid place-items-center bg-c-bg', out && 'splash-out')} role="presentation" onClick={go}>
      <div className="flex flex-col items-center gap-6">
        <LogoMark size={104} animate />
        <Wordmark animate size={36} />
      </div>
      <span className="splash-hint pointer-events-none absolute bottom-8 text-xs text-c-ink2">Tap to skip</span>
    </div>
  );
}
