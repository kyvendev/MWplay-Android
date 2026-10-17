// PWA resources as shipped (files on disk; no browser needed).
import { existsSync, readFileSync } from 'node:fs';
import { join } from 'node:path';
import { describe, expect, it } from 'vitest';

const root = join(__dirname, '..');
const read = (path: string) => readFileSync(join(root, path), 'utf8');

describe('web app manifest', () => {
  const manifest = JSON.parse(read('public/manifest.webmanifest'));

  it('installs as MW Play in standalone mode with the theme colors', () => {
    expect(manifest.name).toBe('MW Play');
    expect(manifest.short_name).toBe('MW Play');
    expect(manifest.display).toBe('standalone');
    expect(manifest.start_url).toBe('/');
    expect(manifest.background_color).toBe('#06070D');
    expect(manifest.theme_color).toBe('#06070D');
  });

  it('points to icons that exist, including a maskable one', () => {
    for (const icon of manifest.icons) expect(existsSync(join(root, 'public', icon.src))).toBe(true);
    expect(manifest.icons.some((icon: { purpose: string; sizes: string }) => icon.purpose === 'maskable' && icon.sizes === '512x512')).toBe(true);
  });
});

describe('iOS Safari metadata', () => {
  const html = read('index.html');
  it('declares the apple touch icon, standalone capability, title and safe-area viewport', () => {
    expect(html).toContain('rel="apple-touch-icon" href="/icons/apple-touch-icon.png"');
    expect(existsSync(join(root, 'public/icons/apple-touch-icon.png'))).toBe(true);
    expect(html).toContain('name="apple-mobile-web-app-capable" content="yes"');
    expect(html).toContain('name="apple-mobile-web-app-title" content="MW Play"');
    expect(html).toContain('viewport-fit=cover');
    expect(html).toContain('rel="manifest"');
  });
});

describe('service worker', () => {
  const worker = read('src/pwa/sw-template.js');
  it('never intercepts other origins (catalogs, accounts, addons, images, streams)', () => {
    expect(worker).toContain("if (url.origin !== self.location.origin) return;");
    expect(worker).toContain("if (request.method !== 'GET') return;");
  });

  it('versions its cache, purges old versions and only takes over on request', () => {
    expect(worker).toContain('mwplay-shell-${VERSION}');
    expect(worker).toContain('caches.delete');
    expect(worker).toContain("event.data === 'SKIP_WAITING'");
  });
});
