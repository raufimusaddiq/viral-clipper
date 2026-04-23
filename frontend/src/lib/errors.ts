// Shared error-reporting util. Every silent `catch (_) { /* noop */ }` in the
// UI used to hide real failures (NVENC crashes, stage errors, dropped polls).
// Route them through here so they land in the console AND can be surfaced in
// a dismissible banner via onError subscribers.

export type ErrorContext = string;
type Listener = (msg: string, ctx: ErrorContext, err: unknown) => void;

const listeners = new Set<Listener>();

export function reportError(err: unknown, context: ErrorContext): void {
  const msg = err instanceof Error ? err.message : String(err);
  // eslint-disable-next-line no-console
  console.error(`[${context}]`, err);
  listeners.forEach(l => {
    try {
      l(msg, context, err);
    } catch {
      // listener bugs must not break the reporter itself
    }
  });
}

export function onError(fn: Listener): () => void {
  listeners.add(fn);
  return () => {
    listeners.delete(fn);
  };
}

/** True for DOM AbortError — these are expected when we cancel a poll on unmount. */
export function isAbortError(err: unknown): boolean {
  if (err instanceof DOMException && err.name === 'AbortError') return true;
  if (err instanceof Error && err.name === 'AbortError') return true;
  return false;
}
