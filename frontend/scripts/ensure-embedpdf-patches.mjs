#!/usr/bin/env node
// Single entry point for the local @embedpdf patches: verifies them, applies
// them if missing, then verifies again. Runs from `postinstall` and from the
// Vite build (see editor/vite.config.ts) so installs that skip lifecycle
// scripts (`npm ci --ignore-scripts`, nixpkgs and other packagers) and builds
// that call `vite build` directly (Tauri beforeBuildCommand) still patch and
// fail loudly on anchor/version drift instead of silently shipping an
// unpatched engine.
import { readdirSync, rmSync } from "node:fs";
import path from "node:path";
import { spawnSync } from "node:child_process";
import { fileURLToPath } from "node:url";

const here = path.dirname(fileURLToPath(import.meta.url));
const nodeModulesDir = path.resolve(here, "../node_modules");
const scripts = ["patch-embedpdf-engines.mjs", "patch-embedpdf-plugins.mjs"];

function run(script, args = []) {
  return (
    spawnSync(process.execPath, [path.join(here, script), ...args], {
      stdio: "inherit",
    }).status === 0
  );
}

export function ensureEmbedpdfPatches() {
  let applied = false;
  for (const script of scripts) {
    if (run(script, ["--check"])) continue;
    if (!run(script)) return false;
    if (!run(script, ["--check"])) return false;
    applied = true;
  }
  if (applied) {
    // Vite's dev prebundle caches the unpatched vendor code; a stale cache
    // would keep serving it even though node_modules is patched now. cacheDir
    // is per-mode (.vite-<mode>), so drop every Vite cache in the workspace.
    for (const entry of readdirSync(nodeModulesDir)) {
      if (entry.startsWith(".vite")) {
        rmSync(path.join(nodeModulesDir, entry), {
          recursive: true,
          force: true,
        });
      }
    }
  }
  return true;
}

const isMain =
  process.argv[1] &&
  path.resolve(process.argv[1]) === fileURLToPath(import.meta.url);
if (isMain) {
  process.exit(ensureEmbedpdfPatches() ? 0 : 1);
}
