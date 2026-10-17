import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { App } from './App';
import { registerServiceWorker } from './pwa/registerServiceWorker';
import { StremioCore } from './services/stremio/core';
import { CoreProvider } from './services/stremio/CoreProvider';
import './styles/tokens.css';
import './styles/app.css';

const core = StremioCore.start();
// Development-only handle for inspecting the real core from the browser console.
if (import.meta.env.DEV) (window as unknown as { __mwCore: StremioCore }).__mwCore = core;

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <CoreProvider core={core}>
      <App />
    </CoreProvider>
  </StrictMode>,
);

registerServiceWorker();
