// Honest device readiness. Until pairing exists (milestone M5), the web app
// knows about no devices, so it says exactly that instead of inventing status.
import { Icon, type IconName } from './Icon';

interface Row { icon: IconName; name: string; status: string; tone: 'ok' | 'none' }

export const DEVICE_ROWS: Row[] = [
  { icon: 'browser', name: 'This browser', status: 'Timer only', tone: 'ok' },
  { icon: 'laptop', name: 'PC', status: 'Not paired', tone: 'none' },
  { icon: 'phone', name: 'Phone', status: 'Not paired', tone: 'none' },
];

export function DeviceStrip({ compact = false }: { compact?: boolean }) {
  return (
    <div className={`devices ${compact ? 'devices--compact' : ''}`}>
      <ul className="devices__list" aria-label="Devices">
        {DEVICE_ROWS.map((d) => (
          <li key={d.name} className={`device device--${d.tone}`}>
            <Icon name={d.icon} />
            <span className="device__name">{d.name}</span>
            <span className="device__status">
              <Icon name={d.tone === 'ok' ? 'check' : 'dash'} size={16} />
              {d.status}
            </span>
          </li>
        ))}
      </ul>
      {!compact && (
        <p className="devices__note">
          Blocking happens in the Lockd In apps for Windows and Android. This browser can time your
          session but can’t restrict other apps. <a href="#/devices">About devices</a>
        </p>
      )}
    </div>
  );
}
