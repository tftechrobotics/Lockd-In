// Design exploration (pass 4): two directions for the web Focus screen, rendered from the
// same sample session. Development-only page (lab.html); not part of the production build.
import { useState } from 'react';
import wordmark from '@design/assets/wordmark.svg?raw';
import { clockTime, durationLabel } from '../ui/format';
import { MiniLock, NumeralTimer, PadlockTimer, SessionRail, type RailSegment, type TimerState } from './timers';

const q = new URLSearchParams(location.search);
const DIR = (q.get('dir') ?? 'a') as 'a' | 'b';
const SCREEN = (q.get('screen') ?? 'setup') as 'setup' | TimerState | 'states';
const SHELL = (q.get('shell') ?? 'web') as 'web' | 'desktop';

// One sample session for both directions: 2 rounds of 40 min with a 10-minute break, 7:00–8:30 PM.
const T0 = new Date('2026-11-03T19:00:00').getTime();
const MIN = 60_000;
const TASK = 'TAM 212 · Problem set 6';
const SEGMENTS: RailSegment[] = [
  { kind: 'focus', minutes: 40, start: T0, end: T0 + 40 * MIN },
  { kind: 'break', minutes: 10, start: T0 + 40 * MIN, end: T0 + 50 * MIN },
  { kind: 'focus', minutes: 40, start: T0 + 50 * MIN, end: T0 + 90 * MIN },
];
const NOW: Record<TimerState, number> = { ready: T0 - 2 * MIN, focus: T0 + 14 * MIN, break: T0 + 44 * MIN, done: T0 + 90 * MIN };
const PROFILES = ['Study', 'Writing', 'Coding'];

function snapshot(state: TimerState) {
  const now = NOW[state];
  const seg = state === 'focus' ? SEGMENTS[0] : state === 'break' ? SEGMENTS[1] : null;
  return {
    now,
    remainingMs: seg ? seg.end - now : state === 'ready' ? 40 * MIN : 0,
    progress: seg ? (now - seg.start) / (seg.end - seg.start) : 0,
    caption: state === 'focus' ? 'left in round 1' : state === 'break' ? 'left in break' : state === 'ready' ? 'per round' : 'Session complete',
  };
}

export function Lab() {
  return (
    <div className={`lab lab--${DIR} lab--${SHELL}`}>
      {SHELL === 'web' ? <Masthead running={SCREEN === 'focus' || SCREEN === 'break'} /> : <DeskBar />}
      <main className="lab__main">
        {SCREEN === 'states' ? <States /> : SCREEN === 'setup' ? (DIR === 'a' ? <SetupA /> : <SetupB />) : (DIR === 'a' ? <RunA state={SCREEN} /> : <RunB state={SCREEN} />)}
      </main>
    </div>
  );
}

function Masthead({ running }: { running: boolean }) {
  return (
    <header className="mast">
      <div className="mast__in">
        <a className="mast__mark" href="#" aria-label="Lockd In, Focus" dangerouslySetInnerHTML={{ __html: wordmark }} />
        <nav aria-label="Main"><ul>
          <li><a href="#" aria-current="page">Focus{running && <span className="mast__live" aria-label="session running" />}</a></li>
          <li><a href="#">Schedule</a></li><li><a href="#">Profiles</a></li><li><a href="#">Devices</a></li>
          <li><a href="#" aria-label="Settings" className="mast__gear"><svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinejoin="round" aria-hidden="true"><path d="M18.97 10.21 L21.27 10.47 L21.27 13.53 L18.97 13.79 L18.20 15.67 L19.64 17.48 L17.48 19.64 L15.67 18.20 L13.79 18.97 L13.53 21.27 L10.47 21.27 L10.21 18.97 L8.33 18.20 L6.52 19.64 L4.36 17.48 L5.80 15.67 L5.03 13.79 L2.73 13.53 L2.73 10.47 L5.03 10.21 L5.80 8.33 L4.36 6.52 L6.52 4.36 L8.33 5.80 L10.21 5.03 L10.47 2.73 L13.53 2.73 L13.79 5.03 L15.67 5.80 L17.48 4.36 L19.64 6.52 L18.20 8.33Z M12 8.6a3.4 3.4 0 1 0 0 6.8 3.4 3.4 0 1 0 0-6.8" /></svg></a></li>
        </ul></nav>
      </div>
    </header>
  );
}

function DeskBar() {
  return (
    <header className="desk">
      <nav className="desk__switch" aria-label="Main"><a href="#" aria-current="page">Focus</a><a href="#">Schedule</a></nav>
      <button type="button" className="btn btn--ghost">Menu</button>
    </header>
  );
}

