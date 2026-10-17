import { createContext, useContext, useEffect, useRef, useState, type ReactNode } from 'react';
import type { StremioCore } from './core';
import type { CoreEvent, CoreField, StateByField } from './types';

type CoreStatus = { kind: 'starting' } | { kind: 'ready' } | { kind: 'failed'; message: string };

const CoreContext = createContext<{ core: StremioCore; status: CoreStatus } | null>(null);

export function CoreProvider({ core, children }: { core: StremioCore; children: ReactNode }) {
  const [status, setStatus] = useState<CoreStatus>({ kind: 'starting' });
  useEffect(() => {
    let active = true;
    core.ready.then(
      () => active && setStatus({ kind: 'ready' }),
      (error: unknown) => active && setStatus({ kind: 'failed', message: describeError(error) }),
    );
    return () => {
      active = false;
    };
  }, [core]);
  return <CoreContext.Provider value={{ core, status }}>{children}</CoreContext.Provider>;
}

export function useCore() {
  const value = useContext(CoreContext);
  if (!value) throw new Error('useCore must be used inside <CoreProvider>');
  return value;
}

/**
 * Current state of a core model, re-read whenever the runtime reports a NewState for that field
 * (the same signal the Android wrapper turns into flows). Reads are coalesced: a burst of events
 * triggers at most one read in flight plus one trailing read.
 */
export function useCoreState<F extends CoreField>(field: F): StateByField[F] | null {
  const { core, status } = useCore();
  const [state, setState] = useState<StateByField[F] | null>(null);

  useEffect(() => {
    if (status.kind !== 'ready') return;
    // Per subscription (not refs), so a re-run of the effect never inherits a cancelled read.
    let active = true;
    let reading = false;
    let pending = false;
    const read = async () => {
      if (reading) {
        pending = true;
        return;
      }
      reading = true;
      try {
        do {
          pending = false;
          const next = await core.getState(field);
          if (active) setState(next);
        } while (pending && active);
      } catch {
        // A transient read failure keeps the last good state; the next event retries.
      } finally {
        reading = false;
      }
    };
    read();
    const unsubscribe = core.onEvent((event) => {
      if (event.name === 'NewState' && event.args.includes(field)) read();
    });
    return () => {
      active = false;
      unsubscribe();
    };
  }, [core, field, status.kind]);

  return state;
}

/** Subscribes to core events (UserAuthenticated, Error, ...). */
export function useCoreEvents(handler: (event: CoreEvent) => void) {
  const { core } = useCore();
  const latest = useRef(handler);
  latest.current = handler;
  useEffect(
    () =>
      core.onEvent((event) => {
        if (event.name === 'CoreEvent') latest.current(event.args);
      }),
    [core],
  );
}

/** Human-readable message from core errors (CtxError / EnvError shapes) or JS errors. */
export function describeError(error: unknown): string {
  if (!error) return 'Erro desconhecido';
  if (typeof error === 'string') return error;
  if (error instanceof Error) return error.message;
  if (typeof error === 'object') {
    const value = error as Record<string, unknown>;
    if (typeof value.message === 'string') return value.message;
    if (value.content !== undefined) return describeError(value.content);
    if (value.error !== undefined) return describeError(value.error);
  }
  return String(error);
}
