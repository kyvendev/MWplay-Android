import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { navigate } from '../../app/router';
import { useBackHandler } from '../../app/back';
import { AspectIcon, AudioIcon, BackIcon, CastIcon, ForwardIcon, FullscreenIcon, MuteIcon, NextIcon, PauseIcon, PlayIcon, RewindIcon, SpeedIcon, SubtitlesIcon, VolumeIcon } from '../../components/icons';
import { Spinner } from '../../components/States';
import { useCore, useCoreState } from '../../services/stremio/CoreProvider';
import { srtToVtt, streamKind } from '../../services/stremio/streams';
import type { Player, Stream, StreamState, Subtitles } from '../../services/stremio/types';
import { PROGRESS_REPORT_INTERVAL_MS } from '../../shared/config';
import { getPlaybackRequest, type PlaybackRequest } from './playbackRequest';
import { findNext, focusElement, type Direction } from '../tv/spatialNav';
import { AudioPanel, MirrorPanel, SubtitleOverlay, SubtitlesPanel } from './PlayerPanels';
import { detectMirrorPlatform, mirrorGuides, startMirroring, type MirrorVideo } from './screenMirror';
import {
  ASPECT_MODES,
  formatSpeed,
  meaningfulTrackName,
  nextSpeed,
  normalizeLanguage,
  pickAudioTrack,
  pickSubtitle,
  SPEEDS,
  type AspectMode,
  type SubtitleOption,
} from './playerOptions';
import { EXTERNAL_TRACK_LABEL, VideoEngine, type EngineError, type TrackOption } from './videoEngine';
import './player.css';

const SEEK_STEP_S = 10;
const CONTROLS_HIDE_MS = 3500;

export function PlayerPage() {
  const request = getPlaybackRequest();
  useEffect(() => {
    if (!request) navigate({ name: 'home' }, { replace: true });
  }, [request]);
  return request ? <PlayerView request={request} /> : null;
}

/** True when the core's Player model belongs to this request (it may still hold the previous one). */
function belongsTo(player: Player | null, stream: Stream): boolean {
  const selected = player?.selected?.stream;
  if (!selected) return false;
  return (
    selected.url === stream.url &&
    selected.infoHash === stream.infoHash &&
    (selected.fileIdx ?? null) === (stream.fileIdx ?? null) &&
    selected.ytId === stream.ytId
  );
}

function formatTime(seconds: number): string {
  if (!Number.isFinite(seconds) || seconds < 0) return '00:00';
  const total = Math.floor(seconds);
  const h = Math.floor(total / 3600);
  const m = Math.floor((total % 3600) / 60);
  const s = total % 60;
  const mm = String(m).padStart(2, '0');
  const ss = String(s).padStart(2, '0');
  return h > 0 ? `${h}:${mm}:${ss}` : `${mm}:${ss}`;
}

const isIOS = typeof navigator !== 'undefined' && /iP(hone|ad|od)/.test(navigator.userAgent);

