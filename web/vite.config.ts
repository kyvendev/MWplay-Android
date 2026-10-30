/// <reference types="vitest" />
import { createHash } from 'node:crypto';
import { readFileSync, readdirSync, statSync } from 'node:fs';
import { join, relative } from 'node:path';
import react from '@vitejs/plugin-react';
import { defineConfig, type Plugin } from 'vite';

/**
 * Emits /sw.js with the exact list of files this build produced (plus public/ icons and manifest)
 * and a version derived from them, so every release gets a fresh cache and old ones are purged.
 */
function serviceWorker(): Plugin {
  return {
    name: 'mwplay-service-worker',
    apply: 'build',
    generateBundle(_options, bundle) {
      const publicDir = join(__dirname, 'public');
      const publicFiles = listFiles(publicDir).map((file) => `/${relative(publicDir, file).replace(/\\/g, '/')}`);
      const built = Object.keys(bundle).map((file) => `/${file}`);
      const precache = ['/', '/index.html', ...built.filter((file) => file !== '/index.html'), ...publicFiles].filter(
        (file, index, all) => all.indexOf(file) === index && !file.endsWith('.map'),
      );
      const version = createHash('sha256').update(precache.join('|')).digest('hex').slice(0, 12);
      const template = readFileSync(join(__dirname, 'src/pwa/sw-template.js'), 'utf8');
      this.emitFile({
        type: 'asset',
        fileName: 'sw.js',
        source: template.split('__VERSION__').join(version).split('__PRECACHE__').join(JSON.stringify(precache)),
      });
    },
  };
}

function listFiles(dir: string): string[] {
  return readdirSync(dir).flatMap((name) => {
    const path = join(dir, name);
    return statSync(path).isDirectory() ? listFiles(path) : [path];
  });
}

export default defineConfig({
  plugins: [react(), serviceWorker()],
  build: {
    // Older TV browsers (Tizen/webOS Chromium builds) still run this syntax.
    target: ['es2019', 'chrome69', 'safari13', 'firefox78'],
    sourcemap: false,
  },
  worker: {
    format: 'es',
  },
  server: {
    port: 5173,
    // Dev equivalent of api/introdb.js (the Vercel function used in production).
    proxy: {
      '/api/introdb': {
        target: 'https://api.introdb.app',
        changeOrigin: true,
        rewrite: (path) => '/segments' + path.slice('/api/introdb'.length),
      },
    },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    include: ['src/**/*.test.{ts,tsx}', 'tests/**/*.test.{ts,tsx}'],
  },
});
