import { useEffect, useState } from 'react';
import wordmark from '@design/assets/wordmark.svg?raw';
import libraryEdge from '@design/assets/library-edge.svg?raw';
import { useStore } from './state/store';
import { FocusPage } from './ui/FocusPage';
import { SchedulePage } from './ui/SchedulePage';
import { DevicesPage, ProfilesPage, SettingsPage } from './ui/OtherPages';
import { Icon } from './ui/Icon';

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
  const Page = ROUTES[route].page;

  return (
    <>
      <a className="skip" href="#main">Skip to content</a>
      <div className="edge edge--left" aria-hidden="true" dangerouslySetInnerHTML={{ __html: libraryEdge }} />
      <div className="edge edge--right" aria-hidden="true" dangerouslySetInnerHTML={{ __html: libraryEdge }} />
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
                    {r === 'focus' && active && <span className="nav__live" aria-label="session running" />}
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
