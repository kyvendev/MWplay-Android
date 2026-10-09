#!/usr/bin/env bash
# Builds libstream_server.so for one Android ABI from the versioned stream-server submodule.
# Used unchanged by GitHub Actions and by the Gradle `buildStreamServer*` tasks, so local and CI
# builds share the same toolchain versions, vcpkg triplets, overlays and cargo flags.
#
# Usage: native/build-stream-server.sh <armeabi-v7a|arm64-v8a|x86|x86_64>
#
# Required environment:
#   VCPKG_ROOT        vcpkg checkout at VCPKG_COMMIT, already bootstrapped.
# Optional environment:
#   ANDROID_NDK_HOME  NDK ANDROID_NDK_VERSION (default: $ANDROID_HOME/ndk/$ANDROID_NDK_VERSION).
#   STREAM_SERVER_VCPKG_INSTALLED_DIR  vcpkg install root for this ABI
#                     (default: build/stream-server/vcpkg_installed/<abi>).
#   STREAM_SERVER_OUTPUT_DIR  receives <abi>/libstream_server.so (default: app/src/main/jniLibs).
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# shellcheck source=stream-server-toolchain.env
source "$repo_root/native/stream-server-toolchain.env"

abi="${1:-}"
case "$abi" in
  armeabi-v7a) rust_target=armv7-linux-androideabi; triplet=arm-android ;;
  arm64-v8a)   rust_target=aarch64-linux-android;   triplet=arm64-android ;;
  x86)         rust_target=i686-linux-android;      triplet=x86-android ;;
  x86_64)      rust_target=x86_64-linux-android;    triplet=x64-android ;;
  *) echo "usage: $0 <armeabi-v7a|arm64-v8a|x86|x86_64>" >&2; exit 2 ;;
esac

fail() { echo "error: $*" >&2; exit 1; }

server_root="$repo_root/stream-server"
[[ -f "$server_root/server/Cargo.toml" ]] ||
  fail "stream-server submodule missing; run: git submodule update --init stream-server"

# --- Toolchain checks: refuse to build with versions other than the pinned ones. ---
export RUSTUP_TOOLCHAIN="$RUST_TOOLCHAIN"
command -v rustup >/dev/null || fail "rustup not found (install Rust via https://rustup.rs)"
rustup toolchain list | grep "^$RUST_TOOLCHAIN" >/dev/null ||
  fail "Rust $RUST_TOOLCHAIN not installed; run: rustup toolchain install $RUST_TOOLCHAIN --profile minimal"
rustup target list --installed --toolchain "$RUST_TOOLCHAIN" | grep -x "$rust_target" >/dev/null ||
  fail "Rust target missing; run: rustup target add --toolchain $RUST_TOOLCHAIN $rust_target"
cargo ndk --version >/dev/null 2>&1 ||
  fail "cargo-ndk not found; run: cargo install cargo-ndk --version $CARGO_NDK_VERSION --locked"
cargo_ndk_version="$(cargo ndk --version | awk '{print $2}')"
[[ "$cargo_ndk_version" == "$CARGO_NDK_VERSION" ]] ||
  fail "cargo-ndk $CARGO_NDK_VERSION required, found '$cargo_ndk_version'; run: cargo install cargo-ndk --version $CARGO_NDK_VERSION --locked --force"

sdk_root="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
export ANDROID_NDK_HOME="${ANDROID_NDK_HOME:-${sdk_root:+$sdk_root/ndk/$ANDROID_NDK_VERSION}}"
[[ -f "${ANDROID_NDK_HOME:-}/source.properties" ]] ||
  fail "NDK $ANDROID_NDK_VERSION ($ANDROID_NDK_RELEASE) not found; set ANDROID_NDK_HOME or install it with sdkmanager \"ndk;$ANDROID_NDK_VERSION\""
ndk_revision="$(sed -n 's/^Pkg.Revision *= *//p' "$ANDROID_NDK_HOME/source.properties" | tr -d '\r')"
[[ "$ndk_revision" == "$ANDROID_NDK_VERSION" ]] ||
  fail "NDK $ANDROID_NDK_VERSION required, $ANDROID_NDK_HOME is $ndk_revision"

[[ -n "${VCPKG_ROOT:-}" ]] || fail "VCPKG_ROOT is not set (vcpkg checkout at $VCPKG_COMMIT)"
vcpkg_bin="$VCPKG_ROOT/vcpkg"
[[ -x "$vcpkg_bin" || -x "$vcpkg_bin.exe" ]] || fail "vcpkg not bootstrapped in $VCPKG_ROOT; run its bootstrap-vcpkg script"
if git -C "$VCPKG_ROOT" rev-parse HEAD >/dev/null 2>&1; then
  vcpkg_head="$(git -C "$VCPKG_ROOT" rev-parse HEAD)"
  [[ "$vcpkg_head" == "$VCPKG_COMMIT" ]] ||
    fail "vcpkg must be at $VCPKG_COMMIT, $VCPKG_ROOT is at $vcpkg_head; run: git -C \"$VCPKG_ROOT\" checkout $VCPKG_COMMIT"
fi

installed_dir="${STREAM_SERVER_VCPKG_INSTALLED_DIR:-$repo_root/build/stream-server/vcpkg_installed/$abi}"
output_dir="${STREAM_SERVER_OUTPUT_DIR:-$repo_root/app/src/main/jniLibs}"

echo "Building libstream_server.so for $abi with Rust $RUST_TOOLCHAIN, cargo-ndk $cargo_ndk_version, NDK $ndk_revision, vcpkg $VCPKG_COMMIT"

# --- Native dependencies (libtorrent, OpenSSL, Boost) from the versioned triplets/overlays. ---
(
  cd "$server_root"
  "$vcpkg_bin" install \
    --triplet "$triplet" \
    --x-install-root="$installed_dir" \
    --overlay-triplets="$repo_root/native/vcpkg-triplets" \
    --overlay-ports="$server_root/vcpkg-overlays"
)

# --- Rust JNI library; --locked keeps the dependency graph identical to stream-server/Cargo.lock. ---
(
  cd "$server_root/server"
  VCPKG_INSTALLED_DIR="$installed_dir" \
  VCPKGRS_TRIPLET="$triplet" \
  PKG_CONFIG_ALLOW_CROSS=1 \
  PKG_CONFIG_PATH="$installed_dir/$triplet/lib/pkgconfig" \
  PKG_CONFIG_SYSROOT_DIR="$installed_dir/$triplet" \
  OPENSSL_DIR="$installed_dir/$triplet" \
    cargo ndk --target "$rust_target" --platform "$ANDROID_PLATFORM" \
      build --release --locked --features "$STREAM_SERVER_FEATURES" --no-default-features
)

built="$server_root/target/$rust_target/release/libstream_server.so"
[[ -s "$built" ]] || fail "build finished without $built"
mkdir -p "$output_dir/$abi"
cp "$built" "$output_dir/$abi/libstream_server.so"
echo "Wrote $output_dir/$abi/libstream_server.so"
sha256sum "$output_dir/$abi/libstream_server.so" 2>/dev/null || shasum -a 256 "$output_dir/$abi/libstream_server.so"
