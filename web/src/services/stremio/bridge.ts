/**
 * Request/response channel between the page and the core worker. It speaks the same message
 * protocol as the bridge shipped in @stremio/stremio-core-web: `{ request: { id, path, args } }`
 * answered by `{ response: { id, result: { data } | { error } } }`, where `path` names a property
 * (or method) on the receiving side's scope.
 */
export interface MessagePort_ {
  postMessage(message: unknown): void;
  addEventListener(type: 'message', listener: (event: MessageEvent) => void): void;
  removeEventListener(type: 'message', listener: (event: MessageEvent) => void): void;
}

interface BridgeRequest {
  id: string;
  path: string[];
  args: unknown[];
}

interface BridgeResponse {
  id: string;
  result: { data: unknown } | { error: unknown };
}

let nextId = 0;
const newId = () => `${Date.now().toString(36)}-${(nextId++).toString(36)}`;

/** Errors must survive structured cloning, so only their message travels. */
function serializeError(error: unknown): unknown {
  if (error instanceof Error) return { message: error.message, name: error.name };
  return error;
}

export class Bridge {
  constructor(
    private readonly scope: object,
    private readonly port: MessagePort_,
  ) {
    port.addEventListener('message', this.onMessage);
  }

  private onMessage = async (event: MessageEvent) => {
    const request = (event.data as { request?: BridgeRequest } | undefined)?.request;
    if (!request) return;
    const { id, path, args } = request;
    try {
      let target: unknown = this.scope;
      let owner: unknown = undefined;
      for (const key of path) {
        owner = target;
        target = (target as Record<string, unknown>)[key];
      }
      const data = typeof target === 'function' ? await (target as (...a: unknown[]) => unknown).apply(owner, args) : await target;
      this.port.postMessage({ response: { id, result: { data } } satisfies BridgeResponse });
    } catch (error) {
      this.port.postMessage({ response: { id, result: { error: serializeError(error) } } satisfies BridgeResponse });
    }
  };

  call<T = unknown>(path: string[], args: unknown[] = []): Promise<T> {
    const id = newId();
    return new Promise<T>((resolve, reject) => {
      const onResponse = (event: MessageEvent) => {
        const response = (event.data as { response?: BridgeResponse } | undefined)?.response;
        if (!response || response.id !== id) return;
        this.port.removeEventListener('message', onResponse);
        if ('error' in response.result) reject(response.result.error);
        else resolve(response.result.data as T);
      };
      this.port.addEventListener('message', onResponse);
      this.port.postMessage({ request: { id, path, args } satisfies BridgeRequest });
    });
  }

  dispose() {
    this.port.removeEventListener('message', this.onMessage);
  }
}
