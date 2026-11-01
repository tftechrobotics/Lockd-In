// Honest device readiness, compact, with a way to act on each item. Until pairing
// exists other devices read "Not paired". In the Windows app, "This PC" reflects
// what the shell reports about the separate blocking service (ADR-0009), never assumed.
import { layout, useHost, type HostInfo } from '../platform';
import { Icon, type IconName } from './Icon';
import { DEMO } from '../demo';

interface Row { icon: IconName; name: string; status: string; ok: boolean; action?: string; demo?: boolean }

const AGENT: Record<HostInfo['agent'], [string, boolean]> = {
  running: ['Blocking on', true],
  stopped: ['Blocking off', false],
  not_installed: ['Blocking not set up', false],
  unknown: ['Status unknown', false],
};

export function deviceRows(host: HostInfo | null): Row[] {
  // Design preview only: sample "paired" devices. The real app has no pairing yet.
  const paired = DEMO?.paired === true;
  const other = (icon: IconName, name: string, action: string): Row =>
    paired ? { icon, name, status: 'In sync', ok: true, demo: true } : { icon, name, status: 'Not paired', ok: false, action };
  if (layout === 'desktop') {
    const [status, ok] = host ? AGENT[host.agent] : ['Checking…', false] as [string, boolean];
    return [
      { icon: 'laptop', name: 'This PC', status, ok, action: ok ? undefined : 'Set up', demo: DEMO?.protect },
      other('phone', 'Phone', 'Pair'),
    ];
  }
  if (layout === 'android') {
    return [
      DEMO?.protect
        ? { icon: 'phone', name: 'This phone', status: 'Blocking on', ok: true, demo: true }
        : { icon: 'phone', name: 'This phone', status: 'Timer only', ok: false, action: 'Set up' },
      other('laptop', 'PC', 'Pair'),
    ];
  }
  return [
    { icon: 'browser', name: 'This browser', status: 'Timer only', ok: true },
    other('laptop', 'PC', 'Set up'),
    other('phone', 'Phone', 'Set up'),
  ];
}

export function DeviceStrip() {
  const host = useHost();
  return (
    <ul className="devices" aria-label="Devices">
      {deviceRows(host).map((d) => (
        <li key={d.name} className="device">
          <Icon name={d.icon} size={18} />
          <span className="device__name">{d.name}</span>
          <span className={`dot ${d.ok ? 'dot--ok' : ''}`} aria-hidden="true" />
          <span className="device__status">{d.status}</span>
          {d.demo && <span className="demo-tag">Demo</span>}
          {d.action && <a className="device__action" href="#/devices" aria-label={`${d.action}: ${d.name}`}>{d.action}</a>}
        </li>
      ))}
    </ul>
  );
}
