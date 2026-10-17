/**
 * TEST DATA ONLY. Shapes captured from the real stremio-core-web runtime (0.64.0) on 2026-10-17,
 * restricted to public Cinemeta catalog/meta data. Streams are synthetic placeholders (a public
 * test HLS URL, a fake infoHash and an example external link): no account, addon configuration
 * or third-party stream URL is included. Never used by the application at runtime.
 */
import type { CatalogsWithExtra, Ctx, MetaDetails } from '../../src/services/stremio/types';

const CINEMETA = { manifest: { id: 'com.linvo.cinemeta', name: 'Cinemeta' } };

export const boardFixture: CatalogsWithExtra = {
  selected: { type: null, extra: [] },
  catalogs: [
    {
      id: 'top',
      name: 'Popular',
      type: 'movie',
      addon: CINEMETA,
      content: {
        type: 'Ready',
        content: [
          { id: 'tt22084616', type: 'movie', name: 'Spider-Man: Brand New Day', poster: 'https://images.metahub.space/poster/small/tt22084616/img', background: 'https://images.metahub.space/background/medium/tt22084616/img', releaseInfo: '2026', posterShape: 'poster' },
          { id: 'tt6933238', type: 'movie', name: 'Unabomber', poster: 'https://images.metahub.space/poster/small/tt6933238/img', background: 'https://images.metahub.space/background/medium/tt6933238/img', releaseInfo: '2026', posterShape: 'poster' },
        ],
      },
      deepLinks: { discover: '#/discover/https%3A%2F%2Fv3-cinemeta.strem.io%2Fmanifest.json/movie/top?' },
    },
    {
      id: 'top',
      name: 'Popular',
      type: 'series',
      addon: CINEMETA,
      content: { type: 'Loading' },
      deepLinks: { discover: '#/discover/https%3A%2F%2Fv3-cinemeta.strem.io%2Fmanifest.json/series/top?' },
    },
    {
      id: 'imdbRating',
      name: 'Featured',
      type: 'movie',
      addon: CINEMETA,
      content: { type: 'Err', content: 'timeout' },
      deepLinks: { discover: '#/discover/https%3A%2F%2Fv3-cinemeta.strem.io%2Fmanifest.json/movie/imdbRating?' },
    },
  ],
};

export const searchFixture: CatalogsWithExtra = {
  selected: { type: null, extra: [['search', 'matrix']] },
  catalogs: [
    {
      id: 'top',
      name: 'Popular',
      type: 'movie',
      addon: CINEMETA,
      content: {
        type: 'Ready',
        content: [
          { id: 'tt0133093', type: 'movie', name: 'The Matrix', poster: null, releaseInfo: '1999', posterShape: 'poster' },
          { id: 'tt0234215', type: 'movie', name: 'The Matrix Reloaded', poster: null, releaseInfo: '2003', posterShape: 'poster' },
        ],
      },
      deepLinks: { discover: '#/discover/https%3A%2F%2Fv3-cinemeta.strem.io%2Fmanifest.json/movie/top?search=matrix' },
    },
  ],
};

export const detailsFixture: MetaDetails = {
  selected: {
    metaPath: { resource: 'meta', type: 'movie', id: 'tt0012349', extra: [] },
    streamPath: { resource: 'stream', type: 'movie', id: 'tt0012349', extra: [] },
    guessStream: false,
  },
  libraryItem: null,
  title: 'The Kid',
  metaItem: {
    addon: { manifest: { id: 'com.linvo.cinemeta', name: 'Cinemeta', logo: null }, transportUrl: 'https://v3-cinemeta.strem.io/manifest.json' },
    content: {
      type: 'Ready',
      content: {
        id: 'tt0012349',
        type: 'movie',
        name: 'The Kid',
        poster: 'https://images.metahub.space/poster/small/tt0012349/img',
        background: 'https://images.metahub.space/background/medium/tt0012349/img',
        logo: null,
        description: 'The Tramp cares for an abandoned child, but events put their relationship in jeopardy.',
        releaseInfo: '1921',
        runtime: '68 min',
        links: [
          { name: '8.2', category: 'imdb', url: 'https://imdb.com/title/tt0012349' },
          { name: 'Comedy', category: 'Genres', url: 'stremio:///discover/x/movie/top?genre=Comedy' },
          { name: 'Drama', category: 'Genres', url: 'stremio:///discover/x/movie/top?genre=Drama' },
          { name: 'Charles Chaplin', category: 'Cast', url: 'stremio:///search?search=Charles%20Chaplin' },
        ],
        videos: [],
        inLibrary: false,
      },
    },
  },
  streams: [
    {
      addon: { manifest: { id: 'test.http', name: 'Teste HTTP' }, transportUrl: 'https://example.invalid/http/manifest.json' },
      content: { type: 'Ready', content: [{ url: 'https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8', name: 'Teste HLS', description: 'Stream público de teste', behaviorHints: { notWebReady: true } }] },
    },
    {
      addon: { manifest: { id: 'test.torrent', name: 'Teste Torrent' }, transportUrl: 'https://example.invalid/torrent/manifest.json' },
      content: { type: 'Ready', content: [{ infoHash: '0000000000000000000000000000000000000000', fileIdx: 0, name: 'Teste torrent', description: 'infoHash fictício' }] },
    },
    {
      addon: { manifest: { id: 'test.external', name: 'Teste Externo' }, transportUrl: 'https://example.invalid/external/manifest.json' },
      content: { type: 'Ready', content: [{ externalUrl: 'https://example.com/watch', name: 'Serviço externo' }] },
    },
    {
      addon: { manifest: { id: 'test.loading', name: 'Teste Lento' }, transportUrl: 'https://example.invalid/slow/manifest.json' },
      content: { type: 'Err', content: 'error' },
    },
  ],
};

export function ctxFixture(authenticated: boolean): Ctx {
  return {
    profile: {
      auth: authenticated ? { key: 'test-key', user: { _id: 'test-user', email: 'teste@example.com' } } : null,
      addons: [],
      settings: {
        streamingServerUrl: 'http://127.0.0.1:11470/',
        subtitlesLanguage: null,
        subtitlesSize: 100,
        audioLanguage: null,
        seekTimeDuration: 10000,
        bingeWatching: true,
      },
    },
  };
}
