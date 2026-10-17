/** Fired when a new version is installed and waiting; the UI offers a reload. */
export const UPDATE_EVENT = 'mwplay:update-ready';

let waiting: ServiceWorker | null = null;

export function registerServiceWorker() {
  if (!import.meta.env.PROD || !('serviceWorker' in navigator)) return;
  window.addEventListener('load', async () => {
    try {
      const registration = await navigator.serviceWorker.register('/sw.js');
      const announce = (worker: ServiceWorker | null) => {
        if (!worker || !navigator.serviceWorker.controller) return;
        waiting = worker;
        window.dispatchEvent(new Event(UPDATE_EVENT));
      };
      announce(registration.waiting);
      registration.addEventListener('updatefound', () => {
        const installing = registration.installing;
        installing?.addEventListener('statechange', () => installing.state === 'installed' && announce(installing));
      });
      // Long-lived sessions (TVs) still check for a new release every hour.
      window.setInterval(() => registration.update().catch(() => undefined), 60 * 60 * 1000);
    } catch {
      // The app works without the worker (no offline shell, no install on some browsers).
    }
  });
  let reloading = false;
  navigator.serviceWorker.addEventListener('controllerchange', () => {
    if (reloading) return;
    reloading = true;
    window.location.reload();
  });
}

export function applyUpdate() {
  waiting?.postMessage('SKIP_WAITING');
}
