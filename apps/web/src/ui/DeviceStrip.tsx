// Honest device readiness. Until pairing exists (milestone M5), the interface
// knows only about the device it runs on, so other devices show "Not paired".
// In the Windows app, "This PC" reflects what the shell reports about the
// separate blocking service (ADR-0009) — never assumed.
import { layout, useHost, type HostInfo } from '../platform';
import { Icon, type IconName } from './Icon';

interface Row { icon: IconName; name: string; status: string; tone: 'ok' | 'none' }

const AGENT_TEXT: Record<HostInfo['agent'], [string, Row['tone']]> = {
  running: ['Blocking service on', 'ok'],
  stopped: ['Blocking service off', 'none'],
  not_installed: ['Blocking not set up', 'none'],
  unknown: ['Status unknown', 'none'],
};

export function deviceRows(host: HostInfo | null): Row[] {
  if (layout === 'desktop') {
    const [status, tone] = host ? AGENT_TEXT[host.agent] : (['Checking…', 'none'] as [string, Row['tone']]);
    return [
      { icon: 'laptop', name: 'This PC', status, tone },
      { icon: 'phone', name: 'Phone', status: 'Not paired', tone: 'none' },
    ];
  }
  return [
    { icon: 'browser', name: 'This browser', status: 'Timer only', tone: 'ok' },
    { icon: 'laptop', name: 'PC', status: 'Not paired', tone: 'none' },
    { icon: 'phone', name: 'Phone', status: 'Not paired', tone: 'none' },
  ];
}

export function DeviceStrip() {
  const host = useHost();
  return (
    <div className="devices">
      <ul className="devices__list" aria-label="Devices">
        {deviceRows(host).map((d) => (
          <li key={d.name} className={`device device--${d.tone}`}>
            <Icon name={d.icon} size={16} />
            <span className="device__name">{d.name}</span>
            <span className={`dot dot--${d.tone}`} aria-hidden="true" />
            <span className="device__status">{d.status}</span>
          </li>
        ))}
      </ul>
    </div>
  );
}
