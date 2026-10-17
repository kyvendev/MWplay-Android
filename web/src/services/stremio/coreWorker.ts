/// <reference lib="webworker" />
/**
 * Runs the official stremio-core (compiled to WebAssembly) off the main thread. This is the
 * browser counterpart of the JNI bridge used by the Android apps: the page provides the key/value
 * storage (localStorage instead of SharedPreferences) and receives every runtime event.
 * It mirrors the worker shipped in @stremio/stremio-core-web, rewritten as an ES module so the
 * bundler can resolve the WebAssembly binary.
 */
import wasmUrl from '@stremio/stremio-core-web/stremio_core_web_bg.wasm?url';
import { Bridge } from './bridge';

declare const self: DedicatedWorkerGlobalScope & Record<string, unknown>;

interface InitArgs {
  appVersion: string;
  shellVersion: string | null;
}

type CoreModule = typeof import('@stremio/stremio-core-web/stremio_core_web.js');

// One bridge per side: it answers the page's calls (scope below) and calls back into the page.
let bridge: Bridge;
let core: CoreModule | null = null;

function requireCore(): CoreModule {
  if (!core) throw new Error('stremio-core is not initialized');
  return core;
}

const api = {
  async init({ appVersion, shellVersion }: InitArgs) {
    // The wasm glue reads these globals from `self`; `document.baseURI` is read when the module
    // loads, so the module is imported only after the shims exist (as the official worker does).
    self.document = { baseURI: self.location.href };
    self.app_version = appVersion;
    self.shell_version = shellVersion;
    self.get_location_hash = () => bridge.call(['location', 'hash']);
    self.local_storage_get_item = (key: string) => bridge.call(['storage', 'getItem'], [key]);
    self.local_storage_set_item = (key: string, value: string) => bridge.call(['storage', 'setItem'], [key, value]);
    self.local_storage_remove_item = (key: string) => bridge.call(['storage', 'removeItem'], [key]);
    core = await import('@stremio/stremio-core-web/stremio_core_web.js');
    await core.default(wasmUrl);
    await core.initialize_runtime((event: unknown) => bridge.call(['onCoreEvent'], [event]));
  },
  getState: (field: string) => requireCore().get_state(field),
  dispatch: (action: unknown, field: string | null, locationHash: string) => requireCore().dispatch(action, field, locationHash),
  decodeStream: (stream: string) => requireCore().decode_stream(stream),
};

bridge = new Bridge(api, self);
