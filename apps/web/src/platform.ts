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
export let layout: Layout =
  webview() || shellParam === 'desktop' ? 'desktop'
    : shellParam === 'android' && import.meta.env.MODE === 'preview' ? 'android'
    : 'web';

/** Design preview only: switch the layout in place (the preview remounts the app afterwards). */
export function setPreviewLayout(next: Layout): void {
  if (import.meta.env.MODE !== 'preview' || webview()) return;
  layout = next;
}

/** Design preview only: whether the stand-in Windows shell reports blocking as running (demo). */
let previewAgentRunning = previewParams().get('protect') === '1';
export function setPreviewAgentRunning(on: boolean): void {
  if (import.meta.env.MODE === 'preview') previewAgentRunning = on;
}

/** The Windows agent's latest report. `fresh` false = not running (stale): never show protection. */
export interface AgentStatus {
  sessionId: string | null;
  protection: 'idle' | 'not_restricting' | 'protecting' | 'degraded';
  fresh: boolean;
  elevatedSkipped: number;
}
export interface OpenApp { exe: string; name: string }

let host: HostInfo | null = null;
let agentStatus: AgentStatus | null = null;
let openApps: OpenApp[] | null = null;
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

export function parseAgentStatus(data: unknown): AgentStatus | null {
  if (!data || typeof data !== 'object') return null;
  const m = data as Record<string, unknown>;
  if (m.v !== 1 || m.type !== 'host.agent_status') return null;
  const p = m.protection;
  if (p !== 'idle' && p !== 'not_restricting' && p !== 'protecting' && p !== 'degraded') return null;
  if (typeof m.fresh !== 'boolean') return null;
  const sid = typeof m.sessionId === 'string' && m.sessionId.length <= 64 ? m.sessionId : null;
  const skipped = typeof m.elevatedSkipped === 'number' && m.elevatedSkipped >= 0 ? m.elevatedSkipped : 0;
  return { sessionId: sid, protection: p, fresh: m.fresh, elevatedSkipped: skipped };
}

export function parseOpenApps(data: unknown): OpenApp[] | null {
  if (!data || typeof data !== 'object') return null;
  const m = data as Record<string, unknown>;
  if (m.v !== 1 || m.type !== 'host.apps' || !Array.isArray(m.apps)) return null;
  return m.apps.slice(0, 200).filter((a): a is OpenApp =>
    !!a && typeof a === 'object' && typeof (a as OpenApp).exe === 'string' && typeof (a as OpenApp).name === 'string'
    && /\.exe$/i.test((a as OpenApp).exe) && (a as OpenApp).exe.length <= 128 && (a as OpenApp).name.length <= 80);
}

export function initBridge(): void {
  const wv = webview();
  if (!wv && import.meta.env.MODE === 'preview' && layout === 'desktop') {
    // Design preview: stand in for the Windows shell. "running" only with the demo toggle.
    host = { platform: 'windows', appVersion: 'preview', agent: previewAgentRunning ? 'running' : 'not_installed' };
    listeners.forEach((l) => l());
    return;
  }
  if (!wv) return;
  wv.addEventListener('message', (e) => {
    const info = parseHostMessage(e.data);
    if (info) { host = info; listeners.forEach((l) => l()); return; }
    const st = parseAgentStatus(e.data);
    if (st) { agentStatus = st; listeners.forEach((l) => l()); return; }
    const apps = parseOpenApps(e.data);
    if (apps) { openApps = apps; listeners.forEach((l) => l()); return; }
    const cmd = parseHostCommand(e.data);
    if (cmd) commandHandlers.forEach((h) => h(cmd));
  });
  wv.postMessage({ v: 1, type: 'ui.ready' });
}

/** Ask the shell to do something native. No-op in a browser. */
export function sendToHost(
  msg: { type: 'ui.session_state'; active: boolean; endsAt: number | null; label: string } | { type: 'ui.theme'; dark: boolean }
    | { type: 'ui.restriction'; plan: RestrictionPlan } | { type: 'ui.list_apps' },
): void {
  webview()?.postMessage({ v: 1, ...msg });
}

/** What the Windows agent enforces (validated again by the shell and the agent). */
export interface RestrictionPlan {
  v: 1;
  sessionId: string;
  issuedAt: number;
  intervals: [number, number][];
  mode: 'block_list' | 'approved_only';
  apps: string[];
}

const subscribe = (l: () => void) => { listeners.add(l); return () => listeners.delete(l); };

export function useAgentStatus(): AgentStatus | null {
  return useSyncExternalStore(subscribe, () => agentStatus);
}

/** Apps with an open window on this PC (asked of the shell on demand). */
export function useOpenApps(): OpenApp[] | null {
  return useSyncExternalStore(subscribe, () => openApps);
}

export function useHost(): HostInfo | null {
  return useSyncExternalStore((l) => { listeners.add(l); return () => listeners.delete(l); }, () => host);
}
