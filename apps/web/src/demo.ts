// Design preview only (`vite --mode preview` / `npm run build:preview`). Lets the browser preview
// show sample sessions and *demo* connected/protected states without any backend. In every other
// build `PREVIEW` is false at compile time, so none of this can make the real app claim blocking
// or pairing.
import { getState, newId, update } from './state/store';

export const PREVIEW = import.meta.env.MODE === 'preview';

const params = (): URLSearchParams =>
  new URLSearchParams(typeof location === 'undefined' ? '' : location.search);

export interface Demo {
  /** Demo: restriction is "verified" on this device (Windows agent running / Android blocking). */
  protect: boolean;
  /** Demo: the other devices are paired and in sync. */
  paired: boolean;
}

export const DEMO: Demo | null = PREVIEW
  ? { protect: params().get('protect') === '1', paired: params().get('paired') === '1' }
  : null;

export type Scene = 'setup' | 'focus' | 'break' | 'pending' | 'done';
const MIN = 60_000;

/** Seeds this frame's (separately stored) state for the requested sample scene. */
export function applyPreviewScene(): void {
  if (!PREVIEW) return;
  const p = params();
  const theme = p.get('theme');
  if (theme === 'light' || theme === 'dark') { update((s) => ({ ...s, theme })); document.documentElement.dataset.theme = theme; }
  if (p.get('text') === '200') {
    document.documentElement.dataset.text = '200';
    const st = document.createElement('style');
    st.textContent = 'html { font-size: 200% !important; }';
    document.head.appendChild(st);
  }
  const scene = (p.get('scene') ?? '') as Scene | '';
  if (!scene) return;
  const now = Date.now();
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
