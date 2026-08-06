/**
 * Helper to check if the current environment is running inside Tauri Desktop
 * without importing desktop-only @tauri-apps/* packages in core modules.
 */
export function isTauriPlatform(): boolean {
  return (
    typeof window !== "undefined" &&
    ("__TAURI_INTERNALS__" in window || "__TAURI__" in window)
  );
}
