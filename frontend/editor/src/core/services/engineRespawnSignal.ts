/**
 * Handoff between the file lifecycle (which knows a removed file's size) and
 * the engine respawn watcher (which cannot read FileContext state after the
 * removal lands). One value at a time: the workbench-empty transition is the
 * only consumer.
 */

let lastRemovedDocumentBytes = 0;

export function noteRemovedDocumentBytes(bytes: number): void {
  lastRemovedDocumentBytes = bytes;
}

export function consumeRemovedDocumentBytes(): number {
  const bytes = lastRemovedDocumentBytes;
  lastRemovedDocumentBytes = 0;
  return bytes;
}
