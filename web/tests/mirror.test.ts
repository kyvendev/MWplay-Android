import { describe, expect, it, vi } from 'vitest';
import { androidIntentUrl, castOptions, detectMirrorPlatform, isCastableUrl, mirrorGuides, startMirroring, WEB_VIDEO_CASTER_PACKAGE, type MirrorVideo } from '../src/features/player/screenMirror';

const video = (extra: Partial<MirrorVideo>) => Object.assign(document.createElement('video'), extra) as MirrorVideo;
const error = (name: string) => Object.assign(new Error(name), { name });

describe('sending the video to a TV', () => {
  it('uses AirPlay on Safari', async () => {
    const picker = vi.fn();
    expect(await startMirroring(video({ webkitShowPlaybackTargetPicker: picker }))).toBe('airplay');
    expect(picker).toHaveBeenCalled();
  });

  it('uses the Remote Playback picker on Chrome and Edge', async () => {
    const prompt = vi.fn(() => Promise.resolve());
    expect(await startMirroring(video({ remote: { prompt } }))).toBe('remote');
  });

  it('does nothing more when the person closes the picker', async () => {
    const prompt = vi.fn(() => Promise.reject(error('NotAllowedError')));
    expect(await startMirroring(video({ remote: { prompt } }))).toBe('cancelled');
  });

  it('falls back to mirroring instructions when the stream or browser is not supported', async () => {
    const prompt = vi.fn(() => Promise.reject(error('NotSupportedError')));
    expect(await startMirroring(video({ remote: { prompt } }))).toBe('instructions');
    expect(await startMirroring(video({}))).toBe('instructions');
    expect(await startMirroring(null)).toBe('instructions');
  });
});

describe('mirroring instructions', () => {
  it('shows the current device first', () => {
    expect(detectMirrorPlatform('Mozilla/5.0 (Linux; Android 14; motorola edge 40 neo)')).toBe('android');
    expect(detectMirrorPlatform('Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X)')).toBe('iphone');
    expect(detectMirrorPlatform('Mozilla/5.0 (Windows NT 10.0; Win64; x64)')).toBe('windows');
    expect(mirrorGuides('iphone')[0].title).toBe('iPhone ou iPad');
    expect(mirrorGuides('other')).toHaveLength(4);
  });
});

describe('other apps', () => {
  it('only sends remote http(s) addresses to another app or device', () => {
    expect(isCastableUrl('https://cdn.example/video.m3u8')).toBe(true);
    expect(isCastableUrl('http://127.0.0.1:11470/abc/0')).toBe(false);
    expect(isCastableUrl('http://localhost:11470/abc')).toBe(false);
    expect(isCastableUrl('http://[::1]:11470/abc')).toBe(false);
    expect(isCastableUrl('magnet:?xt=urn:btih:abc')).toBe(false);
    expect(isCastableUrl('file:///sdcard/video.mp4')).toBe(false);
    expect(isCastableUrl(null)).toBe(false);
  });

  it('builds Android intents for Web Video Caster (with Play Store fallback) or any app', () => {
    const caster = androidIntentUrl('https://cdn.example/a/b.m3u8?x=1', 'Série — T1E2', WEB_VIDEO_CASTER_PACKAGE);
    expect(caster).toBe(
      'intent://cdn.example/a/b.m3u8?x=1#Intent;scheme=https;action=android.intent.action.VIEW;type=application/x-mpegURL;S.title=S%C3%A9rie%20%E2%80%94%20T1E2;package=com.instantbits.cast.webvideo;S.browser_fallback_url=https%3A%2F%2Fplay.google.com%2Fstore%2Fapps%2Fdetails%3Fid%3Dcom.instantbits.cast.webvideo;end',
    );
    expect(androidIntentUrl('http://cdn.example/v.mp4', 'Filme')).toBe('intent://cdn.example/v.mp4#Intent;scheme=http;action=android.intent.action.VIEW;type=video/*;S.title=Filme;end');
  });

  it('offers apps only on Android and never for sources that need their own headers', () => {
    const url = 'https://cdn.example/v.mp4';
    expect(castOptions(video({}), 'android', url, false)).toEqual({ picker: null, androidApps: true });
    expect(castOptions(video({}), 'windows', url, false)).toEqual({ picker: null, androidApps: false });
    expect(castOptions(video({}), 'android', url, true)).toEqual({ picker: null, androidApps: false });
    expect(castOptions(video({ remote: { prompt: vi.fn() } }), 'windows', 'http://127.0.0.1/x', false).picker).toBe('remote');
  });
});
