# MW Play — Claude Code Instructions

## Project
MW Play is a customized Android media client based on the community `stremio-native/stremio-android` project.

Stack: Kotlin, Jetpack Compose, Gradle, Stremio Core, Media3/ExoPlayer, LibVLC and MPV.
Package: `com.mwplay.app`.
This repository is NOT the source of the current official Stremio Android app.

## Branches
- Mobile: `feat/mw-play-mobile`
- Android TV: `feat/mw-play-tv`
- Historical/shared: `feat/mw-play-native`

Before editing, confirm the current branch. Never put TV-specific work into `feat/mw-play-native`. Do not merge Mobile and TV blindly; they contain platform-specific changes.

## Working Rules
Before changing code: reproduce/verify the problem; inspect only relevant code; prefer `git diff`, `git log`, `rg` and targeted reads; avoid whole-repo scans unless necessary; make the smallest reasonable change; build/test; inspect the diff. Do not refactor unrelated code. Do not claim a fix without verification when feasible. Use subagents only when parallel investigation materially helps. Keep output concise.

## Large Files
Never replace a large file after reading only part of it. A previous partial replacement of `StremioMobileApp.kt` corrupted it and had to be reverted. Prefer minimal patches.

## Build
Repository uses submodules. If required: `git submodule update --init --recursive`.
Known config: compileSdk 37, targetSdk 37, minSdk 24. ABIs: arm64-v8a, armeabi-v7a, x86, x86_64.
Important dependencies: Media3 1.11.1, `org.videolan.android:libvlc-all:3.7.7`, `:mpv-android-lib`. LibVLC belongs in `app/build.gradle.kts`, NOT `settings.gradle.kts`.

## Authentication
Session validation uses `POST https://api.strem.io/api/getUser`. Treat HTTP 401/403 or HTTP 200 with `error.code == 1` as revoked. Network failures/unrelated errors must not automatically log out the user. Clear credentials/auth state only on revocation; preserve unrelated preferences and the distinction between credential-only clearing and full reset.

## Playback
Automatic fallback is `EXO -> VLC -> MPV`, with ExoPlayer default. Do not regress to `EXO -> MPV`. VLC is primarily in/around `VlcStreamPlayer.kt`, with options `--network-caching=1500`, `--clock-jitter=0`, `--clock-synchro=0`. VLC may still lack full parity for audio tracks, subtitles/external subtitles, aspect ratio/resize and advanced controls; verify current code first.

## Streams / Safety
Generic playback may support authorized HTTP(S), HLS/M3U8, DASH and direct media URLs. Do not implement DRM/paywall/auth bypass, stolen tokens/headers, provider-specific unauthorized access or unauthorized retransmission. Preserve required upstream license/copyright notices.

## Android TV
TV must be fully usable using only D-pad. Audit navigation, posters, details, stream picker, episodes, player controls, settings, search and dialogs/modals. Focus must be visibly identifiable.
Avoid duplicate focus nodes: clickable/buttons may already be focusable, so do not blindly add `.focusable()`. Preserve TV-specific focus handling such as `tvFocusTarget`.
Preserve collapsible navigation: menu opens, RIGHT can return to content where appropriate, closed menu does not trap focus, content regains focus. Make targeted fixes rather than rewriting the focus system.

## TV Performance
TV already has optimizations for poster loading, focus scaling, animations, image caching and catalog rendering. Do not reintroduce expensive focus animations. Potential remaining optimization: reduce/disable expensive glass/backdrop effects on TV, but inspect/profile before changing.

## Streaming Server
Inherited streaming server is required by parts of upstream architecture. Previous fixes covered lifecycle startup and native server packaging in release APKs. Do not remove them without investigation. Displayed status may historically desync from the real process; verify runtime state first.

## GitHub Actions
Mobile and TV have separate workflows. Debug APK artifacts should be separated by ABI. Mobile names: `mw-play-mobile-arm64-v8a-debug`, `mw-play-mobile-armeabi-v7a-debug`, `mw-play-mobile-x86-debug`, `mw-play-mobile-x86_64-debug`, `mw-play-mobile-universal-debug`. TV uses equivalent `mw-play-tv-*` names. Use CI for deterministic build/test/artifact work where practical.

## Releases
Known tested Mobile release: `v1.0.2`. Before another release, verify tag and `target_commitish` point to intended branch; there was previously an incorrect/ambiguous target involving `feat/mw-play-native`. Never expose/replace production signing secrets or keystores.

## Cast
Cast is future work unless current code proves otherwise. Preferred Mobile design: Google Cast for authorized remote URLs plus external `ACTION_VIEW`/chooser fallback. Never Cast/rebroadcast `127.0.0.1`, `localhost`, `::1`, `file://`, `content://` or `magnet:`. Do not create a phone proxy to retransmit local/torrent streams. Android TV is not automatically a Cast receiver.

## Branding
Visible product name: MW Play. Do not blindly replace every internal `Stremio` occurrence; some belong to APIs, protocols, namespaces, technical identifiers or legal notices. Only change intended UI branding.

## Separate Official-APK Experiment
Any white-label/reverse-engineering experiment involving an official Stremio APK is separate from this repository. Do not mix it into `MWplay-Android` or delete this project because of it.

## Current Priorities
Verify before treating as unresolved: Mobile/TV CI green; manual player selector supports VLC where appropriate; automatic `EXO -> VLC -> MPV` fallback; release branch/tag targeting; VLC parity; remaining TV D-pad/focus issues; TV glass/backdrop performance; incorrect streaming-server status if reproducible; authorized remote-URL Cast later. These are leads, not confirmed bugs. Current code and reproducible behavior are the source of truth.

## Task Completion
Normal flow: `investigate -> edit -> build/test -> inspect diff -> report`. Complete technical work yourself when possible instead of telling the user what to edit. Avoid destructive pushes unless appropriate. Report root cause, files changed, change made, build/test result and remaining limitations briefly.