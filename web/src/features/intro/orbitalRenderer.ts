/**
 * "Orbital" opening, drawn on a 2D canvas: a violet orb draws a ring of light, converges into the
 * play symbol and reveals the MW Play logo. Same timeline and geometry as OrbitalIntro.kt on the
 * Android apps.
 */

export const TIMELINE = {
  /** Logo reveal finished; skipping jumps here. */
  LOGO_IN: 5.0,
  /** Earliest moment the intro may start leaving. */
  LOGO_SETTLED: 5.3,
  FADE_OUT: 0.45,
  /** Never cover the app for longer than this, whatever the startup state. */
  HARD_CAP: 12,
} as const;

export interface IntroClock {
  /** Position on the animation timeline (s). */
  time: number;
  /** Real time since the intro appeared (s). */
  elapsed: number;
  exitAt: number | null;
  skipRequested: boolean;
}

/** Advances the clock by `dt` seconds; returns true once the fade-out is over. */
export function stepIntro(clock: IntroClock, dt: number, ready: boolean, reducedMotion: boolean): boolean {
  const step = Math.min(Math.max(dt, 0), 0.1); // no jumps after a hidden tab
  clock.elapsed += step;
  clock.time = reducedMotion ? TIMELINE.LOGO_IN : clock.time + step;
  if (clock.exitAt === null) {
    const logoShown = clock.time >= TIMELINE.LOGO_SETTLED || (reducedMotion && clock.elapsed >= 0.6);
    if ((ready && (logoShown || clock.skipRequested)) || clock.elapsed >= TIMELINE.HARD_CAP) clock.exitAt = clock.elapsed;
    return false;
  }
  return clock.elapsed - clock.exitAt >= TIMELINE.FADE_OUT;
}

export function skipIntro(clock: IntroClock) {
  clock.skipRequested = true;
  if (clock.time < TIMELINE.LOGO_IN) clock.time = TIMELINE.LOGO_IN;
}

/** Opacity of the whole overlay (fades out at the end). */
export function overlayAlpha(clock: IntroClock): number {
  if (clock.exitAt === null) return 1;
  return 1 - easeInOut(clamp((clock.elapsed - clock.exitAt) / TIMELINE.FADE_OUT));
}

const clamp = (x: number, a = 0, b = 1) => Math.min(b, Math.max(a, x));
const seg = (t: number, a: number, b: number) => clamp((t - a) / (b - a));
const easeOut = (x: number) => 1 - Math.pow(1 - x, 3);
const easeIn = (x: number) => x * x * x;
const easeInOut = (x: number) => (x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2);
const easeOutBack = (x: number) => 1 + 2.2 * Math.pow(x - 1, 3) + 1.2 * Math.pow(x - 1, 2);
const TAU = Math.PI * 2;

type Rgb = readonly [number, number, number];
const VIOLET: Rgb = [138, 99, 255];
const LILAC: Rgb = [185, 162, 255];
const rgba = (c: Rgb, a: number) => `rgba(${c[0]},${c[1]},${c[2]},${Math.min(1, Math.max(0, a))})`;

/** Official mark paths (res/drawable/mw_play_mark.xml, viewport 108×108). */
const M_PATH = 'M8,78 L8,30 L20,30 L31,54 L42,30 L54,30 L54,78 L43,78 L43,50 L35,69 L28,69 L19,50 L19,78 Z';
const W_PATH = 'M57,30 L68,30 L74,61 L81,40 L88,61 L95,30 L106,30 L97,78 L88,78 L81,58 L74,78 L65,78 Z';
let markPaths: { m: Path2D; w: Path2D } | null = null;

export const LOGO_FONT = "800 100px 'Plus Jakarta Sans'";

