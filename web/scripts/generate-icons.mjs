// Rasterizes the official MW Play mark (res/drawable/mw_play_mark.xml) into the PWA / iOS icons.
// Pure Node (zlib + a scanline polygon fill with 4x4 supersampling): no image dependencies.
import { mkdirSync, writeFileSync } from 'node:fs';
import { deflateSync } from 'node:zlib';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const out = join(dirname(fileURLToPath(import.meta.url)), '..', 'public', 'icons');
mkdirSync(out, { recursive: true });

// Same polygons and colors as the Android vector (108x108 viewport).
const SHAPES = [
  { color: [0x82, 0x5c, 0xff], points: [[8, 78], [8, 30], [20, 30], [31, 54], [42, 30], [54, 30], [54, 78], [43, 78], [43, 50], [35, 69], [28, 69], [19, 50], [19, 78]] },
  { color: [0x4e, 0x8c, 0xff], points: [[57, 30], [68, 30], [74, 61], [81, 40], [88, 61], [95, 30], [106, 30], [97, 78], [88, 78], [81, 58], [74, 78], [65, 78]] },
];
const BACKGROUND = [0x06, 0x07, 0x0d];
// Mark bounds inside the vector: x 8..106, y 30..78.
const MARK = { minX: 8, maxX: 106, minY: 30, maxY: 78 };

function insidePolygon(x, y, points) {
  let inside = false;
  for (let i = 0, j = points.length - 1; i < points.length; j = i++) {
    const [xi, yi] = points[i];
    const [xj, yj] = points[j];
    if (yi > y !== yj > y && x < ((xj - xi) * (y - yi)) / (yj - yi) + xi) inside = !inside;
  }
  return inside;
}

/** @param padding fraction of the canvas left empty around the mark (maskable icons need ~20%). */
function render(size, padding) {
  const usable = size * (1 - padding * 2);
  const scale = usable / (MARK.maxX - MARK.minX);
  const offsetX = (size - (MARK.maxX - MARK.minX) * scale) / 2 - MARK.minX * scale;
  const offsetY = (size - (MARK.maxY - MARK.minY) * scale) / 2 - MARK.minY * scale;
  const samples = 4;
  const rows = [];
  for (let py = 0; py < size; py++) {
    const row = Buffer.alloc(1 + size * 3);
    row[0] = 0; // PNG filter: none
    for (let px = 0; px < size; px++) {
      const totals = [0, 0, 0];
      for (let sy = 0; sy < samples; sy++) {
        for (let sx = 0; sx < samples; sx++) {
          const x = (px + (sx + 0.5) / samples - offsetX) / scale;
          const y = (py + (sy + 0.5) / samples - offsetY) / scale;
          const shape = SHAPES.find((candidate) => insidePolygon(x, y, candidate.points));
          const color = shape ? shape.color : BACKGROUND;
          totals[0] += color[0];
          totals[1] += color[1];
          totals[2] += color[2];
        }
      }
      const n = samples * samples;
      row[1 + px * 3] = Math.round(totals[0] / n);
      row[2 + px * 3] = Math.round(totals[1] / n);
      row[3 + px * 3] = Math.round(totals[2] / n);
    }
    rows.push(row);
  }
  return png(size, size, Buffer.concat(rows));
}

const CRC_TABLE = Array.from({ length: 256 }, (_, n) => {
  let c = n;
  for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
  return c >>> 0;
});

function crc32(buffer) {
  let crc = 0xffffffff;
  for (const byte of buffer) crc = CRC_TABLE[(crc ^ byte) & 0xff] ^ (crc >>> 8);
  return (crc ^ 0xffffffff) >>> 0;
}

function chunk(type, data) {
  const length = Buffer.alloc(4);
  length.writeUInt32BE(data.length);
  const body = Buffer.concat([Buffer.from(type, 'ascii'), data]);
  const crc = Buffer.alloc(4);
  crc.writeUInt32BE(crc32(body));
  return Buffer.concat([length, body, crc]);
}

function png(width, height, raw) {
  const header = Buffer.alloc(13);
  header.writeUInt32BE(width, 0);
  header.writeUInt32BE(height, 4);
  header[8] = 8; // bit depth
  header[9] = 2; // RGB
  return Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    chunk('IHDR', header),
    chunk('IDAT', deflateSync(raw, { level: 9 })),
    chunk('IEND', Buffer.alloc(0)),
  ]);
}

const ICONS = [
  ['icon-192.png', 192, 0.14],
  ['icon-512.png', 512, 0.14],
  ['icon-maskable-512.png', 512, 0.22],
  ['apple-touch-icon.png', 180, 0.16],
];
for (const [name, size, padding] of ICONS) {
  writeFileSync(join(out, name), render(size, padding));
  console.log(`wrote ${name}`);
}

writeFileSync(
  join(out, 'mw-play-mark.svg'),
  `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108"><rect width="108" height="108" rx="22" fill="#06070D"/><path fill="#825CFF" d="M8,78 L8,30 L20,30 L31,54 L42,30 L54,30 L54,78 L43,78 L43,50 L35,69 L28,69 L19,50 L19,78 Z"/><path fill="#4E8CFF" d="M57,30 L68,30 L74,61 L81,40 L88,61 L95,30 L106,30 L97,78 L88,78 L81,58 L74,78 L65,78 Z"/></svg>\n`,
);
console.log('wrote mw-play-mark.svg');
