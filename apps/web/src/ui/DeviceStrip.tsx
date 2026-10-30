// Honest device readiness, compact, with a way to act on each item. Until pairing
// exists other devices read "Not paired". In the Windows app, "This PC" reflects
// what the shell reports about the separate blocking service (ADR-0009), never assumed.
import { layout, useHost, type HostInfo } from '../platform';
import { Icon, type IconName } from './Icon';

interface Row { icon: IconName; name: string; status: string; ok: boolean; action?: string }

const AGENT: Record<HostInfo['agent'], [string, boolean]> = {
  running: ['Blocking on', true],
  stopped: ['Blocking off', false],
  not_installed: ['Blocking not set up', false],
  unknown: ['Status unknown', false],
};

export function deviceRows(host: HostInfo | null): Row[] {
  if (layout === 'desktop') {
    const [status, ok] = host ? AGENT[host.agent] : ['Checking…', false] as [string, boolean];
    return [
      { icon: 'laptop', name: 'This PC', status, ok, action: ok ? undefined : 'Set up' },
      { icon: 'phone', name: 'Phone', status: 'Not paired', ok: false, action: 'Pair' },
    ];
  }
  return [
    { icon: 'browser', name: 'This browser', status: 'Timer only', ok: true },
    { icon: 'laptop', name: 'PC', status: 'Not paired', ok: false, action: 'Set up' },
    { icon: 'phone', name: 'Phone', status: 'Not paired', ok: false, action: 'Set up' },
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
          {d.action && <a className="device__action" href="#/devices" aria-label={`${d.action}: ${d.name}`}>{d.action}</a>}
        </li>
      ))}
    </ul>
  );
}
