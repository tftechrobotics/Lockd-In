export const pad = (n: number) => String(n).padStart(2, '0');

/** "40:00", "1:05:09" — whole seconds, rounded up so 0:00 only shows at the end. */
export function countdown(ms: number): string {
  const total = Math.max(0, Math.ceil(ms / 1000));
  const h = Math.floor(total / 3600), m = Math.floor((total % 3600) / 60), s = total % 60;
  return h ? `${h}:${pad(m)}:${pad(s)}` : `${pad(m)}:${pad(s)}`;
}

/** Spoken form for screen readers, at minute precision: "23 minutes left". */
export function spokenRemaining(ms: number): string {
  const min = Math.ceil(ms / 60_000);
  if (min <= 1) return 'Less than a minute left';
  if (min < 60) return `${min} minutes left`;
  const h = Math.floor(min / 60), m = min % 60;
  return `${h} hour${h > 1 ? 's' : ''}${m ? ` ${m} minutes` : ''} left`;
}

export const clockTime = (t: number) =>
  new Date(t).toLocaleTimeString(undefined, { hour: 'numeric', minute: '2-digit' });

export function dayLabel(t: number, now = Date.now()): string {
  const d = new Date(t), n = new Date(now);
  const day = (x: Date) => new Date(x.getFullYear(), x.getMonth(), x.getDate()).getTime();
  const diff = Math.round((day(d) - day(n)) / 86_400_000);
  if (diff === 0) return 'Today';
  if (diff === 1) return 'Tomorrow';
  return d.toLocaleDateString(undefined, { weekday: 'long' });
}

export const minutesLabel = (m: number) => (m >= 60 && m % 60 === 0 ? `${m / 60} h` : `${m} min`);

export const DAYS = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];

/** 55 → "55 min", 90 → "1 h 30 min". */
export const durationLabel = (m: number) => (m < 60 ? `${m} min` : m % 60 === 0 ? `${m / 60} h` : `${Math.floor(m / 60)} h ${m % 60} min`);
