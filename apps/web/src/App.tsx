import { useEffect, useState } from 'react';
import wordmark from '@design/assets/wordmark.svg?raw';
import { useStore } from './state/store';
import { FocusPage } from './ui/FocusPage';
import { SchedulePage } from './ui/SchedulePage';
import { DevicesPage, ProfilesPage, SettingsPage } from './ui/OtherPages';
import { Icon } from './ui/Icon';
import { RunningClock } from './ui/RunningClock';
import { DesktopBar } from './ui/DesktopBar';
import { initBridge, layout, onHostCommand, sendToHost } from './platform';
import { occurrenceFrom } from './engine/session';
import { startQuick, useStore as useStoreSel } from './state/store';

const ROUTES = {
  focus: { label: 'Focus', page: FocusPage },
  schedule: { label: 'Schedule', page: SchedulePage },
  profiles: { label: 'Profiles', page: ProfilesPage },
  devices: { label: 'Devices', page: DevicesPage },
  settings: { label: 'Settings', page: SettingsPage },
} as const;
type Route = keyof typeof ROUTES;

const routeFromHash = (): Route => {
  const r = location.hash.replace(/^#\/?/, '') as Route;
  return r in ROUTES ? r : 'focus';
};

function useTheme() {
  const theme = useStore((s) => s.theme);
  useEffect(() => {
    const mq = window.matchMedia('(prefers-color-scheme: dark)');
    const apply = () => {
      const dark = theme === 'dark' || (theme === 'system' && mq.matches);
      document.documentElement.dataset.theme = dark ? 'dark' : 'light';
      sendToHost({ type: 'ui.theme', dark });
      try { localStorage.setItem('lockdin.theme', theme); } catch { /* storage unavailable */ }
    };
    apply();
    mq.addEventListener('change', apply);
    return () => mq.removeEventListener('change', apply);
  }, [theme]);
}

export function App() {
  const [route, setRoute] = useState<Route>(routeFromHash);
  const active = useStore((s) => s.active !== null);
  useTheme();
  useEffect(() => {
    const on = () => { setRoute(routeFromHash()); window.scrollTo(0, 0); document.getElementById('main')?.focus({ preventScroll: true }); };
    window.addEventListener('hashchange', on);
    return () => window.removeEventListener('hashchange', on);
  }, []);
  useEffect(() => { document.title = `${ROUTES[route].label} · Lockd In`; }, [route]);
  useEffect(() => {
    document.documentElement.dataset.layout = layout;
    initBridge();
    return onHostCommand(() => { startQuick(); location.hash = '#/focus'; });
  }, []);
  const session = useStoreSel((s) => s.active);
  useEffect(() => {
    sendToHost(session
      ? { type: 'ui.session_state', active: true, endsAt: occurrenceFrom(session.start, session.segments).end, label: session.intention?.text ?? '' }
      : { type: 'ui.session_state', active: false, endsAt: null, label: '' });
  }, [session]);
  const Page = ROUTES[route].page;

  if (layout === 'android') {
    // Design preview only: the Compose app's chrome (top bar, bottom navigation) around the shared pages.
    const tabs: [Route, string, 'focus' | 'calendar' | 'book'][] = [['focus', 'Focus', 'focus'], ['schedule', 'Schedule', 'calendar'], ['profiles', 'Profiles', 'book']];
    return (
      <div className="droid">
        <a className="skip" href="#main">Skip to content</a>
        <header className="droid__bar">
          <a className="droid__mark" href="#/focus" aria-label="Lockd In, Focus" dangerouslySetInnerHTML={{ __html: wordmark }} />
          <a className="droid__icon" href="#/settings" aria-label="Settings"><Icon name="gear" size={22} /></a>
        </header>
        <main id="main" tabIndex={-1} className="droid__main">
          <Page />
        </main>
        <nav className="droid__nav" aria-label="Main">
          {tabs.map(([r, label, icon]) => (
            <a key={r} href={`#/${r}`} aria-current={route === r ? 'page' : undefined} className="droid__tab">
              <span className="droid__pill"><Icon name={icon} size={22} /></span>
              {r === 'focus' && active && route !== 'focus'
                ? <span><span className="sr-only">{label} </span><RunningClock /></span>
                : <span>{label}</span>}
            </a>
          ))}
        </nav>
      </div>
    );
  }

  if (layout === 'desktop') {
    return (
      <>
        <a className="skip" href="#main">Skip to content</a>
        <DesktopBar route={route} sessionRunning={active} />
        <main id="main" tabIndex={-1}>
          <Page />
        </main>
      </>
    );
  }

  return (
    <>
      <a className="skip" href="#main">Skip to content</a>
      <header className="masthead">
        <div className="masthead__inner">
          <a className="wordmark" href="#/focus" aria-label="Lockd In, Focus" dangerouslySetInnerHTML={{ __html: wordmark }} />
          <nav aria-label="Main">
            <ul className="nav">
              {(Object.keys(ROUTES) as Route[]).map((r) => (
                <li key={r}>
                  <a href={`#/${r}`} aria-current={route === r ? 'page' : undefined} className={r === 'settings' ? 'nav__icon' : undefined}
                    aria-label={r === 'settings' ? 'Settings' : undefined}>
                    {r === 'settings' ? <Icon name="gear" size={22} /> : ROUTES[r].label}
                    {r === 'focus' && active && route !== 'focus' && <RunningClock />}
                  </a>
                </li>
              ))}
            </ul>
          </nav>
        </div>
      </header>
      <main id="main" tabIndex={-1}>
        <Page />
      </main>
    </>
  );
}
