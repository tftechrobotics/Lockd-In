import { layout, sendToHost, useHost, useOpenApps } from '../platform';
import { DEMO, setDemo } from '../demo';
import { useId, useRef, useState } from 'react';
import { clearAll, isAppId, newId, storageAvailable, update, useStore, type AppRules, type Profile, type ThemeChoice, type Tone } from '../state/store';
import { Icon } from './Icon';
import { message } from './messages';

/** One line describing a profile's rules on this PC. */
export function rulesSummary(r?: AppRules): string {
  if (!r || (r.mode === 'block_list' && r.apps.length === 0)) return 'No apps chosen';
  const n = r.apps.length, apps = `${n} app${n === 1 ? '' : 's'}`;
  return r.mode === 'approved_only' ? (n ? `Only ${apps} allowed` : 'Only Windows itself allowed') : `${apps} blocked`;
}

/** Windows: choose which apps this profile blocks (or allows) on this PC. */
function PcRules({ profile }: { profile: Profile }) {
  const rules = profile.rules ?? { mode: 'block_list' as const, apps: [] };
  const open = useOpenApps();
  const [typed, setTyped] = useState('');
  const [problem, setProblem] = useState<string | null>(null);
  const ids = { typed: useId(), mode: useId() };
  const save = (next: AppRules) => update((s) => ({ ...s, profiles: s.profiles.map((p) => (p.id === profile.id ? { ...p, rules: next } : p)) }));
  const add = (exe: string) => {
    if (!isAppId(exe)) { setProblem('Use the program’s file name, for example Discord.exe.'); return; }
    setProblem(null);
    if (!rules.apps.some((a) => a.toLowerCase() === exe.toLowerCase())) save({ ...rules, apps: [...rules.apps, exe].slice(0, 500) });
  };
  const running = useStore((s) => s.active?.profileId === profile.id);
  return (
    <details className="rules">
      <summary>Choose apps on this PC</summary>
      <fieldset className="rules__mode">
        <legend id={ids.mode}>During focus</legend>
        <label><input type="radio" name={`mode-${profile.id}`} checked={rules.mode === 'block_list'} onChange={() => save({ ...rules, mode: 'block_list' })} /> Block these apps</label>
        <label><input type="radio" name={`mode-${profile.id}`} checked={rules.mode === 'approved_only'} onChange={() => save({ ...rules, mode: 'approved_only' })} /> Allow only these apps</label>
      </fieldset>
      <p className="rules__note">
        {rules.mode === 'block_list' ? 'Their windows are minimized and their sound muted during focus.' : 'Every other app’s windows are minimized and muted during focus.'}
        {' '}Nothing is closed. File Explorer, Settings, Task Manager and sign-in prompts always work.
      </p>
      {running && <p className="rules__note">A session with this profile is running; changes apply from the next session.</p>}
      {rules.apps.length > 0 && (
        <ul className="rules__apps" aria-label="Chosen apps">
          {rules.apps.map((a) => (
            <li key={a}>{a}
              <button type="button" className="button button--quiet" aria-label={`Remove ${a}`}
                onClick={() => save({ ...rules, apps: rules.apps.filter((x) => x !== a) })}><Icon name="x" size={16} /></button>
            </li>
          ))}
        </ul>
      )}
      <div className="rules__add">
        <button type="button" className="button" onClick={() => sendToHost({ type: 'ui.list_apps' })}>Show open apps</button>
        {open && (open.length === 0 ? <p className="rules__note">No other apps have a window open.</p> : (
          <ul className="rules__open" aria-label="Open apps">
            {open.map((a) => (
              <li key={a.exe}><span>{a.name} <small>{a.exe}</small></span>
                <button type="button" className="button button--quiet" disabled={rules.apps.some((x) => x.toLowerCase() === a.exe.toLowerCase())} onClick={() => add(a.exe)}>Add</button>
              </li>
            ))}
          </ul>
        ))}
        <form className="add-row" onSubmit={(e) => { e.preventDefault(); add(typed.trim()); setTyped(''); }}>
          <label htmlFor={ids.typed}>Or type a program</label>
          <input id={ids.typed} className="input" value={typed} maxLength={128} placeholder="Discord.exe" onChange={(e) => setTyped(e.target.value)}
            aria-describedby={problem ? `${ids.typed}-err` : undefined} />
          <button type="submit" className="button" disabled={!typed.trim()}><Icon name="plus" /> Add</button>
        </form>
        {problem && <p id={`${ids.typed}-err`} className="error" role="alert">{problem}</p>}
      </div>
    </details>
  );
}

