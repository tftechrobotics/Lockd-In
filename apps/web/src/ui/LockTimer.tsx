// The padlock timer: the one memorable element. The shackle is open when
// nothing is restricted (ready, break, done) and closed while focusing.
// Time is real text over the SVG so it scales and is readable by assistive
// technology; the minute-level summary is what gets announced.
import { countdown, spokenRemaining } from './format';

// Closed: both legs in the body. Open: the left leg lifts clear; the right stays seated.
const CLOSED = 'M62 138 V96 a58 58 0 0 1 116 0 V138';
const OPEN = 'M62 90 V62 a58 58 0 0 1 116 0 V138';

interface Props {
  /** Milliseconds left in the current phase, or the planned length when idle. */
  remainingMs: number;
  caption: string;
  locked: boolean;
  size?: 'large' | 'compact';
}

export function LockTimer({ remainingMs, caption, locked, size = 'large' }: Props) {
  return (
    <figure className={`lock lock--${size} ${locked ? 'is-locked' : 'is-open'}`} aria-label={`${caption}: ${spokenRemaining(remainingMs)}`}>
      <svg className="lock__art" viewBox="0 0 240 300" aria-hidden="true">
        <path className="lock__shackle" d={locked ? CLOSED : OPEN} />
        <rect className="lock__body" x="14" y="120" width="212" height="172" rx="26" />
      </svg>
      <div className="lock__face">
        <svg className="lock__clock" viewBox="0 0 24 24" aria-hidden="true">
          <circle cx="12" cy="12" r="11" />
          <path d="M12 6.5V12.5H16.5" />
        </svg>
        <span className="lock__time" aria-hidden="true">{countdown(remainingMs)}</span>
        <figcaption className="lock__caption">{caption}</figcaption>
      </div>
    </figure>
  );
}
