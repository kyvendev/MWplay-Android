import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { App } from './App';
import { registerServiceWorker } from './pwa/registerServiceWorker';
import { StremioCore } from './services/stremio/core';
import { CoreProvider } from './services/stremio/CoreProvider';
import { decideFirstRunDefaults } from './services/stremio/firstRunDefaults';
import { shouldShowIntro } from './features/intro/OrbitalIntro';
import './styles/tokens.css';
import './styles/app.css';

// Before the core starts: it writes its own storage keys, which mark an existing installation.
decideFirstRunDefaults();
const core = StremioCore.start();
// Development-only handle for inspecting the real core from the browser console.
if (import.meta.env.DEV) (window as unknown as { __mwCore: StremioCore }).__mwCore = core;

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <CoreProvider core={core}>
      <App showIntro={shouldShowIntro()} />
    </CoreProvider>
  </StrictMode>,
);

registerServiceWorker();
