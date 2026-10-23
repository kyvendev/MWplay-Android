import { useEffect, useRef } from 'react';
import { drawOrbital, LOGO_FONT, overlayAlpha, skipIntro, stepIntro, TIMELINE, type IntroClock } from './orbitalRenderer';

const INTRO_SEEN_KEY = 'mwplay.web.introSeen';
/** Keys that skip the opening; Back keys keep their normal behaviour. */
const SKIP_KEYS = new Set(['Enter', ' ', 'NumpadEnter']);
const BACK_KEYS = new Set(['Escape', 'Backspace', 'GoBack', 'BrowserBack']);
const BACK_KEY_CODES = new Set([461, 10009]); // webOS / Tizen remote Back

/** Once per browser tab session: reloads and in-app navigation never replay it. */
export function shouldShowIntro(storage: Pick<Storage, 'getItem' | 'setItem'> | null = safeSessionStorage()): boolean {
  try {
    if (!storage || storage.getItem(INTRO_SEEN_KEY) === '1') return false;
    storage.setItem(INTRO_SEEN_KEY, '1');
    return true;
  } catch {
    return false;
  }
}

function safeSessionStorage(): Storage | null {
  try {
    return window.sessionStorage;
  } catch {
    return null;
  }
}

/**
 * Animated MW Play opening over the normal startup: the app keeps loading underneath and nothing
 * waits on it. It leaves right after the logo when [ready], holds the logo with a small spinner
 * while it is not, and never stays more than 12 s. Click / OK skips; arrows cannot move focus in
 * the hidden screen underneath (TV mode); reduced motion shows only the logo.
 */
export function OrbitalIntro({ ready, onFinished }: { ready: boolean; onFinished: () => void }) {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const rootRef = useRef<HTMLDivElement>(null);
  const readyRef = useRef(ready);
  readyRef.current = ready;
  const finishedRef = useRef(onFinished);
  finishedRef.current = onFinished;
  const clockRef = useRef<IntroClock | null>(null);

  useEffect(() => {
    const reducedMotion = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches ?? false;
    const clock: IntroClock = { time: reducedMotion ? TIMELINE.LOGO_IN : 0, elapsed: 0, exitAt: null, skipRequested: false };
    clockRef.current = clock;
    document.fonts?.load(LOGO_FONT).catch(() => undefined);

    const onKeyDown = (event: KeyboardEvent) => {
      if (BACK_KEYS.has(event.key) || BACK_KEY_CODES.has(event.keyCode)) return;
      event.preventDefault();
      event.stopImmediatePropagation();
      if (SKIP_KEYS.has(event.key)) skipIntro(clock);
    };
    window.addEventListener('keydown', onKeyDown, true);

    const canvas = canvasRef.current;
    const g = canvas?.getContext('2d') ?? null;
    let frame = 0;
    let last = performance.now();
    let done = false;
    const tick = (now: number) => {
      const finished = stepIntro(clock, (now - last) / 1000, readyRef.current, reducedMotion);
      last = now;
      if (rootRef.current) rootRef.current.style.opacity = String(overlayAlpha(clock));
      if (canvas && g) {
        // Device pixels for crisp lines, capped at 2x to keep low-end TVs smooth.
        const dpr = Math.min(window.devicePixelRatio || 1, 2);
        const w = Math.round(canvas.clientWidth * dpr);
        const h = Math.round(canvas.clientHeight * dpr);
        if (canvas.width !== w || canvas.height !== h) {
          canvas.width = w;
          canvas.height = h;
        }
        drawOrbital(g, w, h, clock, reducedMotion);
      }
      if (finished) {
        done = true;
        finishedRef.current();
        return;
      }
      frame = requestAnimationFrame(tick);
    };
    frame = requestAnimationFrame(tick);

    return () => {
      if (!done) cancelAnimationFrame(frame);
      window.removeEventListener('keydown', onKeyDown, true);
    };
  }, []);

  return (
    <div
      ref={rootRef}
      className="orbital-intro"
      role="img"
      aria-label="MW Play"
      onPointerDown={() => clockRef.current && skipIntro(clockRef.current)}
    >
      <canvas ref={canvasRef} className="orbital-intro__canvas" />
    </div>
  );
}