function PlayerView({ request }: { request: PlaybackRequest }) {
  const { core } = useCore();
  const player = useCoreState('player');
  const container = useRef<HTMLDivElement>(null);
  const video = useRef<HTMLVideoElement>(null);
  const engine = useRef<VideoEngine | null>(null);
  const [paused, setPaused] = useState(true);
  const [buffering, setBuffering] = useState(true);
  const [started, setStarted] = useState(false);
  const [time, setTime] = useState(0);
  const [duration, setDuration] = useState(0);
  const [muted, setMuted] = useState(false);
  const [volume, setVolume] = useState(1);
  const [controls, setControls] = useState(true);
  const [error, setError] = useState<EngineError | null>(null);
  const [menu, setMenu] = useState<'audio' | 'subtitles' | 'mirror' | null>(null);
  const [tracksVersion, setTracksVersion] = useState(0);
  const [externalSubtitle, setExternalSubtitle] = useState<{ id: string; src: string } | null>(null);
  const [subtitleError, setSubtitleError] = useState<string | null>(null);
  const [ended, setEnded] = useState(false);
  const [speed, setSpeed] = useState(1);
  const [aspect, setAspect] = useState<AspectMode>('contain');
  const [subtitleId, setSubtitleId] = useState<string | null>(null);
  const [subtitleDelay, setSubtitleDelay] = useState(0);
  const [subtitleSizeChoice, setSubtitleSizeChoice] = useState<number | null>(null);
  const [subtitleOffsetChoice, setSubtitleOffsetChoice] = useState<number | null>(null);
  const [localSubtitles, setLocalSubtitles] = useState<{ id: string; name: string }[]>([]);
  const hideTimer = useRef<number>();
  const lastReport = useRef(0);
  const settings = useCoreState('ctx')?.profile.settings;

  const current = belongsTo(player, request.stream) ? player : null;
  const converted = current?.stream ?? null;
  const playableUrl = converted?.type === 'Ready' ? converted.content.url ?? null : null;
  const coreFailed = converted?.type === 'Err';

  // Per-title choices (tracks, subtitle delay/size/position, speed) live in the core's stream
  // state, as on Android. Read once when the model is ready; afterwards the local copy is the
  // source, since StreamStateChanged replaces the whole object.
  const streamState = useRef<StreamState | null>(null);
  useEffect(() => {
    if (!current || streamState.current) return;
    const saved = current.streamState ?? {};
    streamState.current = { ...saved };
    if (saved.playbackSpeed && (SPEEDS as readonly number[]).includes(saved.playbackSpeed)) setSpeed(saved.playbackSpeed);
    if (typeof saved.subtitleDelay === 'number') setSubtitleDelay(saved.subtitleDelay);
    if (typeof saved.subtitleSize === 'number') setSubtitleSizeChoice(saved.subtitleSize);
    if (typeof saved.subtitleOffset === 'number') setSubtitleOffsetChoice(saved.subtitleOffset);
  }, [current]);

  const saveStreamState = useCallback(
    (patch: Partial<StreamState>) => {
      if (!streamState.current) return;
      streamState.current = { ...streamState.current, ...patch };
      core.playerStreamStateChanged(streamState.current);
    },
    [core],
  );

  const subtitleSize = subtitleSizeChoice ?? settings?.subtitlesSize ?? 100;
  const subtitleOffset = subtitleOffsetChoice ?? settings?.subtitlesOffset ?? 5;

  // Hand the stream to the core (conversion + progress tracking); release it when leaving.
  useEffect(() => {
    core.loadPlayer(request.stream, request.streamRequest, request.metaRequest);
    return () => {
      core.unload('player');
    };
  }, [core, request]);

  // Exactly one engine per player screen; destroyed (download aborted) on exit.
  useEffect(() => {
    const element = video.current!;
    const instance = new VideoEngine(element, setError, () => setTracksVersion((value) => value + 1));
    engine.current = instance;
    return () => {
      instance.destroy();
      engine.current = null;
    };
  }, []);

  // Resume where the library says the user stopped (same source of truth as the apps).
  const resumeMs = useRef(0);
  resumeMs.current = useMemo(() => {
    const state = current?.libraryItem?.state;
    if (!state || state.timeOffset <= 0) return 0;
    const videoId = request.streamRequest?.path.id ?? null;
    return state.video_id === null || state.video_id === videoId ? state.timeOffset : 0;
  }, [current?.libraryItem, request.streamRequest]);

  useEffect(() => {
    if (!playableUrl || !engine.current) return;
    setError(null);
    setStarted(false);
    setBuffering(true);
    engine.current.load(playableUrl, resumeMs.current).then(() => {
      video.current?.play().catch(() => setPaused(true));
    });
  }, [playableUrl]);

  // defaultPlaybackRate survives the reset a new source causes.
  useEffect(() => {
    const element = video.current;
    if (!element) return;
    element.defaultPlaybackRate = speed;
    element.playbackRate = speed;
  }, [speed, playableUrl]);

  const showControls = useCallback(() => {
    setControls(true);
    window.clearTimeout(hideTimer.current);
    hideTimer.current = window.setTimeout(() => {
      if (!video.current?.paused) setControls(false);
    }, CONTROLS_HIDE_MS);
  }, []);

  useEffect(() => () => window.clearTimeout(hideTimer.current), []);

  // Hiding the controls drops focus to <body>; when they come back the remote resumes on the
  // last control it used (or play/pause) instead of nowhere.
  const lastFocused = useRef<HTMLElement | null>(null);
  const playButton = useRef<HTMLButtonElement>(null);
  useEffect(() => {
    if (!controls) return;
    const frame = requestAnimationFrame(() => {
      if (document.activeElement && document.activeElement !== document.body) return;
      const target = lastFocused.current?.isConnected ? lastFocused.current : playButton.current;
      target?.focus({ preventScroll: true });
    });
    return () => cancelAnimationFrame(frame);
  }, [controls]);

  const togglePlay = useCallback(() => {
    const element = video.current;
    if (!element) return;
    if (element.paused) element.play().catch(() => undefined);
    else element.pause();
    showControls();
  }, [showControls]);

  const seekTo = useCallback(
    (seconds: number) => {
      const element = video.current;
      if (!element || !Number.isFinite(element.duration)) return;
      const target = Math.min(Math.max(0, seconds), element.duration);
      element.currentTime = target;
      setTime(target);
      core.playerSeek(target * 1000, element.duration * 1000);
      showControls();
    },
    [core, showControls],
  );

  const close = useCallback(() => {
    const back = request.returnTo;
    navigate({ name: 'detail', type: back.type, id: back.id, videoId: back.videoId }, { replace: true });
  }, [request]);

  useBackHandler(() => {
    if (menu) {
      setMenu(null);
      return true;
    }
    close();
    return true;
  });

  // Remote/keyboard: hidden controls wake on the first key without acting (like the TV app);
  // media keys and Space always act.
  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      if (menu) return;
      const key = event.key;
      if (key === ' ' || key === 'MediaPlayPause' || key === 'k') {
        if ((event.target as HTMLElement)?.tagName === 'BUTTON' && key === ' ') return;
        event.preventDefault();
        togglePlay();
        return;
      }
      if (key === 'MediaPlay') return void video.current?.play();
      if (key === 'MediaPause') return void video.current?.pause();
      if (key === 'MediaFastForward') return void seekTo((video.current?.currentTime ?? 0) + SEEK_STEP_S);
      if (key === 'MediaRewind') return void seekTo((video.current?.currentTime ?? 0) - SEEK_STEP_S);
      if (!controls && (key.startsWith('Arrow') || key === 'Enter')) {
        event.preventDefault();
        event.stopPropagation();
        showControls();
        return;
      }
      showControls();
    };
    window.addEventListener('keydown', onKeyDown, true);
    return () => window.removeEventListener('keydown', onKeyDown, true);
  }, [controls, menu, seekTo, showControls, togglePlay]);

  // Video element events → UI state and core progress reports.
  useEffect(() => {
    const element = video.current!;
    const onTime = () => {
      setTime(element.currentTime);
      const now = Date.now();
      if (now - lastReport.current >= PROGRESS_REPORT_INTERVAL_MS && Number.isFinite(element.duration) && !element.paused) {
        lastReport.current = now;
        core.playerTimeChanged(element.currentTime * 1000, element.duration * 1000);
      }
    };
    const onPlay = () => {
      setPaused(false);
      core.playerPausedChanged(false);
      showControls();
    };
    const onPause = () => {
      setPaused(true);
      setControls(true);
      core.playerPausedChanged(true);
      if (Number.isFinite(element.duration)) core.playerTimeChanged(element.currentTime * 1000, element.duration * 1000);
    };
    const onWaiting = () => setBuffering(true);
    const onPlaying = () => {
      setBuffering(false);
      setStarted(true);
    };
    const onCanPlay = () => setBuffering(false);
    const onDuration = () => setDuration(Number.isFinite(element.duration) ? element.duration : 0);
    const onVolume = () => {
      setMuted(element.muted);
      setVolume(element.volume);
    };
    const onEnded = () => {
      setEnded(true);
      setControls(true);
      core.playerEnded();
    };
    const onError = () => {
      const mapped = engine.current?.mediaError();
      if (mapped) setError(mapped);
    };
    element.addEventListener('timeupdate', onTime);
    element.addEventListener('play', onPlay);
    element.addEventListener('pause', onPause);
    element.addEventListener('waiting', onWaiting);
    element.addEventListener('playing', onPlaying);
    element.addEventListener('canplay', onCanPlay);
    element.addEventListener('durationchange', onDuration);
    element.addEventListener('volumechange', onVolume);
    element.addEventListener('ended', onEnded);
    element.addEventListener('error', onError);
    element.textTracks.addEventListener?.('addtrack', () => setTracksVersion((value) => value + 1));
    return () => {
      element.removeEventListener('timeupdate', onTime);
      element.removeEventListener('play', onPlay);
      element.removeEventListener('pause', onPause);
      element.removeEventListener('waiting', onWaiting);
      element.removeEventListener('playing', onPlaying);
      element.removeEventListener('canplay', onCanPlay);
      element.removeEventListener('durationchange', onDuration);
      element.removeEventListener('volumechange', onVolume);
      element.removeEventListener('ended', onEnded);
      element.removeEventListener('error', onError);
    };
  }, [core, showControls]);

  // Browser media session: notification / lock screen / hardware media keys, where supported.
  useEffect(() => {
    if (!('mediaSession' in navigator)) return;
    const session = navigator.mediaSession;
    session.metadata = new MediaMetadata({
      title: request.title,
      artist: request.subtitle ?? 'MW Play',
      artwork: request.backdrop ? [{ src: request.backdrop }] : [],
    });
    const handlers: [MediaSessionAction, MediaSessionActionHandler][] = [
      ['play', () => void video.current?.play()],
      ['pause', () => video.current?.pause()],
      ['seekbackward', () => seekTo((video.current?.currentTime ?? 0) - SEEK_STEP_S)],
      ['seekforward', () => seekTo((video.current?.currentTime ?? 0) + SEEK_STEP_S)],
      ['seekto', (details) => details.seekTime !== undefined && seekTo(details.seekTime)],
    ];
    handlers.forEach(([action, handler]) => {
      try {
        session.setActionHandler(action, handler);
      } catch {
        // Action not supported by this browser.
      }
    });
    return () => {
      session.metadata = null;
      handlers.forEach(([action]) => {
        try {
          session.setActionHandler(action, null);
        } catch {
          // Ignore.
        }
      });
    };
  }, [request, seekTo]);

  // Addon subtitles: fetched, converted to WebVTT and attached as a blob track.
  const externalSubtitles: Subtitles[] = useMemo(() => {
    const fromAddons = current?.subtitles ?? [];
    const fromStream = request.stream.subtitles ?? [];
    const seen = new Set<string>();
    return [...fromStream, ...fromAddons].filter((item) => {
      const id = externalSubtitleId(item);
      if (!item.url || seen.has(item.url) || seen.has(id)) return false;
      seen.add(item.url).add(id);
      return true;
    });
  }, [current?.subtitles, request.stream.subtitles]);

  // Converted addon/local subtitles, kept for re-selection and released with the screen.
  const subtitleBlobs = useRef(new Map<string, string>());
  useEffect(() => {
    const blobs = subtitleBlobs.current;
    return () => {
      blobs.forEach((src) => URL.revokeObjectURL(src));
      blobs.clear();
    };
  }, []);

  // Text tracks are enabled as `hidden` (cues load, nothing drawn natively); the overlay renders.
  useEffect(() => {
    const element = video.current;
    if (!element) return;
    Array.from(element.textTracks).forEach((track) => {
      if (track.label === EXTERNAL_TRACK_LABEL) track.mode = externalSubtitle ? 'hidden' : 'disabled';
    });
  }, [externalSubtitle, tracksVersion]);

  const audioTracks: TrackOption[] = useMemo(() => engine.current?.audioTracks() ?? [], [tracksVersion, started]);
  const embeddedSubtitles: TrackOption[] = useMemo(() => engine.current?.embeddedSubtitles() ?? [], [tracksVersion, started]);

  // Addon variants are numbered per language instead of naming where they come from.
  const subtitleOptions: SubtitleOption[] = useMemo(() => {
    const perLanguage = new Map<string, number>();
    const variantLabel = (language: string) => {
      const key = normalizeLanguage(language) ?? 'und';
      const count = (perLanguage.get(key) ?? 0) + 1;
      perLanguage.set(key, count);
      return `Opção ${count}`;
    };
    return [
      ...embeddedSubtitles.map((track): SubtitleOption => ({
        id: track.id,
        source: 'embedded',
        language: track.language,
        label: meaningfulTrackName(track.label, track.language) ?? 'Do vídeo',
        selected: track.id === subtitleId,
      })),
      ...externalSubtitles.map((subtitle): SubtitleOption => {
        const id = externalSubtitleId(subtitle);
        return { id, source: 'addon', language: subtitle.lang, label: variantLabel(subtitle.lang), selected: id === subtitleId };
      }),
      ...localSubtitles.map((local): SubtitleOption => ({ id: local.id, source: 'local', language: null, label: local.name, selected: local.id === subtitleId })),
    ];
  }, [embeddedSubtitles, externalSubtitles, localSubtitles, subtitleId]);

  const subtitleRequest = useRef(0);
  const applySubtitle = useCallback(
    async (option: SubtitleOption | null, persist: boolean) => {
      const token = ++subtitleRequest.current;
      setSubtitleError(null);
      setSubtitleId(option?.id ?? null);
      if (persist) {
        saveStreamState({
          subtitleTrack: option && option.source !== 'local' ? { id: option.id, embedded: option.source === 'embedded', language: option.language } : null,
        });
      }
      if (!option || option.source === 'embedded') {
        setExternalSubtitle(null);
        engine.current?.selectEmbeddedSubtitle(option?.id ?? null);
        setTracksVersion((value) => value + 1);
        return;
      }
      engine.current?.selectEmbeddedSubtitle(null);
      let src = subtitleBlobs.current.get(option.id);
      if (!src) {
        const subtitle = externalSubtitles.find((item) => externalSubtitleId(item) === option.id);
        if (!subtitle) return;
        try {
          const response = await fetch(subtitle.url);
          if (!response.ok) throw new Error(String(response.status));
          src = URL.createObjectURL(new Blob([srtToVtt(await response.text())], { type: 'text/vtt' }));
          subtitleBlobs.current.set(option.id, src);
        } catch {
          if (token !== subtitleRequest.current) return;
          setSubtitleId(null);
          setExternalSubtitle(null);
          setSubtitleError('Não foi possível carregar esta legenda no navegador (o servidor pode bloquear o acesso).');
          return;
        }
      }
      if (token === subtitleRequest.current) setExternalSubtitle({ id: option.id, src });
    },
    [externalSubtitles, saveStreamState],
  );

  const openLocalSubtitle = async (file: File) => {
    try {
      const id = `local:${localSubtitles.length + 1}`;
      const src = URL.createObjectURL(new Blob([srtToVtt(await file.text())], { type: 'text/vtt' }));
      subtitleBlobs.current.set(id, src);
      autoSubtitleDone.current = true;
      setLocalSubtitles((list) => [...list, { id, name: file.name }]);
      subtitleRequest.current++;
      engine.current?.selectEmbeddedSubtitle(null);
      setSubtitleError(null);
      setSubtitleId(id);
      setExternalSubtitle({ id, src });
    } catch {
      setSubtitleError('Não foi possível abrir este arquivo de legenda.');
    }
  };

  // Automatic choice once playback started (embedded tracks are known by then): the subtitle saved
  // for this title, else the profile's preferred language. Retried as addon subtitles arrive, and
  // never after the user picked one.
  const autoSubtitleDone = useRef(false);
  useEffect(() => {
    if (autoSubtitleDone.current || !started || !streamState.current) return;
    const preferred = settings?.subtitlesAutoSelect === false ? null : settings?.subtitlesLanguage;
    const pick = pickSubtitle(subtitleOptions, streamState.current.subtitleTrack, preferred);
    if (!pick) return;
    autoSubtitleDone.current = true;
    applySubtitle(pick, false);
  }, [applySubtitle, settings, started, subtitleOptions]);

  const autoAudioDone = useRef(false);
  useEffect(() => {
    if (autoAudioDone.current || !started || !streamState.current || audioTracks.length < 2) return;
    autoAudioDone.current = true;
    const pick = pickAudioTrack(audioTracks, streamState.current.audioTrack, settings?.audioLanguage);
    if (pick && !pick.selected) {
      engine.current?.selectAudioTrack(pick.id);
      setTracksVersion((value) => value + 1);
    }
  }, [audioTracks, settings, started]);

  // The text track whose cues the overlay draws.
  const activeSubtitleTrack = useCallback((): TextTrack | null => {
    const element = video.current;
    if (!element || !subtitleId) return null;
    if (externalSubtitle && externalSubtitle.id === subtitleId) {
      return Array.from(element.textTracks).find((track) => track.label === EXTERNAL_TRACK_LABEL) ?? null;
    }
    return engine.current?.embeddedTrack(subtitleId) ?? null;
    // tracksVersion: hls.js swaps text tracks when the selection changes.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [externalSubtitle, subtitleId, tracksVersion]);

  // iPhone fullscreen is the native player, which only draws `showing` tracks.
  useEffect(() => {
    const element = video.current;
    if (!element) return;
    const begin = () => {
      const track = activeSubtitleTrack();
      if (track) track.mode = 'showing';
    };
    const end = () => {
      const track = activeSubtitleTrack();
      if (track) track.mode = 'hidden';
    };
    element.addEventListener('webkitbeginfullscreen', begin);
    element.addEventListener('webkitendfullscreen', end);
    return () => {
      element.removeEventListener('webkitbeginfullscreen', begin);
      element.removeEventListener('webkitendfullscreen', end);
    };
  }, [activeSubtitleTrack]);

  // Hands the video to a TV with the browser's own picker, or explains how to mirror the screen.
  const mirror = () => {
    showControls();
    startMirroring(video.current as MirrorVideo | null).then((result) => {
      if (result === 'instructions') setMenu('mirror');
    });
  };

  const enterFullscreen = () => {
    const element = video.current as (HTMLVideoElement & { webkitEnterFullscreen?: () => void }) | null;
    if (document.fullscreenElement) {
      document.exitFullscreen().catch(() => undefined);
      return;
    }
    if (container.current?.requestFullscreen) {
      container.current
        .requestFullscreen()
        .then(() => (screen.orientation as ScreenOrientation & { lock?: (o: string) => Promise<void> })?.lock?.('landscape').catch(() => undefined))
        .catch(() => element?.webkitEnterFullscreen?.());
    } else {
      element?.webkitEnterFullscreen?.();
    }
  };

  const errorMessage = describePlaybackError(coreFailed, error, request.stream);
  const showLoading = !errorMessage && (!started || (buffering && !paused));

  return (
    <div
      ref={container}
      className={`player ${controls ? 'player--controls' : ''}`}
      onMouseMove={showControls}
      onClick={(event) => {
        if (event.target === event.currentTarget || event.target === video.current) {
          if (controls && !paused) setControls(false);
          else showControls();
        }
      }}
    >
      <video ref={video} className="player__video" style={{ objectFit: aspect }} playsInline preload="auto">
        {externalSubtitle && <track key={externalSubtitle.src} kind="subtitles" label={EXTERNAL_TRACK_LABEL} src={externalSubtitle.src} />}
      </video>

      <SubtitleOverlay video={video} track={activeSubtitleTrack} delayMs={subtitleDelay} sizePercent={subtitleSize} offsetPercent={subtitleOffset} settings={settings} />

      {showLoading && (
        <div className="player__loading" aria-live="polite">
          {request.backdrop && <img src={request.backdrop} alt="" />}
          <div className="player__loading-text">
            <strong>{request.title}</strong>
            {request.subtitle && <span>{request.subtitle}</span>}
            <span className="player__loading-hint">
              <Spinner /> {started ? 'Carregando…' : 'Preparando a reprodução'}
            </span>
          </div>
        </div>
      )}

      {errorMessage && (
        <div className="player__error" role="alert">
          <p>{errorMessage}</p>
          <div className="state__actions">
            {!coreFailed && playableUrl && (
              <button type="button" className="button button--primary focusable" onClick={() => engine.current?.load(playableUrl, time * 1000).then(() => video.current?.play().catch(() => undefined))}>
                Tentar novamente
              </button>
            )}
            <button type="button" className="button focusable" onClick={close}>
              Escolher outra fonte
            </button>
          </div>
        </div>
      )}

      <div className="player__chrome" onFocusCapture={(event) => (lastFocused.current = event.target as HTMLElement)}>
        <div className="player__top">
          <button type="button" className="icon-button focusable" aria-label="Voltar" onClick={close}>
            <BackIcon />
          </button>
          <div className="player__title">
            <strong>{request.title}</strong>
            {request.subtitle && <span>{request.subtitle}</span>}
          </div>
        </div>

        <div className="player__center">
          <button type="button" className="player__round focusable" aria-label={`Voltar ${SEEK_STEP_S} segundos`} onClick={() => seekTo(time - SEEK_STEP_S)}>
            <RewindIcon />
          </button>
          <button type="button" className="player__round player__round--main focusable" aria-label={paused ? 'Reproduzir' : 'Pausar'} onClick={togglePlay} ref={playButton} autoFocus>
            {paused ? <PlayIcon /> : <PauseIcon />}
          </button>
          <button type="button" className="player__round focusable" aria-label={`Avançar ${SEEK_STEP_S} segundos`} onClick={() => seekTo(time + SEEK_STEP_S)}>
            <ForwardIcon />
          </button>
        </div>

        <div className="player__bottom">
          <div className="player__timeline">
            <span>{formatTime(time)}</span>
            <input
              type="range"
              className="player__range focusable"
              aria-label="Progresso do vídeo"
              aria-valuetext={`${formatTime(time)} de ${formatTime(duration)}`}
              min={0}
              max={duration || 0}
              step={1}
              value={Math.min(time, duration || 0)}
              disabled={!duration}
              data-nav-horizontal
              onKeyDown={(event) => {
                // LEFT/RIGHT seek by a fixed step; UP/DOWN are left to focus navigation.
                if (event.key === 'ArrowLeft' || event.key === 'ArrowRight') {
                  event.preventDefault();
                  seekTo(time + (event.key === 'ArrowRight' ? SEEK_STEP_S : -SEEK_STEP_S));
                } else if (event.key === 'ArrowUp' || event.key === 'ArrowDown') {
                  // Never a value change: move to the controls above/below instead.
                  event.preventDefault();
                  moveFocus(event.currentTarget, event.key === 'ArrowUp' ? 'up' : 'down');
                }
              }}
              onChange={(event) => seekTo(Number(event.target.value))}
            />
            <span>{formatTime(duration)}</span>
          </div>
          <div className="player__actions">
            <button type="button" className="icon-button focusable" aria-label={muted ? 'Ativar som' : 'Silenciar'} onClick={() => video.current && (video.current.muted = !video.current.muted)}>
              {muted ? <MuteIcon /> : <VolumeIcon />}
            </button>
            {!isIOS && (
              <input
                type="range"
                className="player__volume focusable"
                aria-label="Volume"
                min={0}
                max={1}
                step={0.05}
                value={muted ? 0 : volume}
                data-nav-horizontal
                onKeyDown={(event) => {
                  if (event.key === 'ArrowUp' || event.key === 'ArrowDown') {
                    event.preventDefault();
                    moveFocus(event.currentTarget, event.key === 'ArrowUp' ? 'up' : 'down');
                  }
                }}
                onChange={(event) => {
                  if (!video.current) return;
                  video.current.volume = Number(event.target.value);
                  video.current.muted = Number(event.target.value) === 0;
                }}
              />
            )}
            <button
              type="button"
              className="player__text-button focusable"
              aria-label={`Velocidade ${formatSpeed(speed)}`}
              onClick={() => {
                const value = nextSpeed(speed);
                setSpeed(value);
                saveStreamState({ playbackSpeed: value });
                showControls();
              }}
            >
              <SpeedIcon /> {formatSpeed(speed)}
            </button>
            <span className="top-bar__spacer" />
            {/* Always available, like the apps: local files and subtitle settings need no tracks. */}
            <button type="button" className="icon-button focusable" aria-label="Legendas" onClick={() => setMenu('subtitles')}>
              <SubtitlesIcon />
            </button>
            {audioTracks.length > 0 && (
              <button type="button" className="icon-button focusable" aria-label="Áudio" onClick={() => setMenu('audio')}>
                <AudioIcon />
              </button>
            )}
            {current?.nextVideo && (
              <button
                type="button"
                className="icon-button focusable"
                aria-label="Próximo episódio"
                onClick={() => {
                  core.playerNextVideo();
                  navigate({ name: 'detail', type: request.returnTo.type, id: request.returnTo.id, videoId: current.nextVideo!.id }, { replace: true });
                }}
              >
                <NextIcon />
              </button>
            )}
            <button
              type="button"
              className="player__text-button focusable"
              aria-label={`Proporção: ${ASPECT_MODES.find((item) => item.mode === aspect)!.label}`}
              onClick={() => {
                const index = ASPECT_MODES.findIndex((item) => item.mode === aspect);
                setAspect(ASPECT_MODES[(index + 1) % ASPECT_MODES.length].mode);
                showControls();
              }}
            >
              <AspectIcon /> {ASPECT_MODES.find((item) => item.mode === aspect)!.label}
            </button>
            <button type="button" className="icon-button focusable" aria-label="Transmitir ou espelhar na TV" title="Transmitir ou espelhar na TV" onClick={mirror}>
              <CastIcon />
            </button>
            <button type="button" className="icon-button focusable" aria-label="Tela cheia" onClick={enterFullscreen}>
              <FullscreenIcon />
            </button>
          </div>
        </div>
      </div>

      {ended && current?.nextVideo && (
        <div className="player__next">
          <button
            type="button"
            className="button button--primary focusable"
            onClick={() => navigate({ name: 'detail', type: request.returnTo.type, id: request.returnTo.id, videoId: current.nextVideo!.id }, { replace: true })}
          >
            <NextIcon /> Próximo episódio
          </button>
        </div>
      )}

      {menu === 'mirror' && <MirrorPanel guides={mirrorGuides(detectMirrorPlatform(navigator.userAgent))} onClose={() => setMenu(null)} />}

      {menu === 'audio' && (
        <AudioPanel
          tracks={audioTracks}
          onClose={() => setMenu(null)}
          onSelect={(track) => {
            autoAudioDone.current = true;
            engine.current?.selectAudioTrack(track.id);
            saveStreamState({ audioTrack: { id: track.id, language: track.language } });
            setTracksVersion((value) => value + 1);
            setMenu(null);
          }}
        />
      )}

      {menu === 'subtitles' && (
        <SubtitlesPanel
          options={subtitleOptions}
          delayMs={subtitleDelay}
          sizePercent={subtitleSize}
          offsetPercent={subtitleOffset}
          error={subtitleError}
          onClose={() => setMenu(null)}
          onSelect={(option) => {
            autoSubtitleDone.current = true;
            applySubtitle(option, true);
          }}
          onDelay={(value) => {
            setSubtitleDelay(value);
            saveStreamState({ subtitleDelay: value });
          }}
          onSize={(value) => {
            setSubtitleSizeChoice(value);
            saveStreamState({ subtitleSize: value });
          }}
          onOffset={(value) => {
            setSubtitleOffsetChoice(value);
            saveStreamState({ subtitleOffset: value });
          }}
          onLocalFile={openLocalSubtitle}
        />
      )}
      {subtitleError && !menu && <div className="player__toast">{subtitleError}</div>}
    </div>
  );
}

export function describePlaybackError(coreFailed: boolean, error: EngineError | null, stream: Stream): string | null {
  if (coreFailed) {
    const kind = streamKind(stream);
    return kind === 'torrent' || kind === 'youtube'
      ? 'Esta fonte só funciona no app MW Play. Escolha outra fonte.'
      : 'Não foi possível preparar esta fonte para reprodução.';
  }
  if (!error) return null;
  const kind = streamKind(stream);
  // The core still produces a streaming-server URL when no server runs; the failure shows up here.
  if (error.kind === 'network' && (kind === 'torrent' || kind === 'youtube')) {
    return 'Esta fonte só funciona no app MW Play. Escolha outra fonte.';
  }
  switch (error.kind) {
    case 'unsupported-format':
      return error.format === 'dash'
        ? 'Fluxos DASH não são suportados neste navegador. Escolha outra fonte.'
        : 'Este navegador não reproduz o formato deste vídeo (comum em MKV, HEVC/H.265 ou áudio AC3/DTS). Escolha outra fonte.';
    case 'network':
      return 'Falha de rede ao carregar o vídeo. A fonte pode estar fora do ar ou bloquear a reprodução pelo navegador.';
    case 'decode':
      return 'O navegador não conseguiu decodificar este vídeo. Tente novamente ou escolha outra fonte.';
    case 'source':
      return 'A fonte não respondeu como esperado. Tente novamente ou escolha outra fonte.';
  }
}

/** Stored in the stream state, like the subtitle id the apps save. */
function externalSubtitleId(subtitle: Subtitles): string {
  return `ext:${subtitle.id ?? subtitle.url}`;
}

function moveFocus(from: HTMLElement, direction: Direction) {
  const next = findNext(from, direction);
  if (next) focusElement(next);
}
