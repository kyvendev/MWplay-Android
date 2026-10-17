/**
 * TEST DOUBLE. A transport that answers like the stremio-core-web runtime from in-memory states and
 * records every dispatched action, so UI tests can assert the exact core actions the screens send.
 */
import { StremioCore, type CoreTransport } from '../src/services/stremio/core';
import type { CoreField, RuntimeEvent } from '../src/services/stremio/types';

export interface FakeCore {
  core: StremioCore;
  dispatched: { action: unknown; field: CoreField | null }[];
  setState(field: CoreField, state: unknown): void;
  emit(event: RuntimeEvent): void;
}

export function createFakeCore(states: Partial<Record<CoreField, unknown>> = {}, options: { failStart?: string } = {}): FakeCore {
  const current: Partial<Record<CoreField, unknown>> = { ...states };
  const dispatched: FakeCore['dispatched'] = [];
  const transport: CoreTransport = {
    async call<T>(path: string[], args: unknown[] = []) {
      if (path[0] === 'getState') return current[args[0] as CoreField] as T;
      if (path[0] === 'dispatch') {
        dispatched.push({ action: args[0], field: args[1] as CoreField | null });
        return undefined as T;
      }
      throw new Error(`unexpected call ${path.join('.')}`);
    },
  };
  const core = new StremioCore(transport, () => (options.failStart ? Promise.reject(new Error(options.failStart)) : Promise.resolve()));
  return {
    core,
    dispatched,
    setState(field, state) {
      current[field] = state;
      core.emit({ name: 'NewState', args: [field] });
    },
    emit: (event) => core.emit(event),
  };
}
