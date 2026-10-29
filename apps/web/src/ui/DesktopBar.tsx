// Desktop layout chrome (inside the native window, under the native title
// bar): a compact Focus/Schedule switch and a Menu for secondary places.
// No sidebar, no website navigation.
import { useEffect, useId, useRef, useState } from 'react';
import { Icon } from './Icon';

type Route = 'focus' | 'schedule' | 'profiles' | 'devices' | 'settings';
const MENU: [Route, string][] = [['profiles', 'Profiles'], ['devices', 'Devices'], ['settings', 'Settings']];

export function DesktopBar({ route, sessionRunning }: { route: Route; sessionRunning: boolean }) {
  const [open, setOpen] = useState(false);
  const menuId = useId();
  const button = useRef<HTMLButtonElement>(null);
  const menu = useRef<HTMLUListElement>(null);

  const items = () => Array.from(menu.current?.querySelectorAll<HTMLAnchorElement>('[role="menuitem"]') ?? []);
  useEffect(() => { if (open) items()[0]?.focus(); }, [open]);
  useEffect(() => {
    if (!open) return;
    const close = (e: MouseEvent) => {
      if (!menu.current?.contains(e.target as Node) && !button.current?.contains(e.target as Node)) setOpen(false);
    };
    document.addEventListener('mousedown', close);
    return () => document.removeEventListener('mousedown', close);
  }, [open]);

  const onMenuKey = (e: React.KeyboardEvent) => {
    const list = items();
    const i = list.indexOf(document.activeElement as HTMLAnchorElement);
    if (e.key === 'ArrowDown') { e.preventDefault(); list[(i + 1) % list.length]?.focus(); }
    else if (e.key === 'ArrowUp') { e.preventDefault(); list[(i - 1 + list.length) % list.length]?.focus(); }
    else if (e.key === 'Home') { e.preventDefault(); list[0]?.focus(); }
    else if (e.key === 'End') { e.preventDefault(); list[list.length - 1]?.focus(); }
    else if (e.key === 'Escape' || e.key === 'Tab') { setOpen(false); if (e.key === 'Escape') button.current?.focus(); }
  };

  return (
    <header className="deskbar">
      <nav aria-label="Main" className="switch">
        {(['focus', 'schedule'] as const).map((r) => (
          <a key={r} href={`#/${r}`} className="switch__item" aria-current={route === r ? 'page' : undefined}>
            {r === 'focus' ? 'Focus' : 'Schedule'}
            {r === 'focus' && sessionRunning && <span className="nav__live" aria-label="session running" />}
          </a>
        ))}
      </nav>
      <div className="deskbar__menu">
        <button ref={button} type="button" className="button deskbar__button" aria-haspopup="menu"
          aria-expanded={open} aria-controls={menuId} onClick={() => setOpen((o) => !o)}
          onKeyDown={(e) => { if (e.key === 'ArrowDown') { e.preventDefault(); setOpen(true); } }}>
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" aria-hidden="true"><path d="M4 7h16M4 12h16M4 17h16" /></svg>
          Menu
        </button>
        {open && (
          <ul ref={menu} id={menuId} role="menu" className="menu" aria-label="More" onKeyDown={onMenuKey}>
            {MENU.map(([r, label]) => (
              <li key={r} role="none">
                <a role="menuitem" href={`#/${r}`} tabIndex={-1} aria-current={route === r ? 'page' : undefined}
                  onClick={() => setOpen(false)}>
                  <Icon name={r === 'profiles' ? 'book' : r === 'devices' ? 'laptop' : 'gear'} size={18} />{label}
                </a>
              </li>
            ))}
          </ul>
        )}
      </div>
    </header>
  );
}