export function ProfilesPage() {
  const profiles = useStore((s) => s.profiles);
  const [name, setName] = useState('');
  const id = useId();
  const add = () => {
    const n = name.trim().slice(0, 40);
    if (!n) return;
    update((s) => ({ ...s, profiles: [...s.profiles, { id: newId(), name: n, description: 'Only what you add', builtIn: false }] }));
    setName('');
  };
  return (
    <div className="page">
      <header className="pagehead"><h1>Profiles</h1><p>Which apps are blocked while you focus. Each device keeps its own list.</p></header>
      <ul className="profiles">
        {profiles.map((p) => (
          <li key={p.id} className="profile">
            <div className="profile__head">
              <h2>{p.name}</h2>
              <p>{p.description}</p>
            </div>
            <dl className="profile__devices">
              <div><dt><Icon name="laptop" size={18} /> PC</dt><dd>{layout === 'desktop' ? rulesSummary(p.rules) : 'Set in the Windows app'}</dd></div>
              <div><dt><Icon name="phone" size={18} /> Phone</dt><dd>{layout === 'android' ? 'No apps chosen' : 'Set in the Android app'}</dd></div>
            </dl>
            {layout === 'desktop' && <PcRules profile={p} />}
            {!p.builtIn && (
              <button type="button" className="button button--quiet" aria-label={`Delete ${p.name}`}
                onClick={() => update((s) => ({ ...s, profiles: s.profiles.filter((x) => x.id !== p.id) }))}>Delete</button>
            )}
          </li>
        ))}
      </ul>
      <form className="add-row" onSubmit={(e) => { e.preventDefault(); add(); }}>
        <label htmlFor={id}>New profile</label>
        <input id={id} className="input" value={name} maxLength={40} placeholder="e.g. Lab report" onChange={(e) => setName(e.target.value)} />
        <button type="submit" className="button" disabled={!name.trim()}><Icon name="plus" /> Add</button>
      </form>
    </div>
  );
}

export function DevicesPage() {
  const host = useHost();
  const pcStatus = !host ? 'Checking…' : host.agent === 'running' ? 'Blocking on' : host.agent === 'stopped' ? 'Blocking off' : host.agent === 'unknown' ? 'Status unknown' : 'Not set up';
  // Design preview only: demo switches stand in for setup and pairing, clearly labelled.
  const demo = DEMO;
  const blockingDemo = (on: boolean) => demo && (
    <button type="button" className="button" onClick={() => setDemo({ protect: !on })}>
      {on ? 'Turn off blocking' : 'Turn on blocking'} <span className="demo-tag">Demo</span>
    </button>
  );
  const pairDemo = demo && (
    <button type="button" className="button" onClick={() => setDemo({ paired: !demo.paired })}>
      {demo.paired ? 'Unpair' : 'Pair'} <span className="demo-tag">Demo</span>
    </button>
  );
  const pairStatus = demo?.paired ? <span className="pill-status pill-status--ok">In sync</span> : <span className="pill-status">Not paired</span>;
  return (
    <div className="page">
      <header className="pagehead"><h1>Devices</h1><p>Where Lockd In runs, and what each device can do today.</p></header>
      <ul className="device-list">
        {layout === 'desktop' ? (
          <>
            <li id="windows">
              <Icon name="laptop" size={22} />
              <div><h2>This PC</h2><p>The blocking service restricts apps outside your profile during focus. It runs separately from this window.{demo ? '' : ' It isn’t included in this test build yet, so sessions here are timer only.'}</p>
                {blockingDemo(host?.agent === 'running')}</div>
              <span className={`pill-status ${host?.agent === 'running' ? 'pill-status--ok' : ''}`}>{pcStatus}</span>
            </li>
            <li id="android">
              <Icon name="phone" size={22} />
              <div><h2>Android phone</h2><p>Paired devices share session timing and keep working offline.</p>{pairDemo}</div>
              {pairStatus}
            </li>
          </>
        ) : layout === 'android' ? (
          <>
            <li id="android">
              <Icon name="phone" size={22} />
              <div><h2>This phone</h2><p>Covers apps outside your profile with a block screen during focus.{demo ? '' : ' Blocking isn’t in the current test build.'}</p>
                {blockingDemo(demo?.protect === true)}</div>
              <span className={`pill-status ${demo?.protect ? 'pill-status--ok' : ''}`}>{demo?.protect ? 'Blocking on' : 'Timer only'}</span>
            </li>
            <li id="windows">
              <Icon name="laptop" size={22} />
              <div><h2>Windows PC</h2><p>Paired devices share session timing and keep working offline.</p>{pairDemo}</div>
              {pairStatus}
            </li>
          </>
        ) : (
          <>
            <li>
              <Icon name="browser" size={22} />
              <div><h2>This browser</h2><p>Plans and times sessions. Browsers can’t restrict apps or other websites, so sessions here are timer only.</p></div>
              <span className="pill-status pill-status--ok">Timer only</span>
            </li>
            <li id="windows">
              <Icon name="laptop" size={22} />
              <div><h2>Windows PC</h2><p>The Windows app restricts apps outside your profile during focus.</p>{pairDemo}</div>
              {pairStatus}
            </li>
            <li id="android">
              <Icon name="phone" size={22} />
              <div><h2>Android phone</h2><p>The Android app covers restricted apps with a block screen during focus.</p></div>
              {pairStatus}
            </li>
          </>
        )}
      </ul>
      {!demo && <p className="hint">Pairing isn’t available yet. Once it is, paired devices share session timing and keep working offline.</p>}
    </div>
  );
}

