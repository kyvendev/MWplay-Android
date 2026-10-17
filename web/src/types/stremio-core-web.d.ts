declare module '@stremio/stremio-core-web/stremio_core_web.js' {
  export default function init(input?: string | URL | Response): Promise<unknown>;
  export function initialize_runtime(emitToUi: (event: unknown) => unknown): Promise<void>;
  export function get_state(field: unknown): unknown;
  export function dispatch(action: unknown, field: unknown, locationHash: unknown): void;
  export function decode_stream(stream: unknown): unknown;
}
