import { DateTime } from 'luxon';

/** Resolve wall-clock input without silently choosing a DST gap or overlap. */
export function localTimeToInstant(local: string, zone: string, offset?: string): string {
  const date = DateTime.fromISO(local, { zone });
  if (!date.isValid || date.toFormat("yyyy-MM-dd'T'HH:mm") !== local)
    throw new Error('Choose a valid local time. This time may fall in a clock-change gap.');
  const candidates = date.getPossibleOffsets();
  if (candidates.length > 1) {
    const chosen = candidates.find(candidate => candidate.toFormat('ZZ') === offset);
    if (!chosen) throw new Error('Choose an explicit UTC offset for the repeated local time.');
    return chosen.toUTC().toISO()!;
  }
  return date.toUTC().toISO()!;
}
