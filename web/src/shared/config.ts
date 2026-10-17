/** MW Play release this web build belongs to (same version line as the Android apps). */
export const APP_VERSION = '1.2.0';

/** Default local streaming server, the same address the Android apps use (StremioCore.STREAMING_SERVER_BASE). */
export const DEFAULT_STREAMING_SERVER = 'http://127.0.0.1:11470/';

/** Network guard for UI-level waits on the core (the core itself owns addon request timeouts). */
export const CORE_WAIT_TIMEOUT_MS = 20_000;

/** How often playback progress is reported to the core (library progress and resume position). */
export const PROGRESS_REPORT_INTERVAL_MS = 5_000;
