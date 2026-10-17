import { useEffect, useMemo, useRef, useState, type CSSProperties, type RefObject } from 'react';
import { FileIcon } from '../../components/icons';
import { Modal, OptionList } from '../../components/Modal';
import type { ProfileSettings } from '../../services/stremio/types';
import {
  activeCueText,
  clampStep,
  cssColor,
  formatDelay,
  groupSubtitles,
  languageName,
  meaningfulTrackName,
  SUBTITLE_DELAY,
  SUBTITLE_OFFSET,
  SUBTITLE_SIZE,
  type SubtitleOption,
} from './playerOptions';
import type { TrackOption } from './videoEngine';

const CUE_POLL_MS = 100;

/**
 * Draws the active subtitle cues above the video, so delay, size and position work the same for
 * embedded, addon and local subtitles (the Android players render their own subtitle view too).
 */
export function SubtitleOverlay({
  video,
  track,
  delayMs,
  sizePercent,
  offsetPercent,
  settings,
}: {
  video: RefObject<HTMLVideoElement>;
  track: () => TextTrack | null;
  delayMs: number;
  sizePercent: number;
  offsetPercent: number;
  settings: ProfileSettings | undefined;
}) {
  const [text, setText] = useState('');
  useEffect(() => {
    let last = '';
    const update = () => {
      const element = video.current;
      const next = element ? activeCueText(track()?.cues, element.currentTime, delayMs) : '';
      if (next !== last) {
        last = next;
        setText(next);
      }
    };
    update();
    const timer = window.setInterval(update, CUE_POLL_MS);
    return () => window.clearInterval(timer);
  }, [delayMs, track, video]);

  if (!text) return null;
  const outline = cssColor(settings?.subtitlesOutlineColor, '#000000');
  const style = {
    '--sub-size': sizePercent / 100,
    '--sub-offset': `${offsetPercent}%`,
    '--sub-color': cssColor(settings?.subtitlesTextColor, '#ffffff'),
    '--sub-background': cssColor(settings?.subtitlesBackgroundColor, 'transparent'),
    '--sub-outline': outline,
    '--sub-weight': settings?.subtitlesBold ? 700 : 500,
    opacity: Math.min(100, Math.max(10, settings?.subtitlesOpacity ?? 100)) / 100,
  } as CSSProperties;
  return (
    <div className="player__subtitles" style={style} aria-live="off">
      <span>{text}</span>
    </div>
  );
}

