/* MW Play service worker. Generated at build time: the version and precache list are filled in by vite.config.ts. */
const VERSION = '__VERSION__';
const CACHE = `mwplay-shell-${VERSION}`;
const PRECACHE = __PRECACHE__;

// Only the app shell (HTML, JS, CSS, WebAssembly core, icons) is cached. Catalog, account,
// addon, image and stream requests go to other origins and are never intercepted or stored.
self.addEventListener('install', (event) => {
  event.waitUntil(caches.open(CACHE).then((cache) => cache.addAll(PRECACHE)));
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches
      .keys()
      .then((keys) => Promise.all(keys.filter((key) => key.startsWith('mwplay-shell-') && key !== CACHE).map((key) => caches.delete(key))))
      .then(() => self.clients.claim()),
  );
});

// The page asks the waiting worker to take over when the user accepts an update.
self.addEventListener('message', (event) => {
  if (event.data === 'SKIP_WAITING') self.skipWaiting();
});

self.addEventListener('fetch', (event) => {
  const { request } = event;
  if (request.method !== 'GET') return;
  const url = new URL(request.url);
  if (url.origin !== self.location.origin) return;

  if (request.mode === 'navigate') {
    // Network first so a new release is picked up immediately; cached shell when offline.
    event.respondWith(
      fetch(request).catch(() => caches.match('/index.html', { cacheName: CACHE }).then((cached) => cached || Response.error())),
    );
    return;
  }

  if (PRECACHE.includes(url.pathname)) {
    // Hashed, immutable build assets.
    event.respondWith(caches.match(request, { cacheName: CACHE }).then((cached) => cached || fetch(request)));
  }
});