const Honest = ({ compact = false }: { compact?: boolean }) => (
  <p className={`honest ${compact ? 'honest--compact' : ''}`}>
    <span className="honest__dot" aria-hidden="true" />
    <span>Timer only. {SHELL === 'web' ? 'Browsers can’t restrict apps, so apps and websites stay available.' : 'Apps and websites stay available on this PC.'} <a href="#">Set up blocking</a></span>
  </p>
);

/* ---------------- Direction A: session composer + padlock ---------------- */

function SetupA() {
  const [task, setTask] = useState(TASK);
  const [len, setLen] = useState(40);
  const [profile, setProfile] = useState('Study');
  const [open, setOpen] = useState(false);
  const total = 2 * len + 10;
  return (
    <div className="a-setup">
      <form className="composer" onSubmit={(e) => e.preventDefault()}>
        <div className="composer__q">
          <label htmlFor="a-task">What are you working on?</label>
          <input id="a-task" className="bigfield" value={task} onChange={(e) => setTask(e.target.value)} placeholder="Name the task, or leave it blank" />
        </div>
        <fieldset className="composer__q">
          <legend>How long?</legend>
          <div className="tiles" role="radiogroup" aria-label="Round length">
            {[25, 40, 60].map((m) => (
              <button key={m} type="button" role="radio" aria-checked={len === m} className="tile" onClick={() => setLen(m)}>
                <span className="tile__num">{m}</span><span className="tile__unit">min</span>
              </button>
            ))}
            <button type="button" role="radio" aria-checked={false} className="tile tile--custom"><span className="tile__unit">Custom</span></button>
          </div>
          <div className="rounds">
            <span>2 rounds · 10 min break between</span>
            <button type="button" className="link" aria-expanded={open} onClick={() => setOpen(!open)}>{open ? 'Done' : 'Change'}</button>
          </div>
          {open && (
            <div className="rounds__edit">
              <label>Rounds <select defaultValue="2"><option>1</option><option>2</option><option>3</option><option>4</option></select></label>
              <label>Break <select defaultValue="10"><option value="5">5 min</option><option value="10">10 min</option><option value="15">15 min</option></select></label>
            </div>
          )}
        </fieldset>
        <fieldset className="composer__q">
          <legend>Which profile?</legend>
          <div className="chips" role="radiogroup" aria-label="Profile">
            {PROFILES.map((p) => <button key={p} type="button" role="radio" aria-checked={profile === p} className="chip" onClick={() => setProfile(p)}>{p}</button>)}
          </div>
        </fieldset>
        <div className="composer__go">
          <div className="composer__go-row">
            <button type="submit" className="btn btn--primary btn--large">Start timer</button>
            <p className="go-summary"><strong>Ends at {clockTime(NOW.ready + total * MIN)}</strong> · {durationLabel(total)} with the break</p>
          </div>
          <Honest />
        </div>
      </form>
      <aside className="a-preview" aria-label="Session preview">
        <PadlockTimer state="ready" blocked={false} remainingMs={len * MIN} progress={0} caption="per round" size={248} />
        <p className="a-preview__ends">Ends at {clockTime(NOW.ready + total * MIN)}</p>
        <p className="a-preview__total">{durationLabel(total)} with the break</p>
      </aside>
    </div>
  );
}

function RunA({ state }: { state: TimerState }) {
  const s = snapshot(state);
  const done = state === 'done';
  return (
    <div className="a-run">
      <PadlockTimer state={state} blocked={false} remainingMs={s.remainingMs} progress={s.progress} caption={s.caption} size={300} />
      <h1 className="a-run__task">{TASK}</h1>
      <p className="a-run__meta">
        {state === 'focus' && <>Round 1 of 2 <span aria-hidden="true">·</span> Break at {clockTime(SEGMENTS[0].end)} <span aria-hidden="true">·</span> Ends {clockTime(SEGMENTS[2].end)}</>}
        {state === 'break' && <>Break after round 1 <span aria-hidden="true">·</span> Round 2 starts at {clockTime(SEGMENTS[1].end)}</>}
        {done && <>80 min focused <span aria-hidden="true">·</span> {clockTime(T0)}–{clockTime(SEGMENTS[2].end)}</>}
      </p>
      {done ? (
        <div className="a-run__actions"><button type="button" className="btn btn--primary">Same again</button><button type="button" className="btn btn--ghost">Done</button></div>
      ) : (
        <>
          <Honest compact />
          <div className="a-run__actions"><button type="button" className="btn btn--ghost">Need out?</button></div>
        </>
      )}
    </div>
  );
}

/* ---------------- Direction B: a sentence and the numerals ---------------- */