/** Languages → variants → settings, like the subtitle panel of the Android TV player. */
export function SubtitlesPanel({
  options,
  delayMs,
  sizePercent,
  offsetPercent,
  error,
  onSelect,
  onDelay,
  onSize,
  onOffset,
  onLocalFile,
  onClose,
}: {
  options: SubtitleOption[];
  delayMs: number;
  sizePercent: number;
  offsetPercent: number;
  error: string | null;
  onSelect: (option: SubtitleOption | null) => void;
  onDelay: (value: number) => void;
  onSize: (value: number) => void;
  onOffset: (value: number) => void;
  onLocalFile: (file: File) => void;
  onClose: () => void;
}) {
  const groups = useMemo(() => groupSubtitles(options), [options]);
  const selectedGroup = groups.find((group) => group.options.some((option) => option.selected))?.key ?? null;
  const [browsing, setBrowsing] = useState<string | null>(selectedGroup);
  const variantsGroup = groups.find((group) => group.key === (browsing ?? selectedGroup));
  const fileInput = useRef<HTMLInputElement>(null);

  return (
    <Modal title="Legendas" onClose={onClose} wide>
      <div className="subs-panel">
        <section className="subs-panel__col" aria-label="Idiomas">
          <h3 className="subs-panel__heading">Idiomas</h3>
          <div role="radiogroup" className="list">
            <button
              type="button"
              role="radio"
              className="option focusable"
              aria-checked={selectedGroup === null}
              onClick={() => {
                setBrowsing(null);
                onSelect(null);
              }}
            >
              Desativadas
            </button>
            {groups.map((group) => (
              <button
                key={group.key}
                type="button"
                role="radio"
                className="option focusable"
                aria-checked={group.key === selectedGroup}
                data-browsing={group.key === browsing || undefined}
                onClick={() => {
                  setBrowsing(group.key);
                  // Choosing a language turns on its first variant, as on the TV app.
                  if (group.key !== selectedGroup) onSelect(group.options[0]);
                }}
              >
                {group.label}
                <span className="option__meta">{group.options.length}</span>
              </button>
            ))}
          </div>
          {groups.length === 0 && <p className="subs-panel__hint">Nenhuma legenda encontrada para esta fonte.</p>}
        </section>

        <section className="subs-panel__col" aria-label="Variantes">
          <h3 className="subs-panel__heading">Variantes</h3>
          {variantsGroup ? (
            <div role="radiogroup" className="list">
              {variantsGroup.options.map((option) => (
                <button key={option.id} type="button" role="radio" className="option focusable" aria-checked={option.selected} onClick={() => onSelect(option)}>
                  {option.label}
                  {option.source === 'embedded' && option.label !== 'Do vídeo' && <span className="option__meta">no vídeo</span>}
                </button>
              ))}
            </div>
          ) : (
            <p className="subs-panel__hint">Desativadas</p>
          )}
        </section>

        <section className="subs-panel__col subs-panel__col--settings" aria-label="Ajustes">
          <h3 className="subs-panel__heading">Ajustes</h3>
          <Stepper label="Atraso" value={formatDelay(delayMs)} onStep={(direction) => onDelay(clampStep(delayMs, direction * SUBTITLE_DELAY.step, SUBTITLE_DELAY))} />
          <Stepper label="Tamanho" value={`${sizePercent}%`} onStep={(direction) => onSize(clampStep(sizePercent, direction * SUBTITLE_SIZE.step, SUBTITLE_SIZE))} />
          <Stepper label="Posição" value={`${offsetPercent}%`} onStep={(direction) => onOffset(clampStep(offsetPercent, direction * SUBTITLE_OFFSET.step, SUBTITLE_OFFSET))} />
          <button type="button" className="button focusable subs-panel__local" onClick={() => fileInput.current?.click()}>
            <FileIcon /> Abrir arquivo de legenda
          </button>
          <input
            ref={fileInput}
            type="file"
            accept=".srt,.vtt,text/vtt,application/x-subrip"
            hidden
            onChange={(event) => {
              const file = event.target.files?.[0];
              if (file) onLocalFile(file);
              event.target.value = '';
            }}
          />
        </section>
      </div>
      {error && <p className="subs-panel__error">{error}</p>}
      <button type="button" className="button button--primary focusable subs-panel__close" onClick={onClose}>
        Fechar
      </button>
    </Modal>
  );
}

function Stepper({ label, value, onStep }: { label: string; value: string; onStep: (direction: 1 | -1) => void }) {
  return (
    <div className="stepper" role="group" aria-label={label}>
      <span className="stepper__label">{label}</span>
      <div className="stepper__row">
        <button type="button" className="icon-button focusable" aria-label={`Diminuir ${label.toLowerCase()}`} onClick={() => onStep(-1)}>
          −
        </button>
        <output className="stepper__value" aria-live="polite">
          {value}
        </output>
        <button type="button" className="icon-button focusable" aria-label={`Aumentar ${label.toLowerCase()}`} onClick={() => onStep(1)}>
          +
        </button>
      </div>
    </div>
  );
}

export function AudioPanel({ tracks, onSelect, onClose }: { tracks: TrackOption[]; onSelect: (track: TrackOption) => void; onClose: () => void }) {
  const named = tracks.map((track, index) => {
    const name = track.language ? languageName(track.language) : null;
    const own = meaningfulTrackName(track.label, track.language);
    return { value: track.id, label: name && own ? `${name} · ${own}` : name ?? own ?? `Faixa ${index + 1}` };
  });
  // Same language twice (e.g. stereo and surround renditions): number them.
  const totals = new Map<string, number>();
  named.forEach((item) => totals.set(item.label, (totals.get(item.label) ?? 0) + 1));
  const seen = new Map<string, number>();
  const labelled = named.map((item) => {
    if ((totals.get(item.label) ?? 0) < 2) return item;
    const position = (seen.get(item.label) ?? 0) + 1;
    seen.set(item.label, position);
    return { ...item, label: `${item.label} ${position}` };
  });
  return (
    <Modal title="Áudio" onClose={onClose}>
      <OptionList
        options={labelled}
        selected={tracks.find((track) => track.selected)?.id ?? ''}
        onSelect={(id) => {
          const track = tracks.find((item) => item.id === id);
          if (track) onSelect(track);
        }}
      />
    </Modal>
  );
}
