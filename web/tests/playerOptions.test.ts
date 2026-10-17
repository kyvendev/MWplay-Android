import { describe, expect, it } from 'vitest';
import {
  activeCueText,
  clampStep,
  cleanCueText,
  cssColor,
  formatDelay,
  formatSpeed,
  groupSubtitles,
  languageName,
  nextSpeed,
  normalizeLanguage,
  pickAudioTrack,
  pickSubtitle,
  SUBTITLE_SIZE,
  type SubtitleOption,
} from '../src/features/player/playerOptions';

const option = (id: string, source: SubtitleOption['source'], language: string | null): SubtitleOption => ({ id, source, language, label: id, selected: false });

describe('languages', () => {
  it('normalizes addon, manifest and profile codes', () => {
    expect(normalizeLanguage('por')).toBe('pt');
    expect(normalizeLanguage('pob')).toBe('pt-BR');
    expect(normalizeLanguage('pt-br')).toBe('pt-BR');
    expect(normalizeLanguage('ger')).toBe('de');
    expect(normalizeLanguage('en')).toBe('en');
    expect(normalizeLanguage('und')).toBeNull();
    expect(normalizeLanguage(null)).toBeNull();
  });

  it('names languages in Portuguese', () => {
    expect(languageName('eng')).toBe('Inglês');
    expect(languageName('por')).toBe('Português');
    expect(languageName(null)).toBe('Desconhecido');
  });
});

describe('subtitle choice', () => {
  const options = [option('ext:eng', 'addon', 'eng'), option('ext:por', 'addon', 'por'), option('hls:0', 'embedded', 'pt'), option('local:1', 'local', null)];

  it('groups by language with local files first', () => {
    const groups = groupSubtitles(options);
    expect(groups.map((group) => group.key)).toEqual(['local', 'en', 'pt']);
    expect(groups[2].options.map((item) => item.id)).toEqual(['ext:por', 'hls:0']);
  });

  it('restores the saved subtitle, then falls back to the preferred language (embedded first)', () => {
    expect(pickSubtitle(options, { id: 'ext:eng', embedded: false, language: 'eng' }, 'por')?.id).toBe('ext:eng');
    // Saved id no longer offered (other source): same language and kind.
    expect(pickSubtitle(options, { id: 'ext:gone', embedded: false, language: 'por' }, null)?.id).toBe('ext:por');
    expect(pickSubtitle(options, null, 'por')?.id).toBe('hls:0');
    expect(pickSubtitle(options, null, 'spa')).toBeNull();
    expect(pickSubtitle(options, null, null)).toBeNull();
  });

  it('restores the audio language', () => {
    const tracks = [
      { id: '0', language: 'en' },
      { id: '1', language: 'pt' },
    ];
    expect(pickAudioTrack(tracks, { id: '0', language: 'por' }, 'eng')?.id).toBe('1');
    expect(pickAudioTrack(tracks, null, 'por')?.id).toBe('1');
    expect(pickAudioTrack(tracks, null, null)).toBeNull();
  });
});

describe('cue rendering', () => {
  const cues = [
    { startTime: 1, endTime: 3, text: '<i>Olá</i> &amp; tchau' },
    { startTime: 2, endTime: 4, text: '{\\an8}Linha de cima' },
  ];

  it('shows the cues active at the current time', () => {
    expect(activeCueText(cues, 0.5, 0)).toBe('');
    expect(activeCueText(cues, 1.5, 0)).toBe('Olá & tchau');
    expect(activeCueText(cues, 2.5, 0)).toBe('Olá & tchau\nLinha de cima');
  });

  it('applies the delay: positive shows subtitles later', () => {
    expect(activeCueText(cues, 1.5, 1000)).toBe('');
    expect(activeCueText(cues, 0.5, -1000)).toBe('Olá & tchau');
  });

  it('strips markup', () => {
    expect(cleanCueText('<b>a</b>\\Nb')).toBe('a\nb');
  });
});

describe('controls', () => {
  it('cycles speed and clamps steppers like the TV player', () => {
    expect(nextSpeed(1)).toBe(1.25);
    expect(nextSpeed(2)).toBe(0.5);
    expect(formatSpeed(1.25)).toBe('1,25x');
    expect(clampStep(250, 25, SUBTITLE_SIZE)).toBe(250);
    expect(clampStep(75, -25, SUBTITLE_SIZE)).toBe(75);
    expect(formatDelay(250)).toBe('+0,25 s');
    expect(formatDelay(-1500)).toBe('-1,50 s');
  });

  it('accepts only colour values', () => {
    expect(cssColor('#FFFFFFFF', '#fff')).toBe('#FFFFFFFF');
    expect(cssColor('red;}', '#fff')).toBe('#fff');
  });
});