export function drawOrbital(g: CanvasRenderingContext2D, w: number, h: number, clock: IntroClock, reducedMotion: boolean) {
  const t = clock.time;
  const u = Math.min(w, h) / 1080;
  const cx = w / 2;
  const cy = h / 2;
  const R = 190 * u;
  const contentAlpha = reducedMotion ? clamp(clock.elapsed / 0.3) : 1;
  // Reduced motion freezes the timeline on the logo, so the spinner follows real time.
  const holdTime = reducedMotion ? TIMELINE.LOGO_IN + clock.elapsed : t;

  const glow = (x: number, y: number, r: number, color: Rgb, a: number) => {
    if (a <= 0 || r <= 0) return;
    const gr = g.createRadialGradient(x, y, 0, x, y, r);
    gr.addColorStop(0, rgba(color, a));
    gr.addColorStop(0.35, rgba(color, a * 0.45));
    gr.addColorStop(1, rgba(color, 0));
    g.fillStyle = gr;
    g.beginPath();
    g.arc(x, y, r, 0, TAU);
    g.fill();
  };
  const arc = (x: number, y: number, r: number, from: number, to: number, color: string, width: number) => {
    g.strokeStyle = color;
    g.lineWidth = width;
    g.beginPath();
    g.arc(x, y, r, from, to);
    g.stroke();
  };

  g.globalAlpha = 1;
  g.fillStyle = '#05040a';
  g.fillRect(0, 0, w, h);
  const bgA = easeOut(seg(t, 0, 1.2));
  const bg = g.createRadialGradient(cx, cy, 0, cx, cy, Math.max(w, h) * 0.6);
  bg.addColorStop(0, `rgba(58,30,130,${0.42 * bgA})`);
  bg.addColorStop(0.5, `rgba(30,16,70,${0.25 * bgA})`);
  bg.addColorStop(1, 'rgba(0,0,0,0)');
  g.fillStyle = bg;
  g.fillRect(0, 0, w, h);
  if (contentAlpha <= 0) return;
  g.lineCap = 'round';

  // Orbit geometry.
  const START = -Math.PI / 2;
  const lift = easeInOut(seg(t, 1.0, 1.3));
  const sweep = easeInOut(seg(t, 1.2, 2.5));
  const conv = seg(t, 2.5, 3.0);
  const ang = START + sweep * TAU + easeOut(conv) * Math.PI * 0.9;
  const rad = R * lift * (1 - easeIn(conv));
  const ox = cx + Math.cos(ang) * rad;
  const oy = cy + Math.sin(ang) * rad;

  // Ring: drawn by the orb, pulses as the light converges, then expands and dissolves.
  const ringOut = easeOut(seg(t, 3.45, 4.3));
  const ringR = R * (1 + ringOut * 1.5);
  const ringA = (1 - ringOut) * (t < 3.0 ? 1 : 1 - 0.35 * seg(t, 3.0, 3.4));
  const pulse = Math.sin(Math.PI * seg(t, 2.85, 3.35)) * 0.6;
  if (t >= 1.2) {
    const a1 = START + sweep * TAU;
    const al = Math.min(1, ringA * (1 + pulse * 0.5));
    const uu = u * (1 - ringOut * 0.6);
    if (al > 0 && a1 > START) {
      arc(cx, cy, ringR, START, a1, rgba(VIOLET, 0.12 * al), 34 * uu);
      arc(cx, cy, ringR, START, a1, rgba(VIOLET, 0.32 * al), 14 * uu);
      arc(cx, cy, ringR, START, a1, rgba(VIOLET, 0.55 * al), 6 * uu);
      arc(cx, cy, ringR, START, a1, rgba(LILAC, 0.95 * al), 3.4 * uu);
      const head = sweep < 1 ? 1 : 1 - conv;
      if (head > 0) {
        const tail = Math.min(0.9, a1 - START);
        const steps = 18;
        for (let i = 0; i < steps; i++) {
          const s0 = a1 - tail * (1 - i / steps);
          const s1 = a1 - tail * (1 - (i + 1) / steps);
          arc(cx, cy, ringR, s0, s1 + 0.002, `rgba(255,255,255,${(i / steps) ** 2 * 0.9 * head * al})`, 2.2 * uu);
        }
      }
    }
    const details: [number, number][] = [[0.18, 1.0], [0.52, 0.8], [0.81, 0.9]];
    details.forEach(([p, s], i) => {
      const a = seg(sweep, p, p + 0.08) * (0.6 + 0.4 * Math.sin(t * 5 + i * 2)) * ringA;
      if (a <= 0) return;
      const sa = START + p * TAU;
      const x = cx + Math.cos(sa) * ringR;
      const y = cy + Math.sin(sa) * ringR;
      const d = 3.2 * u * s * 2.2;
      glow(x, y, 3.2 * u * s * 4, LILAC, 0.5 * a);
      g.fillStyle = `rgba(255,255,255,${Math.min(1, a)})`;
      g.beginPath();
      g.moveTo(x, y - d);
      g.quadraticCurveTo(x, y, x + d, y);
      g.quadraticCurveTo(x, y, x, y + d);
      g.quadraticCurveTo(x, y, x - d, y);
      g.quadraticCurveTo(x, y, x, y - d);
      g.fill();
    });
  }

  // Orb.
  const born = easeOut(seg(t, 0.05, 0.9));
  const absorb = easeIn(seg(t, 2.85, 3.15));
  const orbB = born * (1 - absorb);
  if (orbB > 0) {
    const r = (4 + 8 * born) * u * (1 + absorb * 0.8);
    glow(ox, oy, r * 11, VIOLET, 0.22 * orbB);
    glow(ox, oy, r * 4.5, VIOLET, 0.55 * orbB);
    glow(ox, oy, r * 2, LILAC, 0.9 * orbB);
    g.fillStyle = `rgba(255,255,255,${orbB})`;
    g.beginPath();
    g.arc(ox, oy, r, 0, TAU);
    g.fill();
  }

  // Play symbol born from the light.
  const triIn = seg(t, 2.85, 3.3);
  const triOut = easeIn(seg(t, 3.45, 3.72));
  const triA = easeOut(triIn) * (1 - triOut);
  glow(cx, cy, (140 + 160 * pulse) * u, VIOLET, 0.55 * Math.max(pulse, triA * 0.6));
  if (triA > 0) {
    const size = 112 * u * (0.35 + 0.65 * easeOutBack(triIn)) * (1 - 0.35 * triOut);
    const tw = size * 0.92;
    const tcx = cx + 4 * u;
    const x0 = tcx - tw * 0.38;
    const x1 = tcx + tw * 0.62;
    const p: [number, number][] = [[x0, cy - size / 2], [x1, cy], [x0, cy + size / 2]];
    g.save();
    g.globalAlpha = triA;
    g.fillStyle = '#fff';
    g.beginPath();
    g.moveTo((p[0][0] + p[2][0]) / 2, (p[0][1] + p[2][1]) / 2);
    for (let i = 0; i < 3; i++) g.arcTo(p[i][0], p[i][1], p[(i + 1) % 3][0], p[(i + 1) % 3][1], size * 0.09);
    g.closePath();
    g.fill();
    g.restore();
  }

  // Logo: official mark + white "Play", slow and soft reveal.
  const lg = easeInOut(seg(t, 3.6, 5.0));
  const lgA = Math.sin((seg(t, 3.6, 4.9) * Math.PI) / 2) * contentAlpha;
  if (lgA > 0) {
    markPaths ??= { m: new Path2D(M_PATH), w: new Path2D(W_PATH) };
    const markH = 104 * u;
    const k = markH / 48;
    const markW = 98 * k;
    g.font = `800 ${118 * u}px 'Plus Jakarta Sans', system-ui, sans-serif`;
    const textW = g.measureText('Play').width;
    const gap = 22 * u;
    const total = markW + gap + textW;
    const scale = 0.95 + 0.05 * lg;
    g.save();
    g.translate(cx, cy + (1 - lg) * 26 * u);
    g.scale(scale, scale);
    g.globalAlpha = lgA;
    glow(0, 0, total * 0.75, VIOLET, 0.16);
    g.save();
    g.translate(-total / 2 - 8 * k, -54 * k);
    g.scale(k, k);
    g.fillStyle = '#825CFF';
    g.fill(markPaths.m);
    g.fillStyle = '#4E8CFF';
    g.fill(markPaths.w);
    g.restore();
    g.fillStyle = '#fff';
    g.textBaseline = 'middle';
    g.fillText('Play', -total / 2 + markW + gap, 4 * u);
    g.restore();
  }

  // Startup still running after the logo: small, quiet spinner below it.
  const waitA = seg(holdTime, TIMELINE.LOGO_SETTLED + 0.6, TIMELINE.LOGO_SETTLED + 1.0);
  if (waitA > 0) {
    const from = clock.elapsed * 5.2;
    arc(cx, cy + 130 * u, 13 * u, from, from + Math.PI * 0.6, rgba(LILAC, 0.8 * waitA), 2.6 * u);
  }
}
