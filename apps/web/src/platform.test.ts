import { describe, expect, it } from 'vitest';
import { parseHostCommand, parseHostMessage } from './platform';

describe('host bridge messages', () => {
  it('accepts a well-formed hello', () => {
    expect(parseHostMessage({ v: 1, type: 'host.hello', appVersion: '0.1.0', agent: 'not_installed' }))
      .toEqual({ platform: 'windows', appVersion: '0.1.0', agent: 'not_installed' });
  });
  it('rejects unknown versions, types, states and oversized fields', () => {
    expect(parseHostMessage({ v: 2, type: 'host.hello', appVersion: '1', agent: 'running' })).toBeNull();
    expect(parseHostMessage({ v: 1, type: 'host.other', appVersion: '1', agent: 'running' })).toBeNull();
    expect(parseHostMessage({ v: 1, type: 'host.hello', appVersion: '1', agent: 'protected' })).toBeNull();
    expect(parseHostMessage({ v: 1, type: 'host.hello', appVersion: 'x'.repeat(41), agent: 'running' })).toBeNull();
    expect(parseHostMessage('host.hello')).toBeNull();
    expect(parseHostMessage(null)).toBeNull();
  });
});

describe('host commands', () => {
  it('accepts only known commands', () => {
    expect(parseHostCommand({ v: 1, type: 'host.command', command: 'start_quick' })).toBe('start_quick');
    expect(parseHostCommand({ v: 1, type: 'host.command', command: 'end_session' })).toBeNull();
    expect(parseHostCommand({ v: 2, type: 'host.command', command: 'start_quick' })).toBeNull();
  });
});
