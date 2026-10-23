import { useEffect, useState } from 'react';

/**
 * Hash routes (the core also receives location.hash with each action, as on stremio-web):
 *   #/                       Home
 *   #/discover               Discover
 *   #/tv                     TV channels
 *   #/library                Library
 *   #/search?q=...           Search
 *   #/detail/{type}/{id}     Details (optional ?video={videoId}; &resume=1 plays the last server)
 *   #/player                 Player (playback request kept in memory)
 *   #/settings               Settings
 */
export type Route =
  | { name: 'home' }
  | { name: 'discover' }
  | { name: 'tv' }
  | { name: 'library' }
  | { name: 'search'; query: string }
  | { name: 'detail'; type: string; id: string; videoId: string | null; resume?: boolean }
  | { name: 'player' }
  | { name: 'settings' };

export function parseRoute(hash: string): Route {
  const raw = hash.replace(/^#/, '') || '/';
  const [path, query = ''] = raw.split('?');
  const params = new URLSearchParams(query);
  const parts = path.split('/').filter(Boolean).map((part) => decodeURIComponent(part));
  switch (parts[0]) {
    case 'discover':
      return { name: 'discover' };
    case 'tv':
      return { name: 'tv' };
    case 'library':
      return { name: 'library' };
    case 'search':
      return { name: 'search', query: params.get('q') ?? '' };
    case 'detail':
      if (parts[1] && parts[2]) return { name: 'detail', type: parts[1], id: parts[2], videoId: params.get('video'), ...(params.get('resume') === '1' ? { resume: true } : {}) };
      return { name: 'home' };
    case 'player':
      return { name: 'player' };
    case 'settings':
      return { name: 'settings' };
    default:
      return { name: 'home' };
  }
}

export function routeHref(route: Route): string {
  switch (route.name) {
    case 'home':
      return '#/';
    case 'discover':
      return '#/discover';
    case 'tv':
      return '#/tv';
    case 'library':
      return '#/library';
    case 'search':
      return route.query ? `#/search?q=${encodeURIComponent(route.query)}` : '#/search';
    case 'detail': {
      const query = new URLSearchParams();
      if (route.videoId) query.set('video', route.videoId);
      if (route.resume) query.set('resume', '1');
      const search = query.toString();
      return `#/detail/${encodeURIComponent(route.type)}/${encodeURIComponent(route.id)}${search ? `?${search}` : ''}`;
    }
    case 'player':
      return '#/player';
    case 'settings':
      return '#/settings';
  }
}

export function navigate(route: Route, options: { replace?: boolean } = {}) {
  const href = routeHref(route);
  if (options.replace) window.location.replace(href);
  else window.location.hash = href.slice(1);
}

export function useRoute(): Route {
  const [route, setRoute] = useState(() => parseRoute(window.location.hash));
  useEffect(() => {
    const onChange = () => setRoute(parseRoute(window.location.hash));
    window.addEventListener('hashchange', onChange);
    return () => window.removeEventListener('hashchange', onChange);
  }, []);
  return route;
}
