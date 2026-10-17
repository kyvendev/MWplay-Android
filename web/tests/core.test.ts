// The adapter must send exactly the actions stremio-core deserializes (src/runtime/msg/action.rs).
// Uses an in-memory transport (test double) instead of the WebAssembly runtime.
import { describe, expect, it } from 'vitest';
import { Bridge, type MessagePort_ } from '../src/services/stremio/bridge';
import { coreStorage } from '../src/services/stremio/core';
import { createFakeCore } from './fakeCore';

describe('StremioCore actions', () => {
  it('waits for the runtime before dispatching', async () => {
    const fake = createFakeCore();
    await fake.core.loadBoard();
    expect(fake.dispatched).toEqual([{ action: { action: 'Load', args: { model: 'CatalogsWithExtra', args: { type: null, extra: [] } } }, field: 'board' }]);
  });

  it('authenticates through Ctx like the apps', async () => {
    const fake = createFakeCore();
    await fake.core.login('a@b.c', 'secret');
    await fake.core.logout();
    expect(fake.dispatched).toEqual([
      { action: { action: 'Ctx', args: { action: 'Authenticate', args: { type: 'Login', email: 'a@b.c', password: 'secret', facebook: false } } }, field: 'ctx' },
      { action: { action: 'Ctx', args: { action: 'Logout' } }, field: 'ctx' },
    ]);
  });

  it('loads movie streams directly and series streams per episode', async () => {
    const fake = createFakeCore();
    await fake.core.loadMetaDetails('movie', 'tt1', null);
    await fake.core.loadMetaDetails('series', 'tt2', null);
    await fake.core.loadMetaDetails('series', 'tt2', 'tt2:1:3');
    const args = fake.dispatched.map((entry) => (entry.action as { args: { args: unknown } }).args.args);
    expect(args[0]).toEqual({ metaPath: { resource: 'meta', type: 'movie', id: 'tt1', extra: [] }, streamPath: { resource: 'stream', type: 'movie', id: 'tt1', extra: [] }, guessStream: false });
    expect(args[1]).toEqual({ metaPath: { resource: 'meta', type: 'series', id: 'tt2', extra: [] }, streamPath: null, guessStream: true });
    expect(args[2]).toMatchObject({ streamPath: { resource: 'stream', type: 'series', id: 'tt2:1:3', extra: [] }, guessStream: false });
  });

  it('hands streams to the Player model and reports progress as the web device', async () => {
    const fake = createFakeCore();
    const stream = { url: 'https://x/v.m3u8' };
    const request = { base: 'https://addon/manifest.json', path: { resource: 'stream', type: 'movie', id: 'tt1', extra: [] } };
    await fake.core.loadPlayer(stream, request, null);
    await fake.core.playerTimeChanged(12_345.6, 600_000);
    await fake.core.playerSeek(1_000, 600_000);
    await fake.core.playerPausedChanged(true);
    await fake.core.playerEnded();
    await fake.core.unload('player');
    expect(fake.dispatched.map((entry) => entry.action)).toEqual([
      { action: 'Load', args: { model: 'Player', args: { stream, streamRequest: request, metaRequest: null, subtitlesPath: null } } },
      { action: 'Player', args: { action: 'TimeChanged', args: { time: 12_346, duration: 600_000, device: 'web' } } },
      { action: 'Player', args: { action: 'Seek', args: { time: 1_000, duration: 600_000, device: 'web' } } },
      { action: 'Player', args: { action: 'PausedChanged', args: { paused: true } } },
      { action: 'Player', args: { action: 'Ended' } },
      { action: 'Unload' },
    ]);
  });

  it('searches, pages and filters with the core request shapes', async () => {
    const fake = createFakeCore();
    await fake.core.loadSearch('o garoto');
    await fake.core.loadSearchRange(0, 30);
    await fake.core.loadLibrary('movie', 'name');
    await fake.core.loadDiscover(null);
    expect(fake.dispatched.map((entry) => [entry.field, entry.action])).toEqual([
      ['search', { action: 'Load', args: { model: 'CatalogsWithExtra', args: { type: null, extra: [['search', 'o garoto']] } } }],
      ['search', { action: 'CatalogsWithExtra', args: { action: 'LoadRange', args: { start: 0, end: 30 } } }],
      ['library', { action: 'Load', args: { model: 'LibraryWithFilters', args: { request: { type: 'movie', sort: 'name', page: 1 } } } }],
      ['discover', { action: 'Load', args: { model: 'CatalogWithFilters', args: null } }],
    ]);
  });

  it('surfaces a failed runtime start', async () => {
    const fake = createFakeCore({}, { failStart: 'WebAssembly unavailable' });
    await expect(fake.core.ready).rejects.toThrow('WebAssembly unavailable');
  });
});

describe('core storage', () => {
  it('namespaces the core buckets so they never collide with other data on the origin', () => {
    localStorage.clear();
    coreStorage.setItem('profile', '{"auth":null}');
    expect(localStorage.getItem('mwplay.core.profile')).toBe('{"auth":null}');
    expect(coreStorage.getItem('profile')).toBe('{"auth":null}');
    coreStorage.removeItem('profile');
    expect(coreStorage.getItem('profile')).toBeNull();
  });
});

describe('worker bridge', () => {
  /** Two linked in-memory ports, like a page and its worker. */
  function linkedPorts(): [MessagePort_, MessagePort_] {
    const make = () => {
      const listeners = new Set<(event: MessageEvent) => void>();
      return { listeners, port: null as unknown as MessagePort_ };
    };
    const a = make();
    const b = make();
    const port = (self: typeof a, other: typeof b): MessagePort_ => ({
      postMessage: (data) => queueMicrotask(() => other.listeners.forEach((listener) => listener({ data } as MessageEvent))),
      addEventListener: (_type, listener) => self.listeners.add(listener),
      removeEventListener: (_type, listener) => self.listeners.delete(listener),
    });
    return [port(a, b), port(b, a)];
  }

  it('calls methods and reads properties on the other side, and propagates errors', async () => {
    const [pagePort, workerPort] = linkedPorts();
    const worker = new Bridge({ add: (x: number, y: number) => x + y, fail: () => { throw new Error('boom'); } }, workerPort);
    const page = new Bridge({ location: { hash: '#/detail/movie/tt1' } }, pagePort);
    await expect(page.call(['add'], [2, 3])).resolves.toBe(5);
    await expect(worker.call(['location', 'hash'])).resolves.toBe('#/detail/movie/tt1');
    await expect(page.call(['fail'])).rejects.toMatchObject({ message: 'boom' });
  });
});
