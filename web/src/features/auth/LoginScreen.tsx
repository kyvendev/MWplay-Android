import { useState, type FormEvent } from 'react';
import { Logo } from '../../components/Logo';
import { Spinner } from '../../components/States';
import { describeError, useCore, useCoreEvents } from '../../services/stremio/CoreProvider';

/**
 * Same sign-in as the apps: the core authenticates against the Stremio account API
 * (Ctx Authenticate / Login). The password is sent by the core over HTTPS and never stored;
 * the core persists only the resulting session in its storage, as on Android.
 */
export function LoginScreen() {
  const { core } = useCore();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useCoreEvents((event) => {
    if (!busy) return;
    if (event.event === 'Error') {
      setBusy(false);
      setError(loginErrorMessage(event.args?.error));
    } else if (event.event === 'UserAuthenticated') {
      setBusy(false);
    }
  });

  const submit = (event: FormEvent) => {
    event.preventDefault();
    if (!email.trim() || !password) return;
    setError(null);
    setBusy(true);
    core.login(email.trim(), password).catch((reason) => {
      setBusy(false);
      setError(describeError(reason));
    });
  };

  return (
    <main className="login">
      <form className="login__card" onSubmit={submit}>
        <Logo />
        <h1 className="login__title">MW Play</h1>
        <p className="login__subtitle">Entre com o e-mail e a senha fornecidos para acessar o MW Play.</p>
        <input
          className="field focusable"
          type="email"
          autoComplete="username"
          inputMode="email"
          placeholder="E-mail"
          aria-label="E-mail"
          value={email}
          onChange={(event) => setEmail(event.target.value)}
          autoFocus
        />
        <input
          className="field focusable"
          type="password"
          autoComplete="current-password"
          placeholder="Senha"
          aria-label="Senha"
          value={password}
          onChange={(event) => setPassword(event.target.value)}
        />
        {error && (
          <p className="settings__hint" role="alert" style={{ color: 'var(--warning)' }}>
            {error}
          </p>
        )}
        <button type="submit" className="button button--primary focusable" disabled={busy || !email.trim() || !password}>
          {busy ? <Spinner label="Entrando" /> : 'Entrar'}
        </button>
      </form>
    </main>
  );
}

function loginErrorMessage(error: unknown): string {
  const message = describeError(error).toLowerCase();
  if (message.includes('password') || message.includes('user') || message.includes('wrong') || message.includes('not found')) {
    return 'E-mail ou senha incorretos.';
  }
  if (message.includes('fetch') || message.includes('network')) {
    return 'Sem conexão com o serviço de contas. Verifique a internet e tente novamente.';
  }
  return `Não foi possível entrar: ${describeError(error)}`;
}
