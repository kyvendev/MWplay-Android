/**
 * Player options shared by the controls and the subtitle/audio panels, with the same ranges as the
 * Android TV player (PlayerScreen.kt): speed steps, subtitle delay/size/position and aspect modes.
 */

export const SPEEDS = [0.5, 0.75, 1, 1.25, 1.5, 2] as const;

export const SUBTITLE_DELAY = { min: -10_000, max: 10_000, step: 250 } as const;
export const SUBTITLE_SIZE = { min: 75, max: 250, step: 25 } as const;
export const SUBTITLE_OFFSET = { min: 0, max: 100, step: 5 } as const;

export type AspectMode = 'contain' | 'fill' | 'cover';
export const ASPECT_MODES: { mode: AspectMode; label: string }[] = [
  { mode: 'contain', label: 'Ajustar' },
  { mode: 'fill', label: 'Esticar' },
  { mode: 'cover', label: 'Zoom' },
];

export function nextSpeed(current: number): number {
  const index = SPEEDS.findIndex((speed) => speed === current);
  return SPEEDS[(index + 1) % SPEEDS.length];
}

export function formatSpeed(speed: number): string {
  return `${String(speed).replace('.', ',')}x`;
}

export function clampStep(value: number, delta: number, range: { min: number; max: number }): number {
  return Math.min(range.max, Math.max(range.min, value + delta));
}

export function formatDelay(ms: number): string {
  const seconds = (ms / 1000).toFixed(2).replace('.', ',');
  return `${ms > 0 ? '+' : ''}${seconds} s`;
}

// region languages -----------------------------------------------------------------------------

/** ISO 639-2 (including bibliographic codes addons use) to the two-letter code browsers know. */
const ALPHA3: Record<string, string> = {
  por: 'pt', pob: 'pt-BR', eng: 'en', spa: 'es', spl: 'es-419', fre: 'fr', fra: 'fr', ger: 'de', deu: 'de',
  ita: 'it', jpn: 'ja', kor: 'ko', chi: 'zh', zho: 'zh', rus: 'ru', ara: 'ar', hin: 'hi', tur: 'tr',
  pol: 'pl', dut: 'nl', nld: 'nl', swe: 'sv', nor: 'no', nob: 'nb', dan: 'da', fin: 'fi', gre: 'el',
  ell: 'el', heb: 'he', hun: 'hu', cze: 'cs', ces: 'cs', rum: 'ro', ron: 'ro', ukr: 'uk', tha: 'th',
  vie: 'vi', ind: 'id', may: 'ms', msa: 'ms', bul: 'bg', hrv: 'hr', srp: 'sr', slv: 'sl', slo: 'sk',
  slk: 'sk', cat: 'ca', baq: 'eu', eus: 'eu', glg: 'gl', per: 'fa', fas: 'fa', est: 'et', lav: 'lv',
  lit: 'lt', ice: 'is', isl: 'is', tam: 'ta', tel: 'te', ben: 'bn', urd: 'ur', fil: 'fil', tgl: 'tl',
};

/**
 * Canonical language tag ("pt", "pt-BR", "en"…) for codes from addons, HLS manifests and the
 * profile settings, or null when there is none. Brazilian variants keep their region.
 */
export function normalizeLanguage(code: string | null | undefined): string | null {
  if (!code) return null;
  const raw = code.trim().replace('_', '-');
  if (!raw || raw === 'und' || raw === 'unknown') return null;
  const lower = raw.toLowerCase();
  if (ALPHA3[lower]) return ALPHA3[lower];
  if (lower === 'ptbr' || lower === 'pt-br') return 'pt-BR';
  const [base, region] = lower.split('-');
  if (base.length === 3 && ALPHA3[base]) return ALPHA3[base];
  return region ? `${base}-${region.toUpperCase()}` : base;
}

/** Base language without region, so "pt-BR" matches a "por" preference. */
export function languageBase(code: string | null | undefined): string | null {
  return normalizeLanguage(code)?.split('-')[0] ?? null;
}

let displayNames: Intl.DisplayNames | null | undefined;

/** Language name in Portuguese ("Português (Brasil)", "Inglês"); falls back to the raw code. */
export function languageName(code: string | null | undefined): string {
  const tag = normalizeLanguage(code);
  if (!tag) return 'Desconhecido';
  if (displayNames === undefined) {
    try {
      displayNames = new Intl.DisplayNames(['pt-BR'], { type: 'language' });
    } catch {
      displayNames = null;
    }
  }
  let name: string | undefined;
  try {
    name = displayNames?.of(tag);
  } catch {
    name = undefined;
  }
  if (!name || name.toLowerCase() === tag.toLowerCase()) return code ?? tag;
  return name.charAt(0).toUpperCase() + name.slice(1);
}

/**
 * A track's own name when it says something ("Forced", "Commentary"); null for placeholders such as
 * "stream_0", "Faixa 2" or a bare language code/name.
 */
