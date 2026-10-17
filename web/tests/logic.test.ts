// Pure logic: no mocks, no network.
import { describe, expect, it } from 'vitest';
import { isBackKey } from '../src/app/back';
import { parseRoute, routeHref } from '../src/app/router';
import { detectTv, readUiPreference, resolveUiMode, writeUiPreference } from '../src/app/uiMode';
import { directionalScore, ownsArrow } from '../src/features/tv/spatialNav';
import { episodeLabel, metaLinks, parseDiscoverDeepLink, playbackFormat, srtToVtt, streamKind, streamLabel } from '../src/services/stremio/streams';
import { detailsFixture } from './fixtures/coreStates';

describe('stream handling', () => {
  it('classifies addon stream sources for the browser', () => {
    expect(streamKind({ url: 'https://cdn.example/video.mp4' })).toBe('http');
    expect(streamKind({ url: 'rtmp://live.example/stream' })).toBe('unsupported');
    expect(streamKind({ infoHash: 'a'.repeat(40), fileIdx: 1 })).toBe('torrent');
    expect(streamKind({ ytId: 'abc' })).toBe('youtube');
    expect(streamKind({ externalUrl: 'https://example.com' })).toBe('external');
    expect(streamKind({})).toBe('unsupported');
  });

  it('labels sources like the native stream list', () => {
    expect(streamLabel({ name: 'Addon\n1080p', description: 'File' })).toEqual({ title: 'Addon\n1080p', detail: 'File' });
    expect(streamLabel({ behaviorHints: { filename: 'movie.mp4' } })).toEqual({ title: 'movie.mp4', detail: null });
  });

  it('detects the container from the playable URL', () => {
    expect(playbackFormat('https://x/master.m3u8?token=1')).toBe('hls');
    expect(playbackFormat('https://x/manifest.mpd')).toBe('dash');
    expect(playbackFormat('http://127.0.0.1:11470/abc/0')).toBe('file');
  });

  it('turns a core discover deep link back into its catalog request', () => {
    expect(parseDiscoverDeepLink('#/discover/https%3A%2F%2Fv3-cinemeta.strem.io%2Fmanifest.json/movie/top?genre=Drama')).toEqual({
      base: 'https://v3-cinemeta.strem.io/manifest.json',
      path: { resource: 'catalog', type: 'movie', id: 'top', extra: [['genre', 'Drama']] },
    });
    expect(parseDiscoverDeepLink('stremio:///discover/https%3A%2F%2Fa.b%2Fmanifest.json/series/new?')?.path.type).toBe('series');
    expect(parseDiscoverDeepLink('#/library')).toBeNull();
  });

  it('reads genres, cast and IMDb rating from meta links', () => {
    const meta = detailsFixture.metaItem!.content.type === 'Ready' ? detailsFixture.metaItem!.content.content : null;
    expect(metaLinks(meta!)).toMatchObject({ genres: ['Comedy', 'Drama'], cast: ['Charles Chaplin'], imdbRating: '8.2' });
  });

  it('converts SRT subtitles to WebVTT', () => {
    const vtt = srtToVtt('﻿1\r\n00:00:01,500 --> 00:00:03,000\r\nOlá\r\n');
    expect(vtt.startsWith('WEBVTT\n\n1\n00:00:01.500 --> 00:00:03.000\nOlá')).toBe(true);
    expect(srtToVtt('WEBVTT\n\n00:01.000 --> 00:02.000\nx')).toBe('WEBVTT\n\n00:01.000 --> 00:02.000\nx\n');
  });

  it('formats season/episode labels', () => {
    expect(episodeLabel(2, 3)).toBe('Temporada 2 · Episódio 3');
    expect(episodeLabel(0, 1)).toBe('Especial · Episódio 1');
    expect(episodeLabel(undefined, 1)).toBeNull();
  });
});

describe('routes', () => {
  it('round-trips every route through the hash', () => {
    const routes = [
      { name: 'home' },
      { name: 'discover' },
      { name: 'library' },
      { name: 'search', query: 'o garoto' },
      { name: 'detail', type: 'series', id: 'tt1', videoId: 'tt1:1:2' },
      { name: 'detail', type: 'movie', id: 'tt2', videoId: null },
      { name: 'player' },
      { name: 'settings' },
    ] as const;
    for (const route of routes) expect(parseRoute(routeHref(route))).toEqual(route);
    expect(parseRoute('#/unknown')).toEqual({ name: 'home' });
  });
});

