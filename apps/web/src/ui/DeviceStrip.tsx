// Honest device readiness, styled after the reference's "PC ready | Phone
// ready" row. Until pairing exists, other devices read "not paired". In the
// Windows app, this PC's line reflects what the shell reports about the
// separate blocking service (ADR-0009) — never assumed.
import { layout, useHost, type HostInfo } from '../platform';
import { Icon, type IconName } from './Icon';

interface Row { icon: IconName; text: string; tone: 'ok' | 'none' }

const AGENT_TEXT: Record<HostInfo['agent'], [string, Row['tone']]> = {
  running: ['PC blocking on', 'ok'],
  stopped: ['PC blocking off', 'none'],
  not_installed: ['PC blocking off', 'none'],
  unknown: ['PC status unknown', 'none'],
};

export function deviceRows(host: HostInfo | null): Row[] {
  if (layout === 'desktop') {
    const [text, tone] = host ? AGENT_TEXT[host.agent] : (['Checking PC…', 'none'] as [string, Row['tone']]);
    return [
      { icon: 'laptop', text, tone },
      { icon: 'phone', text: 'Phone not paired', tone: 'none' },
    ];
  }
  return [
    { icon: 'laptop', text: 'PC not paired', tone: 'none' },
    { icon: 'phone', text: 'Phone not paired', tone: 'none' },
    { icon: 'browser', text: 'Browser timer only', tone: 'ok' },
  ];
}

export function DeviceStrip() {
  const host = useHost();
  return (
    <ul className="devices" aria-label="Devices">
      {deviceRows(host).map((d) => (
        <li key={d.text} className={`device device--${d.tone}`}>
          <Icon name={d.icon} size={20} />
          <span className={`dot dot--${d.tone}`} aria-hidden="true" />
          <span>{d.text}</span>
        </li>
      ))}
    </ul>
  );
}
