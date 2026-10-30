/**
 * Automatic source switch when a stream does not start, same rules as the apps
 * (player/failover/SourceFailover.kt):
 * - the chosen stream first, then the best stream of each registered addon not tried yet, in
 *   order (Torrentio, FrostStream, BestCine); only installed addons take part;
 * - an attempt fails after 30 s without real playback, or at once on a definitive error;
 * - a stream that failed is never tried again, each addon gets one automatic attempt and the
 *   number of attempts is capped, so the chain always ends;
 * - once a stream plays the session is finished and nothing switches.
 */

export interface SourceFailoverConfig {
  enabled: boolean;
  /** Time a source has to actually start playing (not to answer the addon request). */
  startTimeoutMs: number;
  /** Order in which addons are tried after the chosen stream. */
  addonOrder: string[];
  /** Hard cap on attempts per playback, the chosen stream included. */
  maxAttempts: number;
}

export const DEFAULT_ADDON_ORDER = ['Torrentio', 'FrostStream', 'BestCine'];

export const DEFAULT_FAILOVER_CONFIG: SourceFailoverConfig = {
  enabled: true,
  startTimeoutMs: 30_000,
  addonOrder: DEFAULT_ADDON_ORDER,
  maxAttempts: DEFAULT_ADDON_ORDER.length + 1,
};

/** Accents, case, spaces and punctuation ignored: "Best Cine" = "BestCine". */
export function normalizeAddon(name: string): string {
  return name
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .toLowerCase()
    .replace(/[^a-z0-9]/g, '');
}

/** Position of `addon` in the order, or null when it is not a registered source. */
export function addonRank(addon: string, config: SourceFailoverConfig = DEFAULT_FAILOVER_CONFIG): number | null {
  const normalized = normalizeAddon(addon);
  if (!normalized) return null;
  const index = config.addonOrder.findIndex((name) => normalized.includes(normalizeAddon(name)));
  return index >= 0 ? index : null;
}

export type AttemptOutcome = 'started' | 'timeout' | 'failed' | 'cancelled';

export interface FailoverAttempt {
  index: number;
  key: string;
  addon: string;
  outcome: AttemptOutcome | null;
  elapsedMs: number | null;
  reason: string | null;
}

export class SourceFailoverSession<T> {
  private failedKeys = new Set<string>();
  private triedAddons = new Set<string>();
  private log: FailoverAttempt[] = [];
  private attemptStartedAt = 0;
  current: T | null = null;
  started = false;

  constructor(
    private readonly config: SourceFailoverConfig,
    private readonly keyOf: (item: T) => string,
    private readonly addonOf: (item: T) => string,
    private readonly now: () => number = Date.now,
  ) {}

  get attempts(): FailoverAttempt[] {
    return [...this.log];
  }

  /** True while an attempt is waiting to start playing. */
  get isWaiting(): boolean {
    return this.current !== null && !this.started;
  }

  begin(first: T) {
    this.current = first;
    this.triedAddons.add(normalizeAddon(this.addonOf(first)));
    this.attemptStartedAt = this.now();
    this.log.push({ index: this.log.length + 1, key: this.keyOf(first), addon: this.addonOf(first), outcome: null, elapsedMs: null, reason: null });
  }

  /** Playback really started; returns the time it took, or null if nothing was waiting. */
  onStarted(): number | null {
    if (!this.isWaiting) return null;
    this.started = true;
    return this.finish('started', null);
  }

  cancel() {
    if (this.isWaiting) this.finish('cancelled', null);
    this.current = null;
  }

  /** The attempt timed out or failed for good: the next stream from `available`, or null. */
  onFailed(outcome: 'timeout' | 'failed', reason: string | null, available: T[], best: (a: T, b: T) => number): T | null {
    if (!this.isWaiting) return null;
    this.failedKeys.add(this.keyOf(this.current!));
    this.finish(outcome, reason);
    if (!this.config.enabled || this.log.length >= this.config.maxAttempts) {
      this.current = null;
      return null;
    }
    const next = this.nextCandidate(available, best);
    if (!next) {
      this.current = null;
      return null;
    }
    this.begin(next);
    return next;
  }

  private nextCandidate(available: T[], best: (a: T, b: T) => number): T | null {
    const usable = available.filter((item) => !this.failedKeys.has(this.keyOf(item)));
    for (const addon of this.config.addonOrder) {
      const normalized = normalizeAddon(addon);
      if ([...this.triedAddons].some((tried) => tried.includes(normalized))) continue;
      const pick = usable.filter((item) => normalizeAddon(this.addonOf(item)).includes(normalized)).sort(best)[0];
      if (pick) return pick;
    }
    return null;
  }

  private finish(outcome: AttemptOutcome, reason: string | null): number {
    const elapsed = this.now() - this.attemptStartedAt;
    const last = this.log[this.log.length - 1];
    this.log[this.log.length - 1] = { ...last, outcome, elapsedMs: elapsed, reason };
    return elapsed;
  }
}

/**
 * Real start: position moving forward while playing and not buffering, for at least
 * `requiredProgressMs`. Opening the player, buffering or the resume seek do not count.
 */
export class PlaybackStartDetector {
  private lastPosition: number | null = null;
  private progress = 0;
  started = false;

  constructor(private readonly requiredProgressMs = 1_000) {}

  reset() {
    this.lastPosition = null;
    this.progress = 0;
    this.started = false;
  }

  /** True the first time playback counts as started. */
  onUpdate(isPlaying: boolean, isBuffering: boolean, positionMs: number, hasError: boolean): boolean {
    if (this.started) return false;
    if (!isPlaying || isBuffering || hasError) {
      this.lastPosition = null;
      return false;
    }
    const previous = this.lastPosition;
    this.lastPosition = positionMs;
    if (previous === null) return false;
    const delta = positionMs - previous;
    // Seeks (resume position, user) jump; real playback advances in small steps.
    if (delta > 0 && delta <= 5_000) this.progress += delta;
    if (this.progress >= this.requiredProgressMs) {
      this.started = true;
      return true;
    }
    return false;
  }
}

export const FAILOVER_MESSAGES = {
  allFailed: 'Nenhuma fonte conseguiu iniciar a reprodução.',
  allFailedHint: 'Tente novamente ou escolha outra fonte.',
  switching: (from: string, outcome: 'timeout' | 'failed', to: string, timeoutMs: number) =>
    outcome === 'timeout' ? `${from} não iniciou em ${timeoutMs / 1000} s. Tentando ${to}…` : `${from} falhou. Tentando ${to}…`,
  switched: (to: string) => `Reproduzindo por ${to}`,
};

// region preference -----------------------------------------------------------------------------

const ENABLED_KEY = 'mwplay.web.sourceFailover';

export function readFailoverConfig(): SourceFailoverConfig {
  try {
    const raw = localStorage.getItem(ENABLED_KEY);
    return { ...DEFAULT_FAILOVER_CONFIG, enabled: raw === null ? true : raw === 'true' };
  } catch {
    return DEFAULT_FAILOVER_CONFIG;
  }
}

export function writeFailoverEnabled(enabled: boolean) {
  try {
    localStorage.setItem(ENABLED_KEY, String(enabled));
  } catch {
    // Private mode: this visit only.
  }
}