describe('remote and keyboard input', () => {
  const input = Object.assign(document.createElement('input'), { type: 'search' });
  const range = Object.assign(document.createElement('input'), { type: 'range' });
  range.setAttribute('data-nav-horizontal', '');
  const button = document.createElement('button');

  it('leaves LEFT/RIGHT to sliders and text fields but never UP/DOWN to sliders', () => {
    expect(ownsArrow(range, 'left')).toBe(true);
    expect(ownsArrow(range, 'right')).toBe(true);
    expect(ownsArrow(range, 'up')).toBe(false);
    expect(ownsArrow(range, 'down')).toBe(false);
    expect(ownsArrow(input, 'left')).toBe(true);
    expect(ownsArrow(input, 'down')).toBe(false);
    expect(ownsArrow(button, 'left')).toBe(false);
  });

  it('prefers the nearest element in the same row and ignores elements behind', () => {
    const from = { left: 100, right: 200, top: 0, bottom: 100 };
    const sameRow = { left: 220, right: 320, top: 0, bottom: 100 };
    const nextRow = { left: 220, right: 320, top: 120, bottom: 220 };
    const behind = { left: 0, right: 90, top: 0, bottom: 100 };
    expect(directionalScore(from, behind, 'right')).toBeNull();
    expect(directionalScore(from, sameRow, 'right')!).toBeLessThan(directionalScore(from, nextRow, 'right')!);
  });

  it('lands on the first item of a row when leaving a wide element', () => {
    const hero = { left: 0, right: 1000, top: 0, bottom: 300 };
    const first = { left: 0, right: 120, top: 320, bottom: 500 };
    const middle = { left: 440, right: 560, top: 320, bottom: 500 };
    expect(directionalScore(hero, first, 'down')!).toBeLessThan(directionalScore(hero, middle, 'down')!);
  });

  it('maps Escape, Backspace (outside text fields) and TV remote back keys to back', () => {
    const key = (init: KeyboardEventInit & { keyCode?: number }, target?: EventTarget) => {
      const event = new KeyboardEvent('keydown', init);
      if (init.keyCode) Object.defineProperty(event, 'keyCode', { value: init.keyCode });
      if (target) Object.defineProperty(event, 'target', { value: target });
      return event;
    };
    expect(isBackKey(key({ key: 'Escape' }))).toBe(true);
    expect(isBackKey(key({ key: 'Unidentified', keyCode: 461 }))).toBe(true); // webOS
    expect(isBackKey(key({ key: 'XF86Back', keyCode: 10009 }))).toBe(true); // Tizen
    expect(isBackKey(key({ key: 'Backspace' }, button))).toBe(true);
    expect(isBackKey(key({ key: 'Backspace' }, input))).toBe(false);
    expect(isBackKey(key({ key: 'ArrowLeft' }))).toBe(false);
  });
});

describe('interface mode', () => {
  it('detects TV browsers from the user agent or ?ui=', () => {
    expect(detectTv('Mozilla/5.0 (Web0S; Linux/SmartTV) AppleWebKit/537.36', '')).toBe(true);
    expect(detectTv('Mozilla/5.0 (SMART-TV; Linux; Tizen 6.0) AppleWebKit/538.1', '')).toBe(true);
    expect(detectTv('Mozilla/5.0 (Linux; Android 12; AFTKA) AppleWebKit/537.36', '')).toBe(true);
    expect(detectTv('Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X)', '')).toBe(false);
    expect(detectTv('Mozilla/5.0 (Windows NT 10.0)', '?ui=tv')).toBe(true);
    expect(detectTv('Mozilla/5.0 (SMART-TV; Tizen)', '?ui=standard')).toBe(false);
  });

  it('persists the user choice and falls back to automatic', () => {
    localStorage.clear();
    expect(readUiPreference()).toBe('auto');
    writeUiPreference('tv');
    expect(readUiPreference()).toBe('tv');
    expect(resolveUiMode(readUiPreference())).toBe('tv');
    writeUiPreference('auto');
    expect(readUiPreference()).toBe('auto');
  });
});
