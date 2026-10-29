// One consistent stroke icon set (24 px grid, 1.75 stroke), drawn for Lockd In.
const paths: Record<string, string> = {
  laptop: 'M4 6.5h16v9.5H4zM2 18.5h20',
  phone: 'M7.5 2.5h9v19h-9zM11 18.5h2',
  browser: 'M3 4.5h18v15H3zM3 8.5h18M6 6.5h.01M8.5 6.5h.01',
  calendar: 'M4 5.5h16v15H4zM4 10h16M8.5 3v4M15.5 3v4',
  coffee: 'M4 9h12v5a5 5 0 0 1-5 5H9a5 5 0 0 1-5-5zM16 10.5h1.5a2.5 2.5 0 0 1 0 5H16M8 3.5c0 1.2 1 1.6 1 2.8M12 3.5c0 1.2 1 1.6 1 2.8',
  book: 'M4 5.5c2.5-1.3 5.5-1.3 8 0v14c-2.5-1.3-5.5-1.3-8 0zM12 5.5c2.5-1.3 5.5-1.3 8 0v14c-2.5-1.3-5.5-1.3-8 0z',
  gear: 'M18.97 10.21 L21.27 10.47 L21.27 13.53 L18.97 13.79 L18.20 15.67 L19.64 17.48 L17.48 19.64 L15.67 18.20 L13.79 18.97 L13.53 21.27 L10.47 21.27 L10.21 18.97 L8.33 18.20 L6.52 19.64 L4.36 17.48 L5.80 15.67 L5.03 13.79 L2.73 13.53 L2.73 10.47 L5.03 10.21 L5.80 8.33 L4.36 6.52 L6.52 4.36 L8.33 5.80 L10.21 5.03 L10.47 2.73 L13.53 2.73 L13.79 5.03 L15.67 5.80 L17.48 4.36 L19.64 6.52 L18.20 8.33Z M12 8.6a3.4 3.4 0 1 0 0 6.8 3.4 3.4 0 1 0 0-6.8',
  chevron: 'M9 6l6 6-6 6',
  chevronDown: 'M6 9l6 6 6-6',
  focus: 'M12 3a9 9 0 1 0 0 18 9 9 0 1 0 0-18M12 7.5V12l3 2',
  menu: 'M4 7h16M4 12h16M4 17h16',
  arrow: 'M4.5 12h15M13.5 6l6 6-6 6',
  check: 'M5 12.5l4.5 4.5L19 7.5',
  dash: 'M6 12h12',
  info: 'M12 3a9 9 0 1 0 0 18 9 9 0 1 0 0-18M12 11v5.5M12 7.5h.01',
  link: 'M10 14a4 4 0 0 0 5.7 0l3-3a4 4 0 0 0-5.7-5.7l-1 1M14 10a4 4 0 0 0-5.7 0l-3 3a4 4 0 0 0 5.7 5.7l1-1',
  plus: 'M12 5v14M5 12h14',
  x: 'M6 6l12 12M18 6L6 18',
};

export type IconName = keyof typeof paths;

export function Icon({ name, size = 20, label }: { name: IconName; size?: number; label?: string }) {
  return (
    <svg className="icon" width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor"
      strokeWidth={1.75} strokeLinecap="round" strokeLinejoin="round"
      role={label ? 'img' : undefined} aria-label={label} aria-hidden={label ? undefined : true}>
      <path d={paths[name]} />
    </svg>
  );
}
