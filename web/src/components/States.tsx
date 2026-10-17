import type { ReactNode } from 'react';

export function Spinner({ label = 'Carregando' }: { label?: string }) {
  return <div className="spinner" role="status" aria-label={label} />;
}

export function LoadingState({ label = 'Carregando…' }: { label?: string }) {
  return (
    <div className="state" role="status" style={{ display: 'flex', alignItems: 'center', gap: '1rem' }}>
      <Spinner label={label} />
      <span>{label}</span>
    </div>
  );
}

export function EmptyState({ children }: { children: ReactNode }) {
  return <div className="state">{children}</div>;
}

export function ErrorState({ message, onRetry, children }: { message: string; onRetry?: () => void; children?: ReactNode }) {
  return (
    <div className="state state--error" role="alert">
      <div>{message}</div>
      {(onRetry || children) && (
        <div className="state__actions">
          {onRetry && (
            <button type="button" className="button" onClick={onRetry}>
              Tentar novamente
            </button>
          )}
          {children}
        </div>
      )}
    </div>
  );
}
