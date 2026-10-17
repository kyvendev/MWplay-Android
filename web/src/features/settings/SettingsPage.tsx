import type { UiPreference } from '../../app/uiMode';
import { LogoutIcon } from '../../components/icons';
import { useCore, useCoreState } from '../../services/stremio/CoreProvider';
import { APP_VERSION } from '../../shared/config';

const SUBTITLE_LANGUAGES: { value: string | null; label: string }[] = [
  { value: null, label: 'Nenhum' },
  { value: 'por', label: 'Português' },
  { value: 'eng', label: 'Inglês' },
  { value: 'spa', label: 'Espanhol' },
];

/**
 * Settings that exist on the apps and make sense in a browser. Profile settings are core settings
 * (same semantics and storage as Android: synced with the account), not a parallel web copy.
 */
export function SettingsPage({ uiPreference, onUiPreference }: { uiPreference: UiPreference; onUiPreference: (value: UiPreference) => void }) {
  const { core } = useCore();
  const ctx = useCoreState('ctx');
  const server = useCoreState('streaming_server');
  const settings = ctx?.profile.settings;
  const update = (patch: Partial<NonNullable<typeof settings>>) => {
    if (settings) core.updateSettings({ ...settings, ...patch });
  };

  const serverReady = server?.settings.type === 'Ready' && Boolean(server.baseUrl);

  return (
    <main className="page">
      <h1 className="page-title">Configurações</h1>
      <div className="settings">
        <section className="settings__card" aria-label="Conta">
          <div className="settings__row">
            <span>{ctx?.profile.auth?.user.email ?? 'Conta MW Play'}</span>
            <button type="button" className="button focusable" onClick={() => core.logout()}>
              <LogoutIcon /> Sair
            </button>
          </div>
        </section>

        <section className="settings__card" aria-label="Interface">
          <div className="settings__row">
            <label htmlFor="ui-mode">Modo da interface</label>
            <select id="ui-mode" className="select focusable" value={uiPreference} onChange={(event) => onUiPreference(event.target.value as UiPreference)}>
              <option value="auto">Automático</option>
              <option value="standard">Celular / computador</option>
              <option value="tv">TV (controle remoto)</option>
            </select>
          </div>
          <p className="settings__hint">O modo TV usa layout para telas grandes e navegação pelas setas do controle.</p>
        </section>

        {settings && (
          <section className="settings__card" aria-label="Legendas">
            <div className="settings__row">
              <label htmlFor="sub-lang">Idioma das legendas</label>
              <select
                id="sub-lang"
                className="select focusable"
                value={settings.subtitlesLanguage ?? ''}
                onChange={(event) => update({ subtitlesLanguage: event.target.value || null })}
              >
                {SUBTITLE_LANGUAGES.map((language) => (
                  <option key={language.label} value={language.value ?? ''}>
                    {language.label}
                  </option>
                ))}
              </select>
            </div>
            <div className="settings__row">
              <label htmlFor="sub-size">Tamanho das legendas</label>
              <select id="sub-size" className="select focusable" value={settings.subtitlesSize} onChange={(event) => update({ subtitlesSize: Number(event.target.value) })}>
                {[75, 100, 125, 150, 175, 200, 250].map((size) => (
                  <option key={size} value={size}>
                    {size}%
                  </option>
                ))}
              </select>
            </div>
          </section>
        )}

        <section className="settings__card" aria-label="Servidor de streaming">
          <div className="settings__row">
            <span>Servidor de streaming</span>
            <span style={{ color: serverReady ? '#7ee2a8' : 'var(--warning)', fontWeight: 700 }}>{serverReady ? 'Conectado' : 'Não encontrado'}</span>
          </div>
          <p className="settings__hint">
            {serverReady
              ? `Em uso: ${server?.baseUrl}. Fontes torrent ficam disponíveis.`
              : `Fontes HTTP funcionam direto no navegador. Fontes torrent precisam do servidor de streaming do Stremio rodando neste aparelho (${settings?.streamingServerUrl ?? 'http://127.0.0.1:11470/'}), como o Stremio Service no computador. No celular e na TV pelo navegador ele normalmente não está disponível; use o app MW Play para torrents.`}
          </p>
          <div>
            <button type="button" className="button focusable" onClick={() => core.reloadStreamingServer()}>
              Verificar novamente
            </button>
          </div>
        </section>

        <section className="settings__card" aria-label="Sobre">
          <div className="settings__row">
            <span>Versão do MW Play Web</span>
            <span>{APP_VERSION}</span>
          </div>
          <p className="settings__hint">Catálogos, biblioteca e progresso são sincronizados com a sua conta, como nos apps Android e Android TV.</p>
        </section>
      </div>
    </main>
  );
}
