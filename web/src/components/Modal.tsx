import { useEffect, useRef, type ReactNode } from 'react';
import { useBackHandler } from '../app/back';

/**
 * Bottom sheet on phones, centered dialog elsewhere. It traps arrow navigation (data-nav-scope),
 * focuses its first control, closes with back/Escape or a tap outside, and restores focus.
 */
export function Modal({ title, onClose, children }: { title: string; onClose: () => void; children: ReactNode }) {
  const panel = useRef<HTMLDivElement>(null);
  useBackHandler(() => {
    onClose();
    return true;
  });
  useEffect(() => {
    const previous = document.activeElement as HTMLElement | null;
    const first = panel.current?.querySelector<HTMLElement>('[aria-checked="true"], button, [href], input, select');
    first?.focus({ preventScroll: true });
    return () => previous?.focus({ preventScroll: true });
  }, []);
  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div
        ref={panel}
        className="modal"
        role="dialog"
        aria-modal="true"
        aria-label={title}
        data-nav-scope
        onClick={(event) => event.stopPropagation()}
      >
        <h2 className="modal__title">{title}</h2>
        {children}
      </div>
    </div>
  );
}

export function OptionList<T>({
  options,
  selected,
  onSelect,
}: {
  options: { value: T; label: string }[];
  selected: T;
  onSelect: (value: T) => void;
}) {
  return (
    <div role="radiogroup" className="list">
      {options.map((option, index) => (
        <button
          key={index}
          type="button"
          role="radio"
          className="option focusable"
          aria-checked={option.value === selected}
          onClick={() => onSelect(option.value)}
        >
          {option.label}
        </button>
      ))}
    </div>
  );
}
