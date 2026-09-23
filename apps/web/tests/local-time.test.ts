import { describe, expect, it } from 'vitest';
import { localTimeToInstant } from '../lib/local-time';

describe('local time input', () => {
  it('rejects nonexistent spring-forward times', () => {
    expect(() => localTimeToInstant('2026-03-08T02:30', 'America/New_York')).toThrow('valid local time');
  });
  it('requires and respects an explicit fall-back offset', () => {
    expect(() => localTimeToInstant('2026-11-01T01:30', 'America/New_York')).toThrow('explicit UTC offset');
    expect(localTimeToInstant('2026-11-01T01:30', 'America/New_York', '-04:00')).toBe('2026-11-01T05:30:00.000Z');
    expect(localTimeToInstant('2026-11-01T01:30', 'America/New_York', '-05:00')).toBe('2026-11-01T06:30:00.000Z');
  });
  it('converts ordinary wall-clock times to UTC', () => {
    expect(localTimeToInstant('2026-10-01T12:00', 'Asia/Tokyo')).toBe('2026-10-01T03:00:00.000Z');
  });
});
