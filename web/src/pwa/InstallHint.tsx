import { useEffect, useState } from 'react';
import { applyUpdate, UPDATE_EVENT } from './registerServiceWorker';

const DISMISS_KEY = 'mwplay.web.installHintDismissed';

interface BeforeInstallPromptEvent extends Event {
  prompt: () => Promise<void>;
}

function isStandalone(): boolean {
  return window.matchMedia('(display-mode: standalone)').matches || (navigator as Navigator & { standalone?: boolean }).standalone === true;
}

function isIosSafari(): boolean {
  const ua = navigator.userAgent;
  return /iP(hone|ad|od)/.test(ua) && /Safari/.test(ua) && !/CriOS|FxiOS|EdgiOS/.test(ua);
}

/**
 * Discreet install guidance. iPhone/iPad have no install prompt: Safari installs via Share → Add to
 * Home Screen, so we explain that. Chromium browsers get the native prompt when they offer it.
 * Also announces a new version when the service worker has one ready.
 */
export function InstallHint() {
  const [installEvent, setInstallEvent] = useState<BeforeInstallPromptEvent | null>(null);
  const [updateReady, setUpdateReady] = useState(false);
  const [dismissed, setDismissed] = useState(() => {
    try {
      return localStorage.getItem(DISMISS_KEY) === '1';
    } catch {
      return false;
    }
  });

  useEffect(() => {
    const onPrompt = (event: Event) => {
      event.preventDefault();
      setInstallEvent(event as BeforeInstallPromptEvent);
    };
    const onUpdate = () => setUpdateReady(true);
    window.addEventListener('beforeinstallprompt', onPrompt);
    window.addEventListener(UPDATE_EVENT, onUpdate);
    return () => {
      window.removeEventListener('beforeinstallprompt', onPrompt);
      window.removeEventListener(UPDATE_EVENT, onUpdate);
    };
  }, []);

  const dismiss = () => {
    setDismissed(true);
    try {
      localStorage.setItem(DISMISS_KEY, '1');
    } catch {
      // Session-only.
    }
  };

  if (updateReady) {
    return (
      <div className="install-hint" role="status">
        <p>Uma nova versão do MW Play está pronta.</p>
        <button type="button" className="button button--primary focusable" onClick={applyUpdate}>
          Atualizar
        </button>
      </div>
    );
  }
  if (dismissed || isStandalone() || document.documentElement.dataset.ui === 'tv') return null;
  if (isIosSafari()) {
    return (
      <div className="install-hint" role="note">
        <p>
          Para instalar no iPhone: toque em <strong>Compartilhar</strong> e depois em <strong>Adicionar à Tela de Início</strong>.
        </p>
        <button type="button" className="button focusable" onClick={dismiss}>
          OK
        </button>
      </div>
    );
  }
  if (installEvent) {
    return (
      <div className="install-hint" role="note">
        <p>Instale o MW Play para abrir como um app.</p>
        <button
          type="button"
          className="button button--primary focusable"
          onClick={() => {
            installEvent.prompt().finally(() => setInstallEvent(null));
          }}
        >
          Instalar
        </button>
        <button type="button" className="button focusable" onClick={dismiss}>
          Agora não
        </button>
      </div>
    );
  }
  return null;
}
