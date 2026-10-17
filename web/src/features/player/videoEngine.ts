import type Hls from 'hls.js';
import { playbackFormat, type PlaybackFormat } from '../../services/stremio/streams';

export interface TrackOption {
  id: string;
  label: string;
  language: string | null;
  selected: boolean;
}

export type EngineError =
  | { kind: 'unsupported-format'; format: PlaybackFormat }
  | { kind: 'network' }
  | { kind: 'decode' }
  | { kind: 'source' };

/**
 * Owns one <video> element's source: native playback for MP4/WebM and Safari HLS, hls.js for HLS
 * elsewhere. `load` always tears down the previous source first and `destroy` releases everything,
 * so switching content never leaves two pipelines (or a dangling network download) behind.
 */
export class VideoEngine {
  private hls: Hls | null = null;
  private generation = 0;

  constructor(
    private readonly video: HTMLVideoElement,
    private readonly onError: (error: EngineError) => void,
    private readonly onTracksChanged: () => void = () => {},
  ) {}

  async load(url: string, startAtMs = 0): Promise<void> {
    this.teardown();
    const generation = ++this.generation;
    const format = playbackFormat(url);
    const seekOnReady = () => {
      if (startAtMs > 0 && Number.isFinite(this.video.duration)) this.video.currentTime = startAtMs / 1000;
    };
    this.video.addEventListener('loadedmetadata', seekOnReady, { once: true });

    // Recent Chromium also plays HLS natively, but without audio or subtitle tracks; only WebKit's
    // native player exposes them. Elsewhere hls.js drives HLS whenever the browser supports it.
    const nativeHls = this.video.canPlayType('application/vnd.apple.mpegurl') !== '';
    if (format === 'hls' && !(nativeHls && 'audioTracks' in this.video)) {
      const { default: HlsClass } = await import('hls.js');
      if (generation !== this.generation) return;
      if (!HlsClass.isSupported()) {
        if (nativeHls) {
          this.video.src = url;
          this.video.load();
        } else {
          this.onError({ kind: 'unsupported-format', format });
        }
        return;
      }
      const hls = new HlsClass({ enableWorker: true, backBufferLength: 60 });
      // Cues still load into the text track, but the player draws them (delay, size, position).
      hls.subtitleDisplay = false;
      this.hls = hls;
      hls.on(HlsClass.Events.MANIFEST_PARSED, () => this.onTracksChanged());
      hls.on(HlsClass.Events.AUDIO_TRACKS_UPDATED, () => this.onTracksChanged());
      hls.on(HlsClass.Events.SUBTITLE_TRACKS_UPDATED, () => this.onTracksChanged());
      hls.on(HlsClass.Events.ERROR, (_event, data) => {
        if (!data.fatal) return;
        if (data.type === HlsClass.ErrorTypes.MEDIA_ERROR) {
          // One controlled recovery attempt for decoder hiccups, then report.
          hls.recoverMediaError();
          hls.once(HlsClass.Events.ERROR, (_e, again) => again.fatal && this.onError({ kind: 'decode' }));
          return;
        }
        this.onError({ kind: data.type === HlsClass.ErrorTypes.NETWORK_ERROR ? 'network' : 'source' });
      });
      hls.loadSource(url);
      hls.attachMedia(this.video);
      return;
    }

    if (format === 'dash' && !this.video.canPlayType('application/dash+xml')) {
      this.onError({ kind: 'unsupported-format', format });
      return;
    }
    this.video.src = url;
    this.video.load();
  }

  /** Maps HTMLMediaElement errors to user-facing categories. */
  mediaError(): EngineError | null {
    const error = this.video.error;
    if (!error) return null;
    switch (error.code) {
      case MediaError.MEDIA_ERR_NETWORK:
        return { kind: 'network' };
      case MediaError.MEDIA_ERR_DECODE:
        return { kind: 'decode' };
      case MediaError.MEDIA_ERR_SRC_NOT_SUPPORTED:
        return { kind: 'unsupported-format', format: 'file' };
      default:
        return { kind: 'source' };
    }
  }

