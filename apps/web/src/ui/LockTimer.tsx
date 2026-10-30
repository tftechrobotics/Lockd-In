// The padlock timer: the one memorable element. The shackle is open when
// nothing is restricted (ready, break, done) and closed while focusing.
// Time is real text over the SVG so it scales and is readable by assistive
// technology; the minute-level summary is what gets announced.
import { countdown, spokenRemaining } from './format';

// Geometry measured from the approved reference: the shackle is centered on the
// body, its centerline 42% of the body's width, stroke 11%. Open: the left leg
// stays seated and the right end stops just above the arc's midline (a
// released shackle). Closed: the shackle drops and both legs are seated.
const OPEN = 'M75.5 136 V94 A44.5 44.5 0 0 1 163.8 86.3';
const CLOSED = 'M75.5 136 V108 A44.5 44.5 0 0 1 164.5 108 V136';

interface Props {
  /** Milliseconds left in the current phase, or the planned length when idle. */
  remainingMs: number;
  caption: string;
  locked: boolean;
  size?: 'large' | 'small';
}

export function LockTimer({ remainingMs, caption, locked, size = 'large' }: Props) {
  return (
    <figure className={`lock lock--${size} ${locked ? 'is-locked' : 'is-open'}`} aria-label={`${caption}: ${spokenRemaining(remainingMs)}`}>
      <svg className="lock__art" viewBox="0 36 240 260" aria-hidden="true">
        <path className="lock__shackle" d={locked ? CLOSED : OPEN} />
        <rect className="lock__body" x="14" y="120" width="212" height="172" rx="26" />
      </svg>
      <div className="lock__face">
        {size === 'large' && (
          <svg className="lock__clock" viewBox="0 0 24 24" aria-hidden="true">
            <circle cx="12" cy="12" r="11" />
            <path d="M12 6.5V12.5H16.5" />
          </svg>
        )}
        <span className="lock__time" aria-hidden="true">{countdown(remainingMs)}</span>
        <figcaption className={size === 'large' ? 'lock__caption' : 'sr-only'}>{caption}</figcaption>
      </div>
    </figure>
  );
}
