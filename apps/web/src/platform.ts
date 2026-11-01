// Where the shared interface is running. The Windows shell hosts it in
// WebView2 and announces itself over the bridge; `?shell=desktop` forces the
// desktop layout for development and tests in an ordinary browser.
import { useSyncExternalStore } from 'react';
import { previewParams } from './previewParams';

/** 'android' exists only in the design preview: a browser approximation of the Compose screens. */
export type Layout = 'web' | 'desktop' | 'android';

/** What the native host has told us. Never trusted for anything but display. */
export interface HostInfo {
  platform: 'windows';
  appVersion: string;
  /** State of the separate blocking agent/service (ADR-0007/0009). */
  agent: 'not_installed' | 'stopped' | 'running' | 'unknown';
}

interface WebView { postMessage(m: unknown): void; addEventListener(t: 'message', f: (e: { data: unknown }) => void): void }
const webview = (): WebView | undefined =>
  typeof window === 'undefined' ? undefined : (window as unknown as { chrome?: { webview?: WebView } }).chrome?.webview;

const shellParam = previewParams().get('shell');
export const layout: Layout =
  webview() || shellParam === 'desktop' ? 'desktop'
    : shellParam === 'android' && import.meta.env.MODE === 'preview' ? 'android'
    : 'web';

let host: HostInfo | null = null;
const listeners = new Set<() => void>();
export type HostCommand = 'start_quick';
const commandHandlers = new Set<(c: HostCommand) => void>();

/** Commands the shell may send (tray actions). Anything else is ignored. */
export function parseHostCommand(data: unknown): HostCommand | null {
  if (!data || typeof data !== 'object') return null;
  const m = data as Record<string, unknown>;
  return m.v === 1 && m.type === 'host.command' && m.command === 'start_quick' ? 'start_quick' : null;
}

export function onHostCommand(fn: (c: HostCommand) => void): () => void {
  commandHandlers.add(fn);
  return () => commandHandlers.delete(fn);
}

/** Validate a message from the host; unknown shapes are ignored. */
export function parseHostMessage(data: unknown): HostInfo | null {
  if (!data || typeof data !== 'object') return null;
  const m = data as Record<string, unknown>;
  if (m.v !== 1 || m.type !== 'host.hello') return null;
  const agent = m.agent;
  if (agent !== 'not_installed' && agent !== 'stopped' && agent !== 'running' && agent !== 'unknown') return null;
  if (typeof m.appVersion !== 'string' || m.appVersion.length > 40) return null;
  return { platform: 'windows', appVersion: m.appVersion, agent };
}

export function initBridge(): void {
  const wv = webview();
  if (!wv && import.meta.env.MODE === 'preview' && layout === 'desktop') {
    // Design preview: stand in for the Windows shell. "running" only with the demo toggle.
    const protect = previewParams().get('protect') === '1';
    host = { platform: 'windows', appVersion: 'preview', agent: protect ? 'running' : 'not_installed' };
    listeners.forEach((l) => l());
    return;
  }
  if (!wv) return;
  wv.addEventListener('message', (e) => {
    const info = parseHostMessage(e.data);
    if (info) { host = info; listeners.forEach((l) => l()); return; }
    const cmd = parseHostCommand(e.data);
    if (cmd) commandHandlers.forEach((h) => h(cmd));
  });
  wv.postMessage({ v: 1, type: 'ui.ready' });
}

/** Ask the shell to do something native. No-op in a browser. */
export function sendToHost(
  msg: { type: 'ui.session_state'; active: boolean; endsAt: number | null; label: string } | { type: 'ui.theme'; dark: boolean },
): void {
  webview()?.postMessage({ v: 1, ...msg });
}

export function useHost(): HostInfo | null {
  return useSyncExternalStore((l) => { listeners.add(l); return () => listeners.delete(l); }, () => host);
}