  audioTracks(): TrackOption[] {
    if (this.hls) {
      return this.hls.audioTracks.map((track, index) => ({
        id: String(index),
        label: track.name || track.lang || `Faixa ${index + 1}`,
        language: track.lang ?? null,
        selected: index === this.hls!.audioTrack,
      }));
    }
    // Safari exposes embedded audio tracks natively.
    const tracks = (this.video as HTMLVideoElement & { audioTracks?: { length: number; [index: number]: { id: string; label: string; language: string; enabled: boolean } } }).audioTracks;
    if (!tracks) return [];
    return Array.from({ length: tracks.length }, (_, index) => tracks[index]).map((track, index) => ({
      id: String(index),
      label: track.label || track.language || `Faixa ${index + 1}`,
      language: track.language || null,
      selected: track.enabled,
    }));
  }

  selectAudioTrack(id: string) {
    const index = Number(id);
    if (this.hls) {
      this.hls.audioTrack = index;
      return;
    }
    const tracks = (this.video as HTMLVideoElement & { audioTracks?: { length: number; [index: number]: { enabled: boolean } } }).audioTracks;
    if (!tracks) return;
    for (let i = 0; i < tracks.length; i++) tracks[i].enabled = i === index;
    this.onTracksChanged();
  }

  /** Subtitle tracks that come inside the stream (HLS renditions or native text tracks). */
  embeddedSubtitles(): TrackOption[] {
    if (this.hls) {
      return this.hls.subtitleTracks.map((track, index) => ({
        id: `hls:${index}`,
        label: track.name || track.lang || `Legenda ${index + 1}`,
        language: track.lang ?? null,
        selected: index === this.hls!.subtitleTrack,
      }));
    }
    return Array.from(this.video.textTracks)
      .map((track, index) => ({ track, index }))
      .filter(({ track }) => (track.kind === 'subtitles' || track.kind === 'captions') && track.label !== EXTERNAL_TRACK_LABEL)
      .map(({ track, index }) => ({
        id: `native:${index}`,
        label: track.label || track.language || `Legenda ${index + 1}`,
        language: track.language || null,
        selected: track.mode !== 'disabled',
      }));
  }

  /**
   * Enables one embedded subtitle track (or none). Tracks are `hidden`, not `showing`: their cues
   * load and the player overlay renders them.
   */
  selectEmbeddedSubtitle(id: string | null) {
    if (this.hls) {
      this.hls.subtitleTrack = id?.startsWith('hls:') ? Number(id.slice(4)) : -1;
    }
    Array.from(this.video.textTracks).forEach((track, index) => {
      if (track.label === EXTERNAL_TRACK_LABEL) return;
      if (this.hls && !id?.startsWith('native:')) return; // hls.js manages its own tracks.
      track.mode = id === `native:${index}` ? 'hidden' : 'disabled';
    });
  }

  /** Text track behind an embedded subtitle option, whose cues the overlay renders. */
  embeddedTrack(id: string): TextTrack | null {
    const tracks = Array.from(this.video.textTracks);
    if (id.startsWith('native:')) return tracks[Number(id.slice(7))] ?? null;
    if (!this.hls || !id.startsWith('hls:')) return null;
    const name = this.hls.subtitleTracks[Number(id.slice(4))]?.name;
    const enabled = tracks.filter((track) => track.label !== EXTERNAL_TRACK_LABEL && track.kind === 'subtitles' && track.mode !== 'disabled');
    return enabled.find((track) => track.label === name) ?? enabled[0] ?? null;
  }

  private teardown() {
    this.hls?.destroy();
    this.hls = null;
    this.video.removeAttribute('src');
    // Aborts the current download and resets the element.
    this.video.load();
  }

  destroy() {
    this.generation++;
    this.video.pause();
    this.teardown();
  }
}

/** Label of the <track> the player adds for addon (external) subtitles. */
export const EXTERNAL_TRACK_LABEL = 'mw-external';
