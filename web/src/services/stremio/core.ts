import { Bridge } from './bridge';
import type {
  CoreField,
  ExtraValue,
  LibrarySort,
  MetaItemPreview,
  ProfileSettings,
  ResourcePath,
  ResourceRequest,
  RuntimeEvent,
  StateByField,
  Stream,
} from './types';
import { APP_VERSION } from '../../shared/config';

/**
 * Browser counterpart of the Android `StremioCore.kt` wrapper: same official stremio-core, same
 * actions and models, driven through the WebAssembly runtime instead of JNI. Every method is a thin
 * dispatch of a core action; the UI reads the resulting model state, exactly like the native apps.
 */

/** What the service needs from the core runtime (the worker in production, a fake in tests). */
export interface CoreTransport {
  call<T = unknown>(path: string[], args?: unknown[]): Promise<T>;
}

type Listener = (event: RuntimeEvent) => void;

/** Reported to the core with player progress, like DEVICE_NAME = "android" on the native apps. */
const DEVICE_NAME = 'web';
/** Core storage keys get this prefix so they never collide with other data on the same origin. */
const STORAGE_PREFIX = 'mwplay.core.';

/** Key/value store the core persists its profile, library and addons through (AndroidStorage). */
export const coreStorage = {
  getItem(key: string): string | null {
    try {
      return localStorage.getItem(STORAGE_PREFIX + key);
    } catch {
      return null;
    }
  },
  setItem(key: string, value: string) {
    try {
      localStorage.setItem(STORAGE_PREFIX + key, value);
    } catch {
      // Private mode or quota: the core keeps working in memory for this session.
    }
  },
  removeItem(key: string) {
    try {
      localStorage.removeItem(STORAGE_PREFIX + key);
    } catch {
      // Same as above.
    }
  },
};

export class StremioCore {
  private readonly listeners = new Set<Listener>();
  readonly ready: Promise<void>;

  /**
   * @param transport calls into the runtime. `startRuntime` receives the page-side scope the runtime
   * calls back into (storage, location hash, event sink) and must resolve once the core is ready.
   */
  constructor(
    private readonly transport: CoreTransport,
    startRuntime: () => Promise<void>,
  ) {
    this.ready = startRuntime();
  }

  /** Spawns the WebAssembly core in a worker. */
  static start(): StremioCore {
    const worker = new Worker(new URL('./coreWorker.ts', import.meta.url), { type: 'module', name: 'stremio-core' });
    let core: StremioCore;
    const pageScope = {
      storage: coreStorage,
      location: {
        get hash() {
          return window.location.hash;
        },
      },
      onCoreEvent: (event: RuntimeEvent) => core.emit(event),
    };
    const bridge = new Bridge(pageScope, worker);
    core = new StremioCore(bridge, () => bridge.call(['init'], [{ appVersion: APP_VERSION, shellVersion: null }]));
    return core;
  }

  emit(event: RuntimeEvent) {
    this.listeners.forEach((listener) => listener(event));
  }

  onEvent(listener: Listener): () => void {
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  }

  async getState<F extends CoreField>(field: F): Promise<StateByField[F]> {
    await this.ready;
    return this.transport.call<StateByField[F]>(['getState'], [field]);
  }

  async dispatch(action: unknown, field: CoreField | null = null): Promise<void> {
    await this.ready;
    await this.transport.call(['dispatch'], [action, field, window.location.hash]);
  }

  private load(model: string, args: unknown, field: CoreField) {
    return this.dispatch({ action: 'Load', args: { model, args } }, field);
  }

  private ctx(action: string, args?: unknown) {
    return this.dispatch({ action: 'Ctx', args: args === undefined ? { action } : { action, args } }, 'ctx');
  }

  // region auth ----------------------------------------------------------------------------------

  login(email: string, password: string) {
    return this.ctx('Authenticate', { type: 'Login', email, password, facebook: false });
  }

  logout() {
    return this.ctx('Logout');
  }