export function SettingsPage() {
  const theme = useStore((s) => s.theme);
  const tone = useStore((s) => s.tone);
  const dialog = useRef<HTMLDialogElement>(null);
  const tones: [Tone, string][] = [['calm', 'Calm'], ['direct', 'Direct'], ['playful', 'Playful'], ['quiet', 'Quiet']];
  const themes: [ThemeChoice, string][] = [['system', 'Match system'], ['light', 'Light'], ['dark', 'Dark']];
  return (
    <div className="page">
      <header className="pagehead"><h1>Settings</h1></header>
      <section className="setting">
        <fieldset>
          <legend><h2>Appearance</h2></legend>
          {themes.map(([v, l]) => (
            <label key={v} className="radio radio--inline">
              <input type="radio" name="theme" checked={theme === v} onChange={() => update((s) => ({ ...s, theme: v }))} /> {l}
            </label>
          ))}
        </fieldset>
      </section>
      <section className="setting">
        <fieldset>
          <legend><h2>Messages</h2></legend>
          {tones.map(([v, l]) => (
            <label key={v} className="radio radio--inline">
              <input type="radio" name="tone" checked={tone === v} onChange={() => update((s) => ({ ...s, tone: v }))} /> {l}
            </label>
          ))}
          <p className="nudge" aria-live="polite">{message(tone, 'breakEnding', { task: 'TAM 212' }) ?? 'No messages.'}</p>
        </fieldset>
      </section>
      <section className="setting">
        <h2>Data on this browser</h2>
        <p className="lede lede--small">
          Stored only in this browser.
          {!storageAvailable() && ' This browser is blocking storage, so changes last only until you close the tab.'}
        </p>
        <button type="button" className="button button--danger" onClick={() => { dialog.current?.showModal(); dialog.current?.querySelector<HTMLButtonElement>('[data-safe]')?.focus(); }}>Delete data from this browser</button>
        <dialog ref={dialog} className="dialog" aria-labelledby="del-title">
          <h2 id="del-title">Delete Lockd In data from this browser?</h2>
          <p>Removes your schedule, custom profiles and any session in progress. This can’t be undone.</p>
          <div className="out__actions">
            <button type="button" className="button button--danger-solid" onClick={() => { clearAll(); dialog.current?.close(); }}>Delete</button>
            <button type="button" className="button" data-safe onClick={() => dialog.current?.close()}>Cancel</button>
          </div>
        </dialog>
      </section>
    </div>
  );
}
