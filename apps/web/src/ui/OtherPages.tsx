import { useId, useRef, useState } from 'react';
import { clearAll, newId, storageAvailable, update, useStore, type ThemeChoice, type Tone } from '../state/store';
import { DeviceStrip } from './DeviceStrip';
import { Icon } from './Icon';
import { message } from './messages';

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
      <h1 className="display display--page">Profiles</h1>
      <p className="lede">Apps that stay available during focus. Each device keeps its own list.</p>
      <ul className="profiles">
        {profiles.map((p) => (
          <li key={p.id} className="profile">
            <div className="profile__head">
              <h2>{p.name}</h2>
              <p>{p.description}</p>
            </div>
            <dl className="profile__devices">
              <div><dt><Icon name="laptop" size={18} /> PC</dt><dd>No apps chosen</dd></div>
              <div><dt><Icon name="phone" size={18} /> Phone</dt><dd>No apps chosen</dd></div>
            </dl>
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
  return (
    <div className="page">
      <h1 className="display display--page">Devices</h1>
      <DeviceStrip />
      <div className="explain">
        <dl>
          <div><dt>Windows app</dt><dd>Restricts apps outside your profile during focus.</dd></div>
          <div><dt>Android app</dt><dd>Covers restricted apps with a block screen during focus.</dd></div>
          <div><dt>Browser</dt><dd>Plans and times sessions. Browsers can’t restrict other apps.</dd></div>
        </dl>
        <p className="lede lede--small">Pairing isn’t available yet. Paired devices will share session timing and keep working offline.</p>
      </div>
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
      <h1 className="display display--page">Settings</h1>
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
