// Local state for the web app, kept in this browser only (localStorage).
// Versioned so later releases can migrate. Every read/write is guarded:
// storage can be unavailable (private mode, blocked site data), in which case
// the app still works for the current tab and says so in Settings.
import { useSyncExternalStore } from 'react';
import type { Segment, SessionControls } from '../engine/session';
import { previewParams } from '../previewParams';

export const SCHEMA_VERSION = 1;

export type ThemeChoice = 'system' | 'light' | 'dark';
export type Tone = 'calm' | 'direct' | 'playful' | 'quiet';

export interface Profile {
  id: string;
  name: string;
  description: string;
  builtIn: boolean;
}

/** What the user is working on, e.g. "TAM 212 · Practice problems". Optional, free text. */
export interface Intention { text: string }

export interface ScheduledSession {
  id: string;
  title: string;
  /** 0 = Sunday … 6 = Saturday, like Date.getDay(). */
  days: number[];
  /** One-time session on this local date (YYYY-MM-DD); `days` is then ignored. */
  date?: string | null;
  /** Disabled sessions stay listed but never come up. Missing = enabled. */
  enabled?: boolean;
  /** Local wall-clock start, "HH:MM". */
  startTime: string;
  focusMinutes: number;
  breakMinutes: number;
  rounds: number;
  profileId: string;
}

export interface ActiveSession {
  id: string;
  /** Wall-clock start (epoch ms). The web app has no monotonic clock across reloads. */
  start: number;
  segments: Segment[];
  intention: Intention | null;
  profileId: string;
  controls: SessionControls;
}

export interface LastChoice { focusMinutes: number; breakMinutes: number; rounds: number; profileId: string }

export interface State {
  version: number;
  theme: ThemeChoice;
  tone: Tone;
  profiles: Profile[];
  schedule: ScheduledSession[];
  last: LastChoice;
  active: ActiveSession | null;
  recentIntentions: Intention[];
}

// The design preview runs several frames on one origin; each keeps its own sample state.
const KEY = 'lockdin.v1' + (import.meta.env.MODE === 'preview' && typeof location !== 'undefined'
  ? '.preview.' + (previewParams().get('frame') ?? 'main') : '');

export const BUILT_IN_PROFILES: Profile[] = [
  { id: 'study', name: 'Study', description: 'Course apps, notes, PDFs and a calculator', builtIn: true },
  { id: 'writing', name: 'Writing', description: 'A word processor and your sources', builtIn: true },
  { id: 'coding', name: 'Coding', description: 'Editor, terminal and documentation', builtIn: true },
  { id: 'custom', name: 'Custom', description: 'Only what you add', builtIn: true },
];

export const defaultState = (): State => ({
  version: SCHEMA_VERSION,
  theme: 'system',
  tone: 'calm',
  profiles: BUILT_IN_PROFILES,
  schedule: [],
  last: { focusMinutes: 40, breakMinutes: 10, rounds: 1, profileId: 'study' },
  active: null,
  recentIntentions: [],
});

let storageOk = true;

/** Validate untrusted stored JSON; anything unexpected falls back to defaults. */
export function parseState(raw: string | null): State {
  const base = defaultState();
  if (!raw) return base;
  try {
    const v = JSON.parse(raw) as Partial<State>;
    if (!v || typeof v !== 'object' || v.version !== SCHEMA_VERSION) return base;
    const theme: ThemeChoice = v.theme === 'light' || v.theme === 'dark' ? v.theme : 'system';
    const tone: Tone = (['calm', 'direct', 'playful', 'quiet'] as const).includes(v.tone as Tone) ? (v.tone as Tone) : 'calm';
    return {
      ...base,
      theme,
      tone,
      profiles: Array.isArray(v.profiles) && v.profiles.length ? v.profiles.filter((p) => p && typeof p.id === 'string' && typeof p.name === 'string') : base.profiles,
      schedule: Array.isArray(v.schedule) ? v.schedule.filter((s) => s && typeof s.id === 'string' && Array.isArray(s.days) && (s.days.length > 0 || typeof s.date === 'string')) : [],
      last: v.last && typeof v.last.focusMinutes === 'number' ? v.last : base.last,
      active: v.active && typeof v.active.start === 'number' && Array.isArray(v.active.segments) ? v.active : null,
      recentIntentions: Array.isArray(v.recentIntentions) ? v.recentIntentions.filter((i) => i && typeof i.text === 'string').slice(0, 8) : [],
    };
  } catch {
    return base;
  }
}

function load(): State {
  try {
    return parseState(localStorage.getItem(KEY));
  } catch {
    storageOk = false;
    return defaultState();
  }
}

let state: State = load();
const listeners = new Set<() => void>();

export function getState(): State {
  return state;
}

export function update(fn: (s: State) => State): void {
  state = fn(state);
  try {
    localStorage.setItem(KEY, JSON.stringify(state));
    storageOk = true;
  } catch {
    storageOk = false;
  }
  listeners.forEach((l) => l());
}

export function clearAll(): void {
  try { localStorage.removeItem(KEY); } catch { /* storage unavailable */ }
  state = defaultState();
  listeners.forEach((l) => l());
}

export const storageAvailable = () => storageOk;

export function useStore<T>(select: (s: State) => T): T {
  return useSyncExternalStore(
    (l) => { listeners.add(l); return () => listeners.delete(l); },
    () => select(state),
  );
}

// Other tabs: pick up changes made elsewhere.
if (typeof window !== 'undefined') {
  window.addEventListener('storage', (e) => {
    if (e.key === KEY) { state = parseState(e.newValue); listeners.forEach((l) => l()); }
  });
}

export const newId = () =>
  (typeof crypto !== 'undefined' && 'randomUUID' in crypto) ? crypto.randomUUID() : String(Date.now()) + Math.random().toString(16).slice(2);

/** Tray "Start a 10-minute focus": the quick start from the brief. Does nothing if a session is running. */
export function startQuick(now = Date.now()): boolean {
  if (state.active) return false;
  update((s) => ({
    ...s,
    active: { id: newId(), start: now, segments: [{ kind: 'focus', minutes: 10 }], intention: null, profileId: s.last.profileId, controls: { overrides: [] } },
  }));
  return true;
}