  // region catalogs ------------------------------------------------------------------------------

  loadBoard() {
    return this.load('CatalogsWithExtra', { type: null, extra: [] }, 'board');
  }

  /** Board shelves load lazily, in the visible range (same as BoardRepository on Android). */
  loadBoardRange(start: number, end: number) {
    return this.dispatch({ action: 'CatalogsWithExtra', args: { action: 'LoadRange', args: { start, end } } }, 'board');
  }

  loadDiscover(request: ResourceRequest | null) {
    return this.load('CatalogWithFilters', request ? { request } : null, 'discover');
  }

  loadDiscoverNextPage() {
    return this.dispatch({ action: 'CatalogWithFilters', args: { action: 'LoadNextPage' } }, 'discover');
  }

  loadSearch(query: string) {
    const extra: ExtraValue[] = [['search', query]];
    return this.load('CatalogsWithExtra', { type: null, extra }, 'search');
  }

  loadSearchRange(start: number, end: number) {
    return this.dispatch({ action: 'CatalogsWithExtra', args: { action: 'LoadRange', args: { start, end } } }, 'search');
  }

  // region library -------------------------------------------------------------------------------

  loadLibrary(type: string | null, sort: LibrarySort = 'lastwatched') {
    return this.load('LibraryWithFilters', { request: { type, sort, page: 1 } }, 'library');
  }

  loadLibraryNextPage() {
    return this.dispatch({ action: 'LibraryWithFilters', args: { action: 'LoadNextPage' } }, 'library');
  }

  addToLibrary(preview: MetaItemPreview) {
    return this.ctx('AddToLibrary', preview);
  }

  removeFromLibrary(id: string) {
    return this.ctx('RemoveFromLibrary', id);
  }

  // region details & playback --------------------------------------------------------------------

  /**
   * Loads metadata and, when a video is chosen (or the title is a movie), the streams for it.
   * Mirrors StremioCore.metaDetails(type, id, videoId, guessStreamPath).
   */
  loadMetaDetails(type: string, id: string, videoId: string | null) {
    const metaPath: ResourcePath = { resource: 'meta', type, id, extra: [] };
    const streamId = videoId ?? (type === 'movie' ? id : null);
    const streamPath: ResourcePath | null = streamId ? { resource: 'stream', type, id: streamId, extra: [] } : null;
    return this.load('MetaDetails', { metaPath, streamPath, guessStream: streamPath === null }, 'meta_details');
  }

  /** The core converts the stream (torrents go through the streaming server) and tracks progress. */
  loadPlayer(stream: Stream, streamRequest: ResourceRequest | null, metaRequest: ResourceRequest | null) {
    return this.load('Player', { stream, streamRequest, metaRequest, subtitlesPath: null }, 'player');
  }

  private player(action: string, args?: unknown) {
    return this.dispatch({ action: 'Player', args: args === undefined ? { action } : { action, args } }, 'player');
  }

  playerTimeChanged(timeMs: number, durationMs: number) {
    return this.player('TimeChanged', { time: Math.round(timeMs), duration: Math.round(durationMs), device: DEVICE_NAME });
  }

  playerSeek(timeMs: number, durationMs: number) {
    return this.player('Seek', { time: Math.round(timeMs), duration: Math.round(durationMs), device: DEVICE_NAME });
  }

  playerPausedChanged(paused: boolean) {
    return this.player('PausedChanged', { paused });
  }

  playerEnded() {
    return this.player('Ended');
  }

  playerNextVideo() {
    return this.player('NextVideo');
  }

  unload(field: CoreField) {
    return this.dispatch({ action: 'Unload' }, field);
  }

  // region settings & streaming server ---------------------------------------------------

  updateSettings(settings: ProfileSettings) {
    return this.ctx('UpdateSettings', settings);
  }

  reloadStreamingServer() {
    return this.dispatch({ action: 'StreamingServer', args: { action: 'Reload' } }, 'streaming_server');
  }
}
