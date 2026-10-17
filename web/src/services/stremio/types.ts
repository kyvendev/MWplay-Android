/**
 * Shapes of the state stremio-core-web serializes for the UI (stremio-core-web/src/model/serialize_*.rs).
 * Only the fields the MW Play UI reads are typed; everything else is passed through untouched.
 */

/** Models exposed by the web runtime (WebModelField). */
export type CoreField =
  | 'ctx'
  | 'board'
  | 'discover'
  | 'library'
  | 'continue_watching_preview'
  | 'search'
  | 'meta_details'
  | 'streaming_server'
  | 'player';

export type Loadable<T, E = unknown> =
  | { type: 'Loading' }
  | { type: 'Ready'; content: T }
  | { type: 'Err'; content: E };

/** [name, value] pairs, as stremio-core serializes ExtraValue. */
export type ExtraValue = [string, string];

export interface ResourcePath {
  resource: string;
  type: string;
  id: string;
  extra: ExtraValue[];
}

export interface ResourceRequest {
  base: string;
  path: ResourcePath;
}

export type PosterShape = 'poster' | 'landscape' | 'square';

export interface MetaItemPreview {
  id: string;
  type: string;
  name: string;
  poster?: string | null;
  background?: string | null;
  logo?: string | null;
  description?: string | null;
  releaseInfo?: string | null;
  runtime?: string | null;
  released?: string | null;
  posterShape?: PosterShape;
  links?: { name: string; category: string; url: string }[];
  inLibrary?: boolean;
  watched?: boolean;
}

export interface Subtitles {
  id?: string;
  lang: string;
  url: string;
  label?: string | null;
}

export interface StreamBehaviorHints {
  notWebReady?: boolean;
  bingeGroup?: string;
  proxyHeaders?: { request?: Record<string, string>; response?: Record<string, string> };
  filename?: string;
  videoSize?: number;
}

/** A stream as addons describe it; exactly one source field is set (StreamSource is untagged). */
export interface Stream {
  url?: string;
  ytId?: string;
  infoHash?: string;
  fileIdx?: number | null;
  externalUrl?: string;
  playerFrameUrl?: string;
  name?: string;
  description?: string;
  thumbnail?: string;
  subtitles?: Subtitles[];
  behaviorHints?: StreamBehaviorHints;
  progress?: number | null;
  lastUsed?: boolean | null;
}

export interface DescriptorPreview {
  manifest: { id: string; name: string; logo?: string | null };
  transportUrl?: string;
}

export interface Video {
  id: string;
  title: string;
  released?: string | null;
  overview?: string | null;
  thumbnail?: string | null;
  season?: number;
  episode?: number;
  watched?: boolean;
  progress?: number | null;
  upcoming?: boolean;
}

export interface MetaItem extends MetaItemPreview {
  videos: Video[];
}

export interface CatalogResource {
  id: string;
  name: string;
  type: string;
  addon: DescriptorPreview;
  content: Loadable<MetaItemPreview[], string> | null;
  deepLinks: { discover: string };
}

export interface CatalogsWithExtra {
  selected: { type: string | null; extra: ExtraValue[] } | null;
  catalogs: CatalogResource[];
}

export interface SelectableDeepLinked {
  selected: boolean;
  deepLinks: { discover: string };
}

export interface Discover {
  selected: { request: ResourceRequest } | null;
  selectable: {
    types: (SelectableDeepLinked & { type: string })[];
    catalogs: (SelectableDeepLinked & { id: string; name: string; type: string; addon: DescriptorPreview })[];
    extra: { name: string; isRequired: boolean; options: (SelectableDeepLinked & { value: string | null })[] }[];
    nextPage: boolean;
  };
  catalog: { content: Loadable<MetaItemPreview[], string> | null } | null;
}

export interface LibraryItem {
  _id: string;
  name: string;
  type: string;
  poster?: string | null;
  posterShape?: PosterShape;
  progress: number;
  watched?: boolean;
  state?: { video_id?: string | null; videoId?: string | null };
}