function SetupB() {
  const [task, setTask] = useState(TASK);
  const plan = SEGMENTS.map((s) => ({ ...s, start: s.start - 0, end: s.end }));
  return (
    <div className="b-setup">
      <form onSubmit={(e) => e.preventDefault()}>
        <h1 className="sr-only">Plan a focus session</h1>
        <p className="sentence">
          <span>I’m working on </span>
          <input aria-label="Task" className="inline-field inline-field--task" value={task} onChange={(e) => setTask(e.target.value)} placeholder="something" size={Math.max(10, task.length)} />
          <span> for </span>
          <span className="inline-select"><select aria-label="Round length" defaultValue="40"><option value="25">25 minutes</option><option value="40">40 minutes</option><option value="60">60 minutes</option><option value="c">custom time</option></select></span>
          <span> using my </span>
          <span className="inline-select"><select aria-label="Profile" defaultValue="Study">{PROFILES.map((p) => <option key={p}>{p}</option>)}</select></span>
          <span> profile.</span>
        </p>
        <p className="sentence sentence--small">
          <span>Do it in </span>
          <span className="inline-select"><select aria-label="Rounds" defaultValue="2"><option value="1">1 round</option><option value="2">2 rounds</option><option value="3">3 rounds</option></select></span>
          <span> with a </span>
          <span className="inline-select"><select aria-label="Break length" defaultValue="10"><option value="5">5-minute</option><option value="10">10-minute</option><option value="15">15-minute</option></select></span>
          <span> break.</span>
        </p>
        <div className="b-plan">
          <SessionRail segments={plan} now={NOW.ready} fmt={clockTime} />
          <p className="b-plan__sum"><strong>{durationLabel(90)}</strong> · {clockTime(T0)} to {clockTime(SEGMENTS[2].end)}</p>
        </div>
        <div className="b-go">
          <button type="submit" className="btn btn--primary btn--large">Start timer</button>
          <Honest />
        </div>
      </form>
    </div>
  );
}

function RunB({ state }: { state: TimerState }) {
  const s = snapshot(state);
  const done = state === 'done';
  return (
    <div className="b-run">
      <h1 className="b-run__task">{TASK}</h1>
      <NumeralTimer state={state} blocked={false} remainingMs={s.remainingMs} progress={s.progress} caption={s.caption}
        round={state === 'focus' ? 'round 1 of 2' : state === 'break' ? 'round 2 at ' + clockTime(SEGMENTS[1].end) : undefined} />
      <SessionRail segments={SEGMENTS} now={s.now} fmt={clockTime} />
      <div className="b-run__foot">
        {done ? (
          <>
            <p className="b-run__facts">80 min focused · {clockTime(T0)}–{clockTime(SEGMENTS[2].end)} · Study profile</p>
            <div className="b-run__actions"><button type="button" className="btn btn--primary">Same again</button><button type="button" className="btn btn--ghost">Done</button></div>
          </>
        ) : (
          <>
            <div>
              <p className="b-run__facts">{state === 'focus' ? <>Next break {clockTime(SEGMENTS[0].end)} · Ends {clockTime(SEGMENTS[2].end)} · Study profile</> : <>Focus resumes {clockTime(SEGMENTS[1].end)} · Ends {clockTime(SEGMENTS[2].end)} · Study profile</>}</p>
              <Honest compact />
            </div>
            <div className="b-run__actions"><button type="button" className="btn btn--ghost">Need out?</button></div>
          </>
        )}
      </div>
    </div>
  );
}

/* ---------------- Timer states sheet ---------------- */

function States() {
  const rows: [TimerState, boolean, string][] = [
    ['ready', false, 'Ready'], ['focus', false, 'Focusing, timer only'], ['focus', true, 'Focusing, blocking verified'], ['break', false, 'Break'], ['done', false, 'Complete'],
  ];
  return (
    <div className="states">
      {rows.map(([st, blocked, name]) => {
        const s = snapshot(st);
        return (
          <figure key={name} className="states__cell">
            {DIR === 'a'
              ? <PadlockTimer state={st} blocked={blocked} remainingMs={s.remainingMs} progress={s.progress} caption={s.caption} size={170} />
              : <div className="states__b"><NumeralTimer state={st} blocked={blocked} remainingMs={s.remainingMs} progress={s.progress} caption={s.caption} /></div>}
            <figcaption>{name}</figcaption>
          </figure>
        );
      })}
      {DIR === 'b' && <div className="states__rail"><SessionRail segments={SEGMENTS} now={NOW.focus} fmt={clockTime} /></div>}
      <p className="states__note"><MiniLock closed={false} size={14} /> The shackle closes only when blocking is verified on that device.</p>
    </div>
  );
}
