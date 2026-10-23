import { useEffect, useRef, useState, type ReactNode } from 'react';
import { installBackKeys } from './app/back';
import { useRoute } from './app/router';
import { useUiMode } from './app/uiMode';
import { Nav } from './components/Nav';
import { ErrorState, LoadingState } from './components/States';
import { LoginScreen } from './features/auth/LoginScreen';
import { DetailsPage } from './features/catalog/DetailsPage';
import { OrbitalIntro } from './features/intro/OrbitalIntro';
import { DiscoverPage, TvPage } from './features/catalog/DiscoverPage';
import { HomePage } from './features/home/HomePage';
import { LibraryPage } from './features/library/LibraryPage';
import { PlayerPage } from './features/player/PlayerPage';
import { SearchPage } from './features/search/SearchPage';
import { SettingsPage } from './features/settings/SettingsPage';
import { installSpatialNavigation } from './features/tv/spatialNav';
import { InstallHint } from './pwa/InstallHint';
import { useCore, useCoreState } from './services/stremio/CoreProvider';
import { useFirstRunDefaults } from './services/stremio/firstRunDefaults';
import { useRemoteSessionGuard } from './services/stremio/sessionGuard';

export function App({ showIntro = false }: { showIntro?: boolean } = {}) {
  const { core, status } = useCore();
  const route = useRoute();
  const ui = useUiMode();
  const ctx = useCoreState('ctx');
  // Sessions ended elsewhere (Stremio website) sign this browser out too, like the apps.
  useRemoteSessionGuard();
  // Fresh installation: Portuguese defaults once, on the first signed-in profile.
  useFirstRunDefaults(core, ctx?.profile.settings, Boolean(ctx?.profile.auth));
  const [introVisible, setIntroVisible] = useState(showIntro);
  const modeRef = useRef(ui.mode);
  modeRef.current = ui.mode;

  useEffect(() => installSpatialNavigation(() => modeRef.current === 'tv'), []);
  useEffect(
    () =>
      installBackKeys(() => {
        if (window.location.hash && window.location.hash !== '#/') window.history.back();
      }),
    [],
  );

  // Each screen starts at the top and, on TV, with focus on its first control.
  useEffect(() => {
    window.scrollTo(0, 0);
    if (modeRef.current === 'tv') {
      requestAnimationFrame(() => {
        const first = document.querySelector<HTMLElement>('main button, main a[href], main input');
        if (first && document.activeElement === document.body) first.focus({ preventScroll: true });
      });
    }
  }, [route]);

  // Opening animation over whatever screen is starting underneath (it never delays it).
  const withIntro = (content: ReactNode) => (
    <>
      {content}
      {introVisible && <OrbitalIntro ready={status.kind === 'failed' || Boolean(ctx)} onFinished={() => setIntroVisible(false)} />}
    </>
  );

  if (status.kind === 'failed') {
    return withIntro(
      <main className="login">
        <ErrorState message={`O MW Play não conseguiu iniciar neste navegador (${status.message}). Verifique se o navegador está atualizado.`} onRetry={() => window.location.reload()} />
      </main>
    );
  }
  if (status.kind === 'starting' || !ctx) {
    return withIntro(
      <main className="login">
        <LoadingState label="Iniciando o MW Play…" />
      </main>
    );
  }
  // Same gate as the apps: catalogs open after signing in.
  if (!ctx.profile.auth) return withIntro(<LoginScreen />);

  if (route.name === 'player') return withIntro(<PlayerPage />);

  return withIntro(
    <div className="app-shell">
      <Nav current={route.name === 'detail' || route.name === 'search' ? 'home' : route.name} />
      {route.name === 'home' && <HomePage />}
      {route.name === 'discover' && <DiscoverPage />}
      {route.name === 'tv' && <TvPage />}
      {route.name === 'library' && <LibraryPage />}
      {route.name === 'search' && <SearchPage initialQuery={route.query} />}
      {route.name === 'detail' && <DetailsPage key={`${route.type}:${route.id}`} type={route.type} id={route.id} videoId={route.videoId} resume={route.resume ?? false} />}
      {route.name === 'settings' && <SettingsPage uiPreference={ui.preference} onUiPreference={ui.setPreference} />}
      <InstallHint />
    </div>
  );
}