export type LibrarySort = 'lastwatched' | 'name' | 'namereverse' | 'timeswatched' | 'watched' | 'notwatched';

export interface Library {
  selected: { request: { type: string | null; sort: LibrarySort; page: number } } | null;
  selectable: {
    types: { type: string | null; selected: boolean }[];
    sorts: { sort: LibrarySort; selected: boolean }[];
    nextPage: boolean;
  };
  catalog: LibraryItem[];
}

export interface ContinueWatchingPreview {
  items: (LibraryItem & { notifications: number })[];
}

export interface StreamsGroup {
  content: Loadable<Stream[], unknown>;
  addon: DescriptorPreview;
}

export interface MetaDetails {
  selected: { metaPath: ResourcePath; streamPath: ResourcePath | null; guessStream?: boolean } | null;
  metaItem: { content: Loadable<MetaItem, unknown>; addon: DescriptorPreview } | null;
  libraryItem: { _id: string } | null;
  streams: StreamsGroup[];
  title: string | null;
}

export interface PlayerSubtitles extends Subtitles {
  addonSubtitleId?: string;
  origin?: string;
}

export interface Player {
  selected: {
    stream: Stream;
    streamRequest: ResourceRequest | null;
    metaRequest: ResourceRequest | null;
    subtitlesPath: ResourcePath | null;
  } | null;
  /** The stream after core conversion; torrents become streaming-server URLs here. */
  stream: Loadable<Stream & { url?: string }, unknown> | null;
  metaItem: Loadable<MetaItem, unknown> | null;
  subtitles: PlayerSubtitles[];
  nextVideo: Video | null;
  seriesInfo: { season: number; episode: number } | null;
  libraryItem: { _id: string; state: { timeOffset: number; video_id: string | null } } | null;
  /** Per-title player choices the core keeps (and syncs) for this stream; null until something is saved. */
  streamState?: StreamState | null;
  title: string | null;
}

/** Same fields as `Player.StreamState` on Android; StreamStateChanged replaces the whole object. */
export interface StreamState {
  subtitleTrack?: { id: string; embedded: boolean; language: string | null } | null;
  subtitleDelay?: number | null;
  subtitleSize?: number | null;
  subtitleOffset?: number | null;
  audioTrack?: { id: string; language: string | null } | null;
  audioDelay?: number | null;
  playbackSpeed?: number | null;
  playerType?: string | null;
}

export interface ProfileSettings {
  streamingServerUrl: string;
  subtitlesLanguage: string | null;
  subtitlesSize: number;
  audioLanguage: string | null;
  subtitlesAutoSelect?: boolean;
  subtitlesOffset?: number;
  subtitlesTextColor?: string;
  subtitlesBackgroundColor?: string;
  subtitlesOutlineColor?: string;
  subtitlesBold?: boolean;
  subtitlesOpacity?: number;
  seekTimeDuration: number;
  bingeWatching: boolean;
  [key: string]: unknown;
}

export interface Ctx {
  profile: {
    auth: { key: string; user: { _id: string; email: string } } | null;
    addons: { manifest: { id: string; name: string; version?: string; description?: string; logo?: string | null }; transportUrl: string; flags?: { official?: boolean; protected?: boolean } }[];
    settings: ProfileSettings;
  };
}

export interface StreamingServer {
  selected: { transportUrl: string };
  settings: Loadable<Record<string, unknown>, unknown>;
  baseUrl: string | null;
}

export interface CoreEvent {
  event: string;
  args?: Record<string, unknown>;
}

export type RuntimeEvent =
  | { name: 'NewState'; args: CoreField[] }
  | { name: 'CoreEvent'; args: CoreEvent };

export interface StateByField {
  ctx: Ctx;
  board: CatalogsWithExtra;
  discover: Discover;
  library: Library;
  continue_watching_preview: ContinueWatchingPreview;
  search: CatalogsWithExtra;
  meta_details: MetaDetails;
  streaming_server: StreamingServer;
  player: Player;
}
