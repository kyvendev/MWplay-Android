/**
 * Sending the video to a TV from the browser. A web page cannot start whole-screen mirroring
 * itself; what browsers do offer is handing the <video> element to a nearby screen:
 * - Remote Playback API (Chrome / Edge → Chromecast, Google TV);
 * - AirPlay (Safari on iPhone, iPad and Mac → Apple TV and AirPlay TVs).
 * When neither is available (or the browser refuses this stream) the player shows how to mirror
 * the whole screen with the device's own feature instead.
 */

type RemotePlaybackLike = {
  state?: 'disconnected' | 'connecting' | 'connected';
  prompt(): Promise<void>;
};

export type MirrorVideo = HTMLVideoElement & {
  remote?: RemotePlaybackLike;
  webkitShowPlaybackTargetPicker?: () => void;
};

export type MirrorResult = 'airplay' | 'remote' | 'cancelled' | 'instructions';

/** Opens the browser's own device picker, or reports that instructions should be shown. */
export async function startMirroring(video: MirrorVideo | null): Promise<MirrorResult> {
  if (!video) return 'instructions';
  if (typeof video.webkitShowPlaybackTargetPicker === 'function') {
    try {
      video.webkitShowPlaybackTargetPicker();
      return 'airplay';
    } catch {
      return 'instructions';
    }
  }
  if (video.remote && typeof video.remote.prompt === 'function') {
    try {
      await video.remote.prompt();
      return 'remote';
    } catch (error) {
      // The person closed the picker: nothing to explain.
      if ((error as { name?: string } | null)?.name === 'NotAllowedError') return 'cancelled';
      // NotSupportedError / NotFoundError: this stream or device cannot be sent; mirror instead.
      return 'instructions';
    }
  }
  return 'instructions';
}

export type MirrorPlatform = 'iphone' | 'android' | 'mac' | 'windows' | 'other';

export function detectMirrorPlatform(userAgent: string): MirrorPlatform {
  if (/iP(hone|ad|od)/.test(userAgent)) return 'iphone';
  if (/Android/i.test(userAgent)) return 'android';
  if (/Macintosh|Mac OS X/.test(userAgent)) return 'mac';
  if (/Windows/.test(userAgent)) return 'windows';
  return 'other';
}

export interface MirrorGuide {
  title: string;
  steps: string[];
}

const GUIDES: Record<Exclude<MirrorPlatform, 'other'>, MirrorGuide> = {
  android: {
    title: 'Celular Android',
    steps: [
      'Deslize de cima para baixo para abrir as Configurações rápidas.',
      'Toque em "Transmitir tela" (na Samsung, "Smart View"; em alguns aparelhos, "Espelhamento").',
      'Escolha a TV ou o Chromecast na mesma rede Wi-Fi.',
    ],
  },
  iphone: {
    title: 'iPhone ou iPad',
    steps: [
      'Abra a Central de Controle.',
      'Toque em "Espelhar Tela".',
      'Escolha a Apple TV ou a TV compatível com AirPlay na mesma rede Wi-Fi.',
    ],
  },
  windows: {
    title: 'Computador (Chrome ou Edge)',
    steps: [
      'Abra o menu ⋮ do navegador e escolha "Transmitir…" (no Edge: "Mais ferramentas" → "Transmitir mídia para o dispositivo").',
      'Em "Origens", escolha "Transmitir guia" ou "Transmitir tela".',
      'Selecione o Chromecast ou a TV na mesma rede Wi-Fi.',
    ],
  },
  mac: {
    title: 'Mac',
    steps: [
      'No Safari, use o botão AirPlay; ou abra a Central de Controle e escolha "Espelhar Tela".',
      'No Chrome, use o menu ⋮ → "Transmitir…" → "Transmitir guia".',
      'Escolha a TV na mesma rede Wi-Fi.',
    ],
  },
};

/** The current device's steps first, then the others for reference. */
export function mirrorGuides(platform: MirrorPlatform): MirrorGuide[] {
  const order: Exclude<MirrorPlatform, 'other'>[] = ['android', 'iphone', 'windows', 'mac'];
  const first = platform === 'other' ? [] : [platform];
  return [...first, ...order.filter((item) => item !== platform)].map((item) => GUIDES[item]);
}
