// Short, optional messages in the user's chosen tone. Never shaming, never
// claiming to know how someone feels. "quiet" shows none.
import type { Tone } from '../state/store';

type Moment = 'focusStart' | 'breakStart' | 'breakEnding' | 'done';
type Fill = { task?: string; time?: string };

const lines: Record<Exclude<Tone, 'quiet'>, Record<Moment, (f: Fill) => string>> = {
  calm: {
    focusStart: () => 'Start with one small step.',
    breakStart: ({ time }) => `Break until ${time}. Step away for a bit.`,
    breakEnding: () => 'Focus resumes in about a minute. One step at a time.',
    done: () => 'That’s the session. Take a breath before the next thing.',
  },
  direct: {
    focusStart: ({ task }) => (task ? `You planned ${task}. Go.` : 'Locked in. Go.'),
    breakStart: ({ time }) => `Break. Back at ${time}.`,
    breakEnding: ({ task }) => (task ? `Back to ${task} in a minute.` : 'Back to it in a minute.'),
    done: () => 'Done. Session recorded on this browser.',
  },
  playful: {
    focusStart: () => 'The feed will still be there after this.',
    breakStart: ({ time }) => `Break unlocked until ${time}. Stretch, snack, hydrate.`,
    breakEnding: () => 'One minute left. Wrap up the snack.',
    done: () => 'Session cleared. The lock says hi.',
  },
};

export function message(tone: Tone, moment: Moment, fill: Fill = {}): string | null {
  return tone === 'quiet' ? null : lines[tone][moment](fill);
}
