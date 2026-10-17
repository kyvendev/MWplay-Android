import { isTextInput } from '../../app/back';

/**
 * Arrow-key focus navigation for TV remotes and keyboards. It picks the nearest focusable element in
 * the pressed direction (geometry, like Android TV's focus search), keeps the page from scrolling
 * instead of moving focus, and stays out of the way of text fields and controls that use the arrows
 * themselves:
 *   - text inputs keep LEFT/RIGHT for the caret; UP/DOWN leave the field;
 *   - elements marked `data-nav-horizontal` (sliders, the player timeline) keep LEFT/RIGHT;
 *     UP/DOWN still navigate, so they never change a value (same rule as the Android TV fix).
 * The search is limited to the innermost open `[data-nav-scope]` (modals), so focus cannot escape.
 */
export type Direction = 'up' | 'down' | 'left' | 'right';

const KEY_DIRECTIONS: Record<string, Direction> = {
  ArrowUp: 'up',
  ArrowDown: 'down',
  ArrowLeft: 'left',
  ArrowRight: 'right',
  Up: 'up',
  Down: 'down',
  Left: 'left',
  Right: 'right',
};

const FOCUSABLE =
  'button:not([disabled]), a[href], input:not([disabled]):not([type="hidden"]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])';

export function directionForKey(event: KeyboardEvent): Direction | null {
  return KEY_DIRECTIONS[event.key] ?? null;
}

/** Whether the focused element should handle this arrow itself. */
export function ownsArrow(target: EventTarget | null, direction: Direction): boolean {
  if (!(target instanceof HTMLElement)) return false;
  const horizontal = direction === 'left' || direction === 'right';
  if (isTextInput(target)) return horizontal;
  if (horizontal && target.closest('[data-nav-horizontal]')) return true;
  if (target instanceof HTMLSelectElement) return true;
  return false;
}

function activeScope(): ParentNode {
  const scopes = document.querySelectorAll<HTMLElement>('[data-nav-scope]');
  return scopes.length ? scopes[scopes.length - 1] : document;
}

function isVisible(element: HTMLElement): boolean {
  const rect = element.getBoundingClientRect();
  if (rect.width === 0 && rect.height === 0) return false;
  const style = getComputedStyle(element);
  return style.visibility !== 'hidden' && style.display !== 'none';
}

interface Box {
  left: number;
  right: number;
  top: number;
  bottom: number;
}

/**
 * Distance score from `from` to `to` in `direction`, or null when `to` is not in that direction.
 * Overlap on the cross axis is strongly preferred so focus stays in the same row/column.
 */
export function directionalScore(from: Box, to: Box, direction: Direction): number | null {
  // Leaving a wide element (hero, full-width row) vertically lands on the row's first item, not
  // the one under its center, which is what TV users expect.
  const wide = from.right - from.left > 2 * (to.right - to.left);
  const fromCx = wide ? from.left + (to.right - to.left) / 2 : (from.left + from.right) / 2;
  const fromCy = (from.top + from.bottom) / 2;
  const toCx = (to.left + to.right) / 2;
  const toCy = (to.top + to.bottom) / 2;
  let primary: number;
  let cross: number;
  let overlap: boolean;
  switch (direction) {
    case 'right':
      if (to.left < from.right - 1 && toCx <= fromCx) return null;
      primary = Math.max(0, to.left - from.right);
      cross = Math.abs(toCy - fromCy);
      overlap = to.top < from.bottom && to.bottom > from.top;
      break;
    case 'left':
      if (to.right > from.left + 1 && toCx >= fromCx) return null;
      primary = Math.max(0, from.left - to.right);
      cross = Math.abs(toCy - fromCy);
      overlap = to.top < from.bottom && to.bottom > from.top;
      break;
    case 'down':
      if (to.top < from.bottom - 1 && toCy <= fromCy) return null;
      primary = Math.max(0, to.top - from.bottom);
      cross = Math.abs(toCx - fromCx);
      overlap = to.left < from.right && to.right > from.left;
      break;
    case 'up':
      if (to.bottom > from.top + 1 && toCy >= fromCy) return null;
      primary = Math.max(0, from.top - to.bottom);
      cross = Math.abs(toCx - fromCx);
      overlap = to.left < from.right && to.right > from.left;
      break;
  }
  // Overlapping candidates (same row/column) compete on distance alone, centers only break ties,
  // so a full-width timeline under a button is reached before a control further down.
  return overlap ? primary + cross * 0.05 : primary + cross * 2.5 + 1000;
}

export function findNext(from: HTMLElement | null, direction: Direction, scope: ParentNode = activeScope()): HTMLElement | null {
  const candidates = Array.from(scope.querySelectorAll<HTMLElement>(FOCUSABLE)).filter(
    (element) => element !== from && isVisible(element) && !element.closest('[inert]'),
  );
  if (!from || !document.contains(from) || (scope !== document && !(scope as Node).contains(from))) {
    return candidates[0] ?? null;
  }
  const origin = from.getBoundingClientRect();
  let best: HTMLElement | null = null;
  let bestScore = Infinity;
  for (const candidate of candidates) {
    const score = directionalScore(origin, candidate.getBoundingClientRect(), direction);
    if (score !== null && score < bestScore) {
      best = candidate;
      bestScore = score;
    }
  }
  return best;
}

export function focusElement(element: HTMLElement) {
  element.focus({ preventScroll: true });
  element.scrollIntoView({ block: 'nearest', inline: 'nearest' });
}

/**
 * @param isTv on TV every arrow moves focus. On touch/desktop the arrows only move focus once the
 * user is navigating by keyboard (something is focused); otherwise they keep scrolling the page.
 */
export function installSpatialNavigation(isTv: () => boolean): () => void {
  const onKeyDown = (event: KeyboardEvent) => {
    if (event.defaultPrevented || event.altKey || event.ctrlKey || event.metaKey) return;
    const direction = directionForKey(event);
    if (!direction) return;
    const current = document.activeElement instanceof HTMLElement && document.activeElement !== document.body ? document.activeElement : null;
    if (!current && !isTv()) return;
    if (ownsArrow(current, direction)) return;
    const next = findNext(current, direction);
    // Arrows never scroll the page when they are meant to move focus.
    event.preventDefault();
    if (next) focusElement(next);
  };
  window.addEventListener('keydown', onKeyDown);
  return () => window.removeEventListener('keydown', onKeyDown);
}
