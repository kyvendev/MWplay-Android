import { useEffect, useRef } from 'react';

/**
 * One "back" action for every input method: Escape, Backspace outside text fields, the browser back
 * button and TV remote back keys (webOS 461, Tizen 10009). The most recently mounted handler wins,
 * so an open modal closes before the page navigates.
 */
type BackHandler = () => boolean;

const handlers: BackHandler[] = [];

/** webOS and Tizen remote back buttons. */
const TV_BACK_KEY_CODES = new Set([461, 10009]);

export function isBackKey(event: KeyboardEvent): boolean {
  if (event.key === 'Escape' || event.key === 'GoBack' || event.key === 'BrowserBack') return true;
  if (TV_BACK_KEY_CODES.has(event.keyCode)) return true;
  // Backspace only means "back" when it is not deleting text.
  if (event.key === 'Backspace') return !isTextInput(event.target);
  return false;
}

export function isTextInput(target: EventTarget | null): boolean {
  if (!(target instanceof HTMLElement)) return false;
  if (target.isContentEditable) return true;
  if (target instanceof HTMLTextAreaElement) return true;
  if (target instanceof HTMLInputElement) {
    return !['button', 'checkbox', 'radio', 'range', 'submit', 'reset', 'color', 'file'].includes(target.type);
  }
  return false;
}

/** Runs the top handler; returns true when something consumed the back action. */
export function dispatchBack(): boolean {
  for (let index = handlers.length - 1; index >= 0; index--) {
    if (handlers[index]()) return true;
  }
  return false;
}

export function useBackHandler(handler: () => boolean, enabled = true) {
  const latest = useRef(handler);
  latest.current = handler;
  useEffect(() => {
    if (!enabled) return;
    const entry: BackHandler = () => latest.current();
    handlers.push(entry);
    return () => {
      const index = handlers.lastIndexOf(entry);
      if (index >= 0) handlers.splice(index, 1);
    };
  }, [enabled]);
}

/** Global key listener; pages without a handler fall back to history navigation. */
export function installBackKeys(onUnhandled: () => void): () => void {
  const onKeyDown = (event: KeyboardEvent) => {
    if (!isBackKey(event)) return;
    event.preventDefault();
    if (!dispatchBack()) onUnhandled();
  };
  window.addEventListener('keydown', onKeyDown);
  return () => window.removeEventListener('keydown', onKeyDown);
}
