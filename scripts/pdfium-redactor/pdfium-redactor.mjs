#!/usr/bin/env node

import fs from 'fs/promises';
import path from 'path';
import { fileURLToPath } from 'url';

import { init } from '@embedpdf/pdfium';
import { PdfiumEngine } from '@embedpdf/engines/pdfium';
import { NoopLogger } from '@embedpdf/models';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const DEFAULT_OPTIONS = {
  recurseForms: true,
  drawBlackBoxes: false
};

function bufferToArrayBuffer(buffer) {
  return buffer.buffer.slice(buffer.byteOffset, buffer.byteOffset + buffer.byteLength);
}

function toNumber(value, fallback = 0) {
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : fallback;
}

function convertPagePointToDevice(page, position) {
  const DW = toNumber(page.size?.width, 0);
  const DH = toNumber(page.size?.height, 0);
  const r = (page.rotation ?? 0) & 3;
  if (r === 0) {
    return { x: position.x, y: DH - position.y };
  }
  if (r === 1) {
    return { x: position.y, y: position.x };
  }
  if (r === 2) {
    return { x: DW - position.x, y: position.y };
  }
  return { x: DH - position.y, y: DW - position.x };
}

function rectToDeviceRect(page, rect) {
  const x1 = toNumber(rect.x1);
  const y1 = toNumber(rect.y1);
  const x2 = toNumber(rect.x2);
  const y2 = toNumber(rect.y2);
  if (!(Number.isFinite(x1) && Number.isFinite(y1) && Number.isFinite(x2) && Number.isFinite(y2))) {
    return null;
  }
  const tl = convertPagePointToDevice(page, { x: x1, y: y2 });
  const br = convertPagePointToDevice(page, { x: x2, y: y1 });
  const originX = Math.min(tl.x, br.x);
  const originY = Math.min(tl.y, br.y);
  const width = Math.abs(br.x - tl.x);
  const height = Math.abs(br.y - tl.y);
  if (!(width > 0 && height > 0)) {
    return null;
  }
  return {
    origin: { x: originX, y: originY },
    size: { width, height }
  };
}

async function ensureOutputDir(filePath) {
  await fs.mkdir(path.dirname(filePath), { recursive: true });
}

async function loadConfig(configPath) {
  const raw = await fs.readFile(configPath, 'utf-8');
  const parsed = JSON.parse(raw);
  if (!parsed.inputPath || !parsed.outputPath) {
    throw new Error('Config must include inputPath and outputPath');
  }
  parsed.options = { ...DEFAULT_OPTIONS, ...(parsed.options ?? {}) };
  parsed.rectsByPage = parsed.rectsByPage ?? {};
  parsed.fileName = parsed.fileName ?? 'document.pdf';
  return parsed;
}

async function openDocument(engine, config) {
  const pdfBuffer = await fs.readFile(config.inputPath);
  const file = {
    id: `doc-${Date.now()}`,
    name: config.fileName,
    content: bufferToArrayBuffer(pdfBuffer)
  };
  return engine.openDocumentBuffer(file).toPromise();
}

async function saveDocument(engine, doc, outputPath) {
  const buffer = await engine.saveAsCopy(doc).toPromise();
  await ensureOutputDir(outputPath);
  await fs.writeFile(outputPath, Buffer.from(buffer));
}

async function redactDocument(doc, engine, rectsByPage, options) {
  const entries = Object.entries(rectsByPage ?? {});
  for (const [pageIndexString, rects] of entries) {
    const pageIndex = Number(pageIndexString);
    if (!Number.isInteger(pageIndex)) {
      continue;
    }
    const page = doc.pages?.[pageIndex];
    if (!page) {
      continue;
    }
    const deviceRects = (rects ?? [])
      .map((rect) => rectToDeviceRect(page, rect))
      .filter(Boolean);
    if (deviceRects.length === 0) {
      continue;
    }
    await engine
      .redactTextInRects(doc, page, deviceRects, {
        recurseForms: options.recurseForms,
        drawBlackBoxes: options.drawBlackBoxes
      })
      .toPromise();
  }
}

async function main() {
  const [configPath] = process.argv.slice(2);
  if (!configPath) {
    console.error('Usage: node pdfium-redactor.mjs <config.json>');
    process.exit(2);
  }

  const config = await loadConfig(configPath);
  const wasmPath = path.join(__dirname, 'node_modules', '@embedpdf', 'pdfium', 'dist', 'pdfium.wasm');
  const wasmBinary = await fs.readFile(wasmPath);
  const wasmModule = await init({ wasmBinary });
  const engine = new PdfiumEngine(wasmModule, { logger: new NoopLogger() });
  const doc = await openDocument(engine, config);
  try {
    await redactDocument(doc, engine, config.rectsByPage, config.options);
    await saveDocument(engine, doc, config.outputPath);
  } finally {
    await engine.closeDocument(doc).toPromise().catch(() => undefined);
  }
}

main().catch((err) => {
  console.error('[pdfium-redactor] Failed:', err?.stack ?? err);
  process.exit(1);
});