export function meaningfulTrackName(label: string | null | undefined, language: string | null | undefined): string | null {
  const name = label?.trim();
  if (!name || /^(stream|track|subtitles?|audio|text|faixa|legenda)[\s_-]*\d*$/i.test(name)) return null;
  const lower = name.toLowerCase();
  if (language && (lower === language.toLowerCase() || lower === normalizeLanguage(language)?.toLowerCase() || lower === languageName(language).toLowerCase())) return null;
  return name;
}

// region subtitle options ----------------------------------------------------------------------

export type SubtitleSource = 'embedded' | 'addon' | 'local';

export interface SubtitleOption {
  /** Stable for the stream: `hls:N`/`native:N` (embedded), `ext:<id or url>` (addon), `local:N`. */
  id: string;
  source: SubtitleSource;
  language: string | null;
  label: string;
  selected: boolean;
}

export interface SubtitleGroup {
  key: string;
  label: string;
  options: SubtitleOption[];
}

/** Groups options by language (then embedded before addon variants), like the TV "Languages" column. */
export function groupSubtitles(options: SubtitleOption[]): SubtitleGroup[] {
  const groups = new Map<string, SubtitleGroup>();
  for (const option of options) {
    const key = option.source === 'local' ? 'local' : normalizeLanguage(option.language) ?? 'und';
    let group = groups.get(key);
    if (!group) {
      group = { key, label: key === 'local' ? 'Arquivo local' : key === 'und' ? 'Outros' : languageName(key), options: [] };
      groups.set(key, group);
    }
    group.options.push(option);
  }
  const rank = (group: SubtitleGroup) => (group.key === 'local' ? 0 : group.key === 'und' ? 2 : 1);
  return Array.from(groups.values()).sort((a, b) => rank(a) - rank(b) || a.label.localeCompare(b.label, 'pt-BR'));
}

/**
 * Which subtitle to show automatically: the one saved for this title, otherwise the first one in
 * the profile's preferred language (embedded first). Returns null when nothing matches yet.
 */
export function pickSubtitle(
  options: SubtitleOption[],
  saved: { id: string; embedded: boolean; language: string | null } | null | undefined,
  preferredLanguage: string | null | undefined,
): SubtitleOption | null {
  if (saved) {
    const byId = options.find((option) => option.id === saved.id);
    if (byId) return byId;
    const savedBase = languageBase(saved.language);
    const byLanguage = savedBase && options.find((option) => (option.source === 'embedded') === saved.embedded && languageBase(option.language) === savedBase);
    if (byLanguage) return byLanguage;
  }
  const preferred = languageBase(preferredLanguage);
  if (!preferred) return null;
  const matches = options.filter((option) => option.source !== 'local' && languageBase(option.language) === preferred);
  const exact = normalizeLanguage(preferredLanguage);
  return (
    matches.find((option) => option.source === 'embedded' && normalizeLanguage(option.language) === exact) ??
    matches.find((option) => option.source === 'embedded') ??
    matches.find((option) => normalizeLanguage(option.language) === exact) ??
    matches[0] ??
    null
  );
}

/** Audio track to restore: the saved one (by id, then language), otherwise the preferred language. */
export function pickAudioTrack<T extends { id: string; language: string | null }>(
  tracks: T[],
  saved: { id: string; language: string | null } | null | undefined,
  preferredLanguage: string | null | undefined,
): T | null {
  if (saved) {
    const savedBase = languageBase(saved.language);
    const match = (savedBase && tracks.find((track) => languageBase(track.language) === savedBase)) || tracks.find((track) => track.id === saved.id);
    if (match) return match;
  }
  const preferred = languageBase(preferredLanguage);
  return (preferred && tracks.find((track) => languageBase(track.language) === preferred)) || null;
}

// region cue rendering -------------------------------------------------------------------------

export interface CueLike {
  startTime: number;
  endTime: number;
  text?: string;
}

/** Strips WebVTT/HTML tags and ASS override blocks; the result is rendered as plain text. */
export function cleanCueText(text: string): string {
  return text
    .replace(/\{\\[^}]*\}/g, '')
    .replace(/<[^>]*>/g, '')
    .replace(/&nbsp;/g, ' ')
    .replace(/&lt;/g, '<')
    .replace(/&gt;/g, '>')
    .replace(/&amp;/g, '&')
    .replace(/\\N/g, '\n')
    .trim();
}

/**
 * Text of the cues active at `timeS` with the subtitle delay applied (positive delay shows the
 * subtitles later, like the `sub-delay` of the Android players).
 */
export function activeCueText(cues: ArrayLike<CueLike> | null | undefined, timeS: number, delayMs: number): string {
  if (!cues) return '';
  const at = timeS - delayMs / 1000;
  const lines: string[] = [];
  for (let index = 0; index < cues.length; index++) {
    const cue = cues[index];
    if (cue.startTime <= at && at < cue.endTime && cue.text) {
      const text = cleanCueText(cue.text);
      if (text) lines.push(text);
    }
  }
  return lines.join('\n');
}

/** Profile colours are #RRGGBB or #RRGGBBAA (CSS accepts both); anything else falls back. */
export function cssColor(value: string | null | undefined, fallback: string): string {
  return value && /^#([0-9a-f]{3,4}|[0-9a-f]{6}|[0-9a-f]{8})$/i.test(value) ? value : fallback;
}
