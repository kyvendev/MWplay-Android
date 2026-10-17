import type { SVGProps } from 'react';

/** Material-style outline icons (24px grid), inlined so the app needs no icon font. */
const base = (path: string) =>
  function Icon(props: SVGProps<SVGSVGElement>) {
    return (
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={2} strokeLinecap="round" strokeLinejoin="round" aria-hidden="true" {...props}>
        <path d={path} />
      </svg>
    );
  };

export const HomeIcon = base('M3 10.5 12 3l9 7.5V20a1 1 0 0 1-1 1h-5v-6h-6v6H4a1 1 0 0 1-1-1z');
export const CompassIcon = base('M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18zm3.5-12.5-2 5-5 2 2-5z');
export const LibraryIcon = base('M4 6h12v14H4zM8 2h12v14M10 10l4 3-4 3z');
export const SettingsIcon = base('M12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6zm7.4-3a7.4 7.4 0 0 0-.1-1.2l2-1.6-2-3.4-2.4 1a7.6 7.6 0 0 0-2-1.2L14.5 3h-5l-.4 2.6a7.6 7.6 0 0 0-2 1.2l-2.4-1-2 3.4 2 1.6a7.4 7.4 0 0 0 0 2.4l-2 1.6 2 3.4 2.4-1a7.6 7.6 0 0 0 2 1.2l.4 2.6h5l.4-2.6a7.6 7.6 0 0 0 2-1.2l2.4 1 2-3.4-2-1.6c.1-.4.1-.8.1-1.2z');
export const SearchIcon = base('M11 18a7 7 0 1 0 0-14 7 7 0 0 0 0 14zm9 3-4.3-4.3');
export const BackIcon = base('M19 12H5m6-7-7 7 7 7');
export const PlayIcon = base('M7 4.5v15l13-7.5z');
export const PauseIcon = base('M7 4h3v16H7zm7 0h3v16h-3z');
export const PlusIcon = base('M12 5v14M5 12h14');
export const CheckIcon = base('M5 12.5 10 17l9-10');
export const ChevronRightIcon = base('M9 6l6 6-6 6');
export const RewindIcon = base('M11 18 4 12l7-6zm9 0-7-6 7-6z');
export const ForwardIcon = base('M13 6l7 6-7 6zM4 6l7 6-7 6z');
export const VolumeIcon = base('M4 9h4l5-4v14l-5-4H4zm12.5-1.5a6 6 0 0 1 0 9M19 5a9.5 9.5 0 0 1 0 14');
export const MuteIcon = base('M4 9h4l5-4v14l-5-4H4zm12 0 5 6m0-6-5 6');
export const FullscreenIcon = base('M4 9V4h5M15 4h5v5M20 15v5h-5M9 20H4v-5');
export const SubtitlesIcon = base('M3 5h18v14H3zM7 13h4m2 0h4M7 16h10');
export const AudioIcon = base('M9 18V5l12-2v13M9 18a3 3 0 1 1-6 0 3 3 0 0 1 6 0zm12-2a3 3 0 1 1-6 0 3 3 0 0 1 6 0z');
export const NextIcon = base('M5 5l9 7-9 7zM18 5v14');
export const LogoutIcon = base('M15 4h4v16h-4M10 8l-4 4 4 4M6 12h11');
