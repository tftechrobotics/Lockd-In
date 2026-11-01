// Design preview only (`vite --mode preview` / `npm run build:preview`). Lets the browser preview
// show sample sessions and *demo* connected/protected states without any backend. In every other
// build `PREVIEW` is false at compile time, so none of this can make the real app claim blocking
// or pairing.
import { useSyncExternalStore } from 'react';
import { getState, newId, update } from './state/store';
import { setPreviewAgentRunning } from './platform';
import { previewParams } from './previewParams';

export const PREVIEW = import.meta.env.MODE === 'preview';

const params = previewParams;

export interface Demo {
  /** Demo: restriction is "verified" on this device (Windows agent running / Android blocking). */
  protect: boolean;
  /** Demo: the other devices are paired and in sync. */
  paired: boolean;
}

export const DEMO: Demo | null = PREVIEW
  ? { protect: params().get('protect') === '1', paired: params().get('paired') === '1' }
  : null;

const demoListeners = new Set<() => void>();
let demoVersion = 0;
/** Changes a demo state (preview only); the preview root remounts the app so every screen agrees. */
export function setDemo(change: Partial<Demo>): void {
  if (!DEMO) return;
  Object.assign(DEMO, change);
  setPreviewAgentRunning(DEMO.protect);
  demoVersion++;
  demoListeners.forEach((l) => l());
}
export function useDemoVersion(): number {
  return useSyncExternalStore((l) => { demoListeners.add(l); return () => demoListeners.delete(l); }, () => demoVersion);
}

export type Scene = 'setup' | 'focus' | 'break' | 'pending' | 'done';
const MIN = 60_000;

/** Seeds the sample state from the address (or packaged defaults). */
export function applyPreviewScene(): void {
  if (!PREVIEW) return;
  const p = params();
  const theme = p.get('theme');
  if (theme === 'light' || theme === 'dark') setPreviewTheme(theme);
  if (p.get('text') === '200') setPreviewText(true);
  const scene = (p.get('scene') ?? '') as Scene | '';
  if (scene) applyScene(scene);
}

export function setPreviewTheme(theme: 'light' | 'dark'): void {
  update((s) => ({ ...s, theme }));
  document.documentElement.dataset.theme = theme;
}

let textStyle: HTMLStyleElement | null = null;
export function setPreviewText(large: boolean): void {
  if (large) {
    document.documentElement.dataset.text = '200';
    if (!textStyle) { textStyle = document.createElement('style'); textStyle.textContent = 'html { font-size: 200% !important; }'; document.head.appendChild(textStyle); }
  } else {
    delete document.documentElement.dataset.text;
    textStyle?.remove(); textStyle = null;
  }
}

/** Puts the app into one of the sample moments (setup, focusing, break, unlock pending, done). */
export function applyScene(scene: Scene): void {
  if (!PREVIEW) return;
  const now = Date.now();
  // Sample plan so the idle screen has a next session to show (local data, like a user's own).
  const at = new Date(now + 2 * 60 * MIN);
  const startTime = `${String(at.getHours()).padStart(2, '0')}:00`;
  update((s) => (s.schedule.length ? s : {
    ...s,
    schedule: [{ id: newId(), title: 'Physics review', days: [0, 1, 2, 3, 4, 5, 6], startTime, focusMinutes: 40, breakMinutes: 10, rounds: 2, profileId: s.profiles[0]?.id ?? s.last.profileId }],
  }));
  const segments = [
    { kind: 'focus' as const, minutes: 40 },
    { kind: 'break' as const, minutes: 10 },
    { kind: 'focus' as const, minutes: 40 },
  ];
  const offset: Record<Scene, number> = { setup: 0, focus: 14, break: 44, pending: 14, done: 90 };
  const intention = { text: 'TAM 212 · Problem set 6' };
  update((s) => ({
    ...s,
    last: { ...s.last, focusMinutes: 40, breakMinutes: 10, rounds: 2, profileId: s.profiles[0]?.id ?? s.last.profileId },
    recentIntentions: [intention, { text: 'Read chapter 4' }],
    active: scene === 'setup' ? null : {
      id: newId(),
      start: now - offset[scene] * MIN,
      segments,
      intention,
      profileId: getState().profiles[0]?.id ?? s.last.profileId,
      controls: { overrides: scene === 'pending' ? [{ id: newId(), scope: 'current_focus_period', phaseIndex: 0, requestedAt: now - 20_000, delaySeconds: 90 }] : [] },
    },
  }));
}
